package com.example.email.web.folder;

import com.example.email.common.dto.Folder;
import com.example.email.web.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderRepository folders;

    public record CreateFolderRequest(@NotBlank String name) {}

    @GetMapping
    public List<Folder> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return folders.listForUser(user.userId());
    }

    @PostMapping
    public ResponseEntity<Folder> create(@AuthenticationPrincipal AuthenticatedUser user,
                                         @Valid @RequestBody CreateFolderRequest req) {
        return ResponseEntity.ok(folders.create(user.userId(), req.name(), false));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID folderId) {
        var existing = folders.findOne(user.userId(), folderId).orElseThrow();
        if (existing.systemFolder()) {
            return ResponseEntity.status(409).build();
        }
        folders.delete(user.userId(), folderId);
        return ResponseEntity.noContent().build();
    }
}
