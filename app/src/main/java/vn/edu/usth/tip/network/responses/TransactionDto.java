package vn.edu.usth.tip.network.responses;

import java.math.BigDecimal;
import java.util.UUID;

public class TransactionDto {
    private UUID id;
    private UUID accountId;
    private String accountName;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal amount;
    private String type; // INCOME, EXPENSE, TRANSFER
    private String note;
    private String transactionDate; // Format YYYY-MM-DD
    private String updatedAt;       // ISO-8601 — SERVER version/cursor timestamp
    private String clientUpdatedAt; // ISO-8601 — client edit-time (LWW pull guard)
    private boolean deleted;        // server xác nhận xóa
    private boolean isRecurring;
    private String recurInterval;   // "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY"

    // Getters
    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public String getAccountName() { return accountName; }
    public UUID getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
    public String getNote() { return note; }
    public String getTransactionDate() { return transactionDate; }
    public String getUpdatedAt() { return updatedAt; }
    public String getClientUpdatedAt() { return clientUpdatedAt; }
    public boolean isDeleted() { return deleted; }
    public boolean isRecurring() { return isRecurring; }
    public String getRecurInterval() { return recurInterval; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public void setId(UUID id) { this.id = id; }
}
