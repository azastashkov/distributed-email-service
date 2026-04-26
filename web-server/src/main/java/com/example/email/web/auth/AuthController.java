package com.example.email.web.auth;

import com.example.email.common.dto.UserProfile;
import com.example.email.web.security.AuthenticatedUser;
import com.example.email.web.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public record SignupRequest(@Email @NotBlank String email,
                                @NotBlank @Size(min = 6, max = 128) String password,
                                @NotBlank String displayName) {}

    public record LoginRequest(@Email @NotBlank String email,
                               @NotBlank String password) {}

    @PostMapping("/auth/signup")
    public ResponseEntity<AuthService.TokenResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.ok(authService.signup(req.email(), req.password(), req.displayName()));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthService.TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req.email(), req.password()));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfile> profile(@AuthenticationPrincipal AuthenticatedUser user) {
        var u = userRepository.findById(user.userId()).orElse(null);
        if (u == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new UserProfile(u.userId(), u.email(), u.displayName(), u.createdAt()));
    }
}
