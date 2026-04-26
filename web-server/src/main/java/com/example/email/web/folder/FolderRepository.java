package com.example.email.web.folder;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.example.email.common.dto.Folder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FolderRepository {

    private final CqlSession session;

    private PreparedStatement listForUser;
    private PreparedStatement findOne;
    private PreparedStatement insert;
    private PreparedStatement delete;
    private PreparedStatement findByName;

    @PostConstruct
    void prepare() {
        listForUser = session.prepare("SELECT folder_id, name, system_folder, created_at FROM folders_by_user WHERE user_id = ?");
        findOne     = session.prepare("SELECT folder_id, name, system_folder, created_at FROM folders_by_user WHERE user_id = ? AND folder_id = ?");
        insert      = session.prepare("INSERT INTO folders_by_user (user_id, folder_id, name, system_folder, created_at) VALUES (?, ?, ?, ?, ?)");
        delete      = session.prepare("DELETE FROM folders_by_user WHERE user_id = ? AND folder_id = ?");
        findByName  = session.prepare("SELECT folder_id, name, system_folder, created_at FROM folders_by_user WHERE user_id = ?");
    }

    public List<Folder> listForUser(UUID userId) {
        var rs = session.execute(listForUser.bind(userId));
        List<Folder> out = new ArrayList<>();
        rs.forEach(r -> out.add(new Folder(
                r.getUuid("folder_id"), r.getString("name"),
                r.getBoolean("system_folder"), r.getInstant("created_at"))));
        return out;
    }

    public Optional<Folder> findOne(UUID userId, UUID folderId) {
        var row = session.execute(findOne.bind(userId, folderId)).one();
        if (row == null) return Optional.empty();
        return Optional.of(new Folder(
                row.getUuid("folder_id"), row.getString("name"),
                row.getBoolean("system_folder"), row.getInstant("created_at")));
    }

    public Optional<Folder> findByName(UUID userId, String name) {
        // small list — scan client-side. For real prod would maintain folders_by_name table.
        return listForUser(userId).stream().filter(f -> f.name().equals(name)).findFirst();
    }

    public Folder create(UUID userId, String name, boolean system) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        session.execute(insert.bind(userId, id, name, system, now));
        return new Folder(id, name, system, now);
    }

    public void delete(UUID userId, UUID folderId) {
        session.execute(delete.bind(userId, folderId));
    }
}
