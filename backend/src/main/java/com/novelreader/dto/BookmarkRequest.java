package com.novelreader.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class BookmarkRequest {

    @NotNull
    private Long bookId;

    @NotNull
    private Long paragraphId;

    private String note;
}
