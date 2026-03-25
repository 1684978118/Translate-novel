package com.novelreader.service;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelreader.dto.BookmarkRequest;
import com.novelreader.dto.BookmarkResponse;
import com.novelreader.entity.Book;
import com.novelreader.entity.Bookmark;
import com.novelreader.entity.Paragraph;
import com.novelreader.exception.BusinessException;
import com.novelreader.mapper.BookMapper;
import com.novelreader.mapper.BookmarkMapper;
import com.novelreader.mapper.ParagraphMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookmarkService {

    private final BookmarkMapper bookmarkMapper;
    private final BookMapper bookMapper;
    private final ParagraphMapper paragraphMapper;

    public BookmarkService(BookmarkMapper bookmarkMapper, BookMapper bookMapper, ParagraphMapper paragraphMapper) {
        this.bookmarkMapper = bookmarkMapper;
        this.bookMapper = bookMapper;
        this.paragraphMapper = paragraphMapper;
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
                .paragraphPreview(paragraph == null ? "" : preview(paragraph.getOriginalText()))
                .note(bookmark.getNote())
                .createdAt(bookmark.getCreatedAt())
                .build();
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 90 ? text : text.substring(0, 90) + "...";
    }
}
