package vn.edu.usth.tip.insights.engine;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import vn.edu.usth.tip.insights.models.InsightType;
import vn.edu.usth.tip.models.dto.CategoryMonthlyDTO;

public class AnomalyDetector {

    private static final double Z_SCORE_ANOMALY_THRESHOLD = 2.0;
    private static final double MIN_STD_DEV               = 1.0; // clamp near-zero to avoid IEEE 754 instability
    private static final int    HISTORY_MONTHS            = 3;
    private static final int    MIN_NONZERO_HISTORY       = 2;

    public List<Insight> detect(List<CategoryMonthlyDTO> rows) {
        List<Insight> results = new ArrayList<>();

        YearMonth now        = YearMonth.now();
        String    currentKey = toKey(now);

        // Historical baseline: oldest → newest (e.g. indices 0..2 = 3, 2, 1 months ago)
        String[] historicalKeys = new String[HISTORY_MONTHS];
        for (int i = 0; i < HISTORY_MONTHS; i++) {
            historicalKeys[i] = toKey(now.minusMonths(HISTORY_MONTHS - i));
        }

        // Group DB rows: category → (yearMonth key → totalVnd)
        // SQL GROUP BY guarantees no duplicate (category, yearMonth) pairs, so no merge needed.
        Map<String, Map<String, Long>> byCategory = rows.stream().collect(
                Collectors.groupingBy(
                        r -> r.category,
                        Collectors.toMap(r -> r.yearMonth, r -> r.totalVnd)
                )
        );

        for (Map.Entry<String, Map<String, Long>> entry : byCategory.entrySet()) {
            String           category = entry.getKey();
            Map<String, Long> monthMap = entry.getValue();

            // Current month is comparison target only — excluded from baseline to prevent data leakage
            long current = monthMap.getOrDefault(currentKey, 0L);

            long[] historical = new long[HISTORY_MONTHS];
            for (int i = 0; i < HISTORY_MONTHS; i++) {
                historical[i] = monthMap.getOrDefault(historicalKeys[i], 0L);
            }

            // Require at least MIN_NONZERO_HISTORY months of real spend so stdDev is statistically meaningful
            int nonZeroCount = 0;
            for (long v : historical) if (v > 0) nonZeroCount++;
            if (nonZeroCount < MIN_NONZERO_HISTORY) continue;

            double mean   = RegressionHelper.mean(historical);
            double stdDev = RegressionHelper.sampleStdDev(historical);
            if (stdDev < MIN_STD_DEV) stdDev = MIN_STD_DEV;

            double z = (current - mean) / stdDev;
            if (Math.abs(z) <= Z_SCORE_ANOMALY_THRESHOLD) continue;

            Insight insight = new Insight();
            insight.type               = InsightType.ANOMALY;
            insight.priority           = z > 0 ? InsightPriority.HIGH : InsightPriority.MEDIUM;
            insight.categoryName       = category;
            insight.zScore             = z;
            insight.projectedAmountVnd = current;
            insight.historicalAvgVnd   = (long) mean;

            String currentFmt = Insight.formatVnd(current);
            String avgFmt     = Insight.formatVnd((long) mean);

            if (z > 0) {
                String ratio = String.format("%.1f", current / Math.max(1.0, mean));
                insight.title = category + " tăng đột biến";
                insight.body  = "Chi tiêu " + category + " tháng này (" + currentFmt
                        + ") cao hơn " + ratio + " lần so với mức trung bình ("
                        + avgFmt + "). Bạn có thể cần xem lại.";
            } else {
                insight.title = category + " giảm mạnh";
                insight.body  = "Chi tiêu " + category + " tháng này (" + currentFmt
                        + ") thấp hơn nhiều so với thói quen (" + avgFmt + "). Tiết kiệm tốt!";
            }

            results.add(insight);
        }

        return results;
    }

    private static String toKey(YearMonth ym) {
        int m = ym.getMonthValue();
        return String.valueOf(ym.getYear()) + (m < 10 ? "0" : "") + m;
    }
}
