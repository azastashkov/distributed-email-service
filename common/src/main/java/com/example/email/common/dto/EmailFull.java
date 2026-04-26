package com.example.email.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record EmailFull(
        UUID emailId,
        UUID folderId,
        String subject,
        String body,
        String fromAddr,
        Set<String> toAddrs,
        Set<String> ccAddrs,
        Set<String> bccAddrs,
        Instant receivedAt,
        boolean isRead,
        List<AttachmentRef> attachments
) {
    public record AttachmentRef(String name, String key, String downloadUrl) {}
}
