package vn.edu.usth.tip.network.requests;

/**
 * Request body gửi lên POST /api/invoices/analyze.
 * Chứa toàn bộ văn bản thô từ ML Kit OCR.
 */
public class InvoiceAnalysisRequest {
    private String rawText;

    public InvoiceAnalysisRequest() {}

    public InvoiceAnalysisRequest(String rawText) {
        this.rawText = rawText;
    }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
}
