package vn.edu.usth.tip.insights.engine;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.insights.models.ForecastPoint;
import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.models.Budget;
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

    public InsightEngine(AppDatabase db) {
        this.db = db;
    }

    // Phải gọi từ background thread (ExecutorService) — KHÔNG gọi trên Main Thread
    public AnalysisResult analyzeAll() {
        List<Insight> allInsights = new ArrayList<>();
        List<ForecastPoint> forecastPoints = new ArrayList<>();

        long now = System.currentTimeMillis();

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

        // 4. Budget Forecaster — cảnh báo ngân sách (chỉ budget đang hoạt động tháng này)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();
        long monthEnd = now;

        List<Budget> budgets = db.budgetDao().getAllBudgetsSync();
        for (Budget budget : budgets) {
            // Chỉ xử lý budget đang trong chu kỳ hiện tại
            if (budget.getPeriodStartMs() <= now && budget.getPeriodEndMs() >= now) {
                List<DailySpendDTO> filtered = db.transactionDao()
                        .getDailyExpensesByCategorySync(monthStart, monthEnd, budget.getCategoryName());
                BudgetForecaster.BudgetForecastResult result =
                        budgetForecaster.forecast(budget, filtered);
                if (result.insight != null) allInsights.add(result.insight);
                if (forecastPoints.isEmpty()) forecastPoints.addAll(result.forecastPoints);
            }
        }

        // Sắp xếp: HIGH trước, sau đó MEDIUM, rồi LOW
        allInsights.sort((a, b) -> {
            int pa = a.priority != null ? a.priority.ordinal() : 99;
            int pb = b.priority != null ? b.priority.ordinal() : 99;
            return pa - pb;
        });

        return new AnalysisResult(allInsights, forecastPoints);
    }

    private long subtractMonths(long timeMs, int months) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);
        cal.add(Calendar.MONTH, -months);
        return cal.getTimeInMillis();
    }
}
