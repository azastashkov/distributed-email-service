package com.example.email.web.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.example.email.common.auth.JwtUtil;
import com.example.email.common.dto.Folder;
import com.example.email.web.config.AppProperties;
import com.example.email.web.folder.FolderRepository;
import com.example.email.web.user.UserRepository;
import com.example.email.web.user.UserRepository.UserRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final FolderRepository folders;
    private final AppProperties props;

    public TokenResponse signup(String email, String password, String displayName) {
        if (users.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Email already registered");
        }
        UUID userId = UUID.randomUUID();
        String hash = BCrypt.withDefaults().hashToString(10, password.toCharArray());
        Instant now = Instant.now();

        boolean inserted = users.insert(new UserRecord(userId, email, hash, displayName, now));
        if (!inserted) {
            throw new IllegalStateException("Email already registered");
        }

        for (String name : List.of(Folder.INBOX, Folder.SENT, Folder.DRAFTS, Folder.TRASH)) {
            folders.create(userId, name, true);
        }

        return issueToken(userId, email);
    }

    public TokenResponse login(String email, String password) {
        var user = users.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        var verify = BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash());
        if (!verify.verified) throw new IllegalArgumentException("Invalid credentials");
        return issueToken(user.userId(), user.email());
    }

    private TokenResponse issueToken(UUID userId, String email) {
        Duration ttl = Duration.ofMinutes(props.getJwt().getTtlMinutes());
        String token = JwtUtil.issue(userId, email, ttl, props.getJwt().getSecret());
        return new TokenResponse(userId, token, Instant.now().plus(ttl));
    }

    public record TokenResponse(UUID userId, String token, Instant expiresAt) {}
}
