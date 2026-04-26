package com.example.email.common.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfile(UUID userId, String email, String displayName, Instant createdAt) {}
