package vn.edu.usth.tip.repositories;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.NonNull;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.models.WalletDao;
import vn.edu.usth.tip.network.FinancialApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.responses.FinancialDtos.AccountDto;
import vn.edu.usth.tip.network.requests.FinancialRequests;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class WalletsRepository {
    private final WalletDao walletDao;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;

    public WalletsRepository(Context context) {
        this.walletDao = AppDatabase.getDatabase(context).walletDao();
        this.tokenManager = new TokenManager(context);
        this.financialApi = RetrofitClient.createService(FinancialApi.class, tokenManager);
    }

    private String mapTypeToNeon(Wallet.Type type) {
        if (type == null) return "cash";
        switch (type) {
            case CASH:       return "cash";
            case BANK:       return "bank";
            case EWALLET:    return "e_wallet";
            case INVESTMENT: return "investment";
            default:         return "cash";
        }
    }

    public void addOnline(Wallet w) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        FinancialRequests.CreateAccountRequest req = new FinancialRequests.CreateAccountRequest(
                userId, w.getName(), mapTypeToNeon(w.getType()), new java.math.BigDecimal(w.getBalanceVnd())
        );

        financialApi.createAccount(req).enqueue(new Callback<AccountDto>() {
            @Override public void onResponse(Call<AccountDto> call, Response<AccountDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    android.util.Log.d("WALLET_SYNC", "Wallet created: " + response.body().getId());
                    // Update local wallet ID with server UUID
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        try {
                            walletDao.delete(w);
                            w.setId(response.body().getId().toString());
                            walletDao.insert(w);
                        } catch (Exception ignored) {}
                    });
                } else {
                    String errBody = "";
                    try { if (response.errorBody() != null) errBody = response.errorBody().string(); } catch (Exception ignored) {}
                    android.util.Log.e("WALLET_SYNC", "Add error: " + response.code() + " body=" + errBody);
                }
            }
            @Override public void onFailure(Call<AccountDto> call, Throwable t) {
                android.util.Log.e("WALLET_SYNC", "Add failed: " + t.getMessage());
            }
        });
    }

    public void updateOnline(Wallet w) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        FinancialRequests.CreateAccountRequest req = new FinancialRequests.CreateAccountRequest(
                userId, w.getName(), mapTypeToNeon(w.getType()), new java.math.BigDecimal(w.getBalanceVnd())
        );

        try {
            UUID id = UUID.fromString(w.getId());
            financialApi.updateAccount(id, req).enqueue(new Callback<AccountDto>() {
                @Override public void onResponse(Call<AccountDto> call, Response<AccountDto> response) {}
                @Override public void onFailure(Call<AccountDto> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void deleteOnline(String walletId) {
        try {
            UUID id = UUID.fromString(walletId);
            financialApi.deleteAccount(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void sync(SyncCallback callback) {
        financialApi.getAllAccounts().enqueue(new Callback<List<AccountDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<AccountDto>> call, @NonNull Response<List<AccountDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (AccountDto dto : response.body()) {
                            Wallet incoming = convertToModel(dto);
                            // Xóa record cũ cùng tên nhưng ID giả (không phải UUID)
                            Wallet existing = walletDao.findByNameSync(dto.getName());
                            if (existing != null && !existing.getId().equals(incoming.getId())) {
                                walletDao.delete(existing);
                            }
                            walletDao.insert(incoming);
                        }
                        callback.onSuccess();
                    });
                } else callback.onError("Error: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<List<AccountDto>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private Wallet convertToModel(AccountDto dto) {
        Wallet.Type type = Wallet.Type.OTHER;
        if (dto.getType() != null) {
            String typeStr = dto.getType().toUpperCase();
            if (typeStr.equals("E_WALLET")) typeStr = "EWALLET";
            try { type = Wallet.Type.valueOf(typeStr); } catch (Exception ignored) {}
        }

        return new Wallet(
                dto.getId().toString(),
                dto.getName(),
                dto.getBalance().longValue(),
                "💳", // Default icon
                Color.BLUE,
                type,
                true
        );
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}