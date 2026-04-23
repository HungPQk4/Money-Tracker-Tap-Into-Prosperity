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
                    (d.getType() == DebtLoan.TYPE_LENT) ? "LENT" : "BORROWED", 
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
                    (d.getType() == DebtLoan.TYPE_LENT) ? "LENT" : "BORROWED", 
                    sdf.format(new java.util.Date(d.getDueDate()))
                );
                req.setNote(d.getReason());

                UUID id = UUID.fromString(d.getId());
                financialApi.updateDebt(id, req).enqueue(new Callback<DebtDto>() {
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
        financialApi.getAllDebts().enqueue(new Callback<List<DebtDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<DebtDto>> call, @NonNull Response<List<DebtDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DebtDto dto : response.body()) {
                            debtLoanDao.insert(convertToModel(dto));
                        }
                        callback.onSuccess();
                    });
                } else callback.onError("Error: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<List<DebtDto>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private DebtLoan convertToModel(DebtDto dto) {
        long dueDate = 0;
        try {
            if (dto.getDueDate() != null) dueDate = sdf.parse(dto.getDueDate()).getTime();
        } catch (Exception ignored) {}

        int type = "LENT".equalsIgnoreCase(dto.getType()) ? DebtLoan.TYPE_LENT : DebtLoan.TYPE_I_OWE;

        return new DebtLoan(
            dto.getId().toString(),
            dto.getDebtorName(),
            dto.getNote(),
            dto.getAmount().longValue(),
            dueDate,
            type
        );
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}
