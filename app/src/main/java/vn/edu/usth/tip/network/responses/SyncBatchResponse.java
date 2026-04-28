package vn.edu.usth.tip.network.responses;

import java.util.List;

/**
 * Response từ POST /api/transactions/sync
 */
public class SyncBatchResponse {

    /** Số bản ghi server đã lưu thành công */
    private int savedCount;

    /** Số bản ghi bị bỏ qua do trùng lặp */
    private int skippedCount;

    /** Danh sách giao dịch đã lưu (có UUID server để client cập nhật local ID) */
    private List<TransactionDto> savedTransactions;

    public int getSavedCount()      { return savedCount; }
    public int getSkippedCount()    { return skippedCount; }
    public List<TransactionDto> getSavedTransactions() { return savedTransactions; }
}
