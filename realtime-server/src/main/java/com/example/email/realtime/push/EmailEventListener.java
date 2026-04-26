package com.example.email.realtime.push;

import com.example.email.common.event.EmailEvent;
import com.example.email.realtime.config.RealtimeProperties;
import com.example.email.realtime.session.SessionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventListener {

    private final SessionRegistry registry;
    private final SimpMessagingTemplate template;
    private final StringRedisTemplate redis;
    private final RealtimeProperties props;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "email-events", groupId = "realtime")
    public void onEvent(EmailEvent event) {
        String userId = event.userId().toString();
        String destination = destinationFor(event);
        Object payload = payloadFor(event);

        if (registry.isLocal(userId)) {
            template.convertAndSendToUser(userId, destination, payload);
            Counter.builder("ws.messages.sent.total")
                    .tag("type", event.getClass().getSimpleName())
                    .register(meterRegistry).increment();
            return;
        }

        String otherInstance = registry.lookup(userId);
        if (otherInstance == null || otherInstance.equals(props.getInstanceId())) return;

        try {
            String envelope = objectMapper.writeValueAsString(Map.of(
                    "destination", destination,
                    "userId", userId,
                    "payload", payload));
            redis.convertAndSend("ws:instance:" + otherInstance, envelope);
            Counter.builder("ws.cross.instance.forwards.total")
                    .register(meterRegistry).increment();
        } catch (JsonProcessingException e) {
            log.warn("Failed to forward event for user {}: {}", userId, e.getMessage());
        }
    }

    static String destinationFor(EmailEvent event) {
        return switch (event) {
            case EmailEvent.EmailCreated ignored -> "/queue/email.new";
            case EmailEvent.EmailDeleted ignored -> "/queue/email.deleted";
            case EmailEvent.EmailReadChanged ignored -> "/queue/email.read";
        };
    }

    static Object payloadFor(EmailEvent event) {
        return event;
    }
}
