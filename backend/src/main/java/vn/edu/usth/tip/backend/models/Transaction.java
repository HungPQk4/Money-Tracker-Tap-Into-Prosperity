package vn.edu.usth.tip.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import vn.edu.usth.tip.backend.models.enums.RecurrenceInterval;
import vn.edu.usth.tip.backend.models.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
public class Transaction {
    @Id
    private UUID id; // Client-generated UUID (Offline-First) — no @GeneratedValue

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "receipt_url", columnDefinition = "TEXT")
    private String receiptUrl;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "recur_interval", length = 10)
    private RecurrenceInterval recurInterval;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /**
     * Client-reported edit time, CLAMPED to {@code <= serverNow} by the service (Cách 0).
     * Used ONLY for the Last-Write-Wins decision ("newer edit wins"). The delta cursor /
     * version uses {@link #updatedAt}, which is ALWAYS server-issued (skew-free). May be
     * null for legacy rows created before this column existed.
     */
    @Column(name = "client_updated_at")
    private OffsetDateTime clientUpdatedAt;

    @PrePersist
    protected void onCreate() {
        // Chỉ set createdAt nếu chưa có — để API sync giữ nguyên thời gian gốc của client
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    // Giữ lại để tương thích các lời gọi setClientSync(...) cũ. KHÔNG còn ảnh hưởng updatedAt
    // (biến thể A: updatedAt LUÔN do server cấp; edit-time của client nằm ở clientUpdatedAt).
    @Transient
    private boolean isClientSync = false;

    public void setClientSync(boolean clientSync) { this.isClientSync = clientSync; }

    @PreUpdate
    protected void onUpdate() {
        // Biến thể A: updatedAt LUÔN là mốc version/cursor do server cấp (skew-free).
        updatedAt = OffsetDateTime.now();
    }
}
