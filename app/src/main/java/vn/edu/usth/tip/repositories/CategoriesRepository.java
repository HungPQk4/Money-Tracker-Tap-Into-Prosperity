package vn.edu.usth.tip.repositories;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.NonNull;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.CategoryDao;
import vn.edu.usth.tip.network.FinancialApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.requests.FinancialRequests;
import vn.edu.usth.tip.network.responses.FinancialDtos.CategoryDto;
import vn.edu.usth.tip.utils.NetworkUtils;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class CategoriesRepository {
    private final AppDatabase db;
    private final Context appContext;
    private final CategoryDao categoryDao;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;

    public CategoriesRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AppDatabase.getDatabase(appContext);
        this.categoryDao = db.categoryDao();
        this.tokenManager = TokenManager.getOrCreate(appContext);
        this.financialApi = RetrofitClient.createService(FinancialApi.class, tokenManager);
    }

    public void addOnline(Category c) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        // Backend enum CategoryType uses lowercase: "income", "expense"
        String type = (c.getType() != null) ? c.getType().toLowerCase() : "expense";
        String colorHex = (c.getColorHex() != null) ? c.getColorHex() : "#6C5CE7";
        FinancialRequests.CreateCategoryRequest req = new FinancialRequests.CreateCategoryRequest(
            userId, c.getName(), type, c.getIcon(), colorHex
        );

        financialApi.createCategory(req).enqueue(new Callback<CategoryDto>() {
            @Override public void onResponse(Call<CategoryDto> call, Response<CategoryDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    android.util.Log.d("CAT_SYNC", "Category created on server: " + response.body().getId());
                    // Update local category ID with server UUID
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        try {
                            categoryDao.deleteById(c.getId());
                            c.setId(response.body().getId().toString());
                            categoryDao.insert(c);
                        } catch (Exception e) {
                            android.util.Log.e("CAT_SYNC", "Local ID update failed after server create: " + e.getMessage());
                        }
                    });
                } else {
                    String errBody = "";
                    try { if (response.errorBody() != null) errBody = response.errorBody().string(); } catch (Exception ignored) {}
                    android.util.Log.e("CAT_SYNC", "Add error: " + response.code() + " body=" + errBody);
                }
            }
            @Override public void onFailure(Call<CategoryDto> call, Throwable t) {
                android.util.Log.e("CAT_SYNC", "Add failed: " + t.getMessage());
            }
        });
    }

    public void updateOnline(Category c) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        // Backend enum CategoryType uses lowercase: "income", "expense"
        String type = (c.getType() != null) ? c.getType().toLowerCase() : "expense";
        String colorHex = (c.getColorHex() != null) ? c.getColorHex() : "#6C5CE7";
        FinancialRequests.CreateCategoryRequest req = new FinancialRequests.CreateCategoryRequest(
            userId, c.getName(), type, c.getIcon(), colorHex
        );

        try {
            UUID id = UUID.fromString(c.getId());
            financialApi.updateCategory(id, req).enqueue(new Callback<CategoryDto>() {
                @Override public void onResponse(Call<CategoryDto> call, Response<CategoryDto> response) {
                    if (response.isSuccessful()) {
                        android.util.Log.d("CAT_SYNC", "Update success!");
                    } else {
                        android.util.Log.e("CAT_SYNC", "Update error: " + response.code() + " " + response.message());
                    }
                }
                @Override public void onFailure(Call<CategoryDto> call, Throwable t) {
                    android.util.Log.e("CAT_SYNC", "Update failed: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            android.util.Log.e("CAT_SYNC", "Update UUID parse error: " + e.getMessage());
        }
    }

    public void deleteOnline(String categoryId, @androidx.annotation.Nullable DeleteCallback callback) {
        try {
            UUID id = UUID.fromString(categoryId);
            financialApi.deleteCategory(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        android.util.Log.d("CAT_SYNC", "Delete success!");
                        if (callback != null) callback.onSuccess();
                    } else {
                        String msg = response.code() + " " + response.message();
                        android.util.Log.e("CAT_SYNC", "Delete error: " + msg);
                        if (callback != null) callback.onError(msg);
                    }
                }
                @Override public void onFailure(Call<Void> call, Throwable t) {
                    android.util.Log.e("CAT_SYNC", "Delete failed: " + t.getMessage());
                    if (callback != null) callback.onError(t.getMessage());
                }
            });
        } catch (Exception e) {
            android.util.Log.e("CAT_SYNC", "Delete UUID parse error: " + e.getMessage());
            if (callback != null) callback.onError(e.getMessage());
        }
    }

    public interface DeleteCallback { void onSuccess(); void onError(String msg); }

    public void sync(SyncCallback callback) {
        if (!NetworkUtils.isConnected(appContext)) {
            callback.onError("Không có kết nối internet");
            return;
        }
        final String requestUserId = tokenManager.getUserId();
        if (requestUserId == null) { callback.onError("Not logged in"); return; }
        financialApi.getAllCategories().enqueue(new Callback<List<CategoryDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<CategoryDto>> call, @NonNull Response<List<CategoryDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        if (!requestUserId.equals(tokenManager.getUserId())) return;
                        List<Category> localCategories = categoryDao.getAllCategoriesSync();
                        List<CategoryDto> serverCategories = new java.util.ArrayList<>(response.body());

                        // 1. Đẩy các danh mục tạo offline lên server (blocking network calls —
                        //    intentionally outside DB transaction to avoid holding DB lock during IO)
                        for (Category local : localCategories) {
                            if (local.isAddButton()) continue;
                            boolean foundOnServer = false;
                            for (CategoryDto dto : serverCategories) {
                                String localType = local.getType() != null ? local.getType().trim() : "expense";
                                String dtoType = dto.getType() != null ? dto.getType().trim() : "expense";
                                if (local.getName().trim().equalsIgnoreCase(dto.getName().trim()) &&
                                    localType.equalsIgnoreCase(dtoType)) {
                                    foundOnServer = true;
                                    break;
                                }
                            }
                            if (!foundOnServer) {
                                try {
                                    if (requestUserId != null) {
                                        FinancialRequests.CreateCategoryRequest req =
                                            new FinancialRequests.CreateCategoryRequest(
                                                UUID.fromString(requestUserId),
                                                local.getName(),
                                                local.getType() != null ? local.getType() : "expense",
                                                local.getIcon(),
                                                local.getColorHex()
                                        );
                                        retrofit2.Response<CategoryDto> res = financialApi.createCategory(req).execute();
                                        if (res.isSuccessful() && res.body() != null) {
                                            serverCategories.add(res.body());
                                        } else {
                                            android.util.Log.e("CAT_SYNC", "Offline push error for '" + local.getName() + "': " + res.code());
                                        }
                                    }
                                } catch (Exception e) {
                                    android.util.Log.e("CAT_SYNC", "Offline push failed for '" + local.getName() + "': " + e.getMessage());
                                }
                            }
                        }

                        // 2. Kéo dữ liệu từ server về — dedup server trước, sau đó insertAll trong 1 transaction
                        // Server có thể trả về nhiều category cùng tên/type với UUID khác nhau (do push nhiều lần);
                        // chỉ giữ item đầu tiên của mỗi cặp name+type để tránh duplicate trong local DB.
                        java.util.LinkedHashMap<String, CategoryDto> dedupMap = new java.util.LinkedHashMap<>();
                        for (CategoryDto dto : serverCategories) {
                            String nameKey = dto.getName() != null ? dto.getName().trim().toLowerCase() : "";
                            String typeKey = dto.getType() != null ? dto.getType().trim().toLowerCase() : "expense";
                            dedupMap.putIfAbsent(nameKey + "|" + typeKey, dto);
                        }
                        final List<Category> toInsert = new java.util.ArrayList<>();
                        for (CategoryDto dto : dedupMap.values()) {
                            toInsert.add(convertToModel(dto));
                        }

                        db.runInTransaction(() -> {
                            for (Category serverCategory : toInsert) {
                                for (Category local : localCategories) {
                                    String localType = local.getType() != null ? local.getType().trim() : "expense";
                                    String serverType = serverCategory.getType() != null ? serverCategory.getType().trim() : "expense";
                                    if (local.getName().trim().equalsIgnoreCase(serverCategory.getName().trim())
                                            && localType.equalsIgnoreCase(serverType)
                                            && !local.getId().equals(serverCategory.getId())) {
                                        categoryDao.deleteById(local.getId());
                                    }
                                }
                            }
                            if (!toInsert.isEmpty()) {
                                categoryDao.insertAll(toInsert);
                            }
                        });

                        callback.onSuccess();
                    });
                } else callback.onError("Error: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<List<CategoryDto>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private static final java.util.Map<String, String> NAME_ICON_MAP;
    static {
        NAME_ICON_MAP = new java.util.HashMap<>();
        NAME_ICON_MAP.put("ăn uống", "🍜");
        NAME_ICON_MAP.put("di chuyển", "🛵");
        NAME_ICON_MAP.put("mua sắm", "🛒");
        NAME_ICON_MAP.put("giải trí", "🎬");
        NAME_ICON_MAP.put("sức khỏe", "💊");
        NAME_ICON_MAP.put("hóa đơn", "🧾");
        NAME_ICON_MAP.put("gia đình", "👨‍👩‍👧");
        NAME_ICON_MAP.put("lương", "💰");
        NAME_ICON_MAP.put("thưởng", "🎁");
        NAME_ICON_MAP.put("thu nhập khác", "💵");
    }

    private Category convertToModel(CategoryDto dto) {
        String icon = dto.getIcon();
        if (icon == null || icon.isEmpty()) {
            String key = dto.getName() != null ? dto.getName().trim().toLowerCase() : "";
            icon = NAME_ICON_MAP.getOrDefault(key, "📂");
        }
        Category c = new Category(
            dto.getId().toString(),
            dto.getName(),
            icon,
            dto.getColorHex() != null ? dto.getColorHex() : "#6C5CE7",
            dto.getType() != null ? dto.getType() : "expense",
            Boolean.TRUE.equals(dto.getIsSystem()),
            false
        );
        c.setUserId(dto.getUserId() != null ? dto.getUserId().toString() : null);
        return c;
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}
