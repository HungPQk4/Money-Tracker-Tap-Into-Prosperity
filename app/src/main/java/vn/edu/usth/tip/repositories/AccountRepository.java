package vn.edu.usth.tip.repositories;

import android.content.Context;
import androidx.lifecycle.MutableLiveData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.network.AccountApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.requests.AccountRequest;
import vn.edu.usth.tip.network.responses.AccountResponse;
import vn.edu.usth.tip.utils.TokenManager;

import java.util.List;

public class AccountRepository {
    private final AccountApi accountApi;
    private final MutableLiveData<Boolean> sessionExpired;

    public AccountRepository(Context context, MutableLiveData<Boolean> sessionExpired) {
        TokenManager tokenManager = new TokenManager(context);
        this.accountApi = RetrofitClient.createService(AccountApi.class, tokenManager);
        this.sessionExpired = sessionExpired;
    }

    public void fetchAllAccounts(MutableLiveData<List<AccountResponse>> accountsData, MutableLiveData<String> errorMessage) {
        accountApi.getAllAccounts().enqueue(new Callback<List<AccountResponse>>() {
            @Override
            public void onResponse(Call<List<AccountResponse>> call, Response<List<AccountResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    accountsData.postValue(response.body());
                } else if (response.code() == 401 || response.code() == 403) {
                    // ✅ Token hết hạn → báo hiệu cần logout
                    sessionExpired.postValue(true);
                } else {
                    errorMessage.postValue("Lỗi: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<AccountResponse>> call, Throwable t) {
                errorMessage.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }

    public void createAccount(AccountRequest request, MutableLiveData<AccountResponse> createdAccountData, MutableLiveData<String> errorMessage) {
        accountApi.createAccount(request).enqueue(new Callback<AccountResponse>() {
            @Override
            public void onResponse(Call<AccountResponse> call, Response<AccountResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    createdAccountData.postValue(response.body());
                } else if (response.code() == 401 || response.code() == 403) {
                    sessionExpired.postValue(true);
                } else {
                    errorMessage.postValue("Tạo ví thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<AccountResponse> call, Throwable t) {
                errorMessage.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }

    public void updateAccount(String id, AccountRequest request, MutableLiveData<AccountResponse> updatedAccountData, MutableLiveData<String> errorMessage) {
        accountApi.updateAccount(id, request).enqueue(new Callback<AccountResponse>() {
            @Override
            public void onResponse(Call<AccountResponse> call, Response<AccountResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    updatedAccountData.postValue(response.body());
                } else if (response.code() == 401 || response.code() == 403) {
                    sessionExpired.postValue(true);
                } else {
                    errorMessage.postValue("Sửa ví thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<AccountResponse> call, Throwable t) {
                errorMessage.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }

    public void deleteAccount(String id, MutableLiveData<Boolean> deleteSuccessData, MutableLiveData<String> errorMessage) {
        accountApi.deleteAccount(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    deleteSuccessData.postValue(true);
                } else if (response.code() == 401 || response.code() == 403) {
                    sessionExpired.postValue(true);
                } else {
                    errorMessage.postValue("Xóa ví thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                errorMessage.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }
}
