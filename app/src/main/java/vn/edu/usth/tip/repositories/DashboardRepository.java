package vn.edu.usth.tip.repositories;

import android.content.Context;
import androidx.lifecycle.MutableLiveData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.network.DashboardApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.responses.DashboardSummary;
import vn.edu.usth.tip.network.responses.TransactionResponse;
import vn.edu.usth.tip.utils.TokenManager;

import java.util.List;

public class DashboardRepository {
    private final DashboardApi dashboardApi;

    public DashboardRepository(Context context) {
        TokenManager tokenManager = new TokenManager(context);
        this.dashboardApi = RetrofitClient.createService(DashboardApi.class, tokenManager);
    }

    public void fetchSummary(MutableLiveData<DashboardSummary> summaryData, MutableLiveData<String> errorData) {
        dashboardApi.getDashboardSummary().enqueue(new Callback<DashboardSummary>() {
            @Override
            public void onResponse(Call<DashboardSummary> call, Response<DashboardSummary> response) {
                if (response.isSuccessful() && response.body() != null) {
                    summaryData.postValue(response.body());
                } else {
                    errorData.postValue("Lấy dữ liệu tổng quan thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<DashboardSummary> call, Throwable t) {
                errorData.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }

    public void fetchRecentTransactions(String period, MutableLiveData<List<TransactionResponse>> transactionsData, MutableLiveData<String> errorData) {
        dashboardApi.getRecentTransactions(period).enqueue(new Callback<List<TransactionResponse>>() {
            @Override
            public void onResponse(Call<List<TransactionResponse>> call, Response<List<TransactionResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    transactionsData.postValue(response.body());
                } else {
                    errorData.postValue("Lấy danh sách giao dịch thất bại: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<TransactionResponse>> call, Throwable t) {
                errorData.postValue("Lỗi mạng: " + t.getMessage());
            }
        });
    }
}
