package vn.edu.usth.tip.insights.engine;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.insights.models.ForecastPoint;
import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import vn.edu.usth.tip.insights.models.InsightType;
import vn.edu.usth.tip.models.Budget;
import vn.edu.usth.tip.models.DebtLoan;
import vn.edu.usth.tip.models.Goal;
import vn.edu.usth.tip.models.dto.CategoryMonthlyDTO;
import vn.edu.usth.tip.models.dto.DailySpendDTO;
import vn.edu.usth.tip.models.dto.DayPatternDTO;

public class InsightEngine {

    public static class AnalysisResult {
        public final List<Insight> insights;
        public final List<ForecastPoint> forecastPoints;

        public AnalysisResult(List<Insight> insights, List<ForecastPoint> forecastPoints) {
            this.insights = insights;
            this.forecastPoints = forecastPoints;
        }
    }

    private final AppDatabase db;
    private final BudgetForecaster budgetForecaster = new BudgetForecaster();
    private final GoalAdvisor goalAdvisor = new GoalAdvisor();
    private final AnomalyDetector anomalyDetector = new AnomalyDetector();
    private final PatternAnalyzer patternAnalyzer = new PatternAnalyzer();
    private final DebtLoanAdvisor debtLoanAdvisor = new DebtLoanAdvisor();

    public InsightEngine(AppDatabase db) {
        this.db = db;
    }

    // Phải gọi từ background thread (ExecutorService) — KHÔNG gọi trên Main Thread
    public AnalysisResult analyzeAll() {
        List<Insight> allInsights = new ArrayList<>();

        long now = System.currentTimeMillis();
        long oneDayMs = 24L * 60 * 60 * 1000;

        // 1. Pattern Analysis — xu hướng hành vi theo ngày trong tuần (90 ngày)
        long since90d = now - 90L * 24 * 60 * 60 * 1000;
        List<DayPatternDTO> patternRows = db.transactionDao().getAvgSpendByDayOfWeekSync(since90d);
        Insight patternInsight = patternAnalyzer.analyze(patternRows);
        if (patternInsight != null) allInsights.add(patternInsight);

        // 2. Anomaly Detection — phát hiện bất thường (4 tháng: 3 lịch sử + hiện tại)
        long since4m = subtractMonths(now, 4);
        List<CategoryMonthlyDTO> monthlyRows = db.transactionDao().getCategoryMonthlyTotalsSync(since4m);
        List<Insight> anomalies = anomalyDetector.detect(monthlyRows);
        allInsights.addAll(anomalies);

        // 3. Goal Advisor — tư vấn mục tiêu tiết kiệm
        List<Goal> goals = db.goalDao().getAllGoalsSync();
        for (Goal goal : goals) {
            Insight goalInsight = goalAdvisor.advise(goal);
            if (goalInsight != null) allInsights.add(goalInsight);
        }

        // 4. Budget Forecaster — cảnh báo ngân sách hoạt động + thông báo hoàn thành (24h)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();

        List<Budget> budgets = db.budgetDao().getAllBudgetsSync();
        for (Budget budget : budgets) {
            if (budget.getPeriodStartMs() <= now && budget.getPeriodEndMs() >= now) {
                // Budget đang hoạt động — chạy OLS forecast
                List<DailySpendDTO> filtered = db.transactionDao()
                        .getDailyExpensesByCategorySync(monthStart, now, budget.getCategoryName());
                BudgetForecaster.BudgetForecastResult result =
                        budgetForecaster.forecast(budget, filtered);
                if (result.insight != null) allInsights.add(result.insight);
            } else if (budget.getPeriodEndMs() < now
                    && budget.getPeriodEndMs() >= now - oneDayMs) {
                // Budget vừa kết thúc trong 24h — thông báo hoàn thành (tự biến mất sau 24h)
                Insight completed = new Insight();
                completed.type           = InsightType.BUDGET_COMPLETED;
                completed.priority       = InsightPriority.LOW;
                completed.categoryName   = budget.getCategoryName();
                completed.limitAmountVnd = budget.getLimitAmount();
                completed.title = "✅ Ngân sách " + budget.getCategoryName() + " đã hoàn thành";
                completed.body  = "Kỳ ngân sách " + budget.getCategoryName()
                        + " vừa kết thúc. Xem lại chi tiêu để lên kế hoạch cho kỳ tiếp theo!";
                allInsights.add(completed);
            }
        }

        // 5. Debt/Loan Reminders — nhắc nhở vay nợ với đầy đủ số tiền và ngày tháng
        List<DebtLoan> debtLoans = db.debtLoanDao().getAllDebtLoansSync();
        List<Insight> debtInsights = debtLoanAdvisor.advise(debtLoans);
        allInsights.addAll(debtInsights);

        // 6. Biểu đồ dự báo tổng chi tiêu — dùng TẤT CẢ giao dịch tháng này (không filter category)
        // Đảm bảo chart bám sát thực tế, độc lập với budget
        List<DailySpendDTO> allExpenses = db.transactionDao().getDailyExpensesSync(monthStart, now);
        List<ForecastPoint> forecastPoints = buildTotalSpendForecast(allExpenses);

        // Sắp xếp: HIGH trước, sau đó MEDIUM, rồi LOW
        allInsights.sort((a, b) -> {
            int pa = a.priority != null ? a.priority.ordinal() : 99;
            int pb = b.priority != null ? b.priority.ordinal() : 99;
            return pa - pb;
        });

        return new AnalysisResult(allInsights, forecastPoints);
    }

