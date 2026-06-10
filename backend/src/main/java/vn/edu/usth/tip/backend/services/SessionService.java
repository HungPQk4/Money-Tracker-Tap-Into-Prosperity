package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.exception.InvalidTokenException;
import vn.edu.usth.tip.backend.models.Session;
import vn.edu.usth.tip.backend.repositories.SessionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý vòng đời phiên đăng nhập + refresh token.
 *
 * Refresh token là chuỗi ngẫu nhiên opaque (không phải JWT): gửi cho client một lần,
 * server chỉ lưu SHA-256 của nó. Mỗi lần refresh sẽ XOAY token (rotate) để hạn chế
 * rủi ro lộ token.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    @Value("${jwt.refresh-expiration:2592000000}") // mặc định 30 ngày
    private long refreshExpirationMs;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Kết quả tạo/xoay phiên: trả về refresh token THÔ để gửi cho client (chỉ lần này). */
    public record SessionCreation(Session session, String rawRefreshToken) {}

    /** Tạo phiên mới cho một thiết bị; trả về refresh token thô. */
    @Transactional
    public SessionCreation createSession(UUID userId, String deviceName, String deviceInfo) {
        String rawRefresh = generateRawToken();
        Session s = new Session();
        s.setUserId(userId);
        s.setRefreshTokenHash(sha256(rawRefresh));
        s.setDeviceName(sanitize(deviceName, 120));
        s.setDeviceInfo(sanitize(deviceInfo, 255));
        s.setExpiresAt(OffsetDateTime.now().plus(refreshExpirationMs, ChronoUnit.MILLIS));
        Session saved = sessionRepository.save(s);
        return new SessionCreation(saved, rawRefresh);
    }

    /** Kiểm tra & xoay refresh token. Lỗi → {@link InvalidTokenException} (→ 401). */
    @Transactional
    public SessionCreation rotate(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw new InvalidTokenException("Thiếu refresh token");
        }
        Session s = sessionRepository.findByRefreshTokenHash(sha256(rawRefresh))
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ"));
        if (s.isRevoked() || s.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("Phiên đã hết hạn hoặc bị thu hồi");
        }
        String newRaw = generateRawToken();
        s.setRefreshTokenHash(sha256(newRaw));
        s.setLastSeenAt(OffsetDateTime.now());
        s.setExpiresAt(OffsetDateTime.now().plus(refreshExpirationMs, ChronoUnit.MILLIS));
        sessionRepository.save(s);
        return new SessionCreation(s, newRaw);
    }

    /** Phiên còn sống? Dùng trong JwtAuthFilter để thu hồi token tức thì. */
    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(s -> !s.isRevoked() && s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .orElse(false);
    }

    /** Thu hồi một phiên (chỉ khi thuộc về userId này). */
    @Transactional
    public void revoke(UUID sessionId, UUID userId) {
        if (sessionId == null) return;
        sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .ifPresent(s -> {
                    s.setRevoked(true);
                    sessionRepository.save(s);
                });
    }

    /** Thu hồi tất cả phiên của user TRỪ phiên hiện tại ("đăng xuất các thiết bị khác"). */
    @Transactional
    public void revokeAllExcept(UUID userId, UUID keepSessionId) {
        List<Session> sessions = sessionRepository.findByUserIdAndRevokedFalseOrderByLastSeenAtDesc(userId);
        for (Session s : sessions) {
            if (!s.getId().equals(keepSessionId)) {
                s.setRevoked(true);
            }
        }
        sessionRepository.saveAll(sessions);
    }

    @Transactional(readOnly = true)
    public List<Session> listActive(UUID userId) {
        return sessionRepository.findByUserIdAndRevokedFalseOrderByLastSeenAtDesc(userId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String generateRawToken() {
        byte[] bytes = new byte[32]; // 256-bit
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }

    private static String sanitize(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
