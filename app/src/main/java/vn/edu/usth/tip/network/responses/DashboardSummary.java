package vn.edu.usth.tip.network.responses;

/**
 * DTO nhận từ GET /api/dashboard/summary
 * Backend trả BigDecimal → dùng double để Gson parse đúng
 */
public class DashboardSummary {
    private double netWorth;
    private double totalIncomeThisMonth;
    private double totalExpenseThisMonth;
    private double totalTransferThisMonth;

    public long getNetWorth() { return (long) netWorth; }
    public void setNetWorth(double netWorth) { this.netWorth = netWorth; }

    public long getTotalIncomeThisMonth() { return (long) totalIncomeThisMonth; }
    public void setTotalIncomeThisMonth(double totalIncomeThisMonth) { this.totalIncomeThisMonth = totalIncomeThisMonth; }

    public long getTotalExpenseThisMonth() { return (long) totalExpenseThisMonth; }
    public void setTotalExpenseThisMonth(double totalExpenseThisMonth) { this.totalExpenseThisMonth = totalExpenseThisMonth; }

    public long getTotalTransferThisMonth() { return (long) totalTransferThisMonth; }
    public void setTotalTransferThisMonth(double totalTransferThisMonth) { this.totalTransferThisMonth = totalTransferThisMonth; }
}
