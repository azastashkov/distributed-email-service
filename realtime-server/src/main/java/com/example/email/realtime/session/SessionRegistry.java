package com.example.email.realtime.session;

import com.example.email.realtime.config.RealtimeProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionRegistry {

    private final StringRedisTemplate redis;
    private final RealtimeProperties props;
    private final MeterRegistry meterRegistry;

    /** userId → Set of local sessionIds (a user may have many tabs/devices) */
    private final Map<String, Set<String>> userToSessions = new ConcurrentHashMap<>();

    @PostConstruct
    void registerMetric() {
        Gauge.builder("ws.sessions.active", userToSessions, m -> m.values().stream().mapToInt(Set::size).sum())
                .register(meterRegistry);
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        var user = event.getUser();
        var simpAcc = org.springframework.messaging.simp.stomp.StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = simpAcc.getSessionId();
        if (user == null || sessionId == null) return;
        register(user.getName(), sessionId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        var user = event.getUser();
        if (user == null) return;
        unregister(user.getName(), event.getSessionId());
    }

    public void register(String userId, String sessionId) {
        userToSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        redis.opsForValue().set(redisKey(userId), props.getInstanceId(),
                Duration.ofSeconds(props.getSessionTtlSeconds()));
        log.debug("registered user={} session={} on {}", userId, sessionId, props.getInstanceId());
    }

    public void unregister(String userId, String sessionId) {
        Set<String> sessions = userToSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userToSessions.remove(userId);
                redis.delete(redisKey(userId));
                log.debug("unregistered last session for user={} on {}", userId, props.getInstanceId());
            }
        }
    }

    public boolean isLocal(String userId) {
        return userToSessions.containsKey(userId);
    }

    public String lookup(String userId) {
        return redis.opsForValue().get(redisKey(userId));
    }

    @Scheduled(fixedRate = 60_000)
    public void heartbeatRefresh() {
        if (userToSessions.isEmpty()) return;
        Duration ttl = Duration.ofSeconds(props.getSessionTtlSeconds());
        for (String userId : userToSessions.keySet()) {
            redis.expire(redisKey(userId), ttl);
        }
    }

    private String redisKey(String userId) {
        return "ws:user:" + userId;
    }
}
