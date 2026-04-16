package vn.edu.usth.tip.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import vn.edu.usth.tip.network.responses.DashboardSummary;
import vn.edu.usth.tip.network.responses.TransactionResponse;

import java.util.List;

public interface DashboardApi {
    @GET("dashboard/summary")
    Call<DashboardSummary> getDashboardSummary();

    @GET("dashboard/recent")
    Call<List<TransactionResponse>> getRecentTransactions(@Query("period") String period);
}
