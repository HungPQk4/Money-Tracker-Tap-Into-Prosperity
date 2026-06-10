package vn.edu.usth.tip.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import vn.edu.usth.tip.network.requests.LoginRequest;
import vn.edu.usth.tip.network.requests.RefreshRequest;
import vn.edu.usth.tip.network.requests.RegisterRequest;
import vn.edu.usth.tip.network.responses.AuthResponse;
import vn.edu.usth.tip.network.responses.SessionResponse;

public interface AuthApi {

    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    // Đổi refresh token lấy access token mới (không cần access token hợp lệ).
    @POST("auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshRequest request);

    // ─── Quản lý phiên/thiết bị (cần access token) ──────────────────────────────
    @POST("auth/logout")
    Call<Void> logout();

    @GET("auth/sessions")
    Call<List<SessionResponse>> getSessions();

    @DELETE("auth/sessions/{id}")
    Call<Void> revokeSession(@Path("id") String id);

    @POST("auth/sessions/logout-others")
    Call<Void> logoutOthers();
}
