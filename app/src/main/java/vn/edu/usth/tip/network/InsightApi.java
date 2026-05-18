package vn.edu.usth.tip.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import vn.edu.usth.tip.network.dto.InsightRequest;
import vn.edu.usth.tip.network.dto.InsightResponse;

public interface InsightApi {

    @POST("ai/insights")
    Call<InsightResponse> getAiInsights(@Body InsightRequest request);
}
