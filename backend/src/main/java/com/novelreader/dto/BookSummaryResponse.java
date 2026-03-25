package com.novelreader.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookSummaryResponse {

    private Long id;
    private String title;
    private String author;
    private String sourceType;
    private String status;
    private Integer wordCount;
    private Integer chapterCount;
}
