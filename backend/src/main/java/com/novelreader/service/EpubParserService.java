package com.novelreader.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.novelreader.exception.BusinessException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;
import org.w3c.dom.NodeList;

@Service
public class EpubParserService {

    public EpubBookContent parse(String originalFilename, byte[] fileBytes) {
        Map<String, byte[]> entries = unzip(fileBytes);
        String opfPath = readRootPackagePath(entries.get("META-INF/container.xml"));
        byte[] opfBytes = entries.get(opfPath);
        if (opfBytes == null) {
            throw new BusinessException("EPUB package file not found");
        }

        PackageMetadata metadata = parsePackage(opfBytes, opfPath);
        List<EpubChapterContent> chapters = new ArrayList<EpubChapterContent>();
        String baseDir = parentDirectory(opfPath);
        int fallbackIndex = 1;

        for (String itemId : metadata.spineItemIds) {
            ManifestItem manifestItem = metadata.manifest.get(itemId);
            if (manifestItem == null || !isReadableDocument(manifestItem.mediaType)) {
                continue;
            }

            String resourcePath = resolveEntryPath(baseDir, manifestItem.href);
            byte[] chapterBytes = entries.get(resourcePath);
            if (chapterBytes == null) {
                continue;
            }

            EpubChapterContent chapter = parseChapter(
                    chapterBytes,
                    resourcePath,
                    metadata.manifestByPath,
                    entries,
                    fallbackTitle(manifestItem.href, fallbackIndex));
            if (!chapter.getBlocks().isEmpty()) {
                chapters.add(chapter);
                fallbackIndex++;
            }
        }

        if (chapters.isEmpty()) {
            throw new BusinessException("EPUB did not contain readable XHTML chapters");
        }

        String title = hasText(metadata.title) ? metadata.title : defaultTitle(originalFilename);
        String author = hasText(metadata.author) ? metadata.author : "Unknown";
        return new EpubBookContent(title, author, chapters);
    }

