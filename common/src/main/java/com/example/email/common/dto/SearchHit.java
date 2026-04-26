package com.example.email.common.dto;

import java.util.UUID;

public record SearchHit(UUID emailId, String subject, String snippet, double score) {}
