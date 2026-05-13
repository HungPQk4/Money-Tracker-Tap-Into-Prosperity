package vn.edu.usth.tip.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import vn.edu.usth.tip.network.requests.InvoiceAnalysisRequest;
import vn.edu.usth.tip.network.responses.InvoiceAnalysisResponse;

/**
 * Retrofit interface cho API phân tích hóa đơn bằng LLM.
 * Endpoint: POST /api/invoices/analyze
 */
public interface InvoiceApi {

    @POST("invoices/analyze")
    Call<InvoiceAnalysisResponse> analyzeInvoice(@Body InvoiceAnalysisRequest request);
}
