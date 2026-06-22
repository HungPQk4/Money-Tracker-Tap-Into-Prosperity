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
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.models.WalletDao;

import java.util.ArrayList;
import java.util.List;

public class AccountRepository {
    private final AccountApi accountApi;
    private final WalletDao walletDao;
    private final TokenManager tokenManager;

    public AccountRepository(Context context) {
        this.tokenManager = TokenManager.getOrCreate(context);
        this.accountApi = RetrofitClient.createService(AccountApi.class, tokenManager);
        this.walletDao = AppDatabase.getDatabase(context).walletDao();
    }

    /**
     * Offline-first: seed the wallet list from the local Room cache so the New Transaction form
     * is usable without a network connection, then refresh from the server when it is reachable.
     * A network failure is surfaced as an error only when there is no local cache to fall back on.
     */
    public void fetchAllAccounts(MutableLiveData<List<AccountResponse>> accountsData, MutableLiveData<String> errorMessage, MutableLiveData<Boolean> isLoading) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1) Seed immediately from the local cache (works offline).
            List<AccountResponse> local = loadLocalAccounts();
            if (!local.isEmpty()) {
                accountsData.postValue(local);
            }
            // 2) Refresh from the server when a connection is available.
            try {
                Response<List<AccountResponse>> response = accountApi.getAllAccounts().execute();
                if (isLoading != null) isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    accountsData.postValue(response.body());
                } else if (response.code() != 401 && response.code() != 403 && local.isEmpty()) {
                    errorMessage.postValue("Lỗi: " + response.code());
                }
            } catch (Exception e) {
                if (isLoading != null) isLoading.postValue(false);
                // Offline: only report an error when the local cache was empty.
                if (local.isEmpty()) {
                    errorMessage.postValue("Lỗi mạng: " + e.getMessage());
                }
            }
        });
    }

    /** Maps the locally cached wallets (Room) to the AccountResponse shape used by the UI. */
    private List<AccountResponse> loadLocalAccounts() {
        List<AccountResponse> result = new ArrayList<>();
        String userId = tokenManager.getUserId();
        if (userId == null) return result;
        List<Wallet> wallets = walletDao.getAllWalletsSync(userId);
        if (wallets == null) return result;
        for (Wallet w : wallets) {
            AccountResponse r = new AccountResponse();
            r.setId(w.getId());
            r.setName(w.getName());
            r.setBalance(w.getBalanceVnd());
            r.setIcon(w.getIcon());
            r.setType(w.getType() != null ? w.getType().name() : null);
            r.setIncludeInTotal(w.isIncludedInTotal());
            result.add(r);
        }
        return result;
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
