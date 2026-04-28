package vn.edu.usth.tip.backend.dto.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Payload cho API POST /api/transactions/sync
 * Client gửi lên: userId + danh sách giao dịch offline tối đa 30 ngày.
 */
@Data
public class SyncRequest {

    @NotNull
    private UUID userId;

    @NotNull
    @Size(min = 1, max = 500, message = "Batch size phải từ 1 đến 500 bản ghi")
    @Valid
    private List<SyncTransactionRequest> transactions;
}
