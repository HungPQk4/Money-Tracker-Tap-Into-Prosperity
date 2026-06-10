package vn.edu.usth.tip.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.usth.tip.BuildConfig;
import vn.edu.usth.tip.network.requests.RefreshRequest;
import vn.edu.usth.tip.network.responses.AuthResponse;
import vn.edu.usth.tip.utils.SessionManager;
import vn.edu.usth.tip.utils.TokenManager;

public class RetrofitClient {
    private static final String BASE_URL = "https://aviation-skincare-undertone.ngrok-free.dev/api/";
    private static Retrofit retrofit = null;

    // Shared dispatcher for all authenticated API calls.
    // Calling cancelAllRequests() on logout immediately aborts every in-flight request,
    // preventing zombie responses from writing stale data to the DB of the next user.
    private static final okhttp3.Dispatcher sharedDispatcher = new okhttp3.Dispatcher();

    // Serialize refresh attempts so a burst of concurrent 401s triggers at most one network refresh.
    private static final Object refreshLock = new Object();

    /** Cancel all in-flight authenticated requests — call on logout / session expiry. */
    public static void cancelAllRequests() {
        sharedDispatcher.cancelAll();
    }

    public static <T> T createService(Class<T> serviceClass, TokenManager tokenManager) {
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .dispatcher(sharedDispatcher)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .authenticator(tokenAuthenticator(tokenManager));
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

            // 401/403 = token hết hạn/không hợp lệ. Authenticator (ở trên) đã có cơ hội tự refresh;
            // nếu tới đây vẫn còn 401/403 nghĩa là refresh thất bại (hoặc phiên bị thu hồi từ xa)
            // → buộc đăng xuất. 403 giữ lại vì backend không phân quyền role (mọi 403 đều là lỗi auth).
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
                .dispatcher(sharedDispatcher)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .authenticator(tokenAuthenticator(tokenManager));
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
                    okhttp3.Response response = chain.proceed(builder.build());
                    // Xử lý lỗi auth nhất quán với createService() — 401/403 → buộc đăng xuất.
                    if ((response.code() == 401 || response.code() == 403) && tokenManager != null) {
                        tokenManager.clear();
                        SessionManager.getInstance().triggerSessionExpired();
                    }
                    return response;
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

    /**
     * Đăng xuất phía server cho phiên hiện tại (best-effort). Dùng client RIÊNG — không nằm trên
     * sharedDispatcher — nên không bị cancelAllRequests() huỷ khi logout. Bỏ qua mọi lỗi mạng.
     */
    public static void logoutCurrentSession(TokenManager tokenManager) {
        if (tokenManager == null || tokenManager.getToken() == null) return;
        final String token = tokenManager.getToken();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("ngrok-skip-browser-warning", "true")
                        .header("Authorization", "Bearer " + token)
                        .build()))
                .build();
        AuthApi api = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(AuthApi.class);
        api.logout().enqueue(new retrofit2.Callback<Void>() {
            @Override public void onResponse(retrofit2.Call<Void> c, retrofit2.Response<Void> r) {}
            @Override public void onFailure(retrofit2.Call<Void> c, Throwable t) {}
        });
    }

    // ─── Tự động refresh access token khi gặp 401 ──────────────────────────────
    // OkHttp gọi Authenticator khi nhận 401. Ta dùng refresh token để lấy access token mới
    // (qua client KHÔNG xác thực để tránh đệ quy), rồi thử lại request với token mới.
    private static okhttp3.Authenticator tokenAuthenticator(TokenManager tokenManager) {
        return (route, response) -> {
            if (tokenManager == null) return null;
            if (responseCount(response) >= 2) return null; // đã thử refresh 1 lần rồi → ngừng (tránh lặp)
            String refresh = tokenManager.getRefreshToken();
            if (refresh == null) return null;

            synchronized (refreshLock) {
                String current = tokenManager.getToken();
                String tried   = stripBearer(response.request().header("Authorization"));
                // Một thread khác đã refresh xong (token đã đổi) → chỉ cần thử lại với token mới.
                if (current != null && !current.equals(tried)) {
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + current)
                            .build();
                }
                try {
                    retrofit2.Response<AuthResponse> r =
                            getAuthApi().refresh(new RefreshRequest(refresh)).execute();
                    if (r.isSuccessful() && r.body() != null && r.body().getToken() != null) {
                        tokenManager.updateTokens(r.body().getToken(), r.body().getRefreshToken());
                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + r.body().getToken())
                                .build();
                    }
                } catch (Exception ignored) {
                    // mạng lỗi / refresh hỏng → trả null để 401 lan ra (interceptor sẽ đăng xuất)
                }
                return null;
            }
        };
    }

    private static int responseCount(okhttp3.Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) count++;
        return count;
    }

    private static String stripBearer(String header) {
        if (header == null) return null;
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
