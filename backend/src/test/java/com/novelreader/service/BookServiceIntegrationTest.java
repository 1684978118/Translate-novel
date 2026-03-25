package com.novelreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelreader.dto.BookDetailResponse;
import com.novelreader.dto.BookSummaryResponse;
import com.novelreader.dto.BookmarkRequest;
import com.novelreader.dto.ProgressRequest;
import com.novelreader.entity.Bookmark;
import com.novelreader.entity.Chapter;
import com.novelreader.entity.Paragraph;
import com.novelreader.entity.ReadingProgress;
import com.novelreader.entity.User;
import com.novelreader.mapper.BookMapper;
import com.novelreader.mapper.BookmarkMapper;
import com.novelreader.mapper.ChapterMapper;
import com.novelreader.mapper.ParagraphMapper;
import com.novelreader.mapper.ReadingProgressMapper;
import com.novelreader.mapper.UserMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.upload-dir=./target/test-uploads")
@Transactional
class BookServiceIntegrationTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BookMapper bookMapper;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private ParagraphMapper paragraphMapper;

    @Autowired
    private BookmarkMapper bookmarkMapper;

    @Autowired
    private ReadingProgressMapper readingProgressMapper;

    @AfterEach
    void cleanUploads() throws IOException {
        Path uploads = Paths.get("./target/test-uploads");
        if (Files.exists(uploads)) {
            Files.walk(uploads)
                    .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void uploadTxtRecognizesJapaneseAndEnglishChapterTitles() {
        Long userId = createUser();
        String txt = "Chapter 1 Arrival\n"
                + "最初の段落です。\n\n"
                + "第2章 再会\n"
                + "二つ目の章です。\n"
                + "もう一つの段落です。";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                txt.getBytes(StandardCharsets.UTF_8));

        BookDetailResponse detail = bookService.upload(file, userId);

        assertThat(detail.getSourceType()).isEqualTo("TXT");
        assertThat(detail.getChapters()).hasSize(2);
        assertThat(detail.getChapters().get(0).getTitle()).isEqualTo("Chapter 1 Arrival");
        assertThat(detail.getChapters().get(1).getTitle()).isEqualTo("第2章 再会");

        Long firstChapterId = detail.getChapters().get(0).getId();
        assertThat(paragraphMapper.selectCount(Wrappers.<Paragraph>lambdaQuery().eq(Paragraph::getChapterId, firstChapterId)))
                .isEqualTo(1);
    }

    @Test
    void uploadEpubPersistsMetadataAndChapterStructure() throws Exception {
        Long userId = createUser();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.epub",
                "application/epub+zip",
                createSampleEpub());

        BookDetailResponse detail = bookService.upload(file, userId);

        assertThat(detail.getSourceType()).isEqualTo("EPUB");
        assertThat(detail.getTitle()).isEqualTo("Integration EPUB");
        assertThat(detail.getChapters()).hasSize(2);

        BookSummaryResponse summary = bookService.listBooks(userId).get(0);
        assertThat(summary.getSourceType()).isEqualTo("EPUB");
        assertThat(summary.getChapterCount()).isEqualTo(2);
    }

    @Test
    void deleteBookRemovesRelatedRecordsAndStoredFile() {
        Long userId = createUser();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "delete-me.txt",
                "text/plain",
                ("第1章 开始\n"
                        + "第一段。\n\n"
                        + "第二段。").getBytes(StandardCharsets.UTF_8));

        BookDetailResponse detail = bookService.upload(file, userId);
        Long bookId = detail.getId();
        Long chapterId = detail.getChapters().get(0).getId();
        Paragraph paragraph = paragraphMapper.selectOne(Wrappers.<Paragraph>lambdaQuery()
                .eq(Paragraph::getChapterId, chapterId)
                .last("limit 1"));

        bookmarkService.create(userId, createBookmarkRequest(bookId, paragraph.getId()));

        ProgressRequest progressRequest = new ProgressRequest();
        progressRequest.setChapterId(chapterId);
        progressRequest.setParagraphId(paragraph.getId());
        progressRequest.setScrollPosition(120);
        bookService.updateProgress(bookId, userId, progressRequest);

        String filePath = bookMapper.selectById(bookId).getFilePath();

        bookService.deleteBook(bookId, userId);

        assertThat(bookMapper.selectById(bookId)).isNull();
        assertThat(chapterMapper.selectCount(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getBookId, bookId))).isZero();
        assertThat(paragraphMapper.selectCount(Wrappers.<Paragraph>lambdaQuery().eq(Paragraph::getChapterId, chapterId))).isZero();
        assertThat(bookmarkMapper.selectCount(Wrappers.<Bookmark>lambdaQuery().eq(Bookmark::getBookId, bookId))).isZero();
        assertThat(readingProgressMapper.selectCount(Wrappers.<ReadingProgress>lambdaQuery().eq(ReadingProgress::getBookId, bookId)))
                .isZero();
        assertThat(Files.exists(Paths.get(filePath))).isFalse();
    }

    private Long createUser() {
        User user = new User();
        user.setUsername("user_" + System.nanoTime());
        user.setPassword("encoded");
        user.setEmail("user_" + System.nanoTime() + "@example.com");
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user.getId();
    }

    private BookmarkRequest createBookmarkRequest(Long bookId, Long paragraphId) {
        BookmarkRequest request = new BookmarkRequest();
        request.setBookId(bookId);
        request.setParagraphId(paragraphId);
        request.setNote("test");
        return request;
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
                        + "<dc:title>Integration EPUB</dc:title>"
                        + "<dc:creator>Service Test</dc:creator>"
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
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>"
                        + "<h1>第1章</h1><p>最初の段落です。</p></body></html>");
        addEntry(zipOutputStream, "OEBPS/text/chapter2.xhtml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>"
                        + "<h2>第2章</h2><div>別の章です。</div></body></html>");

        zipOutputStream.close();
        return outputStream.toByteArray();
    }

    private void addEntry(ZipOutputStream outputStream, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        outputStream.putNextEntry(entry);
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        outputStream.closeEntry();
    }
}
