package com.novelreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelreader.dto.WordDefinitionRequest;
import com.novelreader.dto.WordDefinitionResponse;
import com.novelreader.dto.WordRequest;
import com.novelreader.dto.WordResponse;
import com.novelreader.entity.Paragraph;
import com.novelreader.entity.WordDictionary;
import com.novelreader.exception.BusinessException;
import com.novelreader.mapper.ParagraphMapper;
import com.novelreader.mapper.WordDictionaryMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadAndWordServiceTest {

    @Mock
    private WordDictionaryMapper wordDictionaryMapper;

    @Mock
    private JapaneseSegmentService japaneseSegmentService;

    @Mock
    private TranslationService translationService;

    @Mock
    private GrammarAnalysisService grammarAnalysisService;

    @Mock
    private ParagraphMapper paragraphMapper;

    @InjectMocks
    private WordService wordService;

    @Test
    void addUsesReadableDefaultMeaningWhenMeaningMissing() {
        JapaneseSegmentService.TokenData token = new JapaneseSegmentService.TokenData(
                0,
                "猫",
                "ねこ",
                "名词",
                "猫",
                true);
        when(japaneseSegmentService.lookup("猫")).thenReturn(token);
        when(wordDictionaryMapper.selectOne(any())).thenReturn(null);
        when(wordDictionaryMapper.insert(any(WordDictionary.class))).thenAnswer(invocation -> {
            WordDictionary entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(wordDictionaryMapper.selectById(1L)).thenAnswer(invocation -> {
            WordDictionary entity = new WordDictionary();
            entity.setId(1L);
            entity.setUserId(7L);
            entity.setWord("猫");
            entity.setReading("ねこ");
            entity.setMeaning("待补充释义");
            return entity;
        });

        WordRequest request = new WordRequest();
        request.setWord("猫");

        WordResponse response = wordService.add(7L, request);

        ArgumentCaptor<WordDictionary> captor = ArgumentCaptor.forClass(WordDictionary.class);
        verify(wordDictionaryMapper).insert(captor.capture());
        assertThat(captor.getValue().getMeaning()).isEqualTo("待补充释义");
        assertThat(response.getMeaning()).isEqualTo("待补充释义");
    }

    @Test
    void defineWordUsesBaseFormTranslationAsMeaning() {
        JapaneseSegmentService.TokenData token = new JapaneseSegmentService.TokenData(
                0,
                "渡された",
                "わたされた",
                "动词",
                "渡す",
                true);
        when(japaneseSegmentService.lookup("渡された")).thenReturn(token);
        when(translationService.translate("渡す")).thenReturn("递给，交给");

        ReadService readService = new ReadService(
                paragraphMapper,
                translationService,
                japaneseSegmentService,
                grammarAnalysisService,
                new ObjectMapper());

        WordDefinitionResponse response = readService.defineWord("渡された");

        assertThat(response.getWord()).isEqualTo("渡された");
        assertThat(response.getReading()).isEqualTo("わたされた");
        assertThat(response.getMeaning()).isEqualTo("递给，交给");
    }

    @Test
    void defineWordFallsBackToReadableMessageWhenTranslationUnavailable() {
        JapaneseSegmentService.TokenData token = new JapaneseSegmentService.TokenData(
                0,
                "あんなに",
                "あんなに",
                "副词 / 助词类接续",
                "あんなに",
                false);
        when(japaneseSegmentService.lookup("あんなに")).thenReturn(token);
        when(translationService.translate(eq("あんなに"))).thenReturn("[机器翻译暂不可用] あんなに");

        ReadService readService = new ReadService(
                paragraphMapper,
                translationService,
                japaneseSegmentService,
                grammarAnalysisService,
                new ObjectMapper());

        WordDefinitionResponse response = readService.defineWord("あんなに");

        assertThat(response.getWord()).isEqualTo("あんなに");
        assertThat(response.getMeaning()).isEqualTo("暂未获取到可靠释义，可先加入生词本后补充。");
    }

    @Test
    void defineWordPrefersContextPhraseMeaningOverIsolatedWordMeaning() {
        JapaneseSegmentService.TokenData isolatedToken = new JapaneseSegmentService.TokenData(
                0,
                "尻餅",
                "しりもち",
                "名詞 / 一般",
                "尻餅",
                true);
        when(japaneseSegmentService.lookup("尻餅")).thenReturn(isolatedToken);
        when(japaneseSegmentService.segment("ドッと疲れてその場に尻餅をつきそうになるけど、どうにか持ちこたえた。"))
                .thenReturn(java.util.Arrays.asList(
                        new JapaneseSegmentService.TokenData(0, "ドッと", "どっと", "副詞 / 一般", "ドッと", false),
                        new JapaneseSegmentService.TokenData(1, "疲れ", "つかれ", "動詞 / 自立", "疲れる", false),
                        new JapaneseSegmentService.TokenData(2, "て", "て", "助詞 / 接続助詞", "て", false),
                        new JapaneseSegmentService.TokenData(3, "その場", "そのば", "名詞 / 一般", "その場", true),
                        new JapaneseSegmentService.TokenData(4, "に", "に", "助詞 / 格助詞", "に", false),
                        new JapaneseSegmentService.TokenData(5, "尻餅", "しりもち", "名詞 / 一般", "尻餅", true),
                        new JapaneseSegmentService.TokenData(6, "を", "を", "助詞 / 格助詞", "を", false),
                        new JapaneseSegmentService.TokenData(7, "つき", "つき", "動詞 / 自立", "つく", false),
                        new JapaneseSegmentService.TokenData(8, "そう", "そう", "名詞 / 接尾 / 助動詞語幹", "そう", false),
                        new JapaneseSegmentService.TokenData(9, "に", "に", "助詞 / 格助詞", "に", false),
                        new JapaneseSegmentService.TokenData(10, "なる", "なる", "動詞 / 自立", "なる", false),
                        new JapaneseSegmentService.TokenData(11, "けど", "けど", "助詞 / 接続助詞", "けど", false),
                        new JapaneseSegmentService.TokenData(12, "、", "、", "記号 / 読点", "、", false),
                        new JapaneseSegmentService.TokenData(13, "どうにか", "どうにか", "副詞 / 一般", "どうにか", false),
                        new JapaneseSegmentService.TokenData(14, "持ちこたえ", "もちこたえ", "動詞 / 自立", "持ちこたえる", true),
                        new JapaneseSegmentService.TokenData(15, "た", "た", "助動詞", "た", false),
                        new JapaneseSegmentService.TokenData(16, "。", "。", "記号 / 句点", "。", false)));
        ReadService readService = new ReadService(
                paragraphMapper,
                translationService,
                japaneseSegmentService,
                grammarAnalysisService,
                new ObjectMapper());

        WordDefinitionRequest request = new WordDefinitionRequest();
        request.setWord("尻餅");
        request.setTokenIndex(5);
        request.setContextText("ドッと疲れてその場に尻餅をつきそうになるけど、どうにか持ちこたえた。");

        WordDefinitionResponse response = readService.defineWord(request);

        assertThat(response.getWord()).isEqualTo("尻餅");
        assertThat(response.getReading()).isEqualTo("しりもち");
        assertThat(response.getMeaning()).isEqualTo("一屁股跌坐在地");
    }

    @Test
    void translateParagraphRejectsImageBlocks() {
        Paragraph paragraph = new Paragraph();
        paragraph.setId(12L);
        paragraph.setBlockType("IMAGE");
        paragraph.setOriginalText("插画");
        when(paragraphMapper.selectById(12L)).thenReturn(paragraph);

        ReadService readService = new ReadService(
                paragraphMapper,
                translationService,
                japaneseSegmentService,
                grammarAnalysisService,
                new ObjectMapper());

        assertThatThrownBy(() -> readService.translateParagraph(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有文字内容支持翻译");
    }
}
