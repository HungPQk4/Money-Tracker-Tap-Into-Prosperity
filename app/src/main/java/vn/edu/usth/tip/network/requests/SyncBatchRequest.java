package vn.edu.usth.tip.network.requests;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payload cho POST /api/transactions/sync
 * Gửi toàn bộ giao dịch cũ chưa sync lên Neon, giữ nguyên createdAt gốc.
 */
public class SyncBatchRequest {

    private UUID userId;
    private List<SyncItem> transactions;

    public SyncBatchRequest(UUID userId, List<SyncItem> transactions) {
        this.userId = userId;
        this.transactions = transactions;
    }

    public UUID getUserId() { return userId; }
    public List<SyncItem> getTransactions() { return transactions; }

    // ─── Mỗi bản ghi trong batch ──────────────────────────────────────────────
    public static class SyncItem {
        private UUID accountId;
        private UUID categoryId;
        private BigDecimal amount;
        private String type;           // "income" | "expense" | "transfer"
        private String note;
        private String transactionDate; // "YYYY-MM-DD"

        /**
         * Thời điểm tạo thực tế ở client (ISO-8601 với timezone).
         * Ví dụ: "2025-03-15T19:30:00+07:00"
         * Server sẽ GIỮ NGUYÊN giá trị này, không ghi đè bằng now().
         */
        private String createdAt;

        private boolean isRecurring = false;
        private String recurInterval; // "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY"

        // LWW fields
        private UUID transactionId;   // null = giao dịch mới; non-null = edit đã có trên server
        private String clientUpdatedAt; // ISO-8601 UTC cho LWW comparison
        private boolean isDeleted = false; // soft delete signal

        public SyncItem(UUID accountId, UUID categoryId, BigDecimal amount,
                        String type, String note, String transactionDate, String createdAt) {
            this.accountId = accountId;
            this.categoryId = categoryId;
            this.amount = amount;
            this.type = type;
            this.note = note;
            this.transactionDate = transactionDate;
            this.createdAt = createdAt;
        }

        public SyncItem(UUID transactionId, UUID accountId, UUID categoryId, BigDecimal amount,
                        String type, String note, String transactionDate, String createdAt,
                        String clientUpdatedAt) {
            this(accountId, categoryId, amount, type, note, transactionDate, createdAt);
            this.transactionId = transactionId;
            this.clientUpdatedAt = clientUpdatedAt;
        }

        public UUID getAccountId()         { return accountId; }
        public UUID getCategoryId()        { return categoryId; }
        public BigDecimal getAmount()      { return amount; }
        public String getType()            { return type; }
        public String getNote()            { return note; }
        public String getTransactionDate() { return transactionDate; }
        public String getCreatedAt()       { return createdAt; }
        public boolean isRecurring()        { return isRecurring; }
        public String getRecurInterval()    { return recurInterval; }
        public UUID getTransactionId()      { return transactionId; }
        public String getClientUpdatedAt()  { return clientUpdatedAt; }
        public boolean isDeleted()          { return isDeleted; }
        public void setDeleted(boolean deleted) { isDeleted = deleted; }
        public void setRecurring(boolean recurring) { isRecurring = recurring; }
        public void setRecurInterval(String recurInterval) { this.recurInterval = recurInterval; }
    }
}
