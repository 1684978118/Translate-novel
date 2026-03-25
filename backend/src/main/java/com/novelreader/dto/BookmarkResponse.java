package com.novelreader.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookmarkResponse {

    private Long id;
    private Long bookId;
    private Long paragraphId;
    private String bookTitle;
    private String paragraphPreview;
    private String note;
    private LocalDateTime createdAt;
}
