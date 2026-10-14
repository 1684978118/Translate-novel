package com.novelreader.service;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelreader.dto.BookmarkRequest;
import com.novelreader.dto.BookmarkResponse;
import com.novelreader.entity.Book;
import com.novelreader.entity.Bookmark;
import com.novelreader.entity.Chapter;
import com.novelreader.entity.Paragraph;
import com.novelreader.exception.BusinessException;
import com.novelreader.mapper.BookMapper;
import com.novelreader.mapper.BookmarkMapper;
import com.novelreader.mapper.ChapterMapper;
import com.novelreader.mapper.ParagraphMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookmarkService {

    private final BookmarkMapper bookmarkMapper;
    private final BookMapper bookMapper;
    private final ParagraphMapper paragraphMapper;
    private final ChapterMapper chapterMapper;

    public BookmarkService(BookmarkMapper bookmarkMapper, BookMapper bookMapper, ParagraphMapper paragraphMapper,
            ChapterMapper chapterMapper) {
        this.bookmarkMapper = bookmarkMapper;
        this.bookMapper = bookMapper;
        this.paragraphMapper = paragraphMapper;
        this.chapterMapper = chapterMapper;
    }

    public List<BookmarkResponse> list(Long userId) {
        List<Bookmark> bookmarks = bookmarkMapper.selectList(Wrappers.<Bookmark>lambdaQuery()
                .eq(Bookmark::getUserId, userId)
                .orderByDesc(Bookmark::getId));
        List<BookmarkResponse> result = new ArrayList<BookmarkResponse>();
        for (Bookmark bookmark : bookmarks) {
            result.add(toResponse(bookmark));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public BookmarkResponse create(Long userId, BookmarkRequest request) {
        Book book = bookMapper.selectById(request.getBookId());
        if (book == null || !userId.equals(book.getUploadUserId())) {
            throw new BusinessException("You do not have access to this book");
        }
        Paragraph paragraph = paragraphMapper.selectById(request.getParagraphId());
        if (paragraph == null) {
            throw new BusinessException("Paragraph not found");
        }
        Chapter chapter = chapterMapper.selectById(paragraph.getChapterId());
        if (chapter == null || !request.getBookId().equals(chapter.getBookId())) {
            throw new BusinessException("Paragraph does not belong to this book");
        }

        Bookmark bookmark = new Bookmark();
        bookmark.setUserId(userId);
        bookmark.setBookId(request.getBookId());
        bookmark.setParagraphId(request.getParagraphId());
        bookmark.setNote(request.getNote());
        bookmarkMapper.insert(bookmark);
        return toResponse(bookmarkMapper.selectById(bookmark.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
        if (bookmark == null || !userId.equals(bookmark.getUserId())) {
            throw new BusinessException("Bookmark not found");
        }
        bookmarkMapper.deleteById(bookmarkId);
    }

    private BookmarkResponse toResponse(Bookmark bookmark) {
        Book book = bookMapper.selectById(bookmark.getBookId());
        Paragraph paragraph = paragraphMapper.selectById(bookmark.getParagraphId());
        return BookmarkResponse.builder()
                .id(bookmark.getId())
                .bookId(bookmark.getBookId())
                .paragraphId(bookmark.getParagraphId())
                .bookTitle(book == null ? "Unknown Book" : book.getTitle())
                .paragraphPreview(buildPreview(paragraph))
                .note(bookmark.getNote())
                .createdAt(bookmark.getCreatedAt())
                .build();
    }

    private String buildPreview(Paragraph paragraph) {
        if (paragraph == null) {
            return "";
        }
        if ("IMAGE".equals(paragraph.getBlockType())) {
            return preview(paragraph.getOriginalText() == null || paragraph.getOriginalText().trim().isEmpty()
                    ? "[插画]"
                    : paragraph.getOriginalText());
        }
        return preview(paragraph.getOriginalText());
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 90 ? text : text.substring(0, 90) + "...";
    }
}
