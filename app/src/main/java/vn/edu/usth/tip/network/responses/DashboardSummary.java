package vn.edu.usth.tip.network.responses;

public class DashboardSummary {
    private long netWorth;
    private long totalIncomeThisMonth;
    private long totalExpenseThisMonth;
    private long totalTransferThisMonth;

    public long getNetWorth() {
        return netWorth;
    }

    public void setNetWorth(long netWorth) {
        this.netWorth = netWorth;
    }

    public long getTotalIncomeThisMonth() {
        return totalIncomeThisMonth;
    }

    public void setTotalIncomeThisMonth(long totalIncomeThisMonth) {
        this.totalIncomeThisMonth = totalIncomeThisMonth;
    }

    public long getTotalExpenseThisMonth() {
        return totalExpenseThisMonth;
    }

    public void setTotalExpenseThisMonth(long totalExpenseThisMonth) {
        this.totalExpenseThisMonth = totalExpenseThisMonth;
    }

    public long getTotalTransferThisMonth() {
        return totalTransferThisMonth;
    }

    public void setTotalTransferThisMonth(long totalTransferThisMonth) {
        this.totalTransferThisMonth = totalTransferThisMonth;
    }
}
