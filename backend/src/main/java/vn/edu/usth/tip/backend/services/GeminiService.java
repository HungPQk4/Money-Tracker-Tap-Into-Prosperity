package vn.edu.usth.tip.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.InvoiceAnalysisResponse;
import vn.edu.usth.tip.backend.utils.GeminiConstants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Calls the Gemini API to extract structured invoice data from raw OCR text.
 * Flow: Raw OCR Text → Prompt Engineering → Gemini → Parse JSON → InvoiceAnalysisResponse
 */
@Slf4j
@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    // gemini-2.5-flash: tested as the latest flash model accessible with the current API key
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(GeminiConstants.CONNECT_TIMEOUT_SECONDS))
            .build();

    public InvoiceAnalysisResponse analyzeInvoice(String rawText) {
        try {
            String geminiResponse = callGeminiApi(rawText);
            return parseGeminiResponse(geminiResponse);
        } catch (Exception e) {
            log.error("Gemini API error: {}", e.getMessage(), e);
            return InvoiceAnalysisResponse.builder()
                    .success(false)
                    .errorMessage("Lỗi phân tích hóa đơn: " + e.getMessage())
                    .build();
        }
    }

    private String callGeminiApi(String rawText) throws Exception {
        String requestBody = serializeGeminiStructuredRequest(rawText);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_URL))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(GeminiConstants.READ_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.debug("Calling Gemini API...");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Gemini API returned status {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini API error: HTTP " + response.statusCode()
                    + " — " + response.body());
        }

        log.debug("Gemini response received, parsing...");
        return response.body();
    }

    private InvoiceAnalysisResponse parseGeminiResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty() || !candidates.isArray()) {
            throw new RuntimeException("Gemini response has no candidates");
        }

        String text = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

        log.debug("Gemini extracted text: {}", text);

        // Structured Output mode returns JSON, but the model occasionally wraps it in a markdown block
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();

        JsonNode result = objectMapper.readTree(text);

        return InvoiceAnalysisResponse.builder()
                .amount(result.path("amount").asLong(0))
                .shopName(result.path("shopName").asText(""))
                .date(result.path("date").asText(""))
                .note(result.path("note").asText(""))
                .success(true)
                .build();
    }

    private String serializeGeminiStructuredRequest(String rawText) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(rawText))))),
                "generationConfig", buildGenerationConfig()
        );
        return objectMapper.writeValueAsString(requestBody);
    }

    private String buildPrompt(String rawText) {
        String currentDate = LocalDate.now().toString();
        return "Bạn là hệ thống trích xuất dữ liệu hóa đơn siêu thông minh và chính xác.\n"
                + "Ngày hiện tại của hệ thống: " + currentDate + ".\n"
                + "Nhiệm vụ: đọc văn bản OCR thô bên dưới và trích xuất thông tin chặt chẽ theo các quy tắc (RULES) sau:\n"
                + " - RULE 1 (AMOUNT - KHỬ NHIỄU SUBTOTAL & CHỐNG NHIỄU): Ưu tiên số 1 là 'Tổng cộng' / 'Thành tiền' (Total). Quy tắc chỉ rõ sự khác biệt giữa Tổng cộng và Tổng (Subtotal), tuyệt đối không lấy Subtotal. Phớt lờ tiền khách đưa, tiền thối.\n"
                + " - RULE 2 (AMOUNT - ERROR CORRECTION): Nếu OCR đọc thiếu hoặc mất số ở Tổng Tiền do bị che/rách (VD: mất số 0 cuối), hãy dùng khả năng toán học để kiểm tra chéo (VD: Subtotal + VAT = Total) và tự động sửa lại thành giá trị logic nhất.\n"
                + " - RULE 3 (DATE - CHỐNG HALLUCINATION & ÉP FORMAT): TUYỆT ĐỐI KHÔNG TỰ BỊA RA NGÀY. Dựa vào 'Ngày hiện tại' để suy luận nếu hóa đơn chỉ ghi ngày/tháng (thiếu năm). Nếu hóa đơn hoàn toàn không có ngày, trả về rỗng \"\". Nếu có, ép về 'yyyy-MM-dd'.\n"
                + " - RULE 4 (SHOP NAME): Lấy tên cửa hàng ở phần đầu hóa đơn.\n"
                + " - RULE 5 (NOTE): Tóm tắt các mặt hàng (tối đa 3 món tiêu biểu).\n\n"
                + "=== VĂN BẢN OCR THÔ ===\n" + rawText;
    }

    private Map<String, Object> buildResponseSchema() {
        Map<String, Object> amountSchema = Map.of(
                "type", "INTEGER",
                "description", "TỔNG TIỀN KHÁCH PHẢI TRẢ (Tổng cộng, Thành tiền, Total). KHỬ NHIỄU: Phân biệt rõ Tổng cộng và Tổng/Subtotal. CHỐNG NHIỄU: KHÔNG lấy tiền khách đưa, tiền thối. ERROR CORRECTION: Nếu OCR đọc thiếu số ở Tổng tiền, dùng phép toán (VD: Subtotal + VAT) để tự động sửa lại cho đúng. Chỉ trả về số nguyên."
        );
        Map<String, Object> shopNameSchema = Map.of(
                "type", "STRING",
                "description", "Tên cửa hàng, siêu thị, quán cà phê, nhà hàng xuất hóa đơn. Trả về chuỗi rỗng nếu không tìm thấy."
        );
        Map<String, Object> dateSchema = Map.of(
                "type", "STRING",
                "description", "Ngày trên hóa đơn. ÉP FORMAT: Bắt buộc chuẩn yyyy-MM-dd. ANTI-HALLUCINATION: TUYỆT ĐỐI KHÔNG TỰ BỊA RA ngày (không lấy ngày huấn luyện/hiện tại). Nếu hóa đơn không có, bắt buộc trả về chuỗi rỗng \"\"."
        );
        Map<String, Object> noteSchema = Map.of(
                "type", "STRING",
                "description", "Mô tả ngắn gọn nội dung mua bán hoặc các mặt hàng tiêu biểu trên hóa đơn (VD: 'Cà phê, bánh mì'). Trả về rỗng nếu không xác định được."
        );
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "amount", amountSchema,
                        "shopName", shopNameSchema,
                        "date", dateSchema,
                        "note", noteSchema
                ),
                "required", List.of("amount", "shopName", "date", "note")
        );
    }

    private Map<String, Object> buildGenerationConfig() {
        return Map.of(
                "temperature", GeminiConstants.TEMPERATURE_DETERMINISTIC,
                "responseMimeType", "application/json",
                "responseSchema", buildResponseSchema()
        );
    }
}
