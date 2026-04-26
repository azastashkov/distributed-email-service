package com.example.email.common.dto;

import java.util.List;
import java.util.Set;

public record EmailDraftRequest(
        Set<String> to,
        Set<String> cc,
        Set<String> bcc,
        String subject,
        String body,
        List<String> attachmentNames
) {}
