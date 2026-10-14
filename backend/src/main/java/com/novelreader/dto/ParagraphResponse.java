package com.novelreader.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParagraphResponse {

    private Long id;
    private Integer paragraphOrder;
    private String blockType;
    private String originalText;
    private String translation;
    private String furiganaText;
    private String imageUrl;
    private List<SegmentTokenResponse> tokens;
}
