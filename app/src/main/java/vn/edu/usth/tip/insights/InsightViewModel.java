package vn.edu.usth.tip.insights;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.insights.engine.InsightEngine;
import vn.edu.usth.tip.insights.models.ForecastPoint;
import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightType;
import vn.edu.usth.tip.network.InsightApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.dto.InsightRequest;
import vn.edu.usth.tip.network.dto.InsightResponse;
import vn.edu.usth.tip.utils.TokenManager;

public class InsightViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Insight>> insightsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ForecastPoint>> forecastLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>(false);

    // newSingleThreadExecutor: tránh nhiều tính toán song song, tiết kiệm RAM
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Guard Race Condition: người dùng bấm nút nhiều lần / xoay màn hình
    private final AtomicBoolean isCalculating = new AtomicBoolean(false);

    private final InsightEngine engine;
    private final InsightApi insightApi;

    public InsightViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        engine = new InsightEngine(db);
        TokenManager tokenManager = TokenManager.getOrCreate(application);
        insightApi = RetrofitClient.createAiInsightService(tokenManager);
    }

    public LiveData<List<Insight>> getInsights() { return insightsLiveData; }
    public LiveData<List<ForecastPoint>> getForecastPoints() { return forecastLiveData; }
    public LiveData<Boolean> getIsLoading() { return isLoadingLiveData; }

    public void generateInsights() {
        // compareAndSet(false, true) là atomic — an toàn đa luồng
        if (!isCalculating.compareAndSet(false, true)) return;

        isLoadingLiveData.postValue(true);

        executor.execute(() -> {
            try {
                // Bước 1: Tính toán nặng on-device (offline-first)
                String userId = TokenManager.getOrCreate(getApplication()).getUserId();
                InsightEngine.AnalysisResult result = engine.analyzeAll(userId);

                // Bước 2: Push ngay bản template offline lên UI — PHẢI dùng postValue()
                insightsLiveData.postValue(result.insights);
                forecastLiveData.postValue(result.forecastPoints);
                isLoadingLiveData.postValue(false);

                // Bước 3: Gọi API đồng bộ (.execute()) vì đã ở background thread
                InsightRequest request = buildRequest(result.insights);
                Response<InsightResponse> resp = insightApi.getAiInsights(request).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().insights != null) {
                    // Deep Copy bắt buộc — nếu mutate in-place, DiffUtil không phát hiện thay đổi
                    List<Insight> enriched = mergeWithApi(result.insights, resp.body());
                    insightsLiveData.postValue(enriched);
                }
            } catch (Exception e) {
                // Mất mạng / timeout / lỗi API → giữ nguyên bản template đã post ở bước 2
                isLoadingLiveData.postValue(false);
            } finally {
                isCalculating.set(false);
            }
        });
    }

    private InsightRequest buildRequest(List<Insight> insights) {
        InsightRequest req = new InsightRequest();
        req.budgetAlerts = new ArrayList<>();
        req.anomalies = new ArrayList<>();
        req.goalInsights = new ArrayList<>();
        req.patterns = new ArrayList<>();

        for (Insight insight : insights) {
            if (insight.type == InsightType.BUDGET_WARNING) {
                InsightRequest.BudgetAlert alert = new InsightRequest.BudgetAlert();
                alert.category    = insight.categoryName;
                alert.burnRate    = insight.burnRate;
                alert.forecastVnd = insight.projectedAmountVnd;
                alert.limitVnd    = insight.limitAmountVnd;
                alert.daysLeft    = insight.daysLeft;
                req.budgetAlerts.add(alert);

            } else if (insight.type == InsightType.ANOMALY) {
                InsightRequest.AnomalyData anomaly = new InsightRequest.AnomalyData();
                anomaly.category     = insight.categoryName;
                anomaly.zScore       = insight.zScore;
                anomaly.thisMonthVnd = insight.projectedAmountVnd;
                anomaly.avgVnd       = insight.historicalAvgVnd;
                req.anomalies.add(anomaly);

            } else if (insight.type == InsightType.GOAL_WARNING
                    || insight.type == InsightType.GOAL_FORECAST
                    || insight.type == InsightType.GOAL_OVERDUE) {
                InsightRequest.GoalData goal = new InsightRequest.GoalData();
                goal.name           = insight.categoryName;
                goal.remainingVnd   = insight.remainingVnd;
                goal.velocityPerWeek = insight.velocityPerWeekVnd;
                goal.targetDateMs   = insight.goalTargetDateMs;
                goal.projectedDateMs = insight.goalProjectedDateMs;
                req.goalInsights.add(goal);

            } else if (insight.type == InsightType.PATTERN) {
                InsightRequest.PatternData pattern = new InsightRequest.PatternData();
                pattern.topDayLabel  = insight.topDayLabel;
                pattern.ratio        = insight.burnRate;
                pattern.avgTopDayVnd = insight.avgTopDayVnd;
                req.patterns.add(pattern);
            }
        }
        return req;
    }

    // Deep Copy: tạo object mới hoàn toàn để DiffUtil phát hiện thay đổi và re-render UI
    private List<Insight> mergeWithApi(List<Insight> raw, InsightResponse apiResp) {
        List<Insight> enriched = new ArrayList<>();
        for (Insight original : raw) {
            Insight copy = new Insight(original); // copy constructor
            // Tìm API insight tương ứng theo type và referenceName
            for (InsightResponse.AiInsight ai : apiResp.insights) {
                boolean sameType = original.type != null
                        && original.type.name().equals(ai.type);
                boolean sameName = original.categoryName != null
                        && original.categoryName.equals(ai.referenceName);
                if (sameType && sameName) {
                    if (ai.title != null && !ai.title.isEmpty()) copy.title = ai.title;
                    if (ai.body != null && !ai.body.isEmpty()) copy.body = ai.body;
                    break;
                }
            }
            enriched.add(copy);
        }
        return enriched;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Tránh Memory Leak: hủy background thread khi ViewModel bị destroy
        if (!executor.isShutdown()) executor.shutdownNow();
    }
}
