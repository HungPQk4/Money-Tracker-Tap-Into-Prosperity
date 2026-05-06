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
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class CategoriesRepository {
    private final CategoryDao categoryDao;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;

    public CategoriesRepository(Context context) {
        this.categoryDao = AppDatabase.getDatabase(context).categoryDao();
        this.tokenManager = new TokenManager(context);
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
                        } catch (Exception ignored) {}
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
                @Override public void onResponse(Call<CategoryDto> call, Response<CategoryDto> response) {}
                @Override public void onFailure(Call<CategoryDto> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void deleteOnline(String categoryId) {
        try {
            UUID id = UUID.fromString(categoryId);
            financialApi.deleteCategory(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void sync(SyncCallback callback) {
        financialApi.getAllCategories().enqueue(new Callback<List<CategoryDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<CategoryDto>> call, @NonNull Response<List<CategoryDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        List<Category> localCategories = categoryDao.getAllCategoriesSync();
                        List<CategoryDto> serverCategories = new java.util.ArrayList<>(response.body());

                        // 1. Đẩy các danh mục tạo offline lên server
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
                                    String userId = tokenManager.getUserId();
                                    if (userId != null) {
                                        vn.edu.usth.tip.network.requests.FinancialRequests.CreateCategoryRequest req = 
                                            new vn.edu.usth.tip.network.requests.FinancialRequests.CreateCategoryRequest(
                                                UUID.fromString(userId),
                                                local.getName(),
                                                local.getType() != null ? local.getType() : "expense",
                                                local.getIcon(),
                                                local.getColorHex()
                                        );
                                        retrofit2.Response<CategoryDto> res = financialApi.createCategory(req).execute();
                                        if (res.isSuccessful() && res.body() != null) {
                                            serverCategories.add(res.body());
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }

                        // 2. Kéo dữ liệu từ server về và dọn dẹp duplicate
                        List<Category> toInsert = new java.util.ArrayList<>();
                        for (CategoryDto dto : serverCategories) {
                            Category serverCategory = convertToModel(dto);

                            // Xóa các category ở local có cùng tên (thường là do fake UUID sinh ra lúc offline)
                            for (Category local : localCategories) {
                                String localType = local.getType() != null ? local.getType().trim() : "expense";
                                String serverType = serverCategory.getType() != null ? serverCategory.getType().trim() : "expense";
                                if (local.getName().trim().equalsIgnoreCase(serverCategory.getName().trim()) &&
                                    localType.equalsIgnoreCase(serverType)) {
                                    if (!local.getId().equals(serverCategory.getId())) {
                                        categoryDao.deleteById(local.getId());
                                    }
                                }
                            }

                            toInsert.add(serverCategory);
                        }
                        
                        if (!toInsert.isEmpty()) {
                            categoryDao.insertAll(toInsert);
                        }
                        
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

    private Category convertToModel(CategoryDto dto) {
        return new Category(
            dto.getId().toString(),
            dto.getName(),
            dto.getIcon() != null ? dto.getIcon() : "📂",
            dto.getColorHex() != null ? dto.getColorHex() : "#6C5CE7",
            dto.getType() != null ? dto.getType() : "expense",
            true,  // is_system = true (tất cả dữ liệu từ server đều là system)
            false  // is_add_button = false
        );
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}
