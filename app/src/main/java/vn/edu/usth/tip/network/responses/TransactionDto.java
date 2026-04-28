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
}
