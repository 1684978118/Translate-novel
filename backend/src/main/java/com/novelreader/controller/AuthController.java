package com.novelreader.controller;

import javax.validation.Valid;

import com.novelreader.common.ApiResponse;
import com.novelreader.dto.AuthResponse;
import com.novelreader.dto.LoginRequest;
import com.novelreader.dto.RefreshRequest;
import com.novelreader.dto.RegisterRequest;
import com.novelreader.dto.UserProfileResponse;
import com.novelreader.security.AuthenticatedUser;
import com.novelreader.service.AuthService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("register success", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("login success", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success("refresh success", authService.refresh(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(authService.me(user));
    }
}
