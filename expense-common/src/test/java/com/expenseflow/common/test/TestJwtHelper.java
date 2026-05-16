package com.expenseflow.common.test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

public class TestJwtHelper {

    private static final String SECRET = "test-secret-key-min-256-bits-long-enough-for-hs256-algorithm";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public static String generateToken(Long userId, Long tenantId, String... roles) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("tenantId", tenantId)
            .claim("roles", List.of(roles))
            .claim("username", "test-user")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(KEY)
            .compact();
    }

    public static String authHeader(Long userId, String... roles) {
        return "Bearer " + generateToken(userId, 0L, roles);
    }
}
