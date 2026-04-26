package com.example.email.realtime.security;

import com.example.email.common.auth.JwtUtil;
import com.example.email.realtime.config.RealtimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtChannelInterceptorTest {

    private JwtChannelInterceptor interceptor;
    private RealtimeProperties props;

    @BeforeEach void setup() {
        props = new RealtimeProperties();
        props.getJwt().setSecret("0123456789abcdef0123456789abcdef-ok");
        interceptor = new JwtChannelInterceptor(props);
    }

    @Test
    void connect_rejectedWithoutHeader() {
        var msg = stomp(StompCommand.CONNECT, null);
        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connect_rejectedWithInvalidToken() {
        var msg = stomp(StompCommand.CONNECT, "Bearer not-a-jwt");
        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connect_acceptedWithValidToken_setsPrincipal() {
        UUID uid = UUID.randomUUID();
        String token = JwtUtil.issue(uid, "x@y", Duration.ofMinutes(5), props.getJwt().getSecret());

        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
        acc.setLeaveMutable(true);
        acc.setNativeHeader("Authorization", "Bearer " + token);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());

        interceptor.preSend(msg, null);

        // The interceptor mutated the accessor; re-read principal from it.
        assertThat(acc.getUser()).isNotNull();
        assertThat(acc.getUser().getName()).isEqualTo(uid.toString());
    }

    private Message<?> stomp(StompCommand cmd, String authHeader) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        acc.setLeaveMutable(true);
        if (authHeader != null) acc.setNativeHeader("Authorization", authHeader);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }
}
