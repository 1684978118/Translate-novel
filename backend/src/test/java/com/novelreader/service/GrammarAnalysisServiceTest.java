package com.novelreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.novelreader.dto.GrammarPatternResponse;
import com.novelreader.dto.GrammarResponse;

import org.junit.jupiter.api.Test;

class GrammarAnalysisServiceTest {

    private final JapaneseSegmentService japaneseSegmentService = new JapaneseSegmentService();
    private final GrammarAnalysisService grammarAnalysisService = new GrammarAnalysisService(japaneseSegmentService);

    @Test
    void analyzeDetectsPoliteNegativeAndSentenceFrame() {
        GrammarResponse response = grammarAnalysisService.analyze(1L, "私は学校に行きません。");

        assertThat(patternNames(response)).contains("礼貌体", "否定形", "助词线索", "句子骨架");
        assertThat(findExplanation(response, "助词线索")).contains("は").contains("に");
        assertThat(findExplanation(response, "句子骨架")).contains("行く");
    }

    @Test
    void analyzeDetectsProgressiveReasonAndRequestPatterns() {
        GrammarResponse response = grammarAnalysisService.analyze(2L, "雨が降っているので、早く帰ってください。");

        assertThat(patternNames(response)).contains("进行/状态", "原因/理由", "请求表达", "助词线索", "句子骨架");
        assertThat(response.getSummary()).contains("语法线索");
    }

    @Test
    void analyzeUsesSentenceFrameInsteadOfTokenCountFallback() {
        GrammarResponse response = grammarAnalysisService.analyze(3L, "彼は本を読む。");

        assertThat(patternNames(response)).contains("助词线索", "句子骨架");
        assertThat(findExplanation(response, "句子骨架")).contains("読む");
        assertThat(response.getPatterns())
                .extracting(GrammarPatternResponse::getExplanation)
                .noneMatch(explanation -> explanation.contains("词元"));
    }

    private List<String> patternNames(GrammarResponse response) {
        return response.getPatterns()
                .stream()
                .map(GrammarPatternResponse::getName)
                .collect(java.util.stream.Collectors.toList());
    }

    private String findExplanation(GrammarResponse response, String name) {
        return response.getPatterns()
                .stream()
                .filter(pattern -> name.equals(pattern.getName()))
                .map(GrammarPatternResponse::getExplanation)
                .findFirst()
                .orElse("");
    }
}
