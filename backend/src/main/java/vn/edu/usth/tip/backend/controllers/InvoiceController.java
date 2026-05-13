package vn.edu.usth.tip.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.usth.tip.backend.dto.InvoiceAnalysisRequest;
import vn.edu.usth.tip.backend.dto.InvoiceAnalysisResponse;
import vn.edu.usth.tip.backend.services.GeminiService;

/**
 * Controller xử lý phân tích hóa đơn bằng LLM (Gemini).
 * <p>
 * Android gửi raw OCR text → Spring Boot → Gemini → Structured JSON → Android.
 */
@Slf4j
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final GeminiService geminiService;

    /**
     * POST /api/invoices/analyze
     * <p>
     * Nhận văn bản thô từ ML Kit OCR, gửi tới Gemini để phân tích ngữ nghĩa,
     * trả về structured JSON chứa amount, shopName, date, note.
     *
     * @param request Chứa trường rawText — toàn bộ văn bản OCR
     * @return InvoiceAnalysisResponse
     */
    @PostMapping("/analyze")
    public ResponseEntity<InvoiceAnalysisResponse> analyzeInvoice(
            @RequestBody InvoiceAnalysisRequest request) {

        log.info("POST /api/invoices/analyze — rawText length: {}",
                request.getRawText() != null ? request.getRawText().length() : 0);

        if (request.getRawText() == null || request.getRawText().isBlank()) {
            return ResponseEntity.badRequest().body(
                    InvoiceAnalysisResponse.builder()
                            .success(false)
                            .errorMessage("rawText không được để trống")
                            .build()
            );
        }

        InvoiceAnalysisResponse response = geminiService.analyzeInvoice(request.getRawText());

        log.info("Analysis result: success={}, amount={}, shop={}",
                response.isSuccess(), response.getAmount(), response.getShopName());

        return ResponseEntity.ok(response);
    }
}
