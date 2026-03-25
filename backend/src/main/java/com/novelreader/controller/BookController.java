package com.novelreader.controller;

import java.util.List;

import javax.validation.Valid;

import com.novelreader.common.ApiResponse;
import com.novelreader.dto.BookDetailResponse;
import com.novelreader.dto.BookSummaryResponse;
import com.novelreader.dto.ChapterResponse;
import com.novelreader.dto.ProgressRequest;
import com.novelreader.dto.ProgressResponse;
import com.novelreader.security.AuthenticatedUser;
import com.novelreader.service.BookService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping("/upload")
    public ApiResponse<BookDetailResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success("upload success", bookService.upload(file, user.getId()));
    }

    @GetMapping
    public ApiResponse<List<BookSummaryResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(bookService.listBooks(user.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return ApiResponse.success(bookService.getBookDetail(id, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        bookService.deleteBook(id, user.getId());
        return ApiResponse.success("book deleted", null);
    }

    @GetMapping("/{id}/chapters")
    public ApiResponse<List<ChapterResponse>> chapters(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return ApiResponse.success(bookService.getChapters(id, user.getId()));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<ProgressResponse> progress(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return ApiResponse.success(bookService.getProgress(id, user.getId()));
    }

    @PutMapping("/{id}/progress")
    public ApiResponse<ProgressResponse> updateProgress(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody ProgressRequest request) {
        return ApiResponse.success("progress saved", bookService.updateProgress(id, user.getId(), request));
    }
}
