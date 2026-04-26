package com.example.email.realtime.security;

import com.example.email.common.auth.JwtUtil;
import com.example.email.realtime.config.RealtimeProperties;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final RealtimeProperties props;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                log.debug("CONNECT rejected: no bearer");
                throw new IllegalArgumentException("Missing bearer token");
            }
            try {
                JwtUtil.Claims c = JwtUtil.parse(token.substring(7), props.getJwt().getSecret());
                accessor.setUser(new UserPrincipal(c.userId().toString(), c.email()));
                log.debug("CONNECT accepted for {}", c.userId());
            } catch (JwtException e) {
                throw new IllegalArgumentException("Invalid token", e);
            }
        }
        return message;
    }

    public record UserPrincipal(String name, String email) implements Principal {
        @Override public String getName() { return name; }
    }
}
