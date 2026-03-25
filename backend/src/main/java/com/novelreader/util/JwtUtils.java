package com.novelreader.util;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import com.novelreader.security.AuthenticatedUser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public String generateToken(AuthenticatedUser user) {
        return generateAccessToken(user);
    }

    public String generateAccessToken(AuthenticatedUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return buildToken(user, now, expiry, ACCESS_TOKEN_TYPE);
    }

    public String generateRefreshToken(AuthenticatedUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);
        return buildToken(user, now, expiry, REFRESH_TOKEN_TYPE);
    }

    public boolean validateToken(String token) {
        return validateTokenType(token, ACCESS_TOKEN_TYPE);
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenType(token, REFRESH_TOKEN_TYPE);
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public long getExpiration(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    public long getAccessExpirationMillis() {
        return expirationMs;
    }

    public long getRefreshExpirationMillis() {
        return refreshExpirationMs;
    }

    private String buildToken(AuthenticatedUser user, Date now, Date expiry, String tokenType) {
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private boolean validateTokenType(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (Exception exception) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
