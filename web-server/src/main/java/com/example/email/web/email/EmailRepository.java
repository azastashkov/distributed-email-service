package com.example.email.web.email;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.example.email.common.dto.EmailFull;
import com.example.email.common.dto.EmailSummary;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class EmailRepository {

    private final CqlSession session;

    private PreparedStatement insertById;
    private PreparedStatement insertByFolder;
    private PreparedStatement insertByReadStatus;

    private PreparedStatement deleteById;
    private PreparedStatement deleteByFolder;
    private PreparedStatement deleteByReadStatus;

    private PreparedStatement getById;
    private PreparedStatement listByFolder;
    private PreparedStatement listByFolderBefore;
    private PreparedStatement listByReadStatus;

    private PreparedStatement updateReadById;
    private PreparedStatement updateReadByFolder;

    @PostConstruct
    void prepare() {
        insertById = session.prepare("""
            INSERT INTO emails_by_id
            (user_id, email_id, folder_id, subject, body, from_addr, to_addrs, cc_addrs, bcc_addrs,
             received_at, is_read, attachment_keys)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""");

        insertByFolder = session.prepare("""
            INSERT INTO emails_by_folder
            (user_id, folder_id, received_at, email_id, subject, from_addr, to_addrs, preview, has_attachments, is_read)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""");

        insertByReadStatus = session.prepare("""
            INSERT INTO emails_by_read_status
            (user_id, is_read, received_month, received_at, email_id, folder_id, subject, from_addr, preview)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""");

        deleteById         = session.prepare("DELETE FROM emails_by_id WHERE user_id = ? AND email_id = ?");
        deleteByFolder     = session.prepare("DELETE FROM emails_by_folder WHERE user_id = ? AND folder_id = ? AND received_at = ? AND email_id = ?");
        deleteByReadStatus = session.prepare("DELETE FROM emails_by_read_status WHERE user_id = ? AND is_read = ? AND received_month = ? AND received_at = ? AND email_id = ?");

        getById = session.prepare("""
            SELECT user_id, email_id, folder_id, subject, body, from_addr, to_addrs, cc_addrs, bcc_addrs,
                   received_at, is_read, attachment_keys
            FROM emails_by_id WHERE user_id = ? AND email_id = ?""");

        listByFolder = session.prepare("""
            SELECT user_id, folder_id, received_at, email_id, subject, from_addr, to_addrs, preview, has_attachments, is_read
            FROM emails_by_folder WHERE user_id = ? AND folder_id = ? LIMIT ?""");

        listByFolderBefore = session.prepare("""
            SELECT user_id, folder_id, received_at, email_id, subject, from_addr, to_addrs, preview, has_attachments, is_read
            FROM emails_by_folder WHERE user_id = ? AND folder_id = ? AND received_at < ? LIMIT ?""");

        listByReadStatus = session.prepare("""
            SELECT user_id, is_read, received_month, received_at, email_id, folder_id, subject, from_addr, preview
            FROM emails_by_read_status WHERE user_id = ? AND is_read = ? AND received_month = ? LIMIT ?""");

        updateReadById     = session.prepare("UPDATE emails_by_id SET is_read = ? WHERE user_id = ? AND email_id = ?");
        updateReadByFolder = session.prepare("UPDATE emails_by_folder SET is_read = ? WHERE user_id = ? AND folder_id = ? AND received_at = ? AND email_id = ?");
    }

    public UUID generateEmailId() {
        return Uuids.timeBased();
    }

    public void create(EmailRow row) {
        var batch = BatchStatement.builder(BatchType.LOGGED)
                .addStatement(insertById.bind(
                        row.userId, row.emailId, row.folderId, row.subject, row.body, row.fromAddr,
                        row.toAddrs, row.ccAddrs, row.bccAddrs, row.receivedAt, row.isRead, row.attachmentKeys))
                .addStatement(insertByFolder.bind(
                        row.userId, row.folderId, row.receivedAt, row.emailId, row.subject, row.fromAddr,
                        row.toAddrs, row.preview, !row.attachmentKeys.isEmpty(), row.isRead))
                .addStatement(insertByReadStatus.bind(
                        row.userId, row.isRead, monthBucket(row.receivedAt), row.receivedAt, row.emailId,
                        row.folderId, row.subject, row.fromAddr, row.preview))
                .build();
        session.execute(batch);
    }

    public Optional<EmailFull> get(UUID userId, UUID emailId) {
        Row row = session.execute(getById.bind(userId, emailId)).one();
        if (row == null) return Optional.empty();
        return Optional.of(toFull(row));
    }

    public Optional<EmailRow> getRow(UUID userId, UUID emailId) {
        Row row = session.execute(getById.bind(userId, emailId)).one();
        if (row == null) return Optional.empty();
        return Optional.of(EmailRow.from(row));
    }

    public void delete(UUID userId, UUID emailId) {
        var existing = getRow(userId, emailId).orElse(null);
        if (existing == null) return;
        var batch = BatchStatement.builder(BatchType.LOGGED)
                .addStatement(deleteById.bind(userId, emailId))
                .addStatement(deleteByFolder.bind(userId, existing.folderId, existing.receivedAt, emailId))
                .addStatement(deleteByReadStatus.bind(userId, existing.isRead, monthBucket(existing.receivedAt), existing.receivedAt, emailId))
                .build();
        session.execute(batch);
    }

    public List<EmailSummary> listByFolder(UUID userId, UUID folderId, Instant before, int limit) {
        var rs = before == null
                ? session.execute(listByFolder.bind(userId, folderId, limit))
                : session.execute(listByFolderBefore.bind(userId, folderId, before, limit));
        List<EmailSummary> out = new ArrayList<>();
        rs.forEach(r -> out.add(new EmailSummary(
                r.getUuid("email_id"),
                r.getUuid("folder_id"),
                r.getString("subject"),
                r.getString("from_addr"),
                r.getSet("to_addrs", String.class),
                r.getString("preview"),
                r.getInstant("received_at"),
                r.getBoolean("is_read"),
                r.getBoolean("has_attachments"))));
        return out;
    }

    public List<EmailSummary> listByReadStatus(UUID userId, boolean isRead, int monthsBack, int limit) {
        List<EmailSummary> out = new ArrayList<>();
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        for (int i = 0; i < monthsBack && out.size() < limit; i++) {
            int bucket = bucketInt(ym.minusMonths(i));
            int remaining = limit - out.size();
            var rs = session.execute(listByReadStatus.bind(userId, isRead, bucket, remaining));
            rs.forEach(r -> out.add(new EmailSummary(
                    r.getUuid("email_id"),
                    r.getUuid("folder_id"),
                    r.getString("subject"),
                    r.getString("from_addr"),
                    Set.of(),
                    r.getString("preview"),
                    r.getInstant("received_at"),
                    isRead,
                    false)));
        }
        return out;
    }

    public void markRead(UUID userId, UUID emailId, boolean isRead) {
        var existing = getRow(userId, emailId).orElse(null);
        if (existing == null || existing.isRead == isRead) return;

        // update emails_by_id and emails_by_folder atomically.
        var b = BatchStatement.builder(BatchType.LOGGED)
                .addStatement(updateReadById.bind(isRead, userId, emailId))
                .addStatement(updateReadByFolder.bind(isRead, userId, existing.folderId, existing.receivedAt, emailId))
                .build();
        session.execute(b);

        // emails_by_read_status: delete from old partition, insert into new (different partition keys).
        int bucket = monthBucket(existing.receivedAt);
        session.execute(deleteByReadStatus.bind(userId, existing.isRead, bucket, existing.receivedAt, emailId));
        session.execute(insertByReadStatus.bind(userId, isRead, bucket, existing.receivedAt, emailId,
                existing.folderId, existing.subject, existing.fromAddr,
                preview(existing.body)));
    }

    private EmailFull toFull(Row r) {
        return new EmailFull(
                r.getUuid("email_id"),
                r.getUuid("folder_id"),
                r.getString("subject"),
                r.getString("body"),
                r.getString("from_addr"),
                r.getSet("to_addrs", String.class),
                r.getSet("cc_addrs", String.class),
                r.getSet("bcc_addrs", String.class),
                r.getInstant("received_at"),
                r.getBoolean("is_read"),
                List.of()
        );
    }

    private static int monthBucket(Instant t) {
        var d = t.atZone(ZoneOffset.UTC);
        return d.getYear() * 100 + d.getMonthValue();
    }

    private static int bucketInt(YearMonth ym) {
        return ym.getYear() * 100 + ym.getMonthValue();
    }

    public static String preview(String body) {
        if (body == null) return "";
        String trimmed = body.length() > 200 ? body.substring(0, 200) : body;
        return trimmed.replaceAll("\\s+", " ").trim();
    }

    public static class EmailRow {
        public UUID userId;
        public UUID emailId;
        public UUID folderId;
        public String subject;
        public String body;
        public String fromAddr;
        public Set<String> toAddrs = Set.of();
        public Set<String> ccAddrs = Set.of();
        public Set<String> bccAddrs = Set.of();
        public Instant receivedAt;
        public boolean isRead;
        public List<String> attachmentKeys = List.of();
        public String preview;

        static EmailRow from(Row r) {
            EmailRow e = new EmailRow();
            e.userId = r.getUuid("user_id");
            e.emailId = r.getUuid("email_id");
            e.folderId = r.getUuid("folder_id");
            e.subject = r.getString("subject");
            e.body = r.getString("body");
            e.fromAddr = r.getString("from_addr");
            e.toAddrs = r.getSet("to_addrs", String.class);
            e.ccAddrs = r.getSet("cc_addrs", String.class);
            e.bccAddrs = r.getSet("bcc_addrs", String.class);
            e.receivedAt = r.getInstant("received_at");
            e.isRead = r.getBoolean("is_read");
            e.attachmentKeys = r.getList("attachment_keys", String.class);
            e.preview = preview(e.body);
            return e;
        }
    }
}
