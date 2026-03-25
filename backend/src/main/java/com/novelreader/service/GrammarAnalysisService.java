package com.novelreader.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.novelreader.dto.GrammarPatternResponse;
import com.novelreader.dto.GrammarResponse;
import com.novelreader.service.JapaneseSegmentService.TokenData;

import org.springframework.stereotype.Service;

@Service
public class GrammarAnalysisService {

    private final JapaneseSegmentService japaneseSegmentService;

    public GrammarAnalysisService(JapaneseSegmentService japaneseSegmentService) {
        this.japaneseSegmentService = japaneseSegmentService;
    }

    public GrammarResponse analyze(Long paragraphId, String text) {
        String normalizedText = normalizeText(text);
        List<TokenData> tokens = japaneseSegmentService.segment(normalizedText);
        List<GrammarPatternResponse> patterns = new ArrayList<GrammarPatternResponse>();

        addEndingPatterns(patterns, normalizedText);
        addConstructionPatterns(patterns, normalizedText);
        addParticlePattern(patterns, tokens);
        addSentenceFramePattern(patterns, tokens);

        if (patterns.isEmpty()) {
            addPattern(patterns, "句子骨架", "本句没有明显的固定句型，建议先顺着助词和句末形式抓主干。");
        }

        return GrammarResponse.builder()
                .paragraphId(paragraphId)
                .summary(buildSummary(patterns))
                .patterns(patterns)
                .build();
    }

    private void addEndingPatterns(List<GrammarPatternResponse> patterns, String text) {
        String sentenceEnding = trimSentenceEnding(text);
        if (sentenceEnding.isEmpty()) {
            return;
        }

        if (containsAny(sentenceEnding, "です", "ます", "でした", "ました", "ません", "ませんでした", "でしょう", "ましょう")) {
            addPattern(patterns, "礼貌体", "句尾出现 です / ます 一类形式，整体语气偏礼貌、较正式。");
        }
        if (containsAny(sentenceEnding, "ない", "ません", "ぬ", "ず", "なかった", "ませんでした")) {
            addPattern(patterns, "否定形", "句中出现 ない / ません 一类形式，通常表示否定、未发生或不具备。");
        }
        if (endsWithAny(sentenceEnding, "た", "だ", "でした", "ました", "なかった", "ませんでした")) {
            addPattern(patterns, "过去式", "句尾用了过去形式，通常表示动作已经发生，或状态在过去成立。");
        }
        if (endsWithAny(sentenceEnding, "か", "ですか", "ますか", "の", "のか", "かな")) {
            addPattern(patterns, "疑问/确认", "句尾带有疑问色彩，可能是在提问、确认，或带一点自问自答的语气。");
        }
        if (endsWithAny(sentenceEnding, "よ", "ね", "よね", "な")) {
            addPattern(patterns, "句末语气", "句尾词带出说话人的态度，常见于强调、确认或征求共鸣。");
        }
    }

