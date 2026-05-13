package com.expenseflow.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
public class JwtUtil {

    private static final String SECRET = "ExpenseFlow2026SecretKeyForJWTTokenGenerationMustBeLongEnough!!";
    private static final long ACCESS_EXPIRE = 2 * 60 * 60 * 1000L; // 2h
    private static final long REFRESH_EXPIRE = 7 * 24 * 60 * 60 * 1000L; // 7d

    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateAccessToken(Long userId, Long tenantId, String tokenId) {
        return generateToken(userId, tenantId, tokenId, ACCESS_EXPIRE);
    }

    public static String generateRefreshToken(Long userId, Long tenantId, String tokenId) {
        return generateToken(userId, tenantId, tokenId, REFRESH_EXPIRE);
    }

    private static String generateToken(Long userId, Long tenantId, String tokenId, long expire) {
        Date now = new Date();
        return Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expire))
                .signWith(getKey())
                .compact();
    }

    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public static Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static Long getTenantId(Claims claims) {
        return claims.get("tenantId", Long.class);
    }

    public static String getTokenId(Claims claims) {
        return claims.getId();
    }

    public static boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
