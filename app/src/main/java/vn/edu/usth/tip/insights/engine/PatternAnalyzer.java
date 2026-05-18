package vn.edu.usth.tip.insights.engine;

import java.util.List;

import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import vn.edu.usth.tip.insights.models.InsightType;
import vn.edu.usth.tip.models.dto.DayPatternDTO;

public class PatternAnalyzer {

    private static final String[] DAY_LABELS = {
            "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư",
            "Thứ Năm", "Thứ Sáu", "Thứ Bảy"
    };

    public Insight analyze(List<DayPatternDTO> rows) {
        // Zero-Padding: SQL GROUP BY bỏ qua ngày không có giao dịch
        // Khởi tạo mảng 7 phần tử = 0.0 trước, điền từ DB, ngày thiếu giữ = 0
        double[] avgSpendByDay = new double[7];
        for (DayPatternDTO row : rows) {
            try {
                int dayIndex = Integer.parseInt(row.dayOfWeek); // "0"=CN.."6"=Bảy
                if (dayIndex >= 0 && dayIndex < 7) {
                    avgSpendByDay[dayIndex] = row.avgSpend;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Tìm ngày chi tiêu nhiều nhất và ít nhất
        int maxDay = 0, minDay = 0;
        for (int i = 1; i < 7; i++) {
            if (avgSpendByDay[i] > avgSpendByDay[maxDay]) maxDay = i;
            if (avgSpendByDay[i] < avgSpendByDay[minDay]) minDay = i;
        }

        // Guard: tránh chia cho 0 khi người dùng không chi vào ngày minDay
        // Math.max(1.0, ...) đủ vì tiền VNĐ nhỏ nhất là 1 đồng
        double minSpend = Math.max(1.0, avgSpendByDay[minDay]);
        double ratio = avgSpendByDay[maxDay] / minSpend;

        if (ratio < 1.5) return null; // Không có xu hướng đáng kể

        Insight insight = new Insight();
        insight.type = InsightType.PATTERN;
        insight.priority = InsightPriority.LOW;
        insight.title = DAY_LABELS[maxDay] + " là ngày chi tiêu nhiều nhất";
        insight.body = String.format(
                "Bạn thường chi tiêu nhiều nhất vào %s (trung bình %s), gấp %.1f lần so với %s. Hãy lên kế hoạch trước cho ngày này.",
                DAY_LABELS[maxDay],
                Insight.formatVnd((long) avgSpendByDay[maxDay]),
                ratio,
                DAY_LABELS[minDay]);
        insight.burnRate = ratio;
        insight.topDayLabel = DAY_LABELS[maxDay];
        insight.avgTopDayVnd = (long) avgSpendByDay[maxDay];

        return insight;
    }
}
