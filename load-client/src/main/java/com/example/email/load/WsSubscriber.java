package com.example.email.load;

import com.example.email.load.ScenarioRunner.VirtualUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class WsSubscriber {

    private final LoadProperties props;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    private WebSocketStompClient client;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    private static final MappingJackson2MessageConverter CONVERTER = new MappingJackson2MessageConverter();

    public synchronized void openFor(VirtualUser u) {
        if (client == null) {
            scheduler.setPoolSize(2);
            scheduler.initialize();
            client = new WebSocketStompClient(new StandardWebSocketClient());
            client.setMessageConverter(CONVERTER);
            client.setTaskScheduler(scheduler);
            client.setDefaultHeartbeat(new long[]{15_000, 15_000});
        }

        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + u.token);
        client.connectAsync(props.getTargetWsUrl(), (WebSocketHttpHeaders) null, connect, new SessionHandler(u))
                .orTimeout(15, TimeUnit.SECONDS)
                .whenComplete((session, err) -> {
                    if (err != null) {
                        log.warn("WS connect failed for {}: {}", u.email, err.toString());
                        Counter.builder("loadclient.ws.connect.errors").register(meterRegistry).increment();
                    } else {
                        Counter.builder("loadclient.ws.connect.ok").register(meterRegistry).increment();
                    }
                });
    }

    private final class SessionHandler extends StompSessionHandlerAdapter {
        private final VirtualUser user;
        SessionHandler(VirtualUser user) { this.user = user; }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            session.subscribe("/user/queue/email.new", new FrameHandler());
        }

        @Override
        public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            log.warn("STOMP error for {}: {}", user.email, exception.toString());
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            log.debug("WS transport error for {}: {}", user.email, exception.toString());
        }
    }

    private final class FrameHandler implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) { return Map.class; }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            Counter.builder("loadclient.ws.received.total").register(meterRegistry).increment();
            try {
                Map<String, Object> m = (Map<String, Object>) payload;
                String occurredAt = (String) m.get("occurredAt");
                if (occurredAt != null) {
                    Instant t = Instant.parse(occurredAt);
                    Duration lag = Duration.between(t, Instant.now());
                    Timer.builder("loadclient.ws.delivery.lag")
                            .publishPercentileHistogram()
                            .register(meterRegistry)
                            .record(lag);
                }
            } catch (Exception ignored) {}
        }
    }
}
