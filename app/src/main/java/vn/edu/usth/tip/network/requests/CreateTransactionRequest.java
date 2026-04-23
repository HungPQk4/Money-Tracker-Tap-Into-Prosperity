package vn.edu.usth.tip.network.requests;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateTransactionRequest {
    private UUID userId;
    private UUID accountId;
    private UUID categoryId;
    private UUID goalId;
    private BigDecimal amount;
    private String type; // INCOME, EXPENSE, TRANSFER
    private String note;
    private String transactionDate; // YYYY-MM-DD
    private String receiptUrl;
    private Boolean isRecurring = false;
    private String recurInterval;

    public CreateTransactionRequest(UUID userId, UUID accountId, UUID categoryId, BigDecimal amount, String type, String transactionDate) {
        this.userId = userId;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.type = type;
        this.transactionDate = transactionDate;
    }

    // Getters and Setters
    public UUID getUserId()             { return userId; }
    public UUID getAccountId()          { return accountId; }
    public UUID getCategoryId()         { return categoryId; }
    public UUID getGoalId()             { return goalId; }
    public BigDecimal getAmount()       { return amount; }
    public String getType()             { return type; }
    public String getNote()             { return note; }
    public String getTransactionDate()  { return transactionDate; }
    public String getReceiptUrl()       { return receiptUrl; }
    public Boolean getIsRecurring()     { return isRecurring; }
    public String getRecurInterval()    { return recurInterval; }

    public void setNote(String note) { this.note = note; }
    public void setGoalId(UUID goalId) { this.goalId = goalId; }
}