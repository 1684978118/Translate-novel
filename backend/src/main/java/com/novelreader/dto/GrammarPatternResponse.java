package com.novelreader.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrammarPatternResponse {

    private String name;
    private String explanation;
}
