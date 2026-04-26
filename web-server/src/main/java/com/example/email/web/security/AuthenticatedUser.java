package com.example.email.web.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email) {}
