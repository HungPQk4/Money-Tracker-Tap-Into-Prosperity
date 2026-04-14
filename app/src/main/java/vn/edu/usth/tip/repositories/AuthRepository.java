package vn.edu.usth.tip.repositories;

import androidx.lifecycle.MutableLiveData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.network.AuthApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.requests.LoginRequest;
import vn.edu.usth.tip.network.responses.AuthResponse;

public class AuthRepository {
    private AuthApi authApi;

    public AuthRepository() {
        this.authApi = RetrofitClient.getAuthApi();
    }

    public void login(String email, String password, MutableLiveData<AuthResponse> successData, MutableLiveData<String> errorData) {
        authApi.login(new LoginRequest(email, password)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    successData.postValue(response.body());
                } else {
                    errorData.postValue("Đăng nhập thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                errorData.postValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }
}
