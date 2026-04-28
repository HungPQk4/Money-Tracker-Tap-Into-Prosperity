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
 * Quan trọng: createdAt là bắt buộc — giữ đúng thời gian gốc khi nhập offline.
 */
@Data
public class SyncTransactionRequest {

    @NotNull private UUID accountId;
    @NotNull private UUID categoryId;
    private UUID goalId;

    /**
     * Giá trị tuyệt đối của giao dịch — LUÔN DƯƠNG.
     * Chiều hướng (thu/chi) được xác định bởi field `type`.
     * Ví dụ: chi tiêu 59.050đ → amount=59050, type="expense"
     */
    @NotNull
    @DecimalMin(value = "0.01", message = "amount phải lớn hơn 0")
    private BigDecimal amount;


    @NotNull private TransactionType type;
    private String note;

    @NotNull private LocalDate transactionDate;

    /**
     * Thời điểm tạo thực tế ở phía client (offline timestamp).
     * Nếu null → server sẽ dùng thời gian hiện tại (fallback).
     */
    private OffsetDateTime createdAt;

    private String receiptUrl;
    private Boolean isRecurring = false;
    private RecurrenceInterval recurInterval;
}
