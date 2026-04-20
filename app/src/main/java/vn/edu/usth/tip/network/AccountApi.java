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

    @retrofit2.http.PUT("accounts/{id}")
    Call<AccountResponse> updateAccount(@retrofit2.http.Path("id") String id, @Body AccountRequest request);

    @retrofit2.http.DELETE("accounts/{id}")
    Call<Void> deleteAccount(@retrofit2.http.Path("id") String id);
}
