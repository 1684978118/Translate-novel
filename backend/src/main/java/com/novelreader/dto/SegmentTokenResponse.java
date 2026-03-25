package com.novelreader.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SegmentTokenResponse {

    private Integer index;
    private String surface;
    private String kana;
    private String partOfSpeech;
    private String baseForm;
    private boolean kanjiToken;
}
