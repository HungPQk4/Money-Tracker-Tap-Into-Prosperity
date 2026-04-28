package vn.edu.usth.tip.network;

import java.util.List;
import java.util.UUID;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import vn.edu.usth.tip.network.requests.CreateTransactionRequest;
import vn.edu.usth.tip.network.requests.SyncBatchRequest;
import vn.edu.usth.tip.network.responses.SyncBatchResponse;
import vn.edu.usth.tip.network.responses.TransactionDto;

public interface TransactionApi {

    @GET("transactions")
    Call<List<TransactionDto>> getAllTransactions();

    @POST("transactions")
    Call<TransactionDto> createTransaction(@Body CreateTransactionRequest request);

    /**
     * Đồng bộ batch giao dịch cũ từ Room lên Neon.
     * Server giữ nguyên createdAt gốc của từng bản ghi.
     */
    @POST("transactions/sync")
    Call<SyncBatchResponse> syncBatch(@Body SyncBatchRequest request);

    /**
     * Lấy giao dịch N ngày gần nhất từ Neon (mặc định 30 ngày).
     */
    @GET("transactions/recent")
    Call<List<TransactionDto>> getRecentTransactions(@Query("days") int days);

    @PUT("transactions/{id}")
    Call<TransactionDto> updateTransaction(@Path("id") UUID id, @Body CreateTransactionRequest request);

    @DELETE("transactions/{id}")
    Call<Void> deleteTransaction(@Path("id") UUID id);
}
