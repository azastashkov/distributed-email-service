package com.example.email.common.dto;

import java.time.Instant;
import java.util.UUID;

public record Folder(UUID folderId, String name, boolean systemFolder, Instant createdAt) {

    public static final String INBOX = "INBOX";
    public static final String SENT = "SENT";
    public static final String DRAFTS = "DRAFTS";
    public static final String TRASH = "TRASH";
}
