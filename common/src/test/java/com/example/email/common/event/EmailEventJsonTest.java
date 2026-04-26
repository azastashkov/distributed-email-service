package com.example.email.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailEventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void emailCreated_roundtrip_polymorphic() throws Exception {
        EmailEvent event = new EmailEvent.EmailCreated(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "subject", "alice@example.com", "preview body",
                Instant.parse("2026-04-26T10:00:00Z"),
                Instant.parse("2026-04-26T10:00:01Z")
        );

        String json = mapper.writeValueAsString(event);
        EmailEvent parsed = mapper.readValue(json, EmailEvent.class);

        assertThat(parsed).isInstanceOf(EmailEvent.EmailCreated.class);
        assertThat(parsed).isEqualTo(event);
        assertThat(json).contains("\"eventType\":\"EMAIL_CREATED\"");
    }

    @Test
    void emailDeleted_roundtrip() throws Exception {
        EmailEvent event = new EmailEvent.EmailDeleted(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-04-26T10:00:00Z")
        );

        String json = mapper.writeValueAsString(event);
        EmailEvent parsed = mapper.readValue(json, EmailEvent.class);

        assertThat(parsed).isEqualTo(event);
        assertThat(json).contains("\"eventType\":\"EMAIL_DELETED\"");
    }

    @Test
    void emailReadChanged_roundtrip() throws Exception {
        EmailEvent event = new EmailEvent.EmailReadChanged(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true,
                Instant.parse("2026-04-26T10:00:00Z")
        );

        String json = mapper.writeValueAsString(event);
        EmailEvent parsed = mapper.readValue(json, EmailEvent.class);

        assertThat(parsed).isEqualTo(event);
        assertThat(json).contains("\"eventType\":\"EMAIL_READ_CHANGED\"");
    }
}
