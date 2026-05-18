package vn.edu.usth.tip.backend.dto.ai;

import lombok.Data;

import java.util.List;

/**
 * Request body Android gửi lên sau khi tính toán on-device.
 * Chứa raw metrics đã được engine tính sẵn — backend chỉ cần "dress" bằng câu văn tự nhiên.
 */
@Data
public class InsightRequest {

    private List<BudgetAlert>  budgetAlerts;
    private List<AnomalyData>  anomalies;
    private List<GoalData>     goalInsights;
    private List<PatternData>  patterns;

    @Data
    public static class BudgetAlert {
        private String category;
        private double burnRate;      // Tỉ lệ đã chi / giới hạn (0.0–1.0+)
        private long   forecastVnd;   // Dự báo chi tiêu cuối tháng
        private long   limitVnd;      // Giới hạn ngân sách
        private int    daysLeft;      // Số ngày còn lại trong tháng
    }

    @Data
    public static class AnomalyData {
        private String category;
        private double zScore;        // Z-score thống kê (>2 = tăng bất thường)
        private long   thisMonthVnd;  // Chi tiêu tháng hiện tại
        private long   avgVnd;        // Trung bình 3 tháng trước (baseline sạch)
    }

    @Data
    public static class GoalData {
        private String name;              // Tên mục tiêu
        private long   velocityPerWeek;   // Tốc độ tiết kiệm EWMA (VNĐ/tuần)
        private long   remainingVnd;      // Số tiền còn thiếu
        private long   targetDateMs;      // Hạn chót (epoch ms)
        private long   projectedDateMs;   // Ngày hoàn thành dự báo (epoch ms)
    }

    @Data
    public static class PatternData {
        private String topDayLabel;    // Tên ngày chi tiêu nhiều nhất (VD: "Thứ 7")
        private double ratio;          // Tỉ lệ so với ngày ít nhất
        private long   avgTopDayVnd;   // Chi tiêu trung bình ngày đó
    }
}
