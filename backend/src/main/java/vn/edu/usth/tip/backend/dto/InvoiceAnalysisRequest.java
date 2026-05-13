package vn.edu.usth.tip.backend.dto;

import lombok.Data;

/**
 * Request body cho API phân tích hóa đơn bằng LLM.
 * Android gửi toàn bộ văn bản thô từ ML Kit OCR lên đây.
 */
@Data
public class InvoiceAnalysisRequest {
    private String rawText;
}
