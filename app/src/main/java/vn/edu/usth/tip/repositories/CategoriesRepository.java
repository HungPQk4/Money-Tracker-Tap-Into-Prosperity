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
        FinancialRequests.CreateCategoryRequest req = new FinancialRequests.CreateCategoryRequest(
            userId, c.getName(), "EXPENSE", c.getIcon(), "#6C5CE7"
        );

        financialApi.createCategory(req).enqueue(new Callback<CategoryDto>() {
            @Override public void onResponse(Call<CategoryDto> call, Response<CategoryDto> response) {}
            @Override public void onFailure(Call<CategoryDto> call, Throwable t) {}
        });
    }

    public void updateOnline(Category c) {
        UUID userId = UUID.fromString(tokenManager.getUserId());
        FinancialRequests.CreateCategoryRequest req = new FinancialRequests.CreateCategoryRequest(
            userId, c.getName(), "EXPENSE", c.getIcon(), "#6C5CE7"
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
                        for (CategoryDto dto : response.body()) {
                            Category serverCategory = convertToModel(dto);

                            // Xóa các category ở local có cùng tên (thường là do fake UUID sinh ra lúc offline)
                            for (Category local : localCategories) {
                                if (local.getName().trim().equalsIgnoreCase(serverCategory.getName().trim())) {
                                    if (!local.getId().equals(serverCategory.getId())) {
                                        categoryDao.deleteById(local.getId());
                                    }
                                }
                            }

                            categoryDao.insert(serverCategory);
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
