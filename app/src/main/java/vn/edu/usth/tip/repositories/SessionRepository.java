package vn.edu.usth.tip.repositories;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.network.AuthApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.responses.SessionResponse;
import vn.edu.usth.tip.utils.TokenManager;

/** Gọi các endpoint quản lý phiên/thiết bị (cần access token). */
public class SessionRepository {

    private final AuthApi authApi;

    public SessionRepository(Context context) {
        TokenManager tokenManager = TokenManager.getOrCreate(context.getApplicationContext());
        this.authApi = RetrofitClient.createService(AuthApi.class, tokenManager);
    }

    public void loadSessions(MutableLiveData<List<SessionResponse>> data, MutableLiveData<String> error) {
        authApi.getSessions().enqueue(new Callback<List<SessionResponse>>() {
            @Override
            public void onResponse(Call<List<SessionResponse>> call, Response<List<SessionResponse>> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    data.postValue(resp.body());
                } else {
                    error.postValue("Không tải được danh sách phiên (" + resp.code() + ")");
                }
            }

            @Override
            public void onFailure(Call<List<SessionResponse>> call, Throwable t) {
                error.postValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    public void revoke(String sessionId, Runnable onDone, MutableLiveData<String> error) {
        authApi.revokeSession(sessionId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> resp) {
                if (resp.isSuccessful()) onDone.run();
                else error.postValue("Thu hồi thất bại (" + resp.code() + ")");
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                error.postValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    public void logoutOthers(Runnable onDone, MutableLiveData<String> error) {
        authApi.logoutOthers().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> resp) {
                if (resp.isSuccessful()) onDone.run();
                else error.postValue("Thao tác thất bại (" + resp.code() + ")");
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                error.postValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }
}