    /**
     * Xây dựng điểm dự báo tổng chi tiêu tháng này (toàn bộ danh mục).
     * Dùng OLS hồi quy tuyến tính trên tổng tích lũy, fallback tuyến tính nếu < 2 điểm.
     * Đảm bảo chart luôn bám sát thực tế, không phụ thuộc vào việc có budget hay không.
     */
    private List<ForecastPoint> buildTotalSpendForecast(List<DailySpendDTO> dailyRows) {
        Calendar cal = Calendar.getInstance();
        int currentDay  = cal.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        List<ForecastPoint> points = new ArrayList<>();
        List<Integer> xList = new ArrayList<>();
        List<Double>  yList = new ArrayList<>();

        // Tích lũy chi tiêu theo ngày (cumulative sum)
        double cumulative = 0;
        for (DailySpendDTO row : dailyRows) {
            cumulative += row.totalVnd;
            xList.add(row.dayNum);
            yList.add(cumulative);
            points.add(new ForecastPoint(row.dayNum, (long) cumulative, false));
        }

        // Không có dữ liệu hoặc đã cuối tháng → không cần dự báo
        if (xList.isEmpty() || currentDay >= daysInMonth) return points;

        long spentSoFar = (long) cumulative;
        int  n          = xList.size();

        // Fallback tuyến tính đơn giản khi chưa đủ điểm cho OLS
        if (n < 2) {
            if (currentDay > 0 && spentSoFar > 0) {
                int lastAdded = currentDay;
                for (int d = currentDay + 1; d <= daysInMonth; d += 2) {
                    long proj = (long) ((double) spentSoFar / currentDay * d);
                    points.add(new ForecastPoint(d, proj, true));
                    lastAdded = d;
                }
                if (lastAdded != daysInMonth) {
                    long projEnd = (long) ((double) spentSoFar / currentDay * daysInMonth);
                    points.add(new ForecastPoint(daysInMonth, projEnd, true));
                }
            }
            return points;
        }

        // OLS hồi quy tuyến tính trên (dayNum, cumulativeSpend)
        int[]    x = new int[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) { x[i] = xList.get(i); y[i] = yList.get(i); }

        RegressionHelper.OlsResult ols = RegressionHelper.fit(x, y);

        if (ols == null) {
            // OLS không hội tụ (mẫu số < 1.0) → fallback tuyến tính
            int lastAdded = currentDay;
            for (int d = currentDay + 1; d <= daysInMonth; d += 2) {
                long proj = (currentDay > 0)
                        ? (long) ((double) spentSoFar / currentDay * d) : spentSoFar;
                points.add(new ForecastPoint(d, Math.max(0, proj), true));
                lastAdded = d;
            }
            if (lastAdded != daysInMonth) {
                long projEnd = (currentDay > 0)
                        ? (long) ((double) spentSoFar / currentDay * daysInMonth) : spentSoFar;
                points.add(new ForecastPoint(daysInMonth, Math.max(0, projEnd), true));
            }
            return points;
        }

        // Thêm điểm dự báo từ ngày hôm nay+1 đến cuối tháng (bước 2 ngày)
        int lastAdded = currentDay;
        for (int d = currentDay + 1; d <= daysInMonth; d += 2) {
            long proj = Math.max(0, (long) (ols.beta0 + ols.beta1 * d));
            points.add(new ForecastPoint(d, proj, true));
            lastAdded = d;
        }
        // Đảm bảo có điểm tại ngày cuối tháng để đường không bị cụt
        if (lastAdded != daysInMonth) {
            long projEnd = Math.max(0, (long) (ols.beta0 + ols.beta1 * daysInMonth));
            points.add(new ForecastPoint(daysInMonth, projEnd, true));
        }

        return points;
    }

    private long subtractMonths(long timeMs, int months) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);
        cal.add(Calendar.MONTH, -months);
        return cal.getTimeInMillis();
    }
}
