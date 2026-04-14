package vn.edu.usth.tip.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import vn.edu.usth.tip.network.requests.LoginRequest;
import vn.edu.usth.tip.network.responses.AuthResponse;

public interface AuthApi {

    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);
    
    // Thêm register sau này nếu cần
}
