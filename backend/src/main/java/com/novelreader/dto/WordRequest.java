package com.novelreader.dto;

import javax.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class WordRequest {

    @NotBlank
    private String word;

    private String reading;
    private String meaning;
    private String exampleSentence;
}
