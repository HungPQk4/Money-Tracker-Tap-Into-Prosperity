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
        private CategoryDto category;
        private BigDecimal amount;
        private String periodStart;
        private String periodEnd;

        public UUID getId() { return id; }
        public CategoryDto getCategory() { return category; }
        public BigDecimal getAmount() { return amount; }
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
        private String debtorName;
        private BigDecimal amount;
        private String type; // LENT, BORROWED
        private String dueDate;
        private String note;

        public UUID getId() { return id; }
        public String getDebtorName() { return debtorName; }
        public BigDecimal getAmount() { return amount; }
        public String getType() { return type; }
        public String getDueDate() { return dueDate; }
        public String getNote() { return note; }
    }
}
