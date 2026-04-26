package com.example.email.web.event;

import com.example.email.common.event.EmailEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventPublisher {

    public static final String TOPIC = "email-events";

    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public void publish(EmailEvent event) {
        kafkaTemplate.send(TOPIC, event.userId().toString(), event);
        Counter.builder("email.events.published.total")
                .tag("type", event.getClass().getSimpleName())
                .register(meterRegistry)
                .increment();
        log.debug("Published {} for user {}", event.getClass().getSimpleName(), event.userId());
    }
}
