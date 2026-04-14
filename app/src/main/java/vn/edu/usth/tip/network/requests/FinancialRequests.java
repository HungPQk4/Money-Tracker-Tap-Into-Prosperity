package vn.edu.usth.tip.network.requests;

import java.math.BigDecimal;
import java.util.UUID;

public class FinancialRequests {

    public static class CreateAccountRequest {
        private UUID userId;
        private String name;
        private String type;
        private BigDecimal balance;

        public CreateAccountRequest(UUID userId, String name, String type, BigDecimal balance) {
            this.userId = userId;
            this.name = name;
            this.type = type;
            this.balance = balance;
        }
    }

    public static class CreateCategoryRequest {
        private UUID userId;
        private String name;
        private String type;
        private String icon;
        private String colorHex;

        public CreateCategoryRequest(UUID userId, String name, String type, String icon, String colorHex) {
            this.userId = userId;
            this.name = name;
            this.type = type;
            this.icon = icon;
            this.colorHex = colorHex;
        }
    }

    public static class CreateBudgetRequest {
        private UUID userId;
        private UUID categoryId;
        private BigDecimal amount;
        private String periodType; // MONTHLY, WEEKLY
        private String periodStart;
        private String periodEnd;

        public CreateBudgetRequest(UUID userId, UUID categoryId, BigDecimal amount, String periodType, String periodStart, String periodEnd) {
            this.userId = userId;
            this.categoryId = categoryId;
            this.amount = amount;
            this.periodType = periodType;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }
    }

    public static class CreateGoalRequest {
        private UUID userId;
        private String name;
        private BigDecimal targetAmount;
        private BigDecimal currentAmount;
        private String targetDate;

        public CreateGoalRequest(UUID userId, String name, BigDecimal targetAmount, BigDecimal currentAmount, String targetDate) {
            this.userId = userId;
            this.name = name;
            this.targetAmount = targetAmount;
            this.currentAmount = currentAmount;
            this.targetDate = targetDate;
        }
    }

    public static class CreateDebtRequest {
        private UUID userId;
        private String debtorName;
        private BigDecimal amount;
        private String type;
        private String dueDate;
        private String note;

        public CreateDebtRequest(UUID userId, String debtorName, BigDecimal amount, String type, String dueDate) {
            this.userId = userId;
            this.debtorName = debtorName;
            this.amount = amount;
            this.type = type;
            this.dueDate = dueDate;
        }
        
        public void setNote(String note) { this.note = note; }
    }
}
