package com.novelreader.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WordDefinitionResponse {

    private String word;
    private String reading;
    private String partOfSpeech;
    private String meaning;
}