    private void addConstructionPatterns(List<GrammarPatternResponse> patterns, String text) {
        if (containsAny(text, "ている", "でいる", "ています", "でいます", "ていた", "でいた")) {
            addPattern(patterns, "进行/状态", "出现 〜ている，常表示动作正在进行，或某个结果状态持续存在。");
        }
        if (containsAny(text, "てください", "でください")) {
            addPattern(patterns, "请求表达", "〜てください 常用来表示请求、提醒或比较客气的指令。");
        }
        if (containsAny(text, "てもいい", "でもいい", "てもよい", "でもよい")) {
            addPattern(patterns, "许可表达", "〜てもいい 表示“可以做……”，是在给出许可或允许。");
        }
        if (containsAny(text, "てはいけない", "ではいけない", "ちゃいけない", "てはならない", "ではならない")) {
            addPattern(patterns, "禁止表达", "这一类句型表示“不可以……”，语气通常是在禁止或约束。");
        }
        if (containsAny(text, "ながら")) {
            addPattern(patterns, "同时进行", "〜ながら 常表示两个动作并行，通常可理解成“一边……一边……”。");
        }
        if (containsAny(text, "たり")) {
            addPattern(patterns, "列举动作", "〜たり〜たり 常用来举若干代表性动作，语气通常不是完整穷尽。");
        }
        if (containsAny(text, "ので")) {
            addPattern(patterns, "原因/理由", "〜ので 往往用来说明原因，语气通常比 から 更平缓一些。");
        } else if (containsAny(text, "から")) {
            addPattern(patterns, "原因/起点线索", "句中出现 から，它既可能表示原因，也可能表示时间或空间上的起点。");
        }
        if (containsAny(text, "けど", "けれど", "けれども", "のに")) {
            addPattern(patterns, "转折关系", "这里有转折或逆接线索，后半句通常会和前半句形成对比。");
        }
        if (containsAny(text, "ために", "ように")) {
            addPattern(patterns, "目的/结果", "〜ために / 〜ように 常引出目的、努力方向，或希望达到的结果状态。");
        }
        if (containsAny(text, "と思", "と言")) {
            addPattern(patterns, "引用内容", "と 常把前面的内容当作引用、想法或说话内容来看。");
        }
    }

    private void addParticlePattern(List<GrammarPatternResponse> patterns, List<TokenData> tokens) {
        Map<String, String> hints = new LinkedHashMap<String, String>();
        for (TokenData token : tokens) {
            if (!isParticle(token)) {
                continue;
            }

            String surface = token.getSurface();
            if ("は".equals(surface)) {
                hints.put("は", "提示主题");
            } else if ("が".equals(surface)) {
                hints.put("が", "标记主语或新信息");
            } else if ("を".equals(surface)) {
                hints.put("を", "标记动作对象");
            } else if ("に".equals(surface)) {
                hints.put("に", "标记时间点、到达点或对象");
            } else if ("で".equals(surface)) {
                hints.put("で", "标记动作发生的场所或手段");
            } else if ("へ".equals(surface)) {
                hints.put("へ", "强调动作方向");
            } else if ("と".equals(surface)) {
                hints.put("と", "可表示并列、共同动作或引用");
            } else if ("も".equals(surface)) {
                hints.put("も", "表示“也”或补充同类信息");
            } else if ("から".equals(surface)) {
                hints.put("から", "可表示起点，也可能表示原因");
            } else if ("まで".equals(surface)) {
                hints.put("まで", "表示终点、范围或界限");
            } else if ("より".equals(surface)) {
                hints.put("より", "常用来引出比较基准");
            } else if ("の".equals(surface)) {
                hints.put("の", "连接名词，形成修饰或所属关系");
            }
        }

        if (hints.isEmpty()) {
            return;
        }

        List<String> fragments = new ArrayList<String>();
        for (Map.Entry<String, String> entry : hints.entrySet()) {
            fragments.add(entry.getKey() + "（" + entry.getValue() + "）");
            if (fragments.size() >= 4) {
                break;
            }
        }

        addPattern(
                patterns,
                "助词线索",
                "句中可见 " + joinWithDelimiter(fragments, "、") + "，可以先据此抓住句子成分关系。");
    }

    private void addSentenceFramePattern(List<GrammarPatternResponse> patterns, List<TokenData> tokens) {
        List<String> roles = new ArrayList<String>();
        if (hasParticle(tokens, "は")) {
            roles.add("主题");
        }
        if (hasParticle(tokens, "が")) {
            roles.add("主语");
        }
        if (hasParticle(tokens, "を")) {
            roles.add("对象");
        }
        if (hasAnyParticle(tokens, "に", "へ", "で")) {
            roles.add("补语");
        }

        TokenData predicate = findPredicate(tokens);
        if (predicate == null && roles.isEmpty()) {
            return;
        }

        StringBuilder explanation = new StringBuilder();
        if (!roles.isEmpty()) {
            explanation.append("本句大致可按“")
                    .append(joinWithDelimiter(roles, " + "))
                    .append(" + 谓语”来抓主干");
        } else {
            explanation.append("本句可以先从句末谓语回头找前面的成分关系");
        }

        if (predicate != null) {
            String predicateWord = predicate.getBaseForm();
            if (predicateWord == null || predicateWord.trim().isEmpty() || "*".equals(predicateWord)) {
                predicateWord = predicate.getSurface();
            }
            explanation.append("，句末核心谓语大致是「").append(predicateWord).append("」");
        }

        explanation.append("。");
        addPattern(patterns, "句子骨架", explanation.toString());
    }

