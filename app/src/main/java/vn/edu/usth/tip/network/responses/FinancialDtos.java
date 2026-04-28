package vn.edu.usth.tip.network.responses;

import java.math.BigDecimal;
import java.util.UUID;

public class FinancialDtos {

    public static class AccountDto {
        private UUID id;
        private String name;
        private BigDecimal balance;
        private String type; // CASH, BANK, EWALLET, etc.
        private boolean isDefault;

        public UUID getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getBalance() { return balance; }
        public String getType() { return type; }
        public boolean isDefault() { return isDefault; }
    }

    public static class CategoryDto {
        private UUID id;
        private String name;
        private String type; // INCOME, EXPENSE
        private String icon;
        private String colorHex;

        public UUID getId() { return id; }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getIcon() { return icon; }
        public String getColorHex() { return colorHex; }
    }

    public static class BudgetDto {
        private UUID id;
        private UUID userId;
        private UUID categoryId;
        private String categoryName;
        private BigDecimal amount;
        private BigDecimal spentAmount;
        private String periodType;
        private String periodStart;
        private String periodEnd;

        public UUID getId() { return id; }
        public UUID getUserId() { return userId; }
        public UUID getCategoryId() { return categoryId; }
        public String getCategoryName() { return categoryName; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getSpentAmount() { return spentAmount; }
        public String getPeriodType() { return periodType; }
        public String getPeriodStart() { return periodStart; }
        public String getPeriodEnd() { return periodEnd; }
    }

    public static class GoalDto {
        private UUID id;
        private String name;
        private BigDecimal targetAmount;
        private BigDecimal currentAmount;
        private String targetDate;

        public UUID getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getTargetAmount() { return targetAmount; }
        public BigDecimal getCurrentAmount() { return currentAmount; }
        public String getTargetDate() { return targetDate; }
    }

    public static class DebtDto {
        private UUID id;
        private String contactName;
        private BigDecimal amount;
        private String type; // LENT, BORROWED
        private String dueDate;
        private String note;

        public UUID getId() { return id; }
        public String getContactName() { return contactName; }
        public BigDecimal getAmount() { return amount; }
        public String getType() { return type; }
        public String getDueDate() { return dueDate; }
        public String getNote() { return note; }
    }
}
