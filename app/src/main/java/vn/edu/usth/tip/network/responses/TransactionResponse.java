package vn.edu.usth.tip.network.responses;

import com.google.gson.annotations.SerializedName;

/**
 * DTO nhận từ GET /api/dashboard/recent và /api/transactions
 * amount là BigDecimal từ backend → dùng double để Gson parse đúng
 * type là enum lowercase: income, expense, transfer
 */
public class TransactionResponse {
    private String id;
    private String userId;
    private String accountId;
    private String accountName;
    private String categoryId;
    private String categoryName;
    private String goalId;
    private double amount; // BigDecimal JSON → double để tránh mismatch
    private String type;   // "income" | "expense" | "transfer"
    private String note;
    @SerializedName("transactionDate")
    private String transactionDate; // Format YYYY-MM-DD
    private String receiptUrl;
    private Boolean isRecurring;
    private String recurInterval;
    private String createdAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getGoalId() { return goalId; }
    public void setGoalId(String goalId) { this.goalId = goalId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    /** Helper trả về long VND */
    public long getAmountLong() { return (long) amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getTransactionDate() { return transactionDate; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

    public String getReceiptUrl() { return receiptUrl; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }

    public Boolean getIsRecurring() { return isRecurring; }
    public void setIsRecurring(Boolean isRecurring) { this.isRecurring = isRecurring; }

    public String getRecurInterval() { return recurInterval; }
    public void setRecurInterval(String recurInterval) { this.recurInterval = recurInterval; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
