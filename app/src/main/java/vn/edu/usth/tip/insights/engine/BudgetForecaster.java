package vn.edu.usth.tip.insights.engine;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import vn.edu.usth.tip.insights.models.ForecastPoint;
import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import vn.edu.usth.tip.insights.models.InsightType;
import vn.edu.usth.tip.models.Budget;
import vn.edu.usth.tip.models.dto.DailySpendDTO;

public class BudgetForecaster {

    public static class BudgetForecastResult {
        public final Insight insight;
        public final List<ForecastPoint> forecastPoints;

        BudgetForecastResult(Insight insight, List<ForecastPoint> forecastPoints) {
            this.insight = insight;
            this.forecastPoints = forecastPoints;
        }
    }

    public BudgetForecastResult forecast(Budget budget, List<DailySpendDTO> dailyRows) {
        Calendar cal = Calendar.getInstance();
        int currentDay = cal.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Bước 1: Cộng dồn (Running Total) — dailyRows chỉ có chi tiêu từng ngày,
        // OLS cần Y[i] = tổng tích lũy đến ngày i
        List<Integer> xList = new ArrayList<>();
        List<Double> yList = new ArrayList<>();
        List<ForecastPoint> forecastPoints = new ArrayList<>();

        double cumulative = 0;
        for (DailySpendDTO row : dailyRows) {
            cumulative += row.totalVnd;
            xList.add(row.dayNum);
            yList.add(cumulative);
            forecastPoints.add(new ForecastPoint(row.dayNum, (long) cumulative, false));
        }

        long spentSoFar = (long) cumulative;
        long limit = budget.getLimitAmount();

        // Tính burn rate và time elapsed (ép kiểu double — tránh 9/30=0 trong Java)
        double burnRate = (limit > 0) ? (double) spentSoFar / limit : 0;
        double timeElapsedRatio = (double) currentDay / daysInMonth;

        // Dự báo cuối tháng
        long projectedEnd;
        int n = xList.size();

        if (n < 2) {
            // Fallback khi chưa đủ điểm cho OLS: dùng trung bình tuyến tính
            projectedEnd = (currentDay > 0)
                    ? (long) ((double) spentSoFar / currentDay * daysInMonth)
                    : spentSoFar;
        } else {
            int[] x = new int[n];
            double[] y = new double[n];
            for (int i = 0; i < n; i++) { x[i] = xList.get(i); y[i] = yList.get(i); }

            RegressionHelper.OlsResult ols = RegressionHelper.fit(x, y);
            if (ols == null) {
                projectedEnd = spentSoFar;
            } else {
                projectedEnd = Math.max(0, (long) (ols.beta0 + ols.beta1 * daysInMonth));
                // Thêm các điểm dự báo vào chart (ngày hiện tại+1 → cuối tháng)
                int lastAdded = currentDay;
                for (int d = currentDay + 1; d <= daysInMonth; d += 2) {
                    long proj = Math.max(0, (long) (ols.beta0 + ols.beta1 * d));
                    forecastPoints.add(new ForecastPoint(d, proj, true));
                    lastAdded = d;
                }
                // Thêm điểm cuối tháng nếu vòng lặp chưa đến đúng daysInMonth
                if (lastAdded != daysInMonth) {
                    forecastPoints.add(new ForecastPoint(daysInMonth, projectedEnd, true));
                }
            }
        }

        long overrun = projectedEnd - limit;
        int daysLeft = daysInMonth - currentDay;
        String catName = budget.getCategoryName();

        // Tạo insight
        Insight insight = new Insight();
        insight.categoryName = catName;
        insight.burnRate = burnRate;
        insight.projectedAmountVnd = projectedEnd;
        insight.overrunAmountVnd = Math.max(0, overrun);
        insight.limitAmountVnd = limit;
        insight.daysLeft = daysLeft;

        if (overrun > 0) {
            insight.type = InsightType.BUDGET_WARNING;
            insight.priority = InsightPriority.HIGH;
            insight.title = "Ngân sách " + catName + " sắp vượt mức";
            insight.body = String.format(
                    "Với tốc độ hiện tại, bạn sẽ vượt ngân sách %s khoảng %s vào cuối tháng. Còn %d ngày — hãy kiểm soát chi tiêu.",
                    catName, Insight.formatVnd(overrun), daysLeft);
        } else if (burnRate > timeElapsedRatio + 0.10) {
            insight.type = InsightType.BUDGET_WARNING;
            insight.priority = InsightPriority.MEDIUM;
            insight.title = "Ngân sách " + catName + " đang chi nhanh";
            insight.body = String.format(
                    "Bạn đã dùng %.0f%% ngân sách %s nhưng tháng mới qua %.0f%%. Cần chú ý trong %d ngày còn lại.",
                    burnRate * 100, catName, timeElapsedRatio * 100, daysLeft);
        } else {
            insight.type = InsightType.BUDGET_OK;
            insight.priority = InsightPriority.LOW;
            insight.title = "Ngân sách " + catName + " ổn định";
            insight.body = String.format(
                    "Bạn đang kiểm soát tốt ngân sách %s (đã dùng %.0f%%).",
                    catName, burnRate * 100);
        }

        return new BudgetForecastResult(insight, forecastPoints);
    }
}
