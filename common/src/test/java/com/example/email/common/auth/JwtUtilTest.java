package com.example.email.common.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-this-is-32+ bytes";

    @Test
    void roundtrip_returnsClaims() {
        UUID userId = UUID.randomUUID();
        String token = JwtUtil.issue(userId, "alice@example.com", Duration.ofMinutes(5), SECRET);

        JwtUtil.Claims claims = JwtUtil.parse(token, SECRET);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.expiresAt()).isInTheFuture();
    }

    @Test
    void expired_throws() {
        String token = JwtUtil.issue(UUID.randomUUID(), "alice@example.com", Duration.ofMillis(1), SECRET);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        assertThatThrownBy(() -> JwtUtil.parse(token, SECRET)).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongSecret_throws() {
        String token = JwtUtil.issue(UUID.randomUUID(), "alice@example.com", Duration.ofMinutes(5), SECRET);
        String other = "ffffffffffffffffffffffffffffffff-also-32+ bytes-mm";

        assertThatThrownBy(() -> JwtUtil.parse(token, other)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecret_throws() {
        assertThatThrownBy(() ->
                JwtUtil.issue(UUID.randomUUID(), "x", Duration.ofMinutes(5), "too-short"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