    private TokenData findPredicate(List<TokenData> tokens) {
        for (int index = tokens.size() - 1; index >= 0; index--) {
            TokenData token = tokens.get(index);
            if (token == null || isPunctuation(token)) {
                continue;
            }

            String partOfSpeech = token.getPartOfSpeech() == null ? "" : token.getPartOfSpeech();
            if (partOfSpeech.startsWith("動詞") || partOfSpeech.startsWith("形容詞")) {
                return token;
            }
            if (partOfSpeech.startsWith("名詞") && index < tokens.size() - 1 && followsCopula(tokens, index)) {
                return token;
            }
        }
        return null;
    }

    private boolean followsCopula(List<TokenData> tokens, int index) {
        for (int nextIndex = index + 1; nextIndex < tokens.size(); nextIndex++) {
            TokenData next = tokens.get(nextIndex);
            if (next == null || isPunctuation(next)) {
                continue;
            }

            String surface = next.getSurface();
            return "だ".equals(surface) || "です".equals(surface) || "だった".equals(surface) || "でした".equals(surface);
        }
        return false;
    }

    private boolean hasParticle(List<TokenData> tokens, String value) {
        for (TokenData token : tokens) {
            if (isParticle(token) && value.equals(token.getSurface())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyParticle(List<TokenData> tokens, String... values) {
        LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        for (String value : values) {
            candidates.add(value);
        }

        for (TokenData token : tokens) {
            if (isParticle(token) && candidates.contains(token.getSurface())) {
                return true;
            }
        }
        return false;
    }

    private boolean isParticle(TokenData token) {
        return token != null
                && token.getSurface() != null
                && token.getPartOfSpeech() != null
                && token.getPartOfSpeech().startsWith("助詞");
    }

    private boolean isPunctuation(TokenData token) {
        return token != null
                && token.getPartOfSpeech() != null
                && token.getPartOfSpeech().startsWith("記号");
    }

    private String buildSummary(List<GrammarPatternResponse> patterns) {
        if (patterns.isEmpty()) {
            return "当前句子没有识别到明显语法线索。";
        }

        List<String> names = new ArrayList<String>();
        for (GrammarPatternResponse pattern : patterns) {
            names.add(pattern.getName());
            if (names.size() >= 3) {
                break;
            }
        }
        return "识别到 " + patterns.size() + " 条语法线索，可优先关注 " + joinWithDelimiter(names, "、") + "。";
    }

    private void addPattern(List<GrammarPatternResponse> patterns, String name, String explanation) {
        if (name == null || name.trim().isEmpty() || explanation == null || explanation.trim().isEmpty()) {
            return;
        }

        for (GrammarPatternResponse pattern : patterns) {
            if (name.equals(pattern.getName())) {
                return;
            }
        }

        patterns.add(GrammarPatternResponse.builder()
                .name(name)
                .explanation(explanation)
                .build());
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean endsWithAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.endsWith(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace("<br/>", "\n").trim();
    }

    private String trimSentenceEnding(String text) {
        String result = text == null ? "" : text.trim();
        while (!result.isEmpty()) {
            char current = result.charAt(result.length() - 1);
            if ("。！？!?…」』）) ".indexOf(current) < 0) {
                break;
            }
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String joinWithDelimiter(List<String> items, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                builder.append(delimiter);
            }
            builder.append(items.get(index));
        }
        return builder.toString();
    }
}
