package com.equitycommittee.voting.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        return generateToken(userId, email, role, ACCESS_TYPE, accessExpirationMs);
    }

    public String generateRefreshToken(UUID userId, String email, String role) {
        return generateToken(userId, email, role, REFRESH_TYPE, refreshExpirationMs);
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private String generateToken(UUID userId, String email, String role, String type, long expirationMs) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Object tokenType = claims.get(TOKEN_TYPE_CLAIM);
            // Backward compatibility for existing access tokens minted before type claim was added.
            return tokenType == null || ACCESS_TYPE.equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid access token: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return REFRESH_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }
}
