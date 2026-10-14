package com.novelreader.controller;

import java.util.List;

import com.novelreader.common.ApiResponse;
import com.novelreader.dto.FuriganaResponse;
import com.novelreader.dto.GrammarResponse;
import com.novelreader.dto.ParagraphResponse;
import com.novelreader.dto.TranslateResponse;
import com.novelreader.dto.WordDefinitionResponse;
import com.novelreader.dto.WordDefinitionRequest;
import com.novelreader.security.AuthenticatedUser;
import com.novelreader.service.BookService;
import com.novelreader.service.ReadService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/read")
public class ReadController {

    private final ReadService readService;
    private final BookService bookService;

    public ReadController(ReadService readService, BookService bookService) {
        this.readService = readService;
        this.bookService = bookService;
    }

    @GetMapping("/{chapterId}/paragraphs")
    public ApiResponse<List<ParagraphResponse>> paragraphs(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long chapterId) {
        bookService.assertChapterOwnership(chapterId, user.getId());
        return ApiResponse.success(readService.getParagraphs(chapterId));
    }

    @PostMapping("/paragraph/{id}/translate")
    public ApiResponse<TranslateResponse> translate(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        bookService.assertParagraphOwnership(id, user.getId());
        return ApiResponse.success(readService.translateParagraph(id));
    }

    @PostMapping("/paragraph/{id}/furigana")
    public ApiResponse<FuriganaResponse> furigana(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        bookService.assertParagraphOwnership(id, user.getId());
        return ApiResponse.success(readService.buildFurigana(id));
    }

    @PostMapping("/paragraph/{id}/grammar")
    public ApiResponse<GrammarResponse> grammar(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        bookService.assertParagraphOwnership(id, user.getId());
        return ApiResponse.success(readService.analyzeGrammar(id));
    }

    @GetMapping("/word/{word}/definition")
    public ApiResponse<WordDefinitionResponse> define(@PathVariable String word) {
        return ApiResponse.success(readService.defineWord(word));
    }

    @PostMapping("/word/definition")
    public ApiResponse<WordDefinitionResponse> defineWithContext(@RequestBody WordDefinitionRequest request) {
        return ApiResponse.success(readService.defineWord(request));
    }
}
