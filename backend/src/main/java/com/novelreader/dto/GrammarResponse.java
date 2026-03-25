package com.novelreader.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrammarResponse {

    private Long paragraphId;
    private String summary;
    private List<GrammarPatternResponse> patterns;
}
