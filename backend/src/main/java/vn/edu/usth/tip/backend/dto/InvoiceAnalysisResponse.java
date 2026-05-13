package vn.edu.usth.tip.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body chứa kết quả phân tích hóa đơn từ LLM (Gemini).
 * Được trả về cho Android để tự động điền vào ExtractInvoiceFragment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceAnalysisResponse {
    /** Tổng tiền thanh toán (VND), VD: 49300 */
    private long amount;

    /** Tên cửa hàng / đơn vị bán hàng */
    private String shopName;

    /** Ngày trên hóa đơn, định dạng "yyyy-MM-dd" hoặc rỗng nếu không tìm thấy */
    private String date;

    /** Ghi chú bổ sung (mô tả ngắn) */
    private String note;

    /** true nếu LLM phân tích thành công, false nếu fallback hoặc lỗi */
    private boolean success;

    /** Thông báo lỗi (nếu có) */
    private String errorMessage;
}
