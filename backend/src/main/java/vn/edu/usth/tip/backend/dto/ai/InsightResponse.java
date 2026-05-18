package vn.edu.usth.tip.backend.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response trả về Android: danh sách insight với tiêu đề và body bằng tiếng Việt tự nhiên.
 * Android dùng type + referenceName để match đúng insight và thay thế template text.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightResponse {

    private List<AiInsight> insights;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiInsight {
        /** Loại insight: BUDGET_WARNING / ANOMALY / GOAL_WARNING / PATTERN */
        private String type;

        /** Tên danh mục hoặc mục tiêu — Android dùng để match đúng insight */
        private String referenceName;

        /** Tiêu đề ngắn gọn (dưới 10 từ) */
        private String title;

        /** Mô tả chi tiết bằng tiếng Việt tự nhiên (1-2 câu, có số liệu) */
        private String body;
    }
}
