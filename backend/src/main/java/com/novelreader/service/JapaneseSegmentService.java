package com.novelreader.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Service
public class JapaneseSegmentService {

    private static final Pattern INNER_NON_KANJI_PATTERN = Pattern.compile("[^\\p{IsHan}]+");
    private final Tokenizer tokenizer = new Tokenizer();

    public List<TokenData> segment(String text) {
        List<TokenData> result = new ArrayList<TokenData>();
        int index = 0;
        for (Token token : tokenizer.tokenize(text == null ? "" : text.replace("<br/>", "\n"))) {
            String surface = token.getSurface();
            if ("\n".equals(surface)) {
                result.add(new TokenData(index++, "\n", "", "", "\n", false));
                continue;
            }
            if (surface == null || surface.trim().isEmpty()) {
                continue;
            }

            String reading = token.getReading();
            String kana = normalizeKana(surface, reading);
            String baseForm = token.getBaseForm();
            if (baseForm == null || "*".equals(baseForm)) {
                baseForm = surface;
            }

            result.add(new TokenData(
                    index++,
                    surface,
                    kana,
                    buildPartOfSpeech(token),
                    baseForm,
                    containsKanji(surface)));
        }
        return result;
    }

    public TokenData lookup(String text) {
        List<TokenData> tokens = segment(text);
        return tokens.isEmpty() ? new TokenData(0, text, "", "", text, false) : tokens.get(0);
    }

    public String toRubyHtml(String text) {
        StringBuilder html = new StringBuilder();
        for (TokenData token : segment(text)) {
            if ("\n".equals(token.getSurface())) {
                html.append("<br/>");
                continue;
            }

            if (token.isKanjiToken() && token.getKana() != null && !token.getKana().isEmpty()
                    && !token.getSurface().equals(token.getKana())) {
                html.append(buildRubyHtml(token.getSurface(), token.getKana()));
            } else {
                html.append(HtmlUtils.htmlEscape(token.getSurface()));
            }
        }
        return html.toString();
    }

    private String buildRubyHtml(String surface, String kana) {
        List<RubyPart> parts = new ArrayList<RubyPart>();
        splitRubyParts(surface, kana == null ? "" : kana, parts);

        StringBuilder html = new StringBuilder();
        for (RubyPart part : parts) {
            if (part.getReading() == null || part.getReading().isEmpty()) {
                html.append(HtmlUtils.htmlEscape(part.getText()));
                continue;
            }
            html.append("<ruby>")
                    .append(HtmlUtils.htmlEscape(part.getText()))
                    .append("<rt>")
                    .append(HtmlUtils.htmlEscape(part.getReading()))
                    .append("</rt></ruby>");
        }
        return html.toString();
    }

