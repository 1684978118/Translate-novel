package com.novelreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JapaneseSegmentServiceTest {

    private final JapaneseSegmentService japaneseSegmentService = new JapaneseSegmentService();

    @Test
    void toRubyHtmlBuildsPartialRubyForMixedKanjiAndKana() {
        String rubyHtml = japaneseSegmentService.toRubyHtml("受け取る。");

        assertThat(rubyHtml).contains("<ruby>受<rt>う</rt></ruby>");
        assertThat(rubyHtml).contains("け");
        assertThat(rubyHtml).contains("<ruby>取<rt>と</rt></ruby>");
    }

    @Test
    void toRubyHtmlKeepsLeadingKanaAndAnnotatesKanjiBody() {
        String rubyHtml = japaneseSegmentService.toRubyHtml("お礼を言う。");

        assertThat(rubyHtml).contains("お<ruby>礼<rt>れい</rt></ruby>");
        assertThat(rubyHtml).contains("<ruby>言<rt>い</rt></ruby>う");
    }
}
