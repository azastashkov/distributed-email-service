package com.example.email.realtime.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.email.realtime.config.RealtimeProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrossInstancePushListener {

    private final RedisConnectionFactory connectionFactory;
    private final SimpMessagingTemplate template;
    private final RealtimeProperties props;
    private final ObjectMapper objectMapper;
    private RedisMessageListenerContainer container;

    @PostConstruct
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener = (message, pattern) -> {
            try {
                JsonNode env = objectMapper.readTree(message.getBody());
                String userId = env.get("userId").asText();
                String destination = env.get("destination").asText();
                Object payload = objectMapper.treeToValue(env.get("payload"), Object.class);
                template.convertAndSendToUser(userId, destination, payload);
            } catch (IOException e) {
                log.warn("Bad cross-instance message: {}", e.getMessage());
            }
        };
        container.addMessageListener(listener, new ChannelTopic("ws:instance:" + props.getInstanceId()));
        container.afterPropertiesSet();
        container.start();
        log.info("Subscribed to cross-instance channel ws:instance:{}", props.getInstanceId());
    }

    @PreDestroy
    public void stop() {
        if (container != null) container.stop();
    }
}
