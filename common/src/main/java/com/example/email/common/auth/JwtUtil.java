package com.example.email.common.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class JwtUtil {

    private JwtUtil() {}

    public record Claims(UUID userId, String email, Instant expiresAt) {}

    public static String issue(UUID userId, String email, Duration ttl, String secret) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key(secret), Jwts.SIG.HS256)
                .compact();
    }

    public static Claims parse(String token, String secret) {
        var c = Jwts.parser()
                .verifyWith(key(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new Claims(
                UUID.fromString(c.getSubject()),
                c.get("email", String.class),
                c.getExpiration().toInstant()
        );
    }

    private static SecretKey key(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
