package com.novelreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.net.SocketTimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TranslationServiceTest {

    private TranslationService translationService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(translationService, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();

        ReflectionTestUtils.setField(translationService, "provider", "auto");
        ReflectionTestUtils.setField(translationService, "openAiBaseUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(translationService, "openAiModel", "gpt-5-mini");
        ReflectionTestUtils.setField(translationService, "openAiMaxOutputTokens", 400);
        ReflectionTestUtils.setField(translationService, "googleWebBaseUrl", "https://translate.googleapis.com/translate_a/single");
        ReflectionTestUtils.setField(translationService, "googleWebProxyResolved", true);
        ReflectionTestUtils.setField(translationService, "resolvedGoogleWebProxy", null);
    }

    @Test
    void translateUsesOpenAiResponseWhenApiKeyConfigured() {
        ReflectionTestUtils.setField(translationService, "provider", "openai");
        ReflectionTestUtils.setField(translationService, "openAiApiKey", "test-key");
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"这是翻译结果。\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(translationService.translate("これはテストです。")).isEqualTo("这是翻译结果。");
        assertThat(translationService.getProvider()).isEqualTo("openai");
        server.verify();
    }

    @Test
    void translateUsesGoogleWebFallbackWhenOpenAiKeyMissing() {
        ReflectionTestUtils.setField(translationService, "provider", "auto");
        ReflectionTestUtils.setField(translationService, "openAiApiKey", "");
        server.expect(requestTo(
                URI.create(
                        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=ja&tl=zh-CN&dt=t&q=%E4%BB%8A%E6%97%A5%E3%81%AF%E8%89%AF%E3%81%84%E5%A4%A9%E6%B0%97%E3%81%A7%E3%81%99%E3%80%82")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[[[\"今天天气很好。\",\"今日は良い天気です。\",null,null,3]]]",
                        MediaType.APPLICATION_JSON));

        assertThat(translationService.translate("今日は良い天気です。")).isEqualTo("今天天气很好。");
        assertThat(translationService.getProvider()).isEqualTo("google-web");
        server.verify();
    }

    @Test
    void translateRetriesGoogleOverHttpWhenHttpsConnectionFails() {
        ReflectionTestUtils.setField(translationService, "provider", "auto");
        ReflectionTestUtils.setField(translationService, "openAiApiKey", "");
        server.expect(requestTo(
                URI.create(
                        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=ja&tl=zh-CN&dt=t&q=%E4%BB%8A%E6%97%A5%E3%81%AF%E8%89%AF%E3%81%84%E5%A4%A9%E6%B0%97%E3%81%A7%E3%81%99%E3%80%82")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new SocketTimeoutException("connect timeout")));
        server.expect(requestTo(
                URI.create(
                        "http://translate.googleapis.com/translate_a/single?client=gtx&sl=ja&tl=zh-CN&dt=t&q=%E4%BB%8A%E6%97%A5%E3%81%AF%E8%89%AF%E3%81%84%E5%A4%A9%E6%B0%97%E3%81%A7%E3%81%99%E3%80%82")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[[[\"今天天气很好。\",\"今日は良い天気です。\",null,null,3]]]",
                        MediaType.APPLICATION_JSON));

        assertThat(translationService.translate("今日は良い天気です。")).isEqualTo("今天天气很好。");
        server.verify();
    }

    @Test
    void translateFallsBackToReadablePlaceholderWhenProvidersFail() {
        ReflectionTestUtils.setField(translationService, "provider", "placeholder");

        assertThat(translationService.translate("今日は良い天気です。"))
                .isEqualTo("[机器翻译暂不可用] 今日は良い天気です。");
        assertThat(translationService.getProvider()).isEqualTo("placeholder");
    }
}
