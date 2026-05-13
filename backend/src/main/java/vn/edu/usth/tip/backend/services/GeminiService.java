package vn.edu.usth.tip.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.InvoiceAnalysisResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service gọi Gemini API để phân tích hóa đơn từ văn bản OCR thô.
 * <p>
 * Flow: Raw OCR Text → Prompt Engineering → Gemini → Parse JSON → InvoiceAnalysisResponse
 */
@Slf4j
@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Gửi raw OCR text tới Gemini API và trả về kết quả phân tích hóa đơn.
     *
     * @param rawText Văn bản thô từ ML Kit OCR
     * @return InvoiceAnalysisResponse chứa amount, shopName, date, note
     */
    public InvoiceAnalysisResponse analyzeInvoice(String rawText) {
        try {
            String prompt = buildPrompt(rawText);
            String geminiResponse = callGeminiApi(prompt);
            return parseGeminiResponse(geminiResponse);
        } catch (Exception e) {
            log.error("Gemini API error: {}", e.getMessage(), e);
            return InvoiceAnalysisResponse.builder()
                    .success(false)
                    .errorMessage("Lỗi phân tích hóa đơn: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Tạo prompt hướng dẫn Gemini phân tích hóa đơn.
     * Prompt được thiết kế để:
     * - Tìm đúng tổng tiền thanh toán (không phải tiền khách đưa, tiền thối)
     * - Bỏ qua mã nhân viên, SĐT, VAT riêng
     * - Trích xuất tên cửa hàng và ngày tháng
     */
    private String buildPrompt(String rawText) {
        return """
                Bạn là hệ thống phân tích hóa đơn/receipt thông minh. Nhiệm vụ: đọc văn bản OCR thô bên dưới và trích xuất thông tin.

                QUY TẮC BẮT BUỘC:
                1. "amount" là TỔNG TIỀN KHÁCH PHẢI TRẢ (tổng cộng, thành tiền, total). KHÔNG lấy tiền khách đưa, tiền thối, tiền mặt, VAT riêng, mã nhân viên, SĐT.
                2. "shopName" là tên cửa hàng/quán/nhà hàng/doanh nghiệp xuất hóa đơn.
                3. "date" là ngày trên hóa đơn, định dạng yyyy-MM-dd. Nếu không tìm thấy, để rỗng.
                4. "note" là mô tả ngắn gọn nội dung mua hàng (VD: "Cà phê, bánh mì").
                5. Số tiền trả về là số nguyên VND (không có dấu chấm/phẩy phân cách). VD: 49300 chứ không phải 49.300.

                TRẢ VỀ ĐÚNG ĐỊNH DẠNG JSON (không markdown, không giải thích):
                {"amount": 49300, "shopName": "Highlands Coffee", "date": "2025-01-15", "note": "Cà phê sữa đá"}

                NẾU KHÔNG TRÍCH XUẤT ĐƯỢC, trả về:
                {"amount": 0, "shopName": "", "date": "", "note": ""}

                === VĂN BẢN OCR THÔ ===
                """ + rawText;
    }

    /**
     * Gọi Gemini REST API với prompt đã xây dựng.
     */
    private String callGeminiApi(String prompt) throws Exception {
        String url = GEMINI_URL + "?key=" + apiKey;

        // Build request body theo Gemini API spec
        String requestBody = serializeGeminiRequest(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.debug("Calling Gemini API...");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Gemini API returned status {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini API error: HTTP " + response.statusCode());
        }

        log.debug("Gemini response received, parsing...");
        return response.body();
    }

    /**
     * Parse response từ Gemini API để lấy JSON kết quả.
     * Gemini trả về cấu trúc: { candidates: [{ content: { parts: [{ text: "..." }] } }] }
     */
    private InvoiceAnalysisResponse parseGeminiResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // Lấy text từ candidates[0].content.parts[0].text
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

        // Gemini có thể bọc JSON trong markdown code block, cần strip
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

        // Parse JSON trả về từ Gemini
        JsonNode result = objectMapper.readTree(text);

        return InvoiceAnalysisResponse.builder()
                .amount(result.path("amount").asLong(0))
                .shopName(result.path("shopName").asText(""))
                .date(result.path("date").asText(""))
                .note(result.path("note").asText(""))
                .success(true)
                .build();
    }

    /**
     * Serialize prompt thành đúng format Gemini API request body:
     * { "contents": [{ "parts": [{ "text": "..." }] }] }
     */
    private String serializeGeminiRequest(String prompt) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of(
                        "contents", java.util.List.of(
                                java.util.Map.of(
                                        "parts", java.util.List.of(
                                                java.util.Map.of("text", prompt)
                                        )
                                )
                        )
                )
        );
    }
}
