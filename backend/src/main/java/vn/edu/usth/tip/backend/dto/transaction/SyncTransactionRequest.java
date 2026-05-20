package vn.edu.usth.tip.backend.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.RecurrenceInterval;
import vn.edu.usth.tip.backend.models.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO cho mỗi bản ghi trong batch sync từ client.
 * Hỗ trợ 3 loại: INSERT mới (transactionId=null), EDIT LWW (transactionId!=null), DELETE (isDeleted=true).
 */
@Data
public class SyncTransactionRequest {

    // ── LWW fields ────────────────────────────────────────────────────────────
    /** null = giao dịch mới (PATH B); non-null = edit đã tồn tại trên server (PATH A) */
    private UUID transactionId;

    /** ISO-8601 timestamp từ client. Dùng cho LWW: so sánh với server.updatedAt */
    private OffsetDateTime clientUpdatedAt;

    /** true = xóa mềm — server xóa record và trả ACK */
    private boolean isDeleted = false;

    // ── Transaction fields (null OK cho delete items) ─────────────────────────
    private UUID accountId;
    private UUID categoryId;
    private UUID goalId;

    /**
     * Giá trị tuyệt đối của giao dịch — LUÔN DƯƠNG.
     * Bắt buộc cho PATH A và PATH B. Null chỉ hợp lệ khi isDeleted=true.
     */
    private BigDecimal amount;

    private TransactionType type;
    private String note;
    private LocalDate transactionDate;

    /**
     * Thời điểm tạo thực tế ở phía client (offline timestamp).
     * Nếu null → server sẽ dùng thời gian hiện tại (fallback).
     */
    private OffsetDateTime createdAt;

    private String receiptUrl;
    private Boolean isRecurring = false;
    private RecurrenceInterval recurInterval;
}
