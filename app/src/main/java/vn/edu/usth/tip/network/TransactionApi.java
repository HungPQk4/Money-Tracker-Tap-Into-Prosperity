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
import vn.edu.usth.tip.network.requests.CreateTransactionRequest;
import vn.edu.usth.tip.network.responses.TransactionDto;

public interface TransactionApi {

    @GET("transactions")
    Call<List<TransactionDto>> getAllTransactions();

    @POST("transactions")
    Call<TransactionDto> createTransaction(@Body CreateTransactionRequest request);

    @PUT("transactions/{id}")
    Call<TransactionDto> updateTransaction(@Path("id") UUID id, @Body CreateTransactionRequest request);

    @DELETE("transactions/{id}")
    Call<Void> deleteTransaction(@Path("id") UUID id);
}
