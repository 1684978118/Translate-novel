package com.novelreader.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novelreader.exception.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationService.class);
    private static final String AUTO_PROVIDER = "auto";
    private static final String OPENAI_PROVIDER = "openai";
    private static final String GOOGLE_WEB_PROVIDER = "google-web";
    private static final String PLACEHOLDER_PROVIDER = "placeholder";
    private static final String SYSTEM_PROMPT =
            "Translate the given Japanese novel paragraph into natural Simplified Chinese. "
                    + "Preserve tone, line breaks, names, and implied context. Output translation only without notes.";
    private static final Pattern REGISTRY_VALUE_PATTERN = Pattern.compile("^\\s*([^\\s]+)\\s+REG_\\w+\\s+(.*)$");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(6);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Value("${translation.provider:auto}")
    private String provider;

    @Value("${translation.openai.api-key:}")
    private String openAiApiKey;

    @Value("${translation.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${translation.openai.model:gpt-5-mini}")
    private String openAiModel;

    @Value("${translation.openai.max-output-tokens:400}")
    private int openAiMaxOutputTokens;

    @Value("${translation.google-web.base-url:https://translate.googleapis.com/translate_a/single}")
    private String googleWebBaseUrl;

    @Value("${translation.google-web.proxy-host:}")
    private String googleWebProxyHost;

    @Value("${translation.google-web.proxy-port:0}")
    private int googleWebProxyPort;

    private final RestTemplateBuilder restTemplateBuilder;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile Proxy resolvedGoogleWebProxy;
    private volatile boolean googleWebProxyResolved;

    public TranslationService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        System.setProperty("java.net.useSystemProxies", "true");
        this.restTemplateBuilder = restTemplateBuilder;
        this.restTemplate = buildRestTemplate(null);
        this.objectMapper = objectMapper;
    }

    public String translate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String resolvedProvider = resolveProvider();
        if (OPENAI_PROVIDER.equals(resolvedProvider)) {
            try {
                return requestOpenAiTranslation(SYSTEM_PROMPT, text);
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                LOGGER.warn("OpenAI translation failed, falling back to Google web translation", exception);
                return requestGoogleOrPlaceholder(text);
            }
        }

        if (GOOGLE_WEB_PROVIDER.equals(resolvedProvider)) {
            return requestGoogleOrPlaceholder(text);
        }

        return fallbackTranslation(text);
    }

    public String getProvider() {
        return resolveProvider();
    }

    private String resolveProvider() {
        String normalizedProvider = provider == null ? AUTO_PROVIDER : provider.trim().toLowerCase(Locale.ROOT);
        if (PLACEHOLDER_PROVIDER.equals(normalizedProvider)) {
            return PLACEHOLDER_PROVIDER;
        }
        if (OPENAI_PROVIDER.equals(normalizedProvider) && hasOpenAiApiKey()) {
            return OPENAI_PROVIDER;
        }
        if (GOOGLE_WEB_PROVIDER.equals(normalizedProvider)) {
            return GOOGLE_WEB_PROVIDER;
        }
        if (AUTO_PROVIDER.equals(normalizedProvider) || OPENAI_PROVIDER.equals(normalizedProvider)) {
            return hasOpenAiApiKey() ? OPENAI_PROVIDER : GOOGLE_WEB_PROVIDER;
        }
        return PLACEHOLDER_PROVIDER;
    }

    /**
     * 是否配置了 OpenAI 兼容的大模型（用于点词释义等对话式能力）。
     */
    public boolean isLlmConfigured() {
        return hasOpenAiApiKey();
    }

    /**
     * 调用 OpenAI 兼容接口进行单轮对话；未配置时抛出异常，由调用方决定降级策略。
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!isLlmConfigured()) {
            throw new BusinessException("LLM provider is not configured");
        }
        return requestOpenAiTranslation(systemPrompt, userPrompt);
    }

    private boolean hasOpenAiApiKey() {
        return openAiApiKey != null && !openAiApiKey.trim().isEmpty();
    }

    private RestTemplate buildRestTemplate(Proxy proxy) {
        if (proxy == null) {
            return restTemplateBuilder
                    .setConnectTimeout(CONNECT_TIMEOUT)
                    .setReadTimeout(READ_TIMEOUT)
                    .build();
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(proxy);
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return restTemplateBuilder
                .requestFactory(() -> factory)
                .build();
    }

    private String requestGoogleOrPlaceholder(String text) {
        Proxy proxy = resolveGoogleWebProxy();
        String httpFallbackUrl = toHttpFallbackUrl();
        Exception failure = null;

        if (proxy != null) {
            try {
                return requestGoogleWebTranslation(text, googleWebBaseUrl, proxy);
            } catch (Exception exception) {
                failure = exception;
                LOGGER.debug("Google HTTPS translation via proxy {} failed", describeProxy(proxy), exception);
            }

            if (httpFallbackUrl != null && failure instanceof ResourceAccessException) {
                try {
                    return requestGoogleWebTranslation(text, httpFallbackUrl, proxy);
                } catch (Exception exception) {
                    failure = exception;
                    LOGGER.debug("Google HTTP translation via proxy {} failed", describeProxy(proxy), exception);
                }
            }
        }

        try {
            return requestGoogleWebTranslation(text, googleWebBaseUrl, null);
        } catch (Exception exception) {
            failure = exception;
            LOGGER.debug("Google direct HTTPS translation failed", exception);
        }

        if (httpFallbackUrl != null && failure instanceof ResourceAccessException) {
            try {
                return requestGoogleWebTranslation(text, httpFallbackUrl, null);
            } catch (Exception exception) {
                failure = exception;
                LOGGER.debug("Google direct HTTP translation failed", exception);
            }
        }

        if (failure != null) {
            LOGGER.warn("Google web translation failed, using placeholder translation: {}", failure.getMessage());
        }
        return fallbackTranslation(text);
    }

    private Proxy resolveGoogleWebProxy() {
        if (googleWebProxyResolved) {
            return resolvedGoogleWebProxy;
        }

        synchronized (this) {
            if (googleWebProxyResolved) {
                return resolvedGoogleWebProxy;
            }

            resolvedGoogleWebProxy = configuredGoogleWebProxy();
            googleWebProxyResolved = true;
            return resolvedGoogleWebProxy;
        }
    }

    private Proxy configuredGoogleWebProxy() {
        if (googleWebProxyHost != null && !googleWebProxyHost.trim().isEmpty() && googleWebProxyPort > 0) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(googleWebProxyHost.trim(), googleWebProxyPort));
        }

        Proxy selectorProxy = resolveProxyFromSelector();
        if (selectorProxy != null) {
            return selectorProxy;
        }

        return resolveProxyFromWindowsRegistry();
    }

    private Proxy resolveProxyFromSelector() {
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(URI.create(googleWebBaseUrl));
            if (proxies == null) {
                return null;
            }
            for (Proxy proxy : proxies) {
                if (proxy != null && !Proxy.NO_PROXY.equals(proxy) && proxy.address() instanceof InetSocketAddress) {
                    return proxy;
                }
            }
        } catch (Exception exception) {
            LOGGER.debug("Failed to resolve system proxy via ProxySelector", exception);
        }
        return null;
    }

    private Proxy resolveProxyFromWindowsRegistry() {
        if (!isWindows()) {
            return null;
        }

        String proxyEnabled = readWindowsRegistryValue("ProxyEnable");
        if (!"1".equals(proxyEnabled) && !"0x1".equalsIgnoreCase(proxyEnabled)) {
            return null;
        }

        String proxyServer = readWindowsRegistryValue("ProxyServer");
        if (proxyServer == null || proxyServer.trim().isEmpty()) {
            return null;
        }

        String selectedProxy = selectProxyAddress(proxyServer.trim(), URI.create(googleWebBaseUrl).getScheme());
        if (selectedProxy == null || selectedProxy.trim().isEmpty()) {
            return null;
        }

        String normalized = selectedProxy.trim();
        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator > -1) {
            normalized = normalized.substring(schemeSeparator + 3);
        }

        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= normalized.length() - 1) {
            return null;
        }

        try {
            String host = normalized.substring(0, separatorIndex).trim();
            int port = Integer.parseInt(normalized.substring(separatorIndex + 1).trim());
            if (host.isEmpty() || port <= 0) {
                return null;
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        } catch (NumberFormatException exception) {
            LOGGER.debug("Failed to parse proxy port from Windows registry value {}", normalized, exception);
            return null;
        }
    }

    private String selectProxyAddress(String proxyServer, String scheme) {
        if (!proxyServer.contains("=")) {
            return proxyServer;
        }

        String defaultProxy = null;
        for (String segment : proxyServer.split(";")) {
            String[] pair = segment.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim().toLowerCase(Locale.ROOT);
            String value = pair[1].trim();
            if (key.equalsIgnoreCase(scheme) || ("https".equalsIgnoreCase(scheme) && "http".equals(key))) {
                return value;
            }
            if ("http".equals(key)) {
                defaultProxy = value;
            }
        }
        return defaultProxy;
    }

    private String readWindowsRegistryValue(String valueName) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "reg",
                    "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v",
                    valueName)
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = REGISTRY_VALUE_PATTERN.matcher(line);
                if (matcher.matches() && valueName.equalsIgnoreCase(matcher.group(1))) {
                    return matcher.group(2).trim();
                }
            }
            process.waitFor();
        } catch (IOException exception) {
            LOGGER.debug("Failed to query Windows proxy registry value {}", valueName, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Interrupted while querying Windows proxy registry value {}", valueName, exception);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String describeProxy(Proxy proxy) {
        if (proxy == null || !(proxy.address() instanceof InetSocketAddress)) {
            return "unknown";
        }
        InetSocketAddress address = (InetSocketAddress) proxy.address();
        return address.getHostString() + ":" + address.getPort();
    }

    private String requestOpenAiTranslation(String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openAiApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<JsonNode> requestEntity = new HttpEntity<JsonNode>(
                buildChatCompletionRequest(systemPrompt, userPrompt), headers);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                openAiBaseUrl + "/chat/completions",
                HttpMethod.POST,
                requestEntity,
                JsonNode.class);

        JsonNode body = response.getBody();
        if (body == null) {
            throw new BusinessException("Translation provider returned an empty response");
        }

        String outputText = body.path("choices").path(0).path("message").path("content").asText("").trim();
        if (outputText.isEmpty()) {
            throw new BusinessException("Translation provider returned no translation text");
        }
        return outputText;
    }

    private JsonNode buildChatCompletionRequest(String systemPrompt, String userPrompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", openAiModel);
        root.put("max_tokens", openAiMaxOutputTokens);

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        return root;
    }

    private String requestGoogleWebTranslation(String text, String baseUrl, Proxy proxy) {
        RestTemplate translationClient = proxy == null ? restTemplate : buildRestTemplate(proxy);
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("client", "gtx")
                .queryParam("sl", "ja")
                .queryParam("tl", "zh-CN")
                .queryParam("dt", "t")
                .queryParam("q", text)
                .build()
                .encode()
                .toUri();

        ResponseEntity<JsonNode> response = translationClient.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class);
        JsonNode body = response.getBody();
        if (body == null || !body.isArray()) {
            throw new BusinessException("Google translation returned an invalid response");
        }

        JsonNode sentences = body.path(0);
        if (!sentences.isArray()) {
            throw new BusinessException("Google translation returned no sentence list");
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode sentence : sentences) {
            String translated = sentence.path(0).asText("").trim();
            if (!translated.isEmpty()) {
                builder.append(translated);
            }
        }

        String translation = builder.toString().trim();
        if (translation.isEmpty()) {
            throw new BusinessException("Google translation returned no translation text");
        }
        return translation;
    }

    private String toHttpFallbackUrl() {
        if (googleWebBaseUrl == null) {
            return null;
        }
        String normalized = googleWebBaseUrl.trim();
        if (!normalized.startsWith("https://translate.googleapis.com/")) {
            return null;
        }
        return "http://" + normalized.substring("https://".length());
    }

    private String fallbackTranslation(String text) {
        return "[机器翻译暂不可用] " + text;
    }
}
