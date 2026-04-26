package com.example.email.web.email;

import com.example.email.common.dto.AttachmentUpload;
import com.example.email.common.dto.EmailDraftRequest;
import com.example.email.common.dto.EmailFull;
import com.example.email.common.dto.EmailSummary;
import com.example.email.common.dto.Folder;
import com.example.email.common.event.EmailEvent;
import com.example.email.web.attachment.AttachmentService;
import com.example.email.web.config.AppProperties;
import com.example.email.web.event.EmailEventPublisher;
import com.example.email.web.folder.FolderRepository;
import com.example.email.web.security.AuthenticatedUser;
import com.example.email.web.user.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmailController {

    private final EmailRepository emails;
    private final FolderRepository folders;
    private final UserRepository users;
    private final AttachmentService attachments;
    private final EmailEventPublisher publisher;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppProperties props;
    private final MeterRegistry meterRegistry;

    public record DraftResponse(UUID emailId, List<AttachmentUpload> attachmentUploads) {}
    public record SendResponse(UUID emailId, String status) {}
    public record MarkReadRequest(boolean isRead) {}
    public record EmailListResponse(List<EmailSummary> items, Instant nextBefore) {}

    // === Read paths ===

    @GetMapping("/folders/{folderId}/emails")
    public EmailListResponse listByFolder(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable UUID folderId,
                                          @RequestParam(defaultValue = "50") int limit,
                                          @RequestParam(required = false) Instant before) {
        var items = emails.listByFolder(user.userId(), folderId, before, Math.min(limit, 200));
        Instant next = items.isEmpty() ? null : items.get(items.size() - 1).receivedAt();
        return new EmailListResponse(items, next);
    }

    @GetMapping("/emails")
    public EmailListResponse listByStatus(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestParam String status,
                                          @RequestParam(defaultValue = "50") int limit) {
        boolean isRead = "read".equalsIgnoreCase(status);
        var items = emails.listByReadStatus(user.userId(), isRead, 3, Math.min(limit, 200));
        return new EmailListResponse(items, null);
    }

    @GetMapping("/emails/{emailId}")
    public EmailFull getOne(@AuthenticationPrincipal AuthenticatedUser user,
                            @PathVariable UUID emailId) {
        String key = cacheKey(user.userId(), emailId);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                Counter.builder("email.cache.hits.total").register(meterRegistry).increment();
                return objectMapper.readValue(cached, EmailFull.class);
            } catch (JsonProcessingException ignored) {}
        }
        Counter.builder("email.cache.misses.total").register(meterRegistry).increment();
        var row = emails.getRow(user.userId(), emailId).orElseThrow();
        var attachmentRefs = row.attachmentKeys.stream()
                .map(k -> new EmailFull.AttachmentRef(displayName(k), k, attachments.presignDownload(k)))
                .toList();
        var full = new EmailFull(
                row.emailId, row.folderId, row.subject, row.body, row.fromAddr,
                row.toAddrs, row.ccAddrs, row.bccAddrs, row.receivedAt, row.isRead, attachmentRefs);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(full),
                    Duration.ofSeconds(props.getCache().getEmailTtlSeconds()));
        } catch (JsonProcessingException ignored) {}
        return full;
    }

    // === Write paths ===

    @PostMapping("/emails/draft")
    public DraftResponse draft(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestBody EmailDraftRequest req) {
        UUID emailId = emails.generateEmailId();
        UUID draftFolderId = folders.findByName(user.userId(), Folder.DRAFTS).orElseThrow().folderId();

        var presigned = new ArrayList<AttachmentUpload>();
        var keys = new ArrayList<String>();
        if (req.attachmentNames() != null) {
            for (String name : req.attachmentNames()) {
                var up = attachments.presignUpload(user.userId(), emailId, name);
                presigned.add(up);
                keys.add(up.key());
            }
        }

        var row = new EmailRepository.EmailRow();
        row.userId = user.userId();
        row.emailId = emailId;
        row.folderId = draftFolderId;
        row.subject = req.subject();
        row.body = req.body();
        row.fromAddr = user.email();
        row.toAddrs = nonNull(req.to());
        row.ccAddrs = nonNull(req.cc());
        row.bccAddrs = nonNull(req.bcc());
        row.receivedAt = Instant.now();
        row.isRead = true;
        row.attachmentKeys = keys;
        row.preview = EmailRepository.preview(req.body());
        emails.create(row);

        return new DraftResponse(emailId, presigned);
    }

    @PostMapping("/emails/{emailId}/send")
    public ResponseEntity<SendResponse> send(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID emailId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        var draft = emails.getRow(user.userId(), emailId).orElseThrow();

        // Validate attachments exist in MinIO
        for (String key : draft.attachmentKeys) {
            if (!attachments.headExists(key)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }

        // Move sender's copy from DRAFTS → SENT
        UUID sentFolderId = folders.findByName(user.userId(), Folder.SENT).orElseThrow().folderId();
        emails.delete(user.userId(), emailId);
        var sentRow = copy(draft);
        sentRow.folderId = sentFolderId;
        sentRow.receivedAt = Instant.now();
        emails.create(sentRow);

        // Deliver to each recipient that exists in our system → INBOX
        Set<String> all = new HashSet<>();
        all.addAll(draft.toAddrs);
        all.addAll(draft.ccAddrs);
        all.addAll(draft.bccAddrs);
        for (String addr : all) {
            users.findByEmail(addr).ifPresent(recipient -> {
                // Eventually consistent read; the recipient may have just signed up.
                // Skip silently if their INBOX isn't visible yet (load test fan-out).
                var inbox = folders.findByName(recipient.userId(), Folder.INBOX).orElse(null);
                if (inbox == null) return;
                var copy = copy(draft);
                copy.userId = recipient.userId();
                copy.emailId = emails.generateEmailId();
                copy.folderId = inbox.folderId();
                copy.isRead = false;
                copy.receivedAt = Instant.now();
                emails.create(copy);
                publisher.publish(new EmailEvent.EmailCreated(
                        UUID.randomUUID(), recipient.userId(), copy.emailId, inbox.folderId(),
                        copy.subject, copy.fromAddr, copy.preview, copy.receivedAt, Instant.now()));
            });
        }

        Counter.builder("email.created.total").register(meterRegistry).increment();
        sample.stop(Timer.builder("email.send.duration").register(meterRegistry));
        return ResponseEntity.accepted().body(new SendResponse(sentRow.emailId, "QUEUED"));
    }

    @DeleteMapping("/emails/{emailId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID emailId) {
        emails.delete(user.userId(), emailId);
        redis.delete(cacheKey(user.userId(), emailId));
        publisher.publish(new EmailEvent.EmailDeleted(
                UUID.randomUUID(), user.userId(), emailId, Instant.now()));
        Counter.builder("email.deleted.total").register(meterRegistry).increment();
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/emails/{emailId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable UUID emailId,
                                         @RequestBody MarkReadRequest req) {
        emails.markRead(user.userId(), emailId, req.isRead());
        redis.delete(cacheKey(user.userId(), emailId));
        publisher.publish(new EmailEvent.EmailReadChanged(
                UUID.randomUUID(), user.userId(), emailId, req.isRead(), Instant.now()));
        Counter.builder("email.read.changed.total")
                .tag("isRead", String.valueOf(req.isRead()))
                .register(meterRegistry)
                .increment();
        return ResponseEntity.noContent().build();
    }

    private static EmailRepository.EmailRow copy(EmailRepository.EmailRow src) {
        var dst = new EmailRepository.EmailRow();
        dst.userId = src.userId;
        dst.emailId = src.emailId;
        dst.folderId = src.folderId;
        dst.subject = src.subject;
        dst.body = src.body;
        dst.fromAddr = src.fromAddr;
        dst.toAddrs = src.toAddrs;
        dst.ccAddrs = src.ccAddrs;
        dst.bccAddrs = src.bccAddrs;
        dst.receivedAt = src.receivedAt;
        dst.isRead = src.isRead;
        dst.attachmentKeys = src.attachmentKeys;
        dst.preview = src.preview;
        return dst;
    }

    private String cacheKey(UUID userId, UUID emailId) {
        return "email:" + userId + ":" + emailId;
    }

    private static String displayName(String key) {
        int idx = key.lastIndexOf('-');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    private static <T> Set<T> nonNull(Set<T> in) { return in == null ? Set.of() : in; }
}
