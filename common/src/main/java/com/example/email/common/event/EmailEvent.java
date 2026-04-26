package com.example.email.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailEvent.EmailCreated.class, name = "EMAIL_CREATED"),
        @JsonSubTypes.Type(value = EmailEvent.EmailDeleted.class, name = "EMAIL_DELETED"),
        @JsonSubTypes.Type(value = EmailEvent.EmailReadChanged.class, name = "EMAIL_READ_CHANGED")
})
public sealed interface EmailEvent {

    UUID eventId();

    UUID userId();

    UUID emailId();

    Instant occurredAt();

    record EmailCreated(
            UUID eventId,
            UUID userId,
            UUID emailId,
            UUID folderId,
            String subject,
            String fromAddr,
            String preview,
            Instant receivedAt,
            Instant occurredAt
    ) implements EmailEvent {}

    record EmailDeleted(
            UUID eventId,
            UUID userId,
            UUID emailId,
            Instant occurredAt
    ) implements EmailEvent {}

    record EmailReadChanged(
            UUID eventId,
            UUID userId,
            UUID emailId,
            boolean isRead,
            Instant occurredAt
    ) implements EmailEvent {}
}