    private Map<String, byte[]> unzip(byte[] fileBytes) {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                entries.put(entry.getName(), outputStream.toByteArray());
            }
            return entries;
        } catch (IOException exception) {
            throw new BusinessException("Failed to unzip EPUB file");
        }
    }

    private String readRootPackagePath(byte[] containerBytes) {
        if (containerBytes == null) {
            throw new BusinessException("EPUB container.xml is missing");
        }

        try {
            org.w3c.dom.Document document = newDocument(containerBytes);
            NodeList nodeList = document.getElementsByTagNameNS("*", "rootfile");
            if (nodeList.getLength() == 0) {
                throw new BusinessException("EPUB container.xml is invalid");
            }
            return nodeList.item(0).getAttributes().getNamedItem("full-path").getNodeValue();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Failed to parse EPUB container.xml");
        }
    }

    private PackageMetadata parsePackage(byte[] opfBytes, String opfPath) {
        try {
            org.w3c.dom.Document document = newDocument(opfBytes);

            String title = readFirstText(document, "title");
            String author = readFirstText(document, "creator");
            String baseDir = parentDirectory(opfPath);

            Map<String, ManifestItem> manifest = new LinkedHashMap<String, ManifestItem>();
            Map<String, ManifestItem> manifestByPath = new LinkedHashMap<String, ManifestItem>();
            NodeList items = document.getElementsByTagNameNS("*", "item");
            for (int index = 0; index < items.getLength(); index++) {
                org.w3c.dom.Node item = items.item(index);
                org.w3c.dom.Node idNode = item.getAttributes().getNamedItem("id");
                org.w3c.dom.Node hrefNode = item.getAttributes().getNamedItem("href");
                org.w3c.dom.Node mediaTypeNode = item.getAttributes().getNamedItem("media-type");
                if (idNode == null || hrefNode == null) {
                    continue;
                }
                ManifestItem manifestItem = new ManifestItem(
                        idNode.getNodeValue(),
                        hrefNode.getNodeValue(),
                        mediaTypeNode == null ? "" : mediaTypeNode.getNodeValue());
                manifest.put(idNode.getNodeValue(), manifestItem);
                manifestByPath.put(resolveEntryPath(baseDir, manifestItem.href), manifestItem);
            }

            List<String> spineItemIds = new ArrayList<String>();
            NodeList itemRefs = document.getElementsByTagNameNS("*", "itemref");
            for (int index = 0; index < itemRefs.getLength(); index++) {
                org.w3c.dom.Node itemRef = itemRefs.item(index);
                org.w3c.dom.Node idRef = itemRef.getAttributes().getNamedItem("idref");
                if (idRef != null) {
                    spineItemIds.add(idRef.getNodeValue());
                }
            }

            if (spineItemIds.isEmpty()) {
                throw new BusinessException("EPUB spine is empty");
            }

            return new PackageMetadata(title, author, manifest, manifestByPath, spineItemIds, opfPath);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Failed to parse EPUB package");
        }
    }

    private EpubChapterContent parseChapter(byte[] chapterBytes, String chapterPath,
            Map<String, ManifestItem> manifestByPath, Map<String, byte[]> entries, String fallbackTitle) {
        try {
            Document document = Jsoup.parse(new ByteArrayInputStream(chapterBytes), null, "", Parser.xmlParser());
            document.outputSettings().escapeMode(Entities.EscapeMode.xhtml);

            Element body = document.selectFirst("body");
            if (body == null) {
                return new EpubChapterContent(fallbackTitle, new ArrayList<EpubContentBlock>());
            }

            body.select("script, style, nav, iframe").remove();
            String title = firstNonBlank(
                    textOf(body.selectFirst("h1, h2, h3")),
                    textOf(document.selectFirst("head > title")),
                    fallbackTitle);

            List<EpubContentBlock> blocks = collectBlocks(body, parentDirectory(chapterPath), manifestByPath, entries);
            if (blocks.isEmpty()) {
                String wholeText = plainText(body);
                if (hasText(wholeText)) {
                    blocks.add(EpubContentBlock.text(wholeText));
                }
            }

            return new EpubChapterContent(title, blocks);
        } catch (IOException exception) {
            throw new BusinessException("Failed to read EPUB chapter");
        }
    }

    private List<EpubContentBlock> collectBlocks(Element body, String chapterBaseDir,
            Map<String, ManifestItem> manifestByPath, Map<String, byte[]> entries) {
        List<EpubContentBlock> blocks = new ArrayList<EpubContentBlock>();
        StringBuilder textBuffer = new StringBuilder();

        for (Node node : body.childNodes()) {
            collectBlocks(node, blocks, textBuffer, chapterBaseDir, manifestByPath, entries);
        }

        flushTextBlock(blocks, textBuffer);
        return blocks;
    }

    private void collectBlocks(Node node, List<EpubContentBlock> blocks, StringBuilder textBuffer, String chapterBaseDir,
            Map<String, ManifestItem> manifestByPath, Map<String, byte[]> entries) {
        if (node instanceof TextNode) {
            textBuffer.append(((TextNode) node).text());
            return;
        }

        if (!(node instanceof Element)) {
            return;
        }

        Element element = (Element) node;
        String tag = element.normalName();

        if (Arrays.asList("script", "style", "nav", "iframe").contains(tag)) {
            return;
        }

        // ruby 注音（rt/rp）不属于正文，跳过以避免读音混入原文（如「静<rt>せい</rt>」变成「静せい」）。
        if ("rt".equals(tag) || "rp".equals(tag)) {
            return;
        }

        if (Arrays.asList("h1", "h2", "h3", "h4", "h5", "h6").contains(tag)) {
            flushTextBlock(blocks, textBuffer);
            return;
        }

        if ("br".equals(tag)) {
            flushTextBlock(blocks, textBuffer);
            return;
        }

        if ("img".equals(tag)) {
            flushTextBlock(blocks, textBuffer);
            addImageBlock(blocks, element.attr("src"), firstNonBlank(element.attr("alt"), element.attr("title")),
                    chapterBaseDir, manifestByPath, entries);
            return;
        }

        if ("svg".equals(tag)) {
            flushTextBlock(blocks, textBuffer);
            Element image = findSvgImageElement(element);
            if (image != null) {
                addImageBlock(
                        blocks,
                        firstNonBlank(image.attr("href"), image.attr("xlink:href"), image.attr("src")),
                        firstNonBlank(element.attr("aria-label"), element.attr("title")),
                        chapterBaseDir,
                        manifestByPath,
                        entries);
            }
            return;
        }

        boolean blockBoundary = isBlockBoundary(tag);
        if (blockBoundary) {
            flushTextBlock(blocks, textBuffer);
        }

        for (Node child : element.childNodes()) {
            collectBlocks(child, blocks, textBuffer, chapterBaseDir, manifestByPath, entries);
        }

        if (blockBoundary) {
            flushTextBlock(blocks, textBuffer);
        }
    }

    private Element findSvgImageElement(Element svgElement) {
        for (Element child : svgElement.getAllElements()) {
            if (!"image".equals(child.normalName())) {
                continue;
            }
            if (hasText(child.attr("href")) || hasText(child.attr("xlink:href")) || hasText(child.attr("src"))) {
                return child;
            }
        }
        return null;
    }

    private boolean isBlockBoundary(String tag) {
        return Arrays.asList("p", "div", "li", "blockquote", "pre", "section", "article", "figure", "figcaption")
                .contains(tag);
    }

    private void flushTextBlock(List<EpubContentBlock> blocks, StringBuilder textBuffer) {
        if (textBuffer.length() == 0) {
            return;
        }
        addTextBlock(blocks, textBuffer.toString());
        textBuffer.setLength(0);
    }

    private void addTextBlock(List<EpubContentBlock> blocks, String candidate) {
        String normalized = normalizeText(candidate);
        if (!hasText(normalized)) {
            return;
        }
        if (!blocks.isEmpty()) {
            EpubContentBlock lastBlock = blocks.get(blocks.size() - 1);
            if ("TEXT".equals(lastBlock.getType()) && normalized.equals(lastBlock.getText())) {
                return;
            }
        }
        blocks.add(EpubContentBlock.text(normalized));
    }

    private void addImageBlock(List<EpubContentBlock> blocks, String source, String description,
            String chapterBaseDir, Map<String, ManifestItem> manifestByPath, Map<String, byte[]> entries) {
        String normalizedSource = normalizeResourceReference(source);
        if (!hasText(normalizedSource) || normalizedSource.startsWith("data:")) {
            return;
        }

        String resourcePath = resolveEntryPath(chapterBaseDir, normalizedSource);
        byte[] imageBytes = entries.get(resourcePath);
        if (imageBytes == null || imageBytes.length == 0) {
            return;
        }

        ManifestItem manifestItem = manifestByPath.get(resourcePath);
        String mediaType = manifestItem == null ? inferMediaType(resourcePath) : manifestItem.mediaType;
        if (!isSupportedImage(mediaType)) {
            return;
        }

        blocks.add(EpubContentBlock.image(
                normalizeText(description),
                imageBytes,
                mediaType,
                filenameOf(resourcePath)));
    }

    private String normalizeResourceReference(String source) {
        if (source == null) {
            return "";
        }
        String normalized = source.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        return normalized.trim();
    }

    private boolean isSupportedImage(String mediaType) {
        return mediaType != null && mediaType.toLowerCase().startsWith("image/");
    }

    private String inferMediaType(String resourcePath) {
        String path = resourcePath == null ? "" : resourcePath.toLowerCase();
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (path.endsWith(".gif")) {
            return "image/gif";
        }
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "";
    }

    private String filenameOf(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return "image";
        }
        int slashIndex = resourcePath.lastIndexOf('/');
        return slashIndex >= 0 ? resourcePath.substring(slashIndex + 1) : resourcePath;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private org.w3c.dom.Document newDocument(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private String readFirstText(org.w3c.dom.Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent() == null ? "" : nodes.item(0).getTextContent().trim();
    }

    private boolean isReadableDocument(String mediaType) {
        return "application/xhtml+xml".equalsIgnoreCase(mediaType) || "text/html".equalsIgnoreCase(mediaType);
    }

    private String resolveEntryPath(String baseDir, String href) {
        String resolved = Paths.get(baseDir == null || baseDir.isEmpty() ? "." : baseDir)
                .resolve(href)
                .normalize()
                .toString()
                .replace('\\', '/');
        return resolved.startsWith("./") ? resolved.substring(2) : resolved;
    }

    private String parentDirectory(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }

    private String defaultTitle(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String fallbackTitle(String href, int index) {
        String name = href;
        int slashIndex = name.lastIndexOf('/');
        if (slashIndex >= 0) {
            name = name.substring(slashIndex + 1);
        }
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        name = name.replace('_', ' ').replace('-', ' ').trim();
        return hasText(name) ? name : "Chapter " + index;
    }

    private String textOf(Element element) {
        return plainText(element);
    }

    /**
     * 提取可见正文；EPUB 中的 ruby 注音（rt/rp）不属于正文，需要剔除，
     * 否则会把读音拼进原文（如「静<rt>せい</rt>」变成「静せい」）。
     */
    private String plainText(Element element) {
        if (element == null) {
            return "";
        }
        Element clone = element.clone();
        clone.select("rt, rp").remove();
        return normalizeText(clone.text());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class PackageMetadata {
        private final String title;
        private final String author;
        private final Map<String, ManifestItem> manifest;
        private final Map<String, ManifestItem> manifestByPath;
        private final List<String> spineItemIds;
        @SuppressWarnings("unused")
        private final String opfPath;

        private PackageMetadata(String title, String author, Map<String, ManifestItem> manifest,
                Map<String, ManifestItem> manifestByPath, List<String> spineItemIds, String opfPath) {
            this.title = title;
            this.author = author;
            this.manifest = manifest;
            this.manifestByPath = manifestByPath;
            this.spineItemIds = spineItemIds;
            this.opfPath = opfPath;
        }
    }

    private static class ManifestItem {
        private final String id;
        private final String href;
        private final String mediaType;

        private ManifestItem(String id, String href, String mediaType) {
            this.id = id;
            this.href = href;
            this.mediaType = mediaType;
        }
    }

    public static class EpubBookContent {
        private final String title;
        private final String author;
        private final List<EpubChapterContent> chapters;

        public EpubBookContent(String title, String author, List<EpubChapterContent> chapters) {
            this.title = title;
            this.author = author;
            this.chapters = chapters;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public List<EpubChapterContent> getChapters() {
            return chapters;
        }
    }

    public static class EpubChapterContent {
        private final String title;
        private final List<EpubContentBlock> blocks;

        public EpubChapterContent(String title, List<EpubContentBlock> blocks) {
            this.title = title;
            this.blocks = blocks;
        }

        public String getTitle() {
            return title;
        }

        public List<EpubContentBlock> getBlocks() {
            return blocks;
        }

        public List<String> getParagraphs() {
            List<String> paragraphs = new ArrayList<String>();
            for (EpubContentBlock block : blocks) {
                if ("TEXT".equals(block.getType()) && block.getText() != null && !block.getText().trim().isEmpty()) {
                    paragraphs.add(block.getText());
                }
            }
            return paragraphs;
        }
    }

    public static class EpubContentBlock {
        private final String type;
        private final String text;
        private final String description;
        private final byte[] imageBytes;
        private final String imageMediaType;
        private final String imageFilename;

        private EpubContentBlock(String type, String text, String description, byte[] imageBytes, String imageMediaType,
                String imageFilename) {
            this.type = type;
            this.text = text;
            this.description = description;
            this.imageBytes = imageBytes;
            this.imageMediaType = imageMediaType;
            this.imageFilename = imageFilename;
        }

        public static EpubContentBlock text(String text) {
            return new EpubContentBlock("TEXT", text, "", null, "", "");
        }

        public static EpubContentBlock image(String description, byte[] imageBytes, String imageMediaType,
                String imageFilename) {
            return new EpubContentBlock("IMAGE", "", description, imageBytes, imageMediaType, imageFilename);
        }

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public String getDescription() {
            return description;
        }

        public byte[] getImageBytes() {
            return imageBytes;
        }

        public String getImageMediaType() {
            return imageMediaType;
        }

        public String getImageFilename() {
            return imageFilename;
        }
    }
}
