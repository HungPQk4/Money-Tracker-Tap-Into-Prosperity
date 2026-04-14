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
import vn.edu.usth.tip.models.Goal;
import vn.edu.usth.tip.models.GoalDao;
import vn.edu.usth.tip.network.FinancialApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.responses.FinancialDtos.GoalDto;
import vn.edu.usth.tip.network.requests.FinancialRequests;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class GoalsRepository {
    private final GoalDao goalDao;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public GoalsRepository(Context context) {
        this.goalDao = AppDatabase.getDatabase(context).goalDao();
        this.tokenManager = new TokenManager(context);
        this.financialApi = RetrofitClient.createService(FinancialApi.class, tokenManager);
    }

    public void addOnline(Goal g) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        FinancialRequests.CreateGoalRequest req = new FinancialRequests.CreateGoalRequest(
            userId, g.getName(), new java.math.BigDecimal(g.getTargetAmount()),
            new java.math.BigDecimal(g.getSavedAmount()), sdf.format(new java.util.Date(g.getTargetDateMs()))
        );

        financialApi.createGoal(req).enqueue(new Callback<GoalDto>() {
            @Override public void onResponse(Call<GoalDto> call, Response<GoalDto> response) {}
            @Override public void onFailure(Call<GoalDto> call, Throwable t) {}
        });
    }

    public void updateOnline(Goal g) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        FinancialRequests.CreateGoalRequest req = new FinancialRequests.CreateGoalRequest(
            userId, g.getName(), new java.math.BigDecimal(g.getTargetAmount()),
            new java.math.BigDecimal(g.getSavedAmount()), sdf.format(new java.util.Date(g.getTargetDateMs()))
        );

        try {
            UUID id = UUID.fromString(g.getId());
            financialApi.updateGoal(id, req).enqueue(new Callback<GoalDto>() {
                @Override public void onResponse(Call<GoalDto> call, Response<GoalDto> response) {}
                @Override public void onFailure(Call<GoalDto> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void deleteOnline(String goalId) {
        try {
            UUID id = UUID.fromString(goalId);
            financialApi.deleteGoal(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void sync(SyncCallback callback) {
        financialApi.getAllGoals().enqueue(new Callback<List<GoalDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<GoalDto>> call, @NonNull Response<List<GoalDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (GoalDto dto : response.body()) {
                            goalDao.insert(convertToModel(dto));
                        }
                        callback.onSuccess();
                    });
                } else callback.onError("Error: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<List<GoalDto>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private Goal convertToModel(GoalDto dto) {
        long targetDate = 0;
        try {
            if (dto.getTargetDate() != null) targetDate = sdf.parse(dto.getTargetDate()).getTime();
        } catch (Exception ignored) {}

        return new Goal(
            dto.getId().toString(),
            dto.getName(),
            "🎯", // Emoji mặc định
            dto.getTargetAmount().longValue(),
            dto.getCurrentAmount().longValue(),
            targetDate,
            "#6C5CE7" // Color mặc định
        );
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}
