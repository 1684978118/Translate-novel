package com.novelreader.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class ProgressRequest {

    @NotNull
    private Long chapterId;

    @NotNull
    private Long paragraphId;

    private Integer scrollPosition = 0;
}
