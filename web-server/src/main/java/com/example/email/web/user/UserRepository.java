package com.example.email.web.user;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final CqlSession session;

    private PreparedStatement findByEmail;
    private PreparedStatement findById;
    private PreparedStatement insertByEmail;
    private PreparedStatement insertById;

    @PostConstruct
    void prepare() {
        findByEmail = session.prepare("SELECT email, user_id, password_hash, display_name, created_at FROM users_by_email WHERE email = ?");
        findById    = session.prepare("SELECT user_id, email, display_name, created_at FROM users_by_id WHERE user_id = ?");
        insertByEmail = session.prepare("INSERT INTO users_by_email (email, user_id, password_hash, display_name, created_at) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS");
        insertById    = session.prepare("INSERT INTO users_by_id (user_id, email, display_name, created_at) VALUES (?, ?, ?, ?)");
    }

    public Optional<UserRecord> findByEmail(String email) {
        Row row = session.execute(findByEmail.bind(email)).one();
        if (row == null) return Optional.empty();
        return Optional.of(new UserRecord(
                row.getUuid("user_id"),
                row.getString("email"),
                row.getString("password_hash"),
                row.getString("display_name"),
                row.getInstant("created_at")
        ));
    }

    public Optional<UserRecord> findById(UUID userId) {
        Row row = session.execute(findById.bind(userId)).one();
        if (row == null) return Optional.empty();
        return Optional.of(new UserRecord(
                row.getUuid("user_id"),
                row.getString("email"),
                null,
                row.getString("display_name"),
                row.getInstant("created_at")
        ));
    }

    /** Returns true if inserted; false if email already taken. */
    public boolean insert(UserRecord user) {
        var rs = session.execute(insertByEmail.bind(
                user.email(), user.userId(), user.passwordHash(), user.displayName(), user.createdAt()));
        boolean applied = rs.wasApplied();
        if (applied) {
            session.execute(insertById.bind(user.userId(), user.email(), user.displayName(), user.createdAt()));
        }
        return applied;
    }

    public record UserRecord(UUID userId, String email, String passwordHash, String displayName, Instant createdAt) {}
}
