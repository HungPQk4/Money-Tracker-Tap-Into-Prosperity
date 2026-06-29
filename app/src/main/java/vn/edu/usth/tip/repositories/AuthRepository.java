package vn.edu.usth.tip.repositories;

import androidx.lifecycle.MutableLiveData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.network.AuthApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.requests.LoginRequest;
import vn.edu.usth.tip.network.requests.RegisterRequest;
import vn.edu.usth.tip.network.responses.AuthResponse;

public class AuthRepository {
    private AuthApi authApi;

    public AuthRepository() {
        this.authApi = RetrofitClient.getAuthApi();
    }

    public void login(String email, String password, MutableLiveData<AuthResponse> successData, MutableLiveData<String> errorData) {
        LoginRequest request = new LoginRequest(email, password);
        request.setDeviceName(deviceName());
        authApi.login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    successData.postValue(response.body());
                } else {
                    errorData.postValue(parseError(response, "Đăng nhập thất bại (" + response.code() + ")"));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                errorData.postValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    public void register(String email, String password, String fullName, MutableLiveData<AuthResponse> successData, MutableLiveData<String> errorData) {
        RegisterRequest request = new RegisterRequest(email, password, fullName);
        request.setDeviceName(deviceName());
        authApi.register(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    successData.postValue(response.body());
                } else {
                    errorData.postValue(parseError(response, "Đăng ký thất bại (" + response.code() + ")"));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                errorData.postValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    /**
     * Trích message lỗi do backend trả về ({@code {"message":...}} hoặc {@code {"messages":[...]}})
     * để hiển thị câu thông báo có nghĩa (vd "Sai email hoặc mật khẩu!") thay vì chỉ mã HTTP.
     * Mọi lỗi parse/IO đều rơi về {@code fallback}.
     */
    private static String parseError(Response<?> response, String fallback) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                if (body != null && !body.isEmpty()) {
                    com.google.gson.JsonObject obj =
                            com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                    if (obj.has("message") && obj.get("message").isJsonPrimitive()) {
                        return obj.get("message").getAsString();
                    }
                    if (obj.has("messages") && obj.get("messages").isJsonArray()) {
                        com.google.gson.JsonArray arr = obj.getAsJsonArray("messages");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < arr.size(); i++) {
                            if (i > 0) sb.append('\n');
                            sb.append(arr.get(i).getAsString());
                        }
                        if (sb.length() > 0) return sb.toString();
                    }
                }
            }
        } catch (Exception ignored) {
            // body rỗng / không phải JSON / đọc lỗi → dùng fallback
        }
        return fallback;
    }

    /** Tên thiết bị hiển thị trong danh sách phiên (vd "samsung SM-G991B"). */
    private static String deviceName() {
        String manufacturer = android.os.Build.MANUFACTURER != null ? android.os.Build.MANUFACTURER : "";
        String model = android.os.Build.MODEL != null ? android.os.Build.MODEL : "";
        String name = (manufacturer + " " + model).trim();
        return name.isEmpty() ? "Thiết bị Android" : name;
    }
}
