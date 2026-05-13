package vn.edu.usth.tip.network.responses;

/**
 * Response body từ POST /api/invoices/analyze.
 * Chứa kết quả phân tích hóa đơn từ LLM (Gemini).
 */
public class InvoiceAnalysisResponse {
    private long amount;
    private String shopName;
    private String date;
    private String note;
    private boolean success;
    private String errorMessage;

    public InvoiceAnalysisResponse() {}

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
