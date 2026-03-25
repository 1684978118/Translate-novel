package com.novelreader.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelreader.dto.FuriganaResponse;
import com.novelreader.dto.ParagraphResponse;
import com.novelreader.dto.SegmentTokenResponse;
import com.novelreader.dto.TranslateResponse;
import com.novelreader.dto.WordDefinitionRequest;
import com.novelreader.dto.WordDefinitionResponse;
import com.novelreader.entity.Paragraph;
import com.novelreader.exception.BusinessException;
import com.novelreader.mapper.ParagraphMapper;
import com.novelreader.service.JapaneseSegmentService.TokenData;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadService {

    private static final String WORD_MEANING_FALLBACK = "暂未获取到可靠释义，可先加入生词本后补充。";
    private static final Map<String, String> CONTEXT_MEANING_OVERRIDES = buildContextMeaningOverrides();

    private final ParagraphMapper paragraphMapper;
    private final TranslationService translationService;
    private final JapaneseSegmentService japaneseSegmentService;
    private final GrammarAnalysisService grammarAnalysisService;
    private final ObjectMapper objectMapper;
    private final Map<String, WordDefinitionResponse> wordDefinitionCache = new ConcurrentHashMap<String, WordDefinitionResponse>();

    public ReadService(ParagraphMapper paragraphMapper, TranslationService translationService,
            JapaneseSegmentService japaneseSegmentService, GrammarAnalysisService grammarAnalysisService,
            ObjectMapper objectMapper) {
        this.paragraphMapper = paragraphMapper;
        this.translationService = translationService;
        this.japaneseSegmentService = japaneseSegmentService;
        this.grammarAnalysisService = grammarAnalysisService;
        this.objectMapper = objectMapper;
    }

    public List<ParagraphResponse> getParagraphs(Long chapterId) {
        List<Paragraph> paragraphs = paragraphMapper.selectList(Wrappers.<Paragraph>lambdaQuery()
                .eq(Paragraph::getChapterId, chapterId)
                .orderByAsc(Paragraph::getParagraphOrder));

        List<ParagraphResponse> result = new ArrayList<ParagraphResponse>();
        for (Paragraph paragraph : paragraphs) {
            result.add(toParagraphResponse(paragraph));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public TranslateResponse translateParagraph(Long paragraphId) {
        Paragraph paragraph = getParagraph(paragraphId);
        if (paragraph.getTranslation() == null || paragraph.getTranslation().trim().isEmpty()) {
            paragraph.setTranslation(translationService.translate(paragraph.getOriginalText()));
            paragraphMapper.updateById(paragraph);
        }

        return TranslateResponse.builder()
                .paragraphId(paragraph.getId())
                .translation(paragraph.getTranslation())
                .provider(translationService.getProvider())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public FuriganaResponse buildFurigana(Long paragraphId) {
        Paragraph paragraph = getParagraph(paragraphId);
        if (paragraph.getFuriganaText() == null || paragraph.getFuriganaText().trim().isEmpty()) {
            paragraph.setFuriganaText(japaneseSegmentService.toRubyHtml(paragraph.getOriginalText()));
            paragraphMapper.updateById(paragraph);
        }

        return FuriganaResponse.builder()
                .paragraphId(paragraph.getId())
                .rubyHtml(paragraph.getFuriganaText())
                .build();
    }

    public com.novelreader.dto.GrammarResponse analyzeGrammar(Long paragraphId) {
        Paragraph paragraph = getParagraph(paragraphId);
        return grammarAnalysisService.analyze(paragraphId, paragraph.getOriginalText());
    }

    public WordDefinitionResponse defineWord(String word) {
        WordDefinitionRequest request = new WordDefinitionRequest();
        request.setWord(word);
        return defineWord(request);
    }

    public WordDefinitionResponse defineWord(WordDefinitionRequest request) {
        WordDefinitionRequest normalizedRequest = normalizeWordDefinitionRequest(request);
        String cacheKey = buildWordDefinitionCacheKey(normalizedRequest);
        return wordDefinitionCache.computeIfAbsent(cacheKey, key -> buildWordDefinition(normalizedRequest));
    }

    private WordDefinitionResponse buildWordDefinition(WordDefinitionRequest request) {
        TokenData fallbackToken = japaneseSegmentService.lookup(request.getWord());
        ContextWordMatch contextWordMatch = resolveContextWordMatch(request, fallbackToken);
        TokenData resolvedToken = contextWordMatch.getToken();

        String meaning = resolveMeaning(resolvedToken, contextWordMatch);
        if (isUnavailableMeaning(meaning)) {
            meaning = WORD_MEANING_FALLBACK;
        }

        return WordDefinitionResponse.builder()
                .word(resolvedToken.getSurface())
                .reading(resolvedToken.getKana())
                .partOfSpeech(resolvedToken.getPartOfSpeech())
                .meaning(meaning)
                .build();
    }

    private WordDefinitionRequest normalizeWordDefinitionRequest(WordDefinitionRequest request) {
        WordDefinitionRequest normalized = new WordDefinitionRequest();
        normalized.setWord(request == null || request.getWord() == null ? "" : request.getWord().trim());
        normalized.setContextText(
                request == null || request.getContextText() == null ? "" : request.getContextText().trim());
        normalized.setTokenIndex(request == null ? null : request.getTokenIndex());

        if (normalized.getWord().isEmpty()) {
            throw new BusinessException("Word not found");
        }
        return normalized;
    }

    private String buildWordDefinitionCacheKey(WordDefinitionRequest request) {
        StringBuilder builder = new StringBuilder(request.getWord());
        builder.append('#').append(request.getTokenIndex() == null ? "-" : request.getTokenIndex());
        if (request.getContextText() != null && !request.getContextText().isEmpty()) {
            builder.append('#').append(request.getContextText());
        }
        return builder.toString();
    }

    private ContextWordMatch resolveContextWordMatch(WordDefinitionRequest request, TokenData fallbackToken) {
        String contextText = request.getContextText();
        if (contextText == null || contextText.isEmpty()) {
            return new ContextWordMatch(fallbackToken, "");
        }

        List<TokenData> contextTokens = japaneseSegmentService.segment(contextText);
        int matchedIndex = findContextTokenIndex(contextTokens, request, fallbackToken);
        if (matchedIndex < 0 || matchedIndex >= contextTokens.size()) {
            return new ContextWordMatch(fallbackToken, "");
        }

        TokenData matchedToken = contextTokens.get(matchedIndex);
        String phraseCandidate = buildContextPhrase(contextTokens, matchedIndex);
        return new ContextWordMatch(matchedToken, phraseCandidate);
    }

    private int findContextTokenIndex(List<TokenData> tokens, WordDefinitionRequest request, TokenData fallbackToken) {
        if (request.getTokenIndex() != null) {
            for (int index = 0; index < tokens.size(); index++) {
                TokenData token = tokens.get(index);
                if (token.getIndex() == request.getTokenIndex().intValue()) {
                    return index;
                }
            }
        }

        String query = request.getWord();
        for (int index = 0; index < tokens.size(); index++) {
            if (query.equals(tokens.get(index).getSurface())) {
                return index;
            }
        }

        String fallbackLookupTarget = normalizeLookupTarget(fallbackToken);
        for (int index = 0; index < tokens.size(); index++) {
            if (fallbackLookupTarget.equals(normalizeLookupTarget(tokens.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    private String buildContextPhrase(List<TokenData> tokens, int matchedIndex) {
        TokenData token = tokens.get(matchedIndex);
        if (isNoun(token)) {
            String nounPhrase = buildNounCenteredPhrase(tokens, matchedIndex);
            if (!nounPhrase.isEmpty()) {
                return nounPhrase;
            }
        }

        if (isVerbOrAdjective(token)) {
            return buildPredicateCenteredPhrase(tokens, matchedIndex);
        }

        return "";
    }

    private String buildNounCenteredPhrase(List<TokenData> tokens, int matchedIndex) {
        if (matchedIndex + 2 >= tokens.size()) {
            return "";
        }

        TokenData particle = firstNonPunctuationToken(tokens, matchedIndex + 1, 1);
        if (!isParticle(particle)) {
            return "";
        }

        TokenData predicate = firstContentToken(tokens, matchedIndex + 2, 4);
        if (predicate == null) {
            return "";
        }

        return tokens.get(matchedIndex).getSurface() + particle.getSurface() + normalizeLookupTarget(predicate);
    }

    private String buildPredicateCenteredPhrase(List<TokenData> tokens, int matchedIndex) {
        TokenData target = tokens.get(matchedIndex);
        StringBuilder builder = new StringBuilder();

        int particleIndex = findPreviousParticleIndex(tokens, matchedIndex, 4);
        if (particleIndex > 0) {
            int phraseStart = particleIndex - 1;
            while (phraseStart > 0 && shouldExpandBackward(tokens.get(phraseStart - 1))) {
                phraseStart--;
            }

            for (int index = phraseStart; index < particleIndex; index++) {
                TokenData current = tokens.get(index);
                if (!isPunctuation(current) && !isParticle(current)) {
                    builder.append(current.getSurface());
                }
            }
            builder.append(tokens.get(particleIndex).getSurface());
        }

        builder.append(normalizeLookupTarget(target));
        return builder.toString();
    }

    private int findPreviousParticleIndex(List<TokenData> tokens, int startIndex, int maxDistance) {
        int contentCount = 0;
        for (int index = startIndex - 1; index >= 0; index--) {
            TokenData token = tokens.get(index);
            if (isPunctuation(token)) {
                break;
            }
            if (isParticle(token)) {
                return index;
            }
            if (!isAuxiliary(token)) {
                contentCount++;
            }
            if (contentCount >= maxDistance) {
                break;
            }
        }
        return -1;
    }

    private boolean shouldExpandBackward(TokenData token) {
        return token != null
                && !isPunctuation(token)
                && !isParticle(token)
                && !isAuxiliary(token);
    }

    private TokenData firstNonPunctuationToken(List<TokenData> tokens, int startIndex, int maxDistance) {
        for (int index = startIndex; index < tokens.size() && index < startIndex + maxDistance; index++) {
            TokenData token = tokens.get(index);
            if (!isPunctuation(token)) {
                return token;
            }
        }
        return null;
    }

    private TokenData firstContentToken(List<TokenData> tokens, int startIndex, int maxDistance) {
        int limit = Math.min(tokens.size(), startIndex + maxDistance);
        for (int index = startIndex; index < limit; index++) {
            TokenData token = tokens.get(index);
            if (token == null || isPunctuation(token) || isParticle(token)) {
                continue;
            }
            if (isVerbOrAdjective(token) || isNoun(token)) {
                return token;
            }
        }
        return null;
    }

    private String resolveMeaning(TokenData token, ContextWordMatch contextWordMatch) {
        String overrideMeaning = resolveOverrideMeaning(token, contextWordMatch);
        if (!overrideMeaning.isEmpty()) {
            return overrideMeaning;
        }

        List<String> candidates = new ArrayList<String>();

        if (contextWordMatch.getPhraseCandidate() != null && !contextWordMatch.getPhraseCandidate().isEmpty()) {
            candidates.add(contextWordMatch.getPhraseCandidate());
        }

        String lookupTarget = normalizeLookupTarget(token);
        candidates.add(lookupTarget);

        String surface = token.getSurface() == null ? "" : token.getSurface().trim();
        if (!surface.isEmpty() && !surface.equals(lookupTarget)) {
            candidates.add(surface);
        }

        for (String candidate : candidates) {
            String meaning = translateMeaning(candidate);
            if (!isUnavailableMeaning(meaning) && !isSameText(meaning, candidate)) {
                return meaning;
            }
        }
        return WORD_MEANING_FALLBACK;
    }

    private String resolveOverrideMeaning(TokenData token, ContextWordMatch contextWordMatch) {
        String phraseCandidate = contextWordMatch.getPhraseCandidate();
        if (phraseCandidate != null) {
            String phraseMeaning = CONTEXT_MEANING_OVERRIDES.get(phraseCandidate);
            if (phraseMeaning != null && !phraseMeaning.trim().isEmpty()) {
                return phraseMeaning;
            }
        }

        String lookupTarget = normalizeLookupTarget(token);
        String tokenMeaning = CONTEXT_MEANING_OVERRIDES.get(lookupTarget);
        if (tokenMeaning != null && !tokenMeaning.trim().isEmpty()) {
            return tokenMeaning;
        }
        return "";
    }

    private String normalizeLookupTarget(TokenData token) {
        if (token.getBaseForm() != null && !token.getBaseForm().trim().isEmpty() && !"*".equals(token.getBaseForm())) {
            return token.getBaseForm().trim();
        }
        return token.getSurface();
    }

    private String translateMeaning(String text) {
        String candidate = text == null ? "" : text.trim();
        if (candidate.isEmpty()) {
            return WORD_MEANING_FALLBACK;
        }

        String translation = translationService.translate(candidate);
        return translation == null ? "" : translation.trim();
    }

    private boolean isUnavailableMeaning(String meaning) {
        return meaning == null
                || meaning.trim().isEmpty()
                || meaning.startsWith("[机器翻译暂不可用]");
    }

    private boolean isSameText(String translation, String source) {
        return source != null && source.trim().equals(translation == null ? "" : translation.trim());
    }

    private boolean isNoun(TokenData token) {
        return hasPartOfSpeechPrefix(token, "名詞");
    }

    private boolean isVerbOrAdjective(TokenData token) {
        return hasPartOfSpeechPrefix(token, "動詞") || hasPartOfSpeechPrefix(token, "形容詞");
    }

    private boolean isParticle(TokenData token) {
        return hasPartOfSpeechPrefix(token, "助詞");
    }

    private boolean isAuxiliary(TokenData token) {
        return hasPartOfSpeechPrefix(token, "助動詞");
    }

    private boolean isPunctuation(TokenData token) {
        return hasPartOfSpeechPrefix(token, "記号");
    }

    private boolean hasPartOfSpeechPrefix(TokenData token, String prefix) {
        return token != null
                && token.getPartOfSpeech() != null
                && token.getPartOfSpeech().startsWith(prefix);
    }

    private static Map<String, String> buildContextMeaningOverrides() {
        Map<String, String> overrides = new LinkedHashMap<String, String>();
        overrides.put("尻餅をつく", "一屁股跌坐在地");
        overrides.put("尻餅", "屁股墩；跌坐在地");
        return Collections.unmodifiableMap(overrides);
    }

    private Paragraph getParagraph(Long paragraphId) {
        Paragraph paragraph = paragraphMapper.selectById(paragraphId);
        if (paragraph == null) {
            throw new BusinessException("Paragraph not found");
        }
        return paragraph;
    }

    private ParagraphResponse toParagraphResponse(Paragraph paragraph) {
        return ParagraphResponse.builder()
                .id(paragraph.getId())
                .paragraphOrder(paragraph.getParagraphOrder())
                .originalText(paragraph.getOriginalText())
                .translation(paragraph.getTranslation())
                .furiganaText(paragraph.getFuriganaText())
                .tokens(readTokens(paragraph.getSegmentedText()))
                .build();
    }

    private List<SegmentTokenResponse> readTokens(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<TokenData> tokens = objectMapper.readValue(
                    json,
                    new TypeReference<List<TokenData>>() {
                    });
            List<SegmentTokenResponse> result = new ArrayList<SegmentTokenResponse>();
            for (TokenData token : tokens) {
                result.add(SegmentTokenResponse.builder()
                        .index(token.getIndex())
                        .surface(token.getSurface())
                        .kana(token.getKana())
                        .partOfSpeech(token.getPartOfSpeech())
                        .baseForm(token.getBaseForm())
                        .kanjiToken(token.isKanjiToken())
                        .build());
            }
            return result;
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private static class ContextWordMatch {

        private final TokenData token;
        private final String phraseCandidate;

        ContextWordMatch(TokenData token, String phraseCandidate) {
            this.token = token;
            this.phraseCandidate = phraseCandidate;
        }

        private TokenData getToken() {
            return token;
        }

        private String getPhraseCandidate() {
            return phraseCandidate;
        }
    }
}
