package com.novelreader.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WordDefinitionRequest {

    private String word;
    private String contextText;
    private Integer tokenIndex;
}
