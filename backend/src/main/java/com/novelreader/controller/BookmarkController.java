package com.novelreader.controller;

import java.util.List;

import javax.validation.Valid;

import com.novelreader.common.ApiResponse;
import com.novelreader.dto.BookmarkRequest;
import com.novelreader.dto.BookmarkResponse;
import com.novelreader.security.AuthenticatedUser;
import com.novelreader.service.BookmarkService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @GetMapping
    public ApiResponse<List<BookmarkResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(bookmarkService.list(user.getId()));
    }

    @PostMapping
    public ApiResponse<BookmarkResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody BookmarkRequest request) {
        return ApiResponse.success("bookmark saved", bookmarkService.create(user.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        bookmarkService.delete(user.getId(), id);
        return ApiResponse.success("bookmark deleted", null);
    }
}
