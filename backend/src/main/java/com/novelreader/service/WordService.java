package com.novelreader.service;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelreader.dto.WordRequest;
import com.novelreader.dto.WordResponse;
import com.novelreader.entity.WordDictionary;
import com.novelreader.mapper.WordDictionaryMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WordService {

    private final WordDictionaryMapper wordDictionaryMapper;
    private final JapaneseSegmentService japaneseSegmentService;

    public WordService(WordDictionaryMapper wordDictionaryMapper, JapaneseSegmentService japaneseSegmentService) {
        this.wordDictionaryMapper = wordDictionaryMapper;
        this.japaneseSegmentService = japaneseSegmentService;
    }

    public List<WordResponse> list(Long userId) {
        List<WordDictionary> words = wordDictionaryMapper.selectList(Wrappers.<WordDictionary>lambdaQuery()
                .eq(WordDictionary::getUserId, userId)
                .orderByDesc(WordDictionary::getId));
        List<WordResponse> result = new ArrayList<WordResponse>();
        for (WordDictionary word : words) {
            result.add(toResponse(word));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public WordResponse add(Long userId, WordRequest request) {
        JapaneseSegmentService.TokenData token = japaneseSegmentService.lookup(request.getWord());

        WordDictionary existing = wordDictionaryMapper.selectOne(Wrappers.<WordDictionary>lambdaQuery()
                .eq(WordDictionary::getUserId, userId)
                .eq(WordDictionary::getWord, token.getSurface()));

        if (existing != null) {
            existing.setMeaning(request.getMeaning() == null || request.getMeaning().trim().isEmpty()
                    ? existing.getMeaning()
                    : request.getMeaning());
            existing.setExampleSentence(request.getExampleSentence());
            wordDictionaryMapper.updateById(existing);
            return toResponse(wordDictionaryMapper.selectById(existing.getId()));
        }

        WordDictionary word = new WordDictionary();
        word.setUserId(userId);
        word.setWord(token.getSurface());
        word.setReading(request.getReading() == null || request.getReading().trim().isEmpty()
                ? token.getKana()
                : request.getReading());
        word.setMeaning(request.getMeaning() == null || request.getMeaning().trim().isEmpty()
                ? "待补充释义"
                : request.getMeaning());
        word.setExampleSentence(request.getExampleSentence());
        wordDictionaryMapper.insert(word);
        return toResponse(wordDictionaryMapper.selectById(word.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long wordId) {
        wordDictionaryMapper.delete(Wrappers.<WordDictionary>lambdaQuery()
                .eq(WordDictionary::getId, wordId)
                .eq(WordDictionary::getUserId, userId));
    }

    private WordResponse toResponse(WordDictionary word) {
        return WordResponse.builder()
                .id(word.getId())
                .word(word.getWord())
                .reading(word.getReading())
                .meaning(word.getMeaning())
                .exampleSentence(word.getExampleSentence())
                .createdAt(word.getCreatedAt())
                .build();
    }
}
