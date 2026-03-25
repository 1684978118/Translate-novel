package com.novelreader.service;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelreader.dto.AuthResponse;
import com.novelreader.dto.LoginRequest;
import com.novelreader.dto.RefreshRequest;
import com.novelreader.dto.RegisterRequest;
import com.novelreader.dto.UserProfileResponse;
import com.novelreader.entity.User;
import com.novelreader.exception.BusinessException;
import com.novelreader.mapper.UserMapper;
import com.novelreader.security.AuthenticatedUser;
import com.novelreader.util.JwtUtils;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtUtils jwtUtils) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest request) {
        if (userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, request.getUsername())) != null) {
            throw new BusinessException("Username already exists");
        }
        if (userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, request.getEmail())) != null) {
            throw new BusinessException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        return issueTokens(new AuthenticatedUser(user));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        if (!jwtUtils.validateRefreshToken(request.getRefreshToken())) {
            throw new BusinessException("Refresh token is invalid or expired");
        }

        User user = userMapper.selectById(jwtUtils.getUserId(request.getRefreshToken()));
        if (user == null) {
            throw new BusinessException("User not found");
        }

        return issueTokens(new AuthenticatedUser(user));
    }

    public UserProfileResponse me(AuthenticatedUser user) {
        return toProfile(user.getUser());
    }

    private AuthResponse issueTokens(AuthenticatedUser user) {
        String accessToken = jwtUtils.generateAccessToken(user);
        String refreshToken = jwtUtils.generateRefreshToken(user);
        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(jwtUtils.getExpiration(accessToken))
                .refreshExpiresAt(jwtUtils.getExpiration(refreshToken))
                .user(toProfile(user.getUser()))
                .build();
    }

    private UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
