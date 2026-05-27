package vn.edu.usth.tip.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.ai.InsightRequest;
import vn.edu.usth.tip.backend.dto.ai.InsightResponse;
import vn.edu.usth.tip.backend.utils.GeminiConstants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Calls Gemini to generate natural-language Vietnamese insight sentences.
 * Android computes the numbers on-device; this service only "dresses" them in language.
 */
@Slf4j
@Service
public class InsightService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("'tháng' M'/'yyyy");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(GeminiConstants.CONNECT_TIMEOUT_SECONDS))
            .build();

    public InsightResponse generateInsights(InsightRequest request) {
        try {
            if (isEmpty(request)) {
                return InsightResponse.builder().insights(Collections.emptyList()).build();
            }
            String prompt = buildPrompt(request);
            String raw = callGemini(prompt);
            return parseResponse(raw);
        } catch (Exception e) {
            log.error("InsightService error: {}", e.getMessage(), e);
            // Trả về rỗng — Android đã có offline template fallback
            return InsightResponse.builder().insights(Collections.emptyList()).build();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isEmpty(InsightRequest req) {
        return (req.getBudgetAlerts() == null   || req.getBudgetAlerts().isEmpty())
            && (req.getAnomalies()    == null   || req.getAnomalies().isEmpty())
            && (req.getGoalInsights() == null   || req.getGoalInsights().isEmpty())
            && (req.getPatterns()     == null   || req.getPatterns().isEmpty());
    }

    private String buildPrompt(InsightRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý tài chính của ứng dụng quản lý chi tiêu cá nhân tại Việt Nam.\n");
        sb.append("Nhiệm vụ: Với mỗi phân tích bên dưới, viết title (tiêu đề ngắn ≤10 từ) ");
        sb.append("và body (1-2 câu tự nhiên, có số liệu cụ thể, đơn vị ₫).\n");
        sb.append("Quy tắc: số tiền dùng dấu chấm ngăn hàng nghìn (1.200.000₫). ");
        sb.append("Giọng điệu: thân thiện, cảnh báo nhẹ nhàng, khích lệ khi tốt.\n\n");

        sb.append("=== DỮ LIỆU ===\n\n");

        // Budget alerts
        if (req.getBudgetAlerts() != null) {
            for (InsightRequest.BudgetAlert b : req.getBudgetAlerts()) {
                long overrun = b.getForecastVnd() - b.getLimitVnd();
                sb.append("[BUDGET_WARNING] Danh mục: ").append(b.getCategory()).append("\n");
                sb.append("  Giới hạn: ").append(fmt(b.getLimitVnd()))
                  .append(" | Dự báo cuối tháng: ").append(fmt(b.getForecastVnd())).append("\n");
                sb.append("  Đã chi: ").append(String.format("%.0f%%", b.getBurnRate() * 100))
                  .append(" | Còn ").append(b.getDaysLeft()).append(" ngày");
                if (overrun > 0) sb.append(" | Sẽ vượt khoảng: ").append(fmt(overrun));
                sb.append("\n\n");
            }
        }

        // Anomalies
        if (req.getAnomalies() != null) {
            for (InsightRequest.AnomalyData a : req.getAnomalies()) {
                String dir = a.getZScore() > 0 ? "TĂNG" : "GIẢM";
                double ratio = a.getAvgVnd() > 0
                        ? (double) a.getThisMonthVnd() / a.getAvgVnd() : 0;
                sb.append("[ANOMALY-").append(dir).append("] Danh mục: ").append(a.getCategory()).append("\n");
                sb.append("  Tháng này: ").append(fmt(a.getThisMonthVnd()))
                  .append(" | TB 3 tháng trước: ").append(fmt(a.getAvgVnd()));
                if (ratio > 0) sb.append(String.format(" | Gấp %.1f lần", ratio));
                sb.append("\n\n");
            }
        }

        // Goals
        if (req.getGoalInsights() != null) {
            for (InsightRequest.GoalData g : req.getGoalInsights()) {
                sb.append("[GOAL_WARNING] Mục tiêu: ").append(g.getName()).append("\n");
                sb.append("  Còn thiếu: ").append(fmt(g.getRemainingVnd()))
                  .append(" | Tốc độ tiết kiệm: ").append(fmt(g.getVelocityPerWeek())).append("/tuần\n");
                if (g.getTargetDateMs() > 0) {
                    sb.append("  Hạn chót: ").append(toMonthLabel(g.getTargetDateMs()));
                }
                if (g.getProjectedDateMs() > 0) {
                    sb.append(" | Dự kiến hoàn thành: ").append(toMonthLabel(g.getProjectedDateMs()));
                }
                sb.append("\n\n");
            }
        }

        // Patterns
        if (req.getPatterns() != null) {
            for (InsightRequest.PatternData p : req.getPatterns()) {
                sb.append("[PATTERN] Ngày chi nhiều nhất: ").append(p.getTopDayLabel()).append("\n");
                sb.append("  TB ngày đó: ").append(fmt(p.getAvgTopDayVnd()))
                  .append(String.format(" | Gấp %.1f lần ngày ít nhất\n\n", p.getRatio()));
            }
        }

        sb.append("=== OUTPUT ===\n");
        sb.append("JSON array, mỗi phần tử: {type, referenceName, title, body}.\n");
        sb.append("type: BUDGET_WARNING / ANOMALY / GOAL_WARNING / PATTERN\n");
        sb.append("referenceName: tên danh mục hoặc mục tiêu (khớp chính xác với tên trong Danh mục / Mục tiêu)\n");
        sb.append("Số lượng phần tử = đúng bằng số block DỮ LIỆU ở trên.\n");

        return sb.toString();
    }

    private String callGemini(String prompt) throws Exception {
        HttpRequest httpReq = buildGeminiRequest(prompt);

        log.debug("Calling Gemini for insight generation...");
        HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Gemini error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini HTTP " + response.statusCode());
        }

        return response.body();
    }

    private HttpRequest buildGeminiRequest(String prompt) throws Exception {
        // API key in URL param is required by the Gemini REST endpoint for server-to-server calls
        String url = GEMINI_URL + "?key=" + apiKey;

        Map<String, Object> itemSchema = Map.of(
            "type",       "OBJECT",
            "properties", Map.of(
                "type",          Map.of("type", "STRING"),
                "referenceName", Map.of("type", "STRING"),
                "title",         Map.of("type", "STRING"),
                "body",          Map.of("type", "STRING")
            ),
            "required", List.of("type", "referenceName", "title", "body")
        );
        Map<String, Object> responseSchema = Map.of(
            "type", "OBJECT",
            "properties", Map.of("insights", Map.of("type", "ARRAY", "items", itemSchema)),
            "required", List.of("insights")
        );
        Map<String, Object> generationConfig = Map.of(
            "temperature",      GeminiConstants.TEMPERATURE_CREATIVE,
            "responseMimeType", "application/json",
            "responseSchema",   responseSchema
        );
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", generationConfig
        );

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(GeminiConstants.READ_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
    }

    private InsightResponse parseResponse(String rawBody) throws Exception {
        JsonNode root = objectMapper.readTree(rawBody);
        String text = root.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

        // Strip markdown code block nếu có
        text = text.trim();
        if (text.startsWith("```json")) text = text.substring(7);
        else if (text.startsWith("```"))  text = text.substring(3);
        if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        text = text.trim();

        JsonNode parsed       = objectMapper.readTree(text);
        JsonNode insightsNode = parsed.path("insights");

        List<InsightResponse.AiInsight> insights = new ArrayList<>();
        if (insightsNode.isArray()) {
            for (JsonNode node : insightsNode) {
                String title = node.path("title").asText();
                String body  = node.path("body").asText();
                // Skip nếu Gemini trả về rỗng (trường hợp không đủ dữ liệu)
                if (title.isBlank() && body.isBlank()) continue;

                insights.add(new InsightResponse.AiInsight(
                        node.path("type").asText(),
                        node.path("referenceName").asText(),
                        title,
                        body
                ));
            }
        }

        log.info("Gemini returned {} insights", insights.size());
        return InsightResponse.builder().insights(insights).build();
    }

    // ── Format helpers ────────────────────────────────────────────────────────

    private static String fmt(long amount) {
        return String.format("%,d₫", amount).replace(",", ".");
    }

    private String toMonthLabel(long epochMs) {
        if (epochMs <= 0) return "";
        LocalDate date = LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
        return date.format(MONTH_FMT);
    }
}
