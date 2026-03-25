package com.novelreader.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookDetailResponse {

    private Long id;
    private String title;
    private String author;
    private String sourceType;
    private String status;
    private Integer wordCount;
    private List<ChapterResponse> chapters;
}
