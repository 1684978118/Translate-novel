package com.novelreader.controller;

import java.util.List;

import javax.validation.Valid;

import com.novelreader.common.ApiResponse;
import com.novelreader.dto.WordRequest;
import com.novelreader.dto.WordResponse;
import com.novelreader.security.AuthenticatedUser;
import com.novelreader.service.WordService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/words")
public class WordController {

    private final WordService wordService;

    public WordController(WordService wordService) {
        this.wordService = wordService;
    }

    @GetMapping
    public ApiResponse<List<WordResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(wordService.list(user.getId()));
    }

    @PostMapping
    public ApiResponse<WordResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody WordRequest request) {
        return ApiResponse.success("word saved", wordService.add(user.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        wordService.delete(user.getId(), id);
        return ApiResponse.success("word deleted", null);
    }
}
