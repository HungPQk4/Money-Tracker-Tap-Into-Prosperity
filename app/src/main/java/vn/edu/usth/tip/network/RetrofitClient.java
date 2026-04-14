package vn.edu.usth.tip.network;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.usth.tip.utils.TokenManager;

public class RetrofitClient {
    private static final String BASE_URL = "http://10.0.2.2:8080/api/";
    private static Retrofit retrofit = null;

    public static <T> T createService(Class<T> serviceClass, TokenManager tokenManager) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        httpClient.addInterceptor(logging);

        // Interceptor để thêm Authorization Header và xử lý lỗi 401
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            Request.Builder builder = original.newBuilder();
            
            if (tokenManager != null && tokenManager.getToken() != null) {
                builder.header("Authorization", "Bearer " + tokenManager.getToken());
            }

            okhttp3.Response response = chain.proceed(builder.build());

            // Nếu gặp lỗi 401 (Hết hạn token hoặc không hợp lệ), xóa token
            if (response.code() == 401 && tokenManager != null) {
                tokenManager.clear(); 
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

    // Giữ lại hàm cũ cho Login (không cần token)
    public static AuthApi getAuthApi() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(logging).build();
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit.create(AuthApi.class);
    }
}
