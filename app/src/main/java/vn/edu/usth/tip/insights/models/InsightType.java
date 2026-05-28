package vn.edu.usth.tip.insights.models;

public enum InsightType {
    BUDGET_WARNING,
    BUDGET_OK,
    BUDGET_COMPLETED,   // Kỳ ngân sách vừa kết thúc (hiển thị 24h)
    GOAL_WARNING,
    GOAL_NOT_STARTED,
    GOAL_OVERDUE,
    GOAL_FORECAST,
    GOAL_COMPLETED,     // Mục tiêu tiết kiệm đã đạt
    ANOMALY,
    PATTERN,
    SAVING_TIP,
    DEBT_DUE_SOON,      // Nợ sắp đến hạn / nhắc nhở chung
    DEBT_OVERDUE,       // Nợ đã quá hạn
    LOAN_REMINDER       // Nhắc nhở khoản đã cho vay
}
