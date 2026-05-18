package vn.edu.usth.tip.insights.engine;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import vn.edu.usth.tip.insights.models.InsightType;
import vn.edu.usth.tip.models.Goal;

public class GoalAdvisor {

    private static final long ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("'Tháng' M'/'yyyy", Locale.forLanguageTag("vi-VN"));

    public Insight advise(Goal goal) {
        long now = System.currentTimeMillis();
        long remaining = goal.getTargetAmount() - goal.getSavedAmount();

        if (remaining <= 0) {
            Insight done = new Insight();
            done.type = InsightType.GOAL_FORECAST;
            done.priority = InsightPriority.LOW;
            done.title = "🎉 Mục tiêu " + goal.getName() + " đã hoàn thành!";
            done.body = "Bạn đã đạt mục tiêu tiết kiệm " + goal.getName() + ". Tiếp tục phát huy!";
            return done;
        }

        // Tính số tuần từ lúc tạo goal đến nay
        // Dùng createdMs từ Goal (thêm qua migration 11→12, DEFAULT 0 cho data cũ)
        // Fallback về 90 ngày nếu createdMs = 0 (data trước migration)
        long createdMs = goal.getCreatedMs();
        if (createdMs <= 0) createdMs = now - 90L * 24 * 60 * 60 * 1000;
        long weeksSinceStart = (now - createdMs) / ONE_WEEK_MS;

        // Guard: ít nhất 1 tuần
        int size = Math.max(1, (int) weeksSinceStart);
        long[] actual = new long[size]; // khởi tạo = 0 (Zero-Padding)

        // Điền từ lịch sử giao dịch tiết kiệm (thu nhập/INCOME vào ví mục tiêu)
        // Thực tế: goal.savedAmount đã là tổng tiết kiệm, dùng nó để ước tính velocity
        // bằng cách chia đều theo số tuần đã qua
        // Điền actual[lastWeek] = savedAmount vì không có lịch sử nạp từng tuần
        if (size > 0 && goal.getSavedAmount() > 0) {
            actual[size - 1] = goal.getSavedAmount();
        }

        // EWMA α=0.3 — BẮT BUỘC double[] tránh sai số cộng dồn khi dùng long[]
        double[] ewma = new double[size];
        ewma[0] = (double) actual[0];
        for (int t = 1; t < size; t++) {
            ewma[t] = 0.3 * (double) actual[t] + 0.7 * ewma[t - 1];
        }

        // velocity = EWMA gần nhất (VNĐ/tuần)
        double velocity = ewma[size - 1];

        // Guard: chưa tiết kiệm đồng nào → velocity = 0 → chia cho 0 → năm 292.278.994
        if (velocity <= 0.01) {
            Insight notStarted = new Insight();
            notStarted.type = InsightType.GOAL_NOT_STARTED;
            notStarted.priority = InsightPriority.HIGH;
            notStarted.categoryName = goal.getName();
            notStarted.title = "Mục tiêu " + goal.getName() + " chưa bắt đầu";
            notStarted.body = "Bạn chưa tiết kiệm cho mục tiêu này. Hãy nạp số tiền đầu tiên ngay hôm nay để bắt đầu!";
            return notStarted;
        }

        double weeksToComplete = remaining / velocity;
        long projectedDateMs = now + (long) (weeksToComplete * ONE_WEEK_MS);
        String projectedDateLabel = DATE_FMT.format(new Date(projectedDateMs));

        // Guard: mục tiêu đã quá hạn → weeksLeft âm hoặc 0
        long weeksLeft = Math.max(1, (goal.getTargetDateMs() - now) / ONE_WEEK_MS);
        long suggestedTopUp = (long) (remaining / (double) weeksLeft - velocity);

        Insight insight = new Insight();
        insight.categoryName = goal.getName();
        insight.projectedAmountVnd = goal.getTargetAmount();
        insight.remainingVnd = remaining;
        insight.velocityPerWeekVnd = (long) velocity;
        insight.suggestedTopUpVnd = Math.max(0, suggestedTopUp);
        insight.projectedDateLabel = projectedDateLabel;
        insight.goalTargetDateMs = goal.getTargetDateMs();
        insight.goalProjectedDateMs = projectedDateMs;

        if (goal.getTargetDateMs() < now) {
            // Mục tiêu quá hạn
            insight.type = InsightType.GOAL_OVERDUE;
            insight.priority = InsightPriority.HIGH;
            insight.title = "Mục tiêu " + goal.getName() + " đã quá hạn";
            insight.body = String.format(
                    "Mục tiêu %s đã qua hạn và còn thiếu %s. Hãy cập nhật hạn hoặc tăng tốc tiết kiệm.",
                    goal.getName(), Insight.formatVnd(remaining));
        } else if (projectedDateMs > goal.getTargetDateMs()) {
            insight.type = InsightType.GOAL_WARNING;
            insight.priority = InsightPriority.MEDIUM;
            insight.title = "Mục tiêu " + goal.getName() + " có thể trễ hạn";
            if (suggestedTopUp > 0) {
                insight.body = String.format(
                        "Với tốc độ hiện tại, bạn đạt mục tiêu %s vào %s — trễ hơn dự kiến. Tiết kiệm thêm %s/tuần để đúng hạn.",
                        goal.getName(), projectedDateLabel, Insight.formatVnd(suggestedTopUp));
            } else {
                insight.body = String.format(
                        "Với tốc độ hiện tại, bạn đạt mục tiêu %s vào %s — trễ hơn dự kiến.",
                        goal.getName(), projectedDateLabel);
            }
        } else {
            insight.type = InsightType.GOAL_FORECAST;
            insight.priority = InsightPriority.LOW;
            insight.title = "Mục tiêu " + goal.getName() + " đang đúng tiến độ";
            insight.body = String.format(
                    "Bạn dự kiến hoàn thành mục tiêu %s vào %s. Tiếp tục duy trì nhịp tiết kiệm!",
                    goal.getName(), projectedDateLabel);
        }

        return insight;
    }
}
