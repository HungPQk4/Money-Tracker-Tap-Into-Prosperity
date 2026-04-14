package vn.edu.usth.tip.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import vn.edu.usth.tip.network.requests.LoginRequest;
import vn.edu.usth.tip.network.requests.RegisterRequest;
import vn.edu.usth.tip.network.responses.AuthResponse;

public interface AuthApi {

    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);
}
