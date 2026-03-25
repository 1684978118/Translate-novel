package com.novelreader.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChapterResponse {

    private Long id;
    private Integer chapterOrder;
    private String title;
    private Integer paragraphCount;
}
