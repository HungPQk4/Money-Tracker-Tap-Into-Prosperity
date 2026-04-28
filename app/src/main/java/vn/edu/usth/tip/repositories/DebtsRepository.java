package vn.edu.usth.tip.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.DebtLoan;
import vn.edu.usth.tip.models.DebtLoanDao;
import vn.edu.usth.tip.network.FinancialApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.responses.FinancialDtos.DebtDto;
import vn.edu.usth.tip.network.requests.FinancialRequests;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class DebtsRepository {
    private final DebtLoanDao debtLoanDao;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public DebtsRepository(Context context) {
        this.debtLoanDao = AppDatabase.getDatabase(context).debtLoanDao();
        this.tokenManager = new TokenManager(context);
        this.financialApi = RetrofitClient.createService(FinancialApi.class, tokenManager);
    }

    public void addOnline(DebtLoan d) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);

                FinancialRequests.CreateDebtRequest req = new FinancialRequests.CreateDebtRequest(
                    userId, d.getPersonName(), new java.math.BigDecimal(d.getAmount()),
                    (d.getType() == DebtLoan.TYPE_LENT) ? "lend" : "borrow", 
                    sdf.format(new java.util.Date(d.getDueDate()))
                );
                req.setNote(d.getReason());

                financialApi.createDebt(req).enqueue(new Callback<DebtDto>() {
                    @Override 
                    public void onResponse(Call<DebtDto> call, Response<DebtDto> response) {
                        if (response.isSuccessful()) {
                            AppDatabase.databaseWriteExecutor.execute(() -> {
                                d.setSynced(true);
                                debtLoanDao.update(d);
                            });
                        }
                    }
                    @Override public void onFailure(Call<DebtDto> call, Throwable t) {}
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void updateOnline(DebtLoan d) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);

                FinancialRequests.CreateDebtRequest req = new FinancialRequests.CreateDebtRequest(
                    userId, d.getPersonName(), new java.math.BigDecimal(d.getAmount()),
                    (d.getType() == DebtLoan.TYPE_LENT) ? "lend" : "borrow", 
                    sdf.format(new java.util.Date(d.getDueDate()))
                );
                req.setNote(d.getReason());

                try {
                    UUID id = UUID.fromString(d.getId());
                    financialApi.updateDebt(id, req).enqueue(new Callback<DebtDto>() {
                        @Override public void onResponse(Call<DebtDto> call, Response<DebtDto> response) {
                            if (!response.isSuccessful()) {
                                android.util.Log.e("DEBT_SYNC", "Update error: " + response.code() + " " + response.message());
                                if (response.code() == 404) {
                                    android.util.Log.d("DEBT_SYNC", "Fallback to addOnline...");
                                    addOnline(d);
                                }
                            } else {
                                android.util.Log.d("DEBT_SYNC", "Update success!");
                            }
                        }
                        @Override public void onFailure(Call<DebtDto> call, Throwable t) {
                            android.util.Log.e("DEBT_SYNC", "Update failure: " + t.getMessage());
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("DEBT_SYNC", "Update UUID parse error: " + e.getMessage());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void deleteOnline(String debtId) {
        try {
            UUID id = UUID.fromString(debtId);
            financialApi.deleteDebt(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void sync(SyncCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 1. Đẩy các khoản vay/nợ chưa đồng bộ lên server trước
                List<DebtLoan> unsynced = debtLoanDao.getUnsyncedDebtsSync();
                String userIdStr = tokenManager.getUserId();
                if (userIdStr != null && unsynced != null && !unsynced.isEmpty()) {
                    UUID userId = UUID.fromString(userIdStr);
                    for (DebtLoan d : unsynced) {
                        FinancialRequests.CreateDebtRequest req = new FinancialRequests.CreateDebtRequest(
                            userId, d.getPersonName(), new java.math.BigDecimal(d.getAmount()),
                            (d.getType() == DebtLoan.TYPE_LENT) ? "lend" : "borrow", 
                            sdf.format(new java.util.Date(d.getDueDate()))
                        );
                        req.setNote(d.getReason());
                        
                        try {
                            Response<DebtDto> res = financialApi.createDebt(req).execute();
                            if (res.isSuccessful() && res.body() != null) {
                                // Xóa bản ghi cũ (ID sinh ở máy) để tránh bị nhân đôi dữ liệu
                                debtLoanDao.delete(d);
                                // Thay bằng bản ghi mới (ID chuẩn từ PostgreSQL server)
                                debtLoanDao.insert(convertToModel(res.body()));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 2. Kéo tất cả dữ liệu từ server về Room
            financialApi.getAllDebts().enqueue(new Callback<List<DebtDto>>() {
                @Override
                public void onResponse(@NonNull Call<List<DebtDto>> call, @NonNull Response<List<DebtDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        // Xóa sạch dữ liệu đã sync cũ (tránh rác nếu có khoản nợ bị xóa ở thiết bị khác)
                        debtLoanDao.deleteSyncedDebts();
                        for (DebtDto dto : response.body()) {
                            debtLoanDao.insert(convertToModel(dto));
                        }
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onSuccess);
                    });
                } else {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError("Error: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<DebtDto>> call, @NonNull Throwable t) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(t.getMessage()));
            }
            });
        });
    }

    private DebtLoan convertToModel(DebtDto dto) {
        long dueDate = 0;
        try {
            if (dto.getDueDate() != null) dueDate = sdf.parse(dto.getDueDate()).getTime();
        } catch (Exception ignored) {}

        int type = "lend".equalsIgnoreCase(dto.getType()) ? DebtLoan.TYPE_LENT : DebtLoan.TYPE_I_OWE;

        DebtLoan model = new DebtLoan(
            dto.getId().toString(),
            dto.getContactName(),
            dto.getNote(),
            dto.getAmount().longValue(),
            dueDate,
            type
        );
        model.setSynced(true);
        return model;
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}
