package vn.edu.usth.tip.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import vn.edu.usth.tip.network.requests.AccountRequest;
import vn.edu.usth.tip.network.responses.AccountResponse;

import java.util.List;

public interface AccountApi {
    @GET("accounts")
    Call<List<AccountResponse>> getAllAccounts();

    @POST("accounts")
    Call<AccountResponse> createAccount(@Body AccountRequest request);
}
