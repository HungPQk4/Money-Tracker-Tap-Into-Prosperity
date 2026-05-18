package vn.edu.usth.tip.insights.engine;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import vn.edu.usth.tip.insights.models.InsightType;
import vn.edu.usth.tip.models.dto.CategoryMonthlyDTO;

public class AnomalyDetector {

    public List<Insight> detect(List<CategoryMonthlyDTO> rows) {
        List<Insight> results = new ArrayList<>();

        // Chuẩn bị nhãn 4 tháng (3 tháng lịch sử + tháng hiện tại)
        // Zero-Padding: SQL GROUP BY bỏ qua tháng không có giao dịch
        Calendar cal = Calendar.getInstance();
        String currentYM = toYearMonth(cal);

        String[] last4 = new String[4];
        for (int i = 3; i >= 0; i--) {
            last4[3 - i] = toYearMonth(cal);
            cal.add(Calendar.MONTH, -1);
        }

        // Gom dữ liệu DB theo category → yearMonth → amount
        Map<String, Map<String, Long>> byCategory = new HashMap<>();
        for (CategoryMonthlyDTO row : rows) {
            byCategory
                .computeIfAbsent(row.category, k -> new HashMap<>())
                .put(row.yearMonth, row.totalVnd);
        }

        for (Map.Entry<String, Map<String, Long>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            Map<String, Long> monthMap = entry.getValue();

            // Data Leakage Prevention: lấy tháng hiện tại riêng, không đưa vào baseline
            long current = monthMap.getOrDefault(currentYM, 0L);

            // 3 tháng lịch sử (Zero-Padding: tháng thiếu = 0)
            long[] historical = new long[3];
            int idx = 0;
            for (int i = 0; i < 3; i++) {
                historical[idx++] = monthMap.getOrDefault(last4[i], 0L);
            }

            // Guard: cần ít nhất 2 tháng lịch sử có nghĩa thống kê (N-1 ≥ 1)
            if (idx < 2) continue;

            double mean = RegressionHelper.mean(historical);
            double stdDev = RegressionHelper.sampleStdDev(historical);

            // IEEE 754 Trap: KHÔNG dùng stdDev == 0 → có thể là 0.0000000000004
            // Nếu stdDev < 1.0 (ổn định hoàn toàn), set = 1.0 để vẫn phát hiện đột biến
            if (stdDev < 1.0) stdDev = 1.0;

            double z = (current - mean) / stdDev;

            if (Math.abs(z) > 2.0) {
                Insight insight = new Insight();
                insight.type = InsightType.ANOMALY;
                insight.priority = z > 0 ? InsightPriority.HIGH : InsightPriority.MEDIUM;
                insight.categoryName = category;
                insight.zScore = z;
                insight.projectedAmountVnd = current;
                insight.historicalAvgVnd = (long) mean;

                if (z > 0) {
                    insight.title = category + " tăng đột biến";
                    insight.body = String.format(
                            "Chi tiêu %s tháng này (%s) cao hơn %.1f lần so với mức trung bình (%s). Bạn có thể cần xem lại.",
                            category, Insight.formatVnd(current), current / Math.max(1, mean),
                            Insight.formatVnd((long) mean));
                } else {
                    insight.title = category + " giảm mạnh";
                    insight.body = String.format(
                            "Chi tiêu %s tháng này (%s) thấp hơn nhiều so với thói quen (%s). Tiết kiệm tốt!",
                            category, Insight.formatVnd(current), Insight.formatVnd((long) mean));
                }
                results.add(insight);
            }
        }

        return results;
    }

    private static String toYearMonth(Calendar cal) {
        return String.format("%04d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }
}
