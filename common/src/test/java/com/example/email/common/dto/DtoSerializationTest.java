package com.example.email.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void emailSummary_roundtrip() throws Exception {
        EmailSummary summary = new EmailSummary(
                UUID.randomUUID(), UUID.randomUUID(),
                "Hello", "alice@example.com", Set.of("bob@example.com"),
                "preview...", Instant.parse("2026-04-26T10:00:00Z"), false, true
        );
        String json = mapper.writeValueAsString(summary);
        EmailSummary parsed = mapper.readValue(json, EmailSummary.class);
        assertThat(parsed).isEqualTo(summary);
    }

    @Test
    void emailFull_roundtrip() throws Exception {
        EmailFull email = new EmailFull(
                UUID.randomUUID(), UUID.randomUUID(),
                "Hello", "body text",
                "alice@example.com", Set.of("bob@example.com"), Set.of(), Set.of(),
                Instant.parse("2026-04-26T10:00:00Z"), false,
                List.of(new EmailFull.AttachmentRef("file.txt", "att/u/e/x", "https://signed/..."))
        );
        String json = mapper.writeValueAsString(email);
        EmailFull parsed = mapper.readValue(json, EmailFull.class);
        assertThat(parsed).isEqualTo(email);
    }

    @Test
    void folder_roundtrip() throws Exception {
        Folder f = new Folder(UUID.randomUUID(), "INBOX", true, Instant.now());
        assertThat(mapper.readValue(mapper.writeValueAsString(f), Folder.class)).isEqualTo(f);
    }

    @Test
    void userProfile_roundtrip() throws Exception {
        UserProfile p = new UserProfile(UUID.randomUUID(), "x@y", "X", Instant.now());
        assertThat(mapper.readValue(mapper.writeValueAsString(p), UserProfile.class)).isEqualTo(p);
    }
}
