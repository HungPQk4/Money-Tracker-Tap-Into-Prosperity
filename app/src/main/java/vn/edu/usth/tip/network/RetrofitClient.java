package vn.edu.usth.tip.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.usth.tip.BuildConfig;
import vn.edu.usth.tip.utils.SessionManager;
import vn.edu.usth.tip.utils.TokenManager;

public class RetrofitClient {
    private static final String BASE_URL = "https://aviation-skincare-undertone.ngrok-free.dev/api/";
    private static Retrofit retrofit = null;

    public static <T> T createService(Class<T> serviceClass, TokenManager tokenManager) {
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            httpClient.addInterceptor(logging);
        }

        // Interceptor để thêm Authorization Header và xử lý lỗi 401
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            Request.Builder builder = original.newBuilder();

            builder.header("ngrok-skip-browser-warning", "true");

            if (tokenManager != null && tokenManager.getToken() != null) {
                builder.header("Authorization", "Bearer " + tokenManager.getToken());
            }

            okhttp3.Response response = chain.proceed(builder.build());

            // Nếu gặp lỗi 401 (Hết hạn token hoặc không hợp lệ), xóa token
            if ((response.code() == 401 || response.code() == 403) && tokenManager != null) {
                tokenManager.clear();
                SessionManager.getInstance().triggerSessionExpired();
            }

            return response;
        });

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient.build())
                .build();

        return retrofit.create(serviceClass);
    }

    // Separate client for AI Insight API: 30s read timeout because Gemini/Claude inference takes 5–15s.
    // Re-using the default client (60s read) risks flaky failures when the LLM is slow.
    public static InsightApi createAiInsightService(TokenManager tokenManager) {
        OkHttpClient.Builder aiClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS);
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            aiClientBuilder.addInterceptor(logging);
        }
        OkHttpClient aiClient = aiClientBuilder
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder()
                            .header("ngrok-skip-browser-warning", "true");
                    if (tokenManager != null && tokenManager.getToken() != null) {
                        builder.header("Authorization", "Bearer " + tokenManager.getToken());
                    }
                    return chain.proceed(builder.build());
                })
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(aiClient)
                .build()
                .create(InsightApi.class);
    }

    // Giữ lại hàm cũ cho Login (không cần token)
    public static AuthApi getAuthApi() {
        if (retrofit == null) {
            OkHttpClient.Builder authClientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS);
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
                authClientBuilder.addInterceptor(logging);
            }
            OkHttpClient client = authClientBuilder
                    .addInterceptor(chain -> chain.proceed(
                            chain.request().newBuilder()
                                    .header("ngrok-skip-browser-warning", "true")
                                    .build()))
                    .build();
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit.create(AuthApi.class);
    }
}
