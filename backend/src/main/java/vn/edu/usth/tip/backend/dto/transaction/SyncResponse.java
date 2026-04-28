package vn.edu.usth.tip.backend.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Response trả về sau khi sync batch hoàn tất.
 */
@Data
@AllArgsConstructor
public class SyncResponse {

    /** Số bản ghi đã lưu thành công vào DB. */
    private int savedCount;

    /** Số bản ghi bị bỏ qua (trùng lặp đã tồn tại trong DB). */
    private int skippedCount;

    /** Danh sách giao dịch đã được lưu (để client cập nhật ID từ server). */
    private List<TransactionResponse> savedTransactions;
}
