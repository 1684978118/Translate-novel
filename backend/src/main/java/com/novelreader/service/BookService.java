package com.novelreader.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelreader.dto.BookDetailResponse;
import com.novelreader.dto.BookSummaryResponse;
import com.novelreader.dto.ChapterResponse;
import com.novelreader.dto.ProgressRequest;
import com.novelreader.dto.ProgressResponse;
import com.novelreader.entity.Book;
import com.novelreader.entity.Bookmark;
import com.novelreader.entity.Chapter;
import com.novelreader.entity.Paragraph;
import com.novelreader.entity.ReadingProgress;
import com.novelreader.exception.BusinessException;
import com.novelreader.mapper.BookMapper;
import com.novelreader.mapper.BookmarkMapper;
import com.novelreader.mapper.ChapterMapper;
import com.novelreader.mapper.ParagraphMapper;
import com.novelreader.mapper.ReadingProgressMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BookService {

    private static final Pattern NUMBERED_CHAPTER_PATTERN = Pattern.compile(
            "^(第\\s*[0-9０-９一二三四五六七八九十百千零〇两兩]+\\s*[章话話节節卷篇部回])(.+)?$");
    private static final Pattern ENGLISH_CHAPTER_PATTERN = Pattern.compile(
            "^(chapter|prologue|epilogue)\\b.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPECIAL_JP_CHAPTER_PATTERN = Pattern.compile(
            "^(序章|终章|終章|後書き|后记|あとがき|幕間|幕间|番外編|番外篇).*$");

    @Value("${app.upload-dir}")
    private String uploadDir;

    private final BookMapper bookMapper;
    private final BookmarkMapper bookmarkMapper;
    private final ChapterMapper chapterMapper;
    private final ParagraphMapper paragraphMapper;
    private final ReadingProgressMapper readingProgressMapper;
    private final JapaneseSegmentService japaneseSegmentService;
    private final EpubParserService epubParserService;
    private final ObjectMapper objectMapper;

    public BookService(BookMapper bookMapper, BookmarkMapper bookmarkMapper, ChapterMapper chapterMapper, ParagraphMapper paragraphMapper,
            ReadingProgressMapper readingProgressMapper, JapaneseSegmentService japaneseSegmentService,
            EpubParserService epubParserService, ObjectMapper objectMapper) {
        this.bookMapper = bookMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.chapterMapper = chapterMapper;
        this.paragraphMapper = paragraphMapper;
        this.readingProgressMapper = readingProgressMapper;
        this.japaneseSegmentService = japaneseSegmentService;
        this.epubParserService = epubParserService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public BookDetailResponse upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Please choose a TXT or EPUB file");
        }

        String originalFilename = file.getOriginalFilename() == null ? "novel.txt" : file.getOriginalFilename();
        String lowerName = originalFilename.toLowerCase();
        byte[] fileBytes = readFileBytes(file);
        ParsedBook parsedBook;
        if (lowerName.endsWith(".epub")) {
            parsedBook = toParsedBook(epubParserService.parse(originalFilename, fileBytes));
        } else if (lowerName.endsWith(".txt")) {
            parsedBook = parseTxt(decodeTxt(fileBytes), originalFilename);
        } else {
            throw new BusinessException("Only TXT and EPUB upload are supported");
        }

        if (parsedBook.chapters.isEmpty()) {
            throw new BusinessException("The uploaded file does not contain readable content");
        }

        Book book = new Book();
        book.setTitle(parsedBook.title);
        book.setAuthor(parsedBook.author);
        book.setSourceType(parsedBook.sourceType);
        book.setStatus("PROCESSING");
        book.setUploadUserId(userId);
        book.setFilePath(storeFile(file, originalFilename));
        book.setWordCount(0);
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());
        bookMapper.insert(book);

        int wordCount = 0;
        int chapterOrder = 1;
        for (ParsedChapter parsedChapter : parsedBook.chapters) {
            Chapter chapter = new Chapter();
            chapter.setBookId(book.getId());
            chapter.setChapterOrder(chapterOrder++);
            chapter.setTitle(parsedChapter.title);
            chapter.setCreatedAt(LocalDateTime.now());
            chapterMapper.insert(chapter);

            int paragraphOrder = 1;
            for (String text : parsedChapter.paragraphs) {
                List<JapaneseSegmentService.TokenData> tokens = japaneseSegmentService.segment(text);

                Paragraph paragraph = new Paragraph();
                paragraph.setChapterId(chapter.getId());
                paragraph.setParagraphOrder(paragraphOrder++);
                paragraph.setOriginalText(text);
                paragraph.setSegmentedText(writeJson(tokens));
                paragraph.setCreatedAt(LocalDateTime.now());
                paragraphMapper.insert(paragraph);

                wordCount += tokens.size();
            }
        }

        book.setStatus("READY");
        book.setWordCount(wordCount);
        book.setUpdatedAt(LocalDateTime.now());
        bookMapper.updateById(book);

        return getBookDetail(book.getId(), userId);
    }

    public List<BookSummaryResponse> listBooks(Long userId) {
        List<Book> books = bookMapper.selectList(Wrappers.<Book>lambdaQuery()
                .eq(Book::getUploadUserId, userId)
                .orderByDesc(Book::getId));

        List<BookSummaryResponse> result = new ArrayList<BookSummaryResponse>();
        for (Book book : books) {
            int chapterCount = chapterMapper.selectCount(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getBookId, book.getId()))
                    .intValue();
            result.add(toSummary(book, chapterCount));
        }
        return result;
    }

    public BookDetailResponse getBookDetail(Long bookId, Long userId) {
        Book book = getOwnedBook(bookId, userId);
        List<ChapterResponse> chapters = getChapters(bookId, userId);
        return BookDetailResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .sourceType(book.getSourceType())
                .status(book.getStatus())
                .wordCount(book.getWordCount())
                .chapters(chapters)
                .build();
    }

    public List<ChapterResponse> getChapters(Long bookId, Long userId) {
        getOwnedBook(bookId, userId);

        List<Chapter> chapters = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                .eq(Chapter::getBookId, bookId)
                .orderByAsc(Chapter::getChapterOrder));

        List<ChapterResponse> result = new ArrayList<ChapterResponse>();
        for (Chapter chapter : chapters) {
            int paragraphCount = paragraphMapper.selectCount(
                    Wrappers.<Paragraph>lambdaQuery().eq(Paragraph::getChapterId, chapter.getId())).intValue();
            result.add(ChapterResponse.builder()
                    .id(chapter.getId())
                    .chapterOrder(chapter.getChapterOrder())
                    .title(chapter.getTitle())
                    .paragraphCount(paragraphCount)
                    .build());
        }
        return result;
    }

    public ProgressResponse getProgress(Long bookId, Long userId) {
        getOwnedBook(bookId, userId);

        ReadingProgress progress = readingProgressMapper.selectOne(Wrappers.<ReadingProgress>lambdaQuery()
                .eq(ReadingProgress::getUserId, userId)
                .eq(ReadingProgress::getBookId, bookId));

        if (progress != null) {
            return ProgressResponse.builder()
                    .bookId(progress.getBookId())
                    .chapterId(progress.getChapterId())
                    .paragraphId(progress.getParagraphId())
                    .scrollPosition(progress.getScrollPosition())
                    .build();
        }

        Chapter firstChapter = chapterMapper.selectOne(Wrappers.<Chapter>lambdaQuery()
                .eq(Chapter::getBookId, bookId)
                .orderByAsc(Chapter::getChapterOrder)
                .last("limit 1"));
        if (firstChapter == null) {
            throw new BusinessException("Book chapters not found");
        }

        Paragraph firstParagraph = paragraphMapper.selectOne(Wrappers.<Paragraph>lambdaQuery()
                .eq(Paragraph::getChapterId, firstChapter.getId())
                .orderByAsc(Paragraph::getParagraphOrder)
                .last("limit 1"));
        if (firstParagraph == null) {
            throw new BusinessException("Book paragraphs not found");
        }

        return ProgressResponse.builder()
                .bookId(bookId)
                .chapterId(firstChapter.getId())
                .paragraphId(firstParagraph.getId())
                .scrollPosition(0)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public ProgressResponse updateProgress(Long bookId, Long userId, ProgressRequest request) {
        getOwnedBook(bookId, userId);

        Long chapterId = request.getChapterId();
        Long paragraphId = request.getParagraphId();
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !bookId.equals(chapter.getBookId())) {
            throw new BusinessException("Chapter does not belong to this book");
        }
        Paragraph paragraph = paragraphMapper.selectById(paragraphId);
        if (paragraph == null || !chapterId.equals(paragraph.getChapterId())) {
            throw new BusinessException("Paragraph does not belong to this chapter");
        }

        ReadingProgress progress = readingProgressMapper.selectOne(Wrappers.<ReadingProgress>lambdaQuery()
                .eq(ReadingProgress::getUserId, userId)
                .eq(ReadingProgress::getBookId, bookId));

        if (progress == null) {
            progress = new ReadingProgress();
            progress.setUserId(userId);
            progress.setBookId(bookId);
        }

        progress.setChapterId(chapterId);
        progress.setParagraphId(paragraphId);
        progress.setScrollPosition(request.getScrollPosition() == null ? 0 : request.getScrollPosition());
        progress.setUpdatedAt(LocalDateTime.now());

        if (progress.getId() == null) {
            readingProgressMapper.insert(progress);
        } else {
            readingProgressMapper.updateById(progress);
        }

        return ProgressResponse.builder()
                .bookId(progress.getBookId())
                .chapterId(progress.getChapterId())
                .paragraphId(progress.getParagraphId())
                .scrollPosition(progress.getScrollPosition())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBook(Long bookId, Long userId) {
        Book book = getOwnedBook(bookId, userId);

        List<Chapter> chapters = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getBookId, bookId));
        List<Long> chapterIds = new ArrayList<Long>();
        for (Chapter chapter : chapters) {
            chapterIds.add(chapter.getId());
        }

        if (!chapterIds.isEmpty()) {
            paragraphMapper.delete(Wrappers.<Paragraph>lambdaQuery().in(Paragraph::getChapterId, chapterIds));
            chapterMapper.delete(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getBookId, bookId));
        }

        bookmarkMapper.delete(Wrappers.<Bookmark>lambdaQuery().eq(Bookmark::getBookId, bookId));
        readingProgressMapper.delete(Wrappers.<ReadingProgress>lambdaQuery().eq(ReadingProgress::getBookId, bookId));
        bookMapper.deleteById(bookId);
        deleteStoredFile(book.getFilePath());
    }

    private Book getOwnedBook(Long bookId, Long userId) {
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("Book not found");
        }
        if (!userId.equals(book.getUploadUserId())) {
            throw new BusinessException("You do not have access to this book");
        }
        return book;
    }

    public void assertChapterOwnership(Long chapterId, Long userId) {
        Chapter chapter = chapterId == null ? null : chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException("Chapter not found");
        }
        getOwnedBook(chapter.getBookId(), userId);
    }

    public void assertParagraphOwnership(Long paragraphId, Long userId) {
        Paragraph paragraph = paragraphId == null ? null : paragraphMapper.selectById(paragraphId);
        if (paragraph == null) {
            throw new BusinessException("Paragraph not found");
        }
        assertChapterOwnership(paragraph.getChapterId(), userId);
    }

    private BookSummaryResponse toSummary(Book book, int chapterCount) {
        return BookSummaryResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .sourceType(book.getSourceType())
                .status(book.getStatus())
                .wordCount(book.getWordCount())
                .chapterCount(chapterCount)
                .build();
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("Failed to read uploaded file");
        }
    }

    /**
     * TXT 编码探测：先按 UTF-8 严格解码（含 BOM 处理），失败则回退 Shift-JIS。
     * 青空文库等日文小说 TXT 普遍使用 Shift-JIS，按 UTF-8 硬解会产生乱码。
     */
    private String decodeTxt(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException exception) {
            return new String(bytes, offset, bytes.length - offset, Charset.forName("Shift_JIS"));
        }
    }

    private String storeFile(MultipartFile file, String originalFilename) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);
            String storedName = UUID.randomUUID().toString() + "-" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path destination = uploadPath.resolve(storedName);
            file.transferTo(destination);
            return destination.toString();
        } catch (IOException exception) {
            throw new BusinessException("Failed to store uploaded file");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to serialize tokens");
        }
    }

    private void deleteStoredFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException exception) {
            throw new BusinessException("Book deleted, but stored file cleanup failed");
        }
    }

    private ParsedBook toParsedBook(EpubParserService.EpubBookContent epubBook) {
        List<ParsedChapter> chapters = new ArrayList<ParsedChapter>();
        for (EpubParserService.EpubChapterContent chapter : epubBook.getChapters()) {
            ParsedChapter parsedChapter = new ParsedChapter(chapter.getTitle());
            parsedChapter.paragraphs.addAll(chapter.getParagraphs());
            chapters.add(parsedChapter);
        }
        return new ParsedBook(epubBook.getTitle(), epubBook.getAuthor(), "EPUB", chapters);
    }

    private ParsedBook parseTxt(String content, String filename) {
        String[] lines = content.replace("\r\n", "\n").split("\n");
        List<ParsedChapter> chapters = new ArrayList<ParsedChapter>();
        ParsedChapter currentChapter = new ParsedChapter("第1章 开始");
        StringBuilder paragraphBuffer = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (isChapterTitle(line)) {
                flushParagraph(currentChapter, paragraphBuffer);
                if (!currentChapter.paragraphs.isEmpty()) {
                    chapters.add(currentChapter);
                }
                currentChapter = new ParsedChapter(line);
                continue;
            }

            if (line.isEmpty()) {
                flushParagraph(currentChapter, paragraphBuffer);
                continue;
            }

            if (paragraphBuffer.length() > 0) {
                paragraphBuffer.append("\n");
            }
            paragraphBuffer.append(line);
        }

        flushParagraph(currentChapter, paragraphBuffer);
        if (!currentChapter.paragraphs.isEmpty()) {
            chapters.add(currentChapter);
        }

        return new ParsedBook(defaultTitle(filename), "Unknown", "TXT", chapters);
    }

    private boolean isChapterTitle(String line) {
        if (line == null) {
            return false;
        }
        String normalized = line.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            return false;
        }
        return NUMBERED_CHAPTER_PATTERN.matcher(normalized).matches()
                || ENGLISH_CHAPTER_PATTERN.matcher(normalized).matches()
                || SPECIAL_JP_CHAPTER_PATTERN.matcher(normalized).matches();
    }

    private void flushParagraph(ParsedChapter chapter, StringBuilder paragraphBuffer) {
        if (paragraphBuffer.length() == 0) {
            return;
        }
        chapter.paragraphs.add(paragraphBuffer.toString().trim());
        paragraphBuffer.setLength(0);
    }

    private String defaultTitle(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private static class ParsedBook {
        private final String title;
        private final String author;
        private final String sourceType;
        private final List<ParsedChapter> chapters;

        private ParsedBook(String title, String author, String sourceType, List<ParsedChapter> chapters) {
            this.title = title;
            this.author = author;
            this.sourceType = sourceType;
            this.chapters = chapters;
        }
    }

    private static class ParsedChapter {
        private final String title;
        private final List<String> paragraphs = new ArrayList<String>();

        private ParsedChapter(String title) {
            this.title = title;
        }
    }
}
