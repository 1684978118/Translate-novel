package com.novelreader.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProgressResponse {

    private Long bookId;
    private Long chapterId;
    private Long paragraphId;
    private Integer scrollPosition;
}
