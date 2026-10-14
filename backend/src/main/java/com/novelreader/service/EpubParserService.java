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

            EpubChapterContent chapter = parseChapter(chapterBytes, fallbackTitle(manifestItem.href, fallbackIndex));
            if (!chapter.getParagraphs().isEmpty()) {
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

            Map<String, ManifestItem> manifest = new LinkedHashMap<String, ManifestItem>();
            NodeList items = document.getElementsByTagNameNS("*", "item");
            for (int index = 0; index < items.getLength(); index++) {
                org.w3c.dom.Node item = items.item(index);
                org.w3c.dom.Node idNode = item.getAttributes().getNamedItem("id");
                org.w3c.dom.Node hrefNode = item.getAttributes().getNamedItem("href");
                org.w3c.dom.Node mediaTypeNode = item.getAttributes().getNamedItem("media-type");
                if (idNode == null || hrefNode == null) {
                    continue;
                }
                manifest.put(idNode.getNodeValue(), new ManifestItem(
                        idNode.getNodeValue(),
                        hrefNode.getNodeValue(),
                        mediaTypeNode == null ? "" : mediaTypeNode.getNodeValue()));
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

            return new PackageMetadata(title, author, manifest, spineItemIds, opfPath);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Failed to parse EPUB package");
        }
    }

    private EpubChapterContent parseChapter(byte[] chapterBytes, String fallbackTitle) {
        try {
            Document document = Jsoup.parse(new ByteArrayInputStream(chapterBytes), null, "", Parser.xmlParser());
            document.outputSettings().escapeMode(Entities.EscapeMode.xhtml);

            Element body = document.selectFirst("body");
            if (body == null) {
                return new EpubChapterContent(fallbackTitle, new ArrayList<String>());
            }

            body.select("script, style, nav, svg, img, iframe").remove();
            String title = firstNonBlank(
                    textOf(body.selectFirst("h1, h2, h3")),
                    textOf(document.selectFirst("head > title")),
                    fallbackTitle);

            List<String> paragraphs = collectParagraphs(body);
            if (paragraphs.isEmpty()) {
                String wholeText = normalizeText(body.text());
                if (hasText(wholeText)) {
                    paragraphs.add(wholeText);
                }
            }

            return new EpubChapterContent(title, paragraphs);
        } catch (IOException exception) {
            throw new BusinessException("Failed to read EPUB chapter");
        }
    }

    private List<String> collectParagraphs(Element body) {
        List<String> paragraphs = new ArrayList<String>();

        List<Element> preferredParagraphs = body.select("p");
        if (!preferredParagraphs.isEmpty()) {
            for (Element paragraph : preferredParagraphs) {
                addParagraph(paragraphs, paragraph.text());
            }
            return paragraphs;
        }

        for (Node node : body.childNodes()) {
            collectFallbackParagraphs(node, paragraphs);
        }
        return paragraphs;
    }

    private void collectFallbackParagraphs(Node node, List<String> paragraphs) {
        if (node instanceof TextNode) {
            addParagraph(paragraphs, ((TextNode) node).text());
            return;
        }

        if (!(node instanceof Element)) {
            return;
        }

        Element element = (Element) node;
        String tag = element.normalName();

        if (Arrays.asList("script", "style", "nav", "svg", "img", "iframe").contains(tag)) {
            return;
        }

        if (Arrays.asList("h1", "h2", "h3", "h4", "h5", "h6").contains(tag)) {
            return;
        }

        if (isFallbackBlock(tag) && !hasFallbackBlockChildren(element)) {
            addParagraph(paragraphs, element.text());
            return;
        }

        for (Node child : element.childNodes()) {
            collectFallbackParagraphs(child, paragraphs);
        }
    }

    private boolean hasFallbackBlockChildren(Element element) {
        for (Element child : element.children()) {
            if (isFallbackBlock(child.normalName()) || "p".equals(child.normalName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isFallbackBlock(String tag) {
        return Arrays.asList("div", "li", "blockquote", "pre", "section", "article")
                .contains(tag);
    }

    private void addParagraph(List<String> paragraphs, String candidate) {
        String normalized = normalizeText(candidate);
        if (!hasText(normalized)) {
            return;
        }
        if (paragraphs.isEmpty() || !normalized.equals(paragraphs.get(paragraphs.size() - 1))) {
            paragraphs.add(normalized);
        }
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
        return element == null ? "" : normalizeText(element.text());
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
        private final List<String> spineItemIds;
        @SuppressWarnings("unused")
        private final String opfPath;

        private PackageMetadata(String title, String author, Map<String, ManifestItem> manifest,
                List<String> spineItemIds, String opfPath) {
            this.title = title;
            this.author = author;
            this.manifest = manifest;
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
        private final List<String> paragraphs;

        public EpubChapterContent(String title, List<String> paragraphs) {
            this.title = title;
            this.paragraphs = paragraphs;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getParagraphs() {
            return paragraphs;
        }
    }
}
