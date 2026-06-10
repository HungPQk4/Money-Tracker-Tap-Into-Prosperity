package vn.edu.usth.tip.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Một phiên đăng nhập (1 dòng = 1 thiết bị). Cho phép quản lý đa thiết bị:
 * liệt kê phiên, đăng xuất từ xa, thu hồi token. Access token (JWT) mang claim
 * "sid" = id của phiên này; mỗi request được {@code JwtAuthFilter} kiểm tra phiên
 * còn sống hay không, nên thu hồi có hiệu lực tức thì.
 */
@Entity
@Table(name = "sessions", indexes = {
        @Index(name = "idx_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_sessions_refresh_token_hash", columnList = "refresh_token_hash")
})
@Data
@NoArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // SHA-256 (hex) của refresh token dạng opaque — KHÔNG bao giờ lưu token thô.
    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt = OffsetDateTime.now();

    // Hạn của refresh token (phiên). Hết hạn → phải đăng nhập lại.
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }
}
