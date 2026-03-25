package com.novelreader.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private String token;
    private String refreshToken;
    private Long expiresAt;
    private Long refreshExpiresAt;
    private UserProfileResponse user;
}
