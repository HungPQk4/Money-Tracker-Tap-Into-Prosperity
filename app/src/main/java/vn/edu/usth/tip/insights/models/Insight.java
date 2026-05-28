package vn.edu.usth.tip.insights.models;

import java.text.NumberFormat;
import java.util.Locale;

public class Insight {
    public InsightType type;
    public InsightPriority priority;
    public String title;
    public String body;

    // Raw metrics — dùng để build template text khi offline
    public String categoryName;
    public long projectedAmountVnd;
    public long overrunAmountVnd;
    public long suggestedTopUpVnd;
    public double zScore;
    public double burnRate;
    public String projectedDateLabel;

    // BUDGET_WARNING — context cho backend sinh câu văn cụ thể
    public long limitAmountVnd;     // Giới hạn ngân sách (VNĐ)
    public int daysLeft;            // Số ngày còn lại trong tháng

    // ANOMALY — baseline cho backend so sánh
    public long historicalAvgVnd;   // Trung bình 3 tháng trước (VNĐ)

    // GOAL types — tiến độ mục tiêu đầy đủ
    public long remainingVnd;       // Số tiền còn thiếu (VNĐ)
    public long velocityPerWeekVnd; // Tốc độ tiết kiệm EWMA hiện tại (VNĐ/tuần)
    public long goalTargetDateMs;   // Hạn chót của mục tiêu (timestamp)
    public long goalProjectedDateMs;// Ngày hoàn thành dự báo (timestamp)

    // PATTERN — chi tiết ngày chi tiêu cao nhất
    public String topDayLabel;      // Tên ngày (VD: "Thứ 7")
    public long avgTopDayVnd;       // Chi tiêu trung bình ngày đó (VNĐ)

    // DEBT/LOAN — context nhắc nhở vay nợ
    public String personName;       // Tên người liên quan
    public long debtDueDateMs;      // Ngày đến hạn (0 = không có hạn)

    public Insight() {}

    // Copy constructor — BẮT BUỘC để mergeWithApi tạo deep copy
    // tránh DiffUtil không phát hiện thay đổi vì cùng tham chiếu bộ nhớ
    public Insight(Insight other) {
        this.type = other.type;
        this.priority = other.priority;
        this.title = other.title;
        this.body = other.body;
        this.categoryName = other.categoryName;
        this.projectedAmountVnd = other.projectedAmountVnd;
        this.overrunAmountVnd = other.overrunAmountVnd;
        this.suggestedTopUpVnd = other.suggestedTopUpVnd;
        this.zScore = other.zScore;
        this.burnRate = other.burnRate;
        this.projectedDateLabel = other.projectedDateLabel;
        this.limitAmountVnd = other.limitAmountVnd;
        this.daysLeft = other.daysLeft;
        this.historicalAvgVnd = other.historicalAvgVnd;
        this.remainingVnd = other.remainingVnd;
        this.velocityPerWeekVnd = other.velocityPerWeekVnd;
        this.goalTargetDateMs = other.goalTargetDateMs;
        this.goalProjectedDateMs = other.goalProjectedDateMs;
        this.topDayLabel = other.topDayLabel;
        this.avgTopDayVnd = other.avgTopDayVnd;
        this.personName = other.personName;
        this.debtDueDateMs = other.debtDueDateMs;
    }

    // Ghim cứng Locale Việt Nam — tránh máy cài English-US hiển thị "$1,200,000.00"
    private static final NumberFormat VND_FORMAT =
            NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public static String formatVnd(long amount) {
        return VND_FORMAT.format(amount);
    }
}
