package com.expenseflow.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class JwtUtil {

    private static final String SECRET = "ExpenseFlow2026SecretKeyForJWTTokenGenerationMustBeLongEnough!!";
    private static final long ACCESS_EXPIRE = 2 * 60 * 60 * 1000L; // 2h
    private static final long REFRESH_EXPIRE = 7 * 24 * 60 * 60 * 1000L; // 7d

    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateAccessToken(Long userId, Long tenantId, String tokenId,
                                              List<String> roles, List<String> permissions, String username) {
        return generateToken(userId, tenantId, tokenId, roles, permissions, username, ACCESS_EXPIRE);
    }

    public static String generateRefreshToken(Long userId, Long tenantId, String tokenId,
                                               List<String> roles, List<String> permissions, String username) {
        return generateToken(userId, tenantId, tokenId, roles, permissions, username, REFRESH_EXPIRE);
    }

    private static String generateToken(Long userId, Long tenantId, String tokenId,
                                         List<String> roles, List<String> permissions, String username, long expire) {
        Date now = new Date();
        return Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("roles", roles != null ? roles : Collections.emptyList())
                .claim("permissions", permissions != null ? permissions : Collections.emptyList())
                .claim("username", username != null ? username : "")
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
        if (claims == null) return null;
        return Long.valueOf(claims.getSubject());
    }

    public static Long getTenantId(Claims claims) {
        if (claims == null) return null;
        return claims.get("tenantId", Long.class);
    }

    public static String getTokenId(Claims claims) {
        if (claims == null) return null;
        return claims.getId();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getRoles(Claims claims) {
        if (claims == null) return Collections.emptyList();
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getPermissions(Claims claims) {
        if (claims == null) return Collections.emptyList();
        Object permsObj = claims.get("permissions");
        if (permsObj instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static String getUsername(Claims claims) {
        if (claims == null) return null;
        return claims.get("username", String.class);
    }

    public static boolean isExpired(Claims claims) {
        if (claims == null || claims.getExpiration() == null) return true;
        return claims.getExpiration().before(new Date());
    }
}
