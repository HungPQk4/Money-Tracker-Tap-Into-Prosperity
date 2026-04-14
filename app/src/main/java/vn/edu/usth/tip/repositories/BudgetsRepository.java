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
import vn.edu.usth.tip.models.Budget;
import vn.edu.usth.tip.models.BudgetDao;
import vn.edu.usth.tip.network.FinancialApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.responses.FinancialDtos.BudgetDto;
import vn.edu.usth.tip.network.requests.FinancialRequests;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class BudgetsRepository {
    private final BudgetDao budgetDao;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public BudgetsRepository(Context context) {
        this.budgetDao = AppDatabase.getDatabase(context).budgetDao();
        this.tokenManager = new TokenManager(context);
        this.financialApi = RetrofitClient.createService(FinancialApi.class, tokenManager);
    }

    public void addOnline(Budget b) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        // Simulating categoryId from name for demo
        UUID categoryId = UUID.nameUUIDFromBytes(b.getCategoryName().getBytes());
        
        FinancialRequests.CreateBudgetRequest req = new FinancialRequests.CreateBudgetRequest(
            userId, categoryId, new java.math.BigDecimal(b.getLimitAmount()),
            "MONTHLY", sdf.format(new java.util.Date(b.getPeriodStartMs())), 
            sdf.format(new java.util.Date(b.getPeriodEndMs()))
        );

        financialApi.createBudget(req).enqueue(new Callback<BudgetDto>() {
            @Override public void onResponse(Call<BudgetDto> call, Response<BudgetDto> response) {}
            @Override public void onFailure(Call<BudgetDto> call, Throwable t) {}
        });
    }

    public void updateOnline(Budget b) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        UUID categoryId = UUID.nameUUIDFromBytes(b.getCategoryName().getBytes());
        
        FinancialRequests.CreateBudgetRequest req = new FinancialRequests.CreateBudgetRequest(
            userId, categoryId, new java.math.BigDecimal(b.getLimitAmount()),
            "MONTHLY", sdf.format(new java.util.Date(b.getPeriodStartMs())), 
            sdf.format(new java.util.Date(b.getPeriodEndMs()))
        );

        try {
            UUID id = UUID.fromString(b.getId());
            financialApi.updateBudget(id, req).enqueue(new Callback<BudgetDto>() {
                @Override public void onResponse(Call<BudgetDto> call, Response<BudgetDto> response) {}
                @Override public void onFailure(Call<BudgetDto> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void deleteOnline(String budgetId) {
        try {
            UUID id = UUID.fromString(budgetId);
            financialApi.deleteBudget(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void sync(SyncCallback callback) {
        financialApi.getAllBudgets().enqueue(new Callback<List<BudgetDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<BudgetDto>> call, @NonNull Response<List<BudgetDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (BudgetDto dto : response.body()) {
                            budgetDao.insert(convertToModel(dto));
                        }
                        callback.onSuccess();
                    });
                } else callback.onError("Error: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<List<BudgetDto>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private Budget convertToModel(BudgetDto dto) {
        long start = 0, end = 0;
        try {
            if (dto.getPeriodStart() != null) start = sdf.parse(dto.getPeriodStart()).getTime();
            if (dto.getPeriodEnd() != null) end = sdf.parse(dto.getPeriodEnd()).getTime();
        } catch (Exception ignored) {}

        return new Budget(
            dto.getId().toString(),
            "Ngân sách mới", // Tên mặc định
            "💰", // Emoji mặc định
            "#6C5CE7", // Màu mặc định
            "General",
            dto.getAmount().longValue(),
            start,
            end,
            System.currentTimeMillis()
        );
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}
