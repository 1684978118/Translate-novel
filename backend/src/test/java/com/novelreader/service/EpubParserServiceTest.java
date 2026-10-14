package com.novelreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class EpubParserServiceTest {

    private final EpubParserService epubParserService = new EpubParserService();

    @Test
    void parseExtractsMetadataChaptersAndParagraphs() throws Exception {
        EpubParserService.EpubBookContent book = epubParserService.parse("sample.epub", createSampleEpub());

        assertThat(book.getTitle()).isEqualTo("Sample EPUB");
        assertThat(book.getAuthor()).isEqualTo("Author Name");
        assertThat(book.getChapters()).hasSize(2);
        assertThat(book.getChapters().get(0).getTitle()).isEqualTo("第一章");
        assertThat(book.getChapters().get(0).getParagraphs())
                .containsExactly("これは最初の段落です。", "続きの段落です。");
        assertThat(book.getChapters().get(1).getTitle()).isEqualTo("第二章");
        assertThat(book.getChapters().get(1).getParagraphs())
                .containsExactly("二つ目の章です。", "別の段落もあります。");
    }

    @Test
    void parseStripsRubyReadingsFromParagraphText() throws Exception {
        EpubParserService.EpubBookContent book = epubParserService.parse("ruby.epub", createRubyEpub());

        assertThat(book.getChapters()).hasSize(1);
        assertThat(book.getChapters().get(0).getParagraphs())
                .containsExactly("静謐な空気で法要の後に、大人達は畳の部屋で会食を始める。");
    }

    private byte[] createRubyEpub() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

        addEntry(zipOutputStream, "mimetype", "application/epub+zip");
        addEntry(zipOutputStream, "META-INF/container.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                        + "<rootfiles>"
                        + "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                        + "</rootfiles>"
                        + "</container>");
        addEntry(zipOutputStream, "OEBPS/content.opf",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<package version=\"2.0\" xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\">"
                        + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                        + "<dc:title>Ruby EPUB</dc:title>"
                        + "</metadata>"
                        + "<manifest>"
                        + "<item id=\"chapter1\" href=\"text/chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
                        + "</manifest>"
                        + "<spine>"
                        + "<itemref idref=\"chapter1\"/>"
                        + "</spine>"
                        + "</package>");
        addEntry(zipOutputStream, "OEBPS/text/chapter1.xhtml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                        + "<head><title>ルビ章</title></head>"
                        + "<body>"
                        + "<h1>ルビ章</h1>"
                        + "<p><ruby>静<rt>せい</rt>謐<rt>ひつ</rt></ruby>な空気で法要の後に、"
                        + "<ruby>大人達<rt>おとなたち</rt></ruby>は<ruby>畳<rt>たたみ</rt></ruby>の部屋で"
                        + "<ruby>会食<rt>かいしょく</rt></ruby>を始める。</p>"
                        + "</body>"
                        + "</html>");

        zipOutputStream.close();
        return outputStream.toByteArray();
    }

    @Test
    void parseKeepsIllustrationsInReadingOrder() throws Exception {
        EpubParserService.EpubBookContent book = epubParserService.parse("illustration.epub", createIllustratedEpub());

        assertThat(book.getChapters()).hasSize(1);
        assertThat(book.getChapters().get(0).getBlocks()).hasSize(3);
        assertThat(book.getChapters().get(0).getBlocks().get(0).getType()).isEqualTo("TEXT");
        assertThat(book.getChapters().get(0).getBlocks().get(0).getText()).isEqualTo("夜空を見上げた。");
        assertThat(book.getChapters().get(0).getBlocks().get(1).getType()).isEqualTo("IMAGE");
        assertThat(book.getChapters().get(0).getBlocks().get(1).getDescription()).isEqualTo("星空の插画");
        assertThat(book.getChapters().get(0).getBlocks().get(1).getImageBytes()).isNotEmpty();
        assertThat(book.getChapters().get(0).getBlocks().get(2).getText()).isEqualTo("彼女は黙っていた。");
    }

    private byte[] createSampleEpub() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

        addEntry(zipOutputStream, "mimetype", "application/epub+zip");
        addEntry(zipOutputStream, "META-INF/container.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                        + "<rootfiles>"
                        + "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                        + "</rootfiles>"
                        + "</container>");
        addEntry(zipOutputStream, "OEBPS/content.opf",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<package version=\"2.0\" xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\">"
                        + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                        + "<dc:title>Sample EPUB</dc:title>"
                        + "<dc:creator>Author Name</dc:creator>"
                        + "</metadata>"
                        + "<manifest>"
                        + "<item id=\"chapter1\" href=\"text/chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
                        + "<item id=\"chapter2\" href=\"text/chapter2.xhtml\" media-type=\"application/xhtml+xml\"/>"
                        + "</manifest>"
                        + "<spine toc=\"ncx\">"
                        + "<itemref idref=\"chapter1\"/>"
                        + "<itemref idref=\"chapter2\"/>"
                        + "</spine>"
                        + "</package>");
        addEntry(zipOutputStream, "OEBPS/text/chapter1.xhtml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                        + "<head><title>第一章</title></head>"
                        + "<body>"
                        + "<h1>第一章</h1>"
                        + "<p>これは最初の段落です。</p>"
                        + "<p>続きの段落です。</p>"
                        + "</body>"
                        + "</html>");
        addEntry(zipOutputStream, "OEBPS/text/chapter2.xhtml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                        + "<head><title>第二章</title></head>"
                        + "<body>"
                        + "<h2>第二章</h2>"
                        + "<div>二つ目の章です。</div>"
                        + "<div>別の段落もあります。</div>"
                        + "</body>"
                        + "</html>");

        zipOutputStream.close();
        return outputStream.toByteArray();
    }

    private byte[] createIllustratedEpub() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

        addEntry(zipOutputStream, "mimetype", "application/epub+zip");
        addEntry(zipOutputStream, "META-INF/container.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                        + "<rootfiles>"
                        + "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                        + "</rootfiles>"
                        + "</container>");
        addEntry(zipOutputStream, "OEBPS/content.opf",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<package version=\"2.0\" xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\">"
                        + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                        + "<dc:title>Illustrated EPUB</dc:title>"
                        + "<dc:creator>Author Name</dc:creator>"
                        + "</metadata>"
                        + "<manifest>"
                        + "<item id=\"chapter1\" href=\"text/chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
                        + "<item id=\"scene\" href=\"images/scene.png\" media-type=\"image/png\"/>"
                        + "</manifest>"
                        + "<spine toc=\"ncx\">"
                        + "<itemref idref=\"chapter1\"/>"
                        + "</spine>"
                        + "</package>");
        addEntry(zipOutputStream, "OEBPS/text/chapter1.xhtml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                        + "<head><title>插画章</title></head>"
                        + "<body>"
                        + "<h1>插画章</h1>"
                        + "<p>夜空を見上げた。</p>"
                        + "<img src=\"../images/scene.png\" alt=\"星空の插画\" />"
                        + "<p>彼女は黙っていた。</p>"
                        + "</body>"
                        + "</html>");
        addBinaryEntry(zipOutputStream, "OEBPS/images/scene.png", tinyPng());

        zipOutputStream.close();
        return outputStream.toByteArray();
    }

    private void addEntry(ZipOutputStream outputStream, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        outputStream.putNextEntry(entry);
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        outputStream.closeEntry();
    }

    private void addBinaryEntry(ZipOutputStream outputStream, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        outputStream.putNextEntry(entry);
        outputStream.write(content);
        outputStream.closeEntry();
    }

    private byte[] tinyPng() {
        return Base64.getDecoder()
                .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0ioAAAAASUVORK5CYII=");
    }
}
