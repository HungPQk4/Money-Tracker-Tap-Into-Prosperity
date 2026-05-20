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

    public AccountRepository(Context context) {
        TokenManager tokenManager = new TokenManager(context);
        this.accountApi = RetrofitClient.createService(AccountApi.class, tokenManager);
    }

    public void fetchAllAccounts(MutableLiveData<List<AccountResponse>> accountsData, MutableLiveData<String> errorMessage, MutableLiveData<Boolean> isLoading) {
        accountApi.getAllAccounts().enqueue(new Callback<List<AccountResponse>>() {
            @Override
            public void onResponse(Call<List<AccountResponse>> call, Response<List<AccountResponse>> response) {
                if (isLoading != null) isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    accountsData.postValue(response.body());
                } else if (response.code() != 401 && response.code() != 403) {
                    errorMessage.postValue("Lỗi: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<AccountResponse>> call, Throwable t) {
                if (isLoading != null) isLoading.postValue(false);
                errorMessage.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }

    public void createAccount(AccountRequest request, MutableLiveData<AccountResponse> createdAccountData, MutableLiveData<String> errorMessage, MutableLiveData<Boolean> isLoading) {
        accountApi.createAccount(request).enqueue(new Callback<AccountResponse>() {
            @Override
            public void onResponse(Call<AccountResponse> call, Response<AccountResponse> response) {
                if (isLoading != null) isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    createdAccountData.postValue(response.body());
                } else if (response.code() != 401 && response.code() != 403) {
                    errorMessage.postValue("Tạo ví thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<AccountResponse> call, Throwable t) {
                if (isLoading != null) isLoading.postValue(false);
                errorMessage.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }

    public void updateAccount(String id, AccountRequest request, MutableLiveData<AccountResponse> updatedAccountData, MutableLiveData<String> errorMessage, MutableLiveData<Boolean> isLoading) {
        accountApi.updateAccount(id, request).enqueue(new Callback<AccountResponse>() {
            @Override
            public void onResponse(Call<AccountResponse> call, Response<AccountResponse> response) {
                if (isLoading != null) isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    updatedAccountData.postValue(response.body());
                } else if (response.code() != 401 && response.code() != 403) {
                    errorMessage.postValue("Sửa ví thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<AccountResponse> call, Throwable t) {
                if (isLoading != null) isLoading.postValue(false);
                errorMessage.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }

    public void deleteAccount(String id, MutableLiveData<Boolean> deleteSuccessData, MutableLiveData<String> errorMessage, MutableLiveData<Boolean> isLoading) {
        accountApi.deleteAccount(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (isLoading != null) isLoading.postValue(false);
                if (response.isSuccessful()) {
                    deleteSuccessData.postValue(true);
                } else if (response.code() != 401 && response.code() != 403) {
                    errorMessage.postValue("Xóa ví thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (isLoading != null) isLoading.postValue(false);
                errorMessage.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }
}
