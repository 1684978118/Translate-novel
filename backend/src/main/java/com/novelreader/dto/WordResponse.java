package com.novelreader.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WordResponse {

    private Long id;
    private String word;
    private String reading;
    private String meaning;
    private String exampleSentence;
    private LocalDateTime createdAt;
}
