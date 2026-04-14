package vn.edu.usth.tip.network;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import vn.edu.usth.tip.network.responses.TransactionDto;

public interface TransactionApi {

    @GET("transactions")
    Call<List<TransactionDto>> getAllTransactions();
}
