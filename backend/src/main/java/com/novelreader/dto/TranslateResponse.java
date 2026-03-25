package com.novelreader.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TranslateResponse {

    private Long paragraphId;
    private String translation;
    private String provider;
}
