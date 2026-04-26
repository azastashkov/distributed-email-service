package com.example.email.common.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EmailSummary(
        UUID emailId,
        UUID folderId,
        String subject,
        String fromAddr,
        Set<String> toAddrs,
        String preview,
        Instant receivedAt,
        boolean isRead,
        boolean hasAttachments
) {}
