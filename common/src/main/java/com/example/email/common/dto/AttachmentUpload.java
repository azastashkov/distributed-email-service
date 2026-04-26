package com.example.email.common.dto;

import java.time.Instant;

public record AttachmentUpload(String name, String key, String presignedUrl, Instant expiresAt) {}
