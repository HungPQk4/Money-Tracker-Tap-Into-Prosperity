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

    public void fetchAllAccounts(MutableLiveData<List<AccountResponse>> accountsData, MutableLiveData<String> errorMessage) {
        accountApi.getAllAccounts().enqueue(new Callback<List<AccountResponse>>() {
            @Override
            public void onResponse(Call<List<AccountResponse>> call, Response<List<AccountResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    accountsData.postValue(response.body());
                } else {
                    errorMessage.postValue("Lấy danh sách ví thất bại: " + response.code());
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
}
