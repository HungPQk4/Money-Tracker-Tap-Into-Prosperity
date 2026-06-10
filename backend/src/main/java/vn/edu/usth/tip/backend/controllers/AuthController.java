package vn.edu.usth.tip.backend.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import vn.edu.usth.tip.backend.dto.auth.AuthResponse;
import vn.edu.usth.tip.backend.dto.auth.LoginRequest;
import vn.edu.usth.tip.backend.dto.auth.RefreshRequest;
import vn.edu.usth.tip.backend.dto.auth.RegisterRequest;
import vn.edu.usth.tip.backend.dto.auth.SessionResponse;
import vn.edu.usth.tip.backend.exception.InvalidTokenException;
import vn.edu.usth.tip.backend.services.AuthService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            return ResponseEntity.ok(authService.login(req));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Sai email hoặc mật khẩu!"));
        } catch (Exception e) {
            log.error("Login error for email {}: {}", req.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error", "message", "Đã xảy ra lỗi, vui lòng thử lại."));
        }
    }

    // ─── Đổi refresh token lấy access token mới (PUBLIC — chỉ cần refresh token) ──
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req) {
        try {
            return ResponseEntity.ok(authService.refresh(req.getRefreshToken()));
        } catch (InvalidTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", e.getMessage()));
        }
    }

    // ─── Đăng xuất phiên hiện tại (cần access token) ─────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(currentSessionId(request));
        return ResponseEntity.noContent().build();
    }

    // ─── Danh sách thiết bị/phiên đang đăng nhập ────────────────────────────────
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> sessions(HttpServletRequest request) {
        return ResponseEntity.ok(authService.listSessions(currentSessionId(request)));
    }

    // ─── Thu hồi một phiên (đăng xuất từ xa một thiết bị) ───────────────────────
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID id) {
        authService.revokeSession(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Đăng xuất tất cả thiết bị khác (giữ phiên hiện tại) ─────────────────────
    @PostMapping("/sessions/logout-others")
    public ResponseEntity<Void> logoutOthers(HttpServletRequest request) {
        authService.logoutOtherSessions(currentSessionId(request));
        return ResponseEntity.noContent().build();
    }

    /** sid của phiên hiện tại do JwtAuthFilter gắn vào request (null nếu token cũ không có). */
    private UUID currentSessionId(HttpServletRequest request) {
        Object sid = request.getAttribute("sessionId");
        return sid instanceof UUID ? (UUID) sid : null;
    }
}