    private void splitRubyParts(String surface, String reading, List<RubyPart> parts) {
        if (surface == null || surface.isEmpty()) {
            return;
        }

        if (!containsKanji(surface) || reading == null || reading.isEmpty()) {
            parts.add(new RubyPart(surface, ""));
            return;
        }

        if (isAllKanji(surface)) {
            parts.add(new RubyPart(surface, reading));
            return;
        }

        int leadingPlainLength = countLeadingNonKanji(surface);
        if (leadingPlainLength > 0) {
            String leading = surface.substring(0, leadingPlainLength);
            String leadingKana = normalizeSurfaceReading(leading);
            parts.add(new RubyPart(leading, ""));
            if (!leadingKana.isEmpty() && reading.startsWith(leadingKana)) {
                splitRubyParts(surface.substring(leadingPlainLength), reading.substring(leadingKana.length()), parts);
            } else {
                splitRubyParts(surface.substring(leadingPlainLength), reading, parts);
            }
            return;
        }

        int trailingPlainStart = findTrailingNonKanjiStart(surface);
        if (trailingPlainStart < surface.length()) {
            String trailing = surface.substring(trailingPlainStart);
            String trailingKana = normalizeSurfaceReading(trailing);
            String remainingReading = reading;
            if (!trailingKana.isEmpty() && reading.endsWith(trailingKana)) {
                remainingReading = reading.substring(0, reading.length() - trailingKana.length());
            }
            splitRubyParts(surface.substring(0, trailingPlainStart), remainingReading, parts);
            parts.add(new RubyPart(trailing, ""));
            return;
        }

        Matcher matcher = INNER_NON_KANJI_PATTERN.matcher(surface);
        while (matcher.find()) {
            if (matcher.start() == 0 || matcher.end() == surface.length()) {
                continue;
            }

            String separator = matcher.group();
            String separatorKana = normalizeSurfaceReading(separator);
            if (separatorKana.isEmpty()) {
                continue;
            }

            int readingIndex = reading.indexOf(separatorKana);
            if (readingIndex <= 0 || readingIndex >= reading.length() - separatorKana.length()) {
                continue;
            }

            splitRubyParts(surface.substring(0, matcher.start()), reading.substring(0, readingIndex), parts);
            parts.add(new RubyPart(separator, ""));
            splitRubyParts(
                    surface.substring(matcher.end()),
                    reading.substring(readingIndex + separatorKana.length()),
                    parts);
            return;
        }

        parts.add(new RubyPart(surface, reading));
    }

    private String normalizeKana(String surface, String reading) {
        if (reading == null || "*".equals(reading)) {
            return containsKana(surface) ? surface : "";
        }
        return katakanaToHiragana(reading);
    }

    private String buildPartOfSpeech(Token token) {
        StringBuilder builder = new StringBuilder();
        appendFeature(builder, token.getPartOfSpeechLevel1());
        appendFeature(builder, token.getPartOfSpeechLevel2());
        appendFeature(builder, token.getPartOfSpeechLevel3());
        return builder.toString();
    }

    private void appendFeature(StringBuilder builder, String feature) {
        if (feature == null || "*".equals(feature)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" / ");
        }
        builder.append(feature);
    }

    private String katakanaToHiragana(String value) {
        StringBuilder builder = new StringBuilder();
        for (char current : value.toCharArray()) {
            if (current >= '\u30A1' && current <= '\u30F6') {
                builder.append((char) (current - 0x60));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private String normalizeSurfaceReading(String value) {
        StringBuilder builder = new StringBuilder();
        for (char current : value.toCharArray()) {
            if (current >= '\u3041' && current <= '\u3096') {
                builder.append(current);
                continue;
            }
            if (current >= '\u30A1' && current <= '\u30F6') {
                builder.append((char) (current - 0x60));
                continue;
            }
            if (Character.isWhitespace(current) || isJapanesePunctuation(current)) {
                continue;
            }
            return "";
        }
        return builder.toString();
    }

    private boolean isJapanesePunctuation(char current) {
        return "。、・「」『』（）()！？!?,，．.-ー".indexOf(current) >= 0;
    }

    private int countLeadingNonKanji(String value) {
        int index = 0;
        while (index < value.length() && !isKanji(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private int findTrailingNonKanjiStart(String value) {
        int index = value.length();
        while (index > 0 && !isKanji(value.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private boolean isKanji(char current) {
        return String.valueOf(current).matches("\\p{IsHan}");
    }

    private boolean isAllKanji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (char current : text.toCharArray()) {
            if (!isKanji(current)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsKanji(String text) {
        return text != null && text.matches(".*\\p{IsHan}.*");
    }

    private boolean containsKana(String text) {
        return text != null && text.matches(".*[\\p{IsHiragana}\\p{IsKatakana}].*");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenData {
        private int index;
        private String surface;
        private String kana;
        private String partOfSpeech;
        private String baseForm;
        private boolean kanjiToken;
    }

    @Data
    @AllArgsConstructor
    private static class RubyPart {
        private String text;
        private String reading;
    }
}
