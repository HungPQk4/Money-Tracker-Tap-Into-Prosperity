package vn.edu.usth.tip.network.responses;

import java.math.BigDecimal;
import java.util.UUID;

public class TransactionDto {
    private UUID id;
    private AccountDto account;
    private CategoryDto category;
    private BigDecimal amount;
    private String type; // INCOME, EXPENSE, TRANSFER
    private String note;
    private String transactionDate; // Format YYYY-MM-DD

    // Getters
    public UUID getId() { return id; }
    public AccountDto getAccount() { return account; }
    public CategoryDto getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
    public String getNote() { return note; }
    public String getTransactionDate() { return transactionDate; }

    public static class AccountDto {
        private String name;
        public String getName() { return name; }
    }

    public static class CategoryDto {
        private String name;
        private String icon; // Icon emoji
        public String getName() { return name; }
        public String getIcon() { return icon; }
    }
}
