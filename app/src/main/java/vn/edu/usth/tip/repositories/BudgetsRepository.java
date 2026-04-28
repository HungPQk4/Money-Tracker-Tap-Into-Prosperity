package vn.edu.usth.tip.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.Budget;
import vn.edu.usth.tip.models.BudgetDao;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.CategoryDao;
import vn.edu.usth.tip.network.FinancialApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.responses.FinancialDtos.BudgetDto;
import vn.edu.usth.tip.network.requests.FinancialRequests;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class BudgetsRepository {
    private final BudgetDao budgetDao;
    private final CategoryDao categoryDao;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public BudgetsRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.budgetDao = db.budgetDao();
        this.categoryDao = db.categoryDao();
        this.tokenManager = new TokenManager(context);
        this.financialApi = RetrofitClient.createService(FinancialApi.class, tokenManager);
    }

    public void addOnline(Budget b) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) {
                    android.util.Log.e("BUDGET_SYNC", "Add failed: userId is null");
                    return;
                }
                UUID userId = UUID.fromString(userIdStr);
                UUID categoryId = resolveCategoryId(b.getCategoryName());
                if (categoryId == null) {
                    android.util.Log.e("BUDGET_SYNC", "Add failed: categoryId not found for '" + b.getCategoryName() + "'");
                    // Log all categories for debugging
                    List<Category> all = categoryDao.getAllCategoriesSync();
                    if (all != null) {
                        for (Category c : all) android.util.Log.d("BUDGET_SYNC", "  Category in DB: '" + c.getName() + "' id=" + c.getId());
                    }
                    return;
                }

                long duration = b.getPeriodEndMs() - b.getPeriodStartMs();
                String pType = (duration <= 8L * 24 * 60 * 60 * 1000) ? "weekly" : "monthly";

                FinancialRequests.CreateBudgetRequest req = new FinancialRequests.CreateBudgetRequest(
                    userId, categoryId, new java.math.BigDecimal(b.getLimitAmount()), new java.math.BigDecimal(b.getSpentAmount()),
                    pType, sdf.format(new java.util.Date(b.getPeriodStartMs())),
                    sdf.format(new java.util.Date(b.getPeriodEndMs()))
                );

                android.util.Log.d("BUDGET_SYNC", "Sending POST /budgets: userId=" + userId + " catId=" + categoryId + " pType=" + pType);

                Response<BudgetDto> response = financialApi.createBudget(req).execute();
                if (response.isSuccessful() && response.body() != null) {
                    android.util.Log.d("BUDGET_SYNC", "Add success! Server id=" + response.body().getId());
                    // Replace local record with server-assigned UUID
                    budgetDao.delete(b);
                    budgetDao.insert(convertToModel(response.body()));
                } else {
                    String errBody = "";
                    try { if (response.errorBody() != null) errBody = response.errorBody().string(); } catch (Exception ignored) {}
                    android.util.Log.e("BUDGET_SYNC", "Add error: " + response.code() + " " + response.message() + " body=" + errBody);
                }
            } catch (Exception e) {
                android.util.Log.e("BUDGET_SYNC", "Add exception: " + e.getMessage(), e);
            }
        });
    }

    public void updateOnline(Budget b) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);
                UUID categoryId = resolveCategoryId(b.getCategoryName());
                if (categoryId == null) {
                    android.util.Log.e("BUDGET_SYNC", "Update failed: categoryId not found for '" + b.getCategoryName() + "'");
                    return;
                }

                long duration = b.getPeriodEndMs() - b.getPeriodStartMs();
                String pType = (duration <= 8L * 24 * 60 * 60 * 1000) ? "weekly" : "monthly";

                FinancialRequests.CreateBudgetRequest req = new FinancialRequests.CreateBudgetRequest(
                    userId, categoryId, new java.math.BigDecimal(b.getLimitAmount()), new java.math.BigDecimal(b.getSpentAmount()),
                    pType, sdf.format(new java.util.Date(b.getPeriodStartMs())),
                    sdf.format(new java.util.Date(b.getPeriodEndMs()))
                );

                try {
                    UUID id = UUID.fromString(b.getId());
                    Response<BudgetDto> response = financialApi.updateBudget(id, req).execute();
                    if (!response.isSuccessful()) {
                        android.util.Log.e("BUDGET_SYNC", "Update error: " + response.code());
                        if (response.code() == 404) {
                            android.util.Log.d("BUDGET_SYNC", "404 → Fallback to addOnline...");
                            addOnline(b);
                        }
                    } else {
                        android.util.Log.d("BUDGET_SYNC", "Update success!");
                    }
                } catch (IllegalArgumentException e) {
                    // ID không phải UUID chuẩn → record chưa lên server bao giờ, POST mới
                    android.util.Log.d("BUDGET_SYNC", "Non-UUID id, fallback to addOnline");
                    addOnline(b);
                }
            } catch (Exception e) {
                android.util.Log.e("BUDGET_SYNC", "Update exception: " + e.getMessage(), e);
            }
        });
    }

    public void deleteOnline(String budgetId) {
        try {
            UUID id = UUID.fromString(budgetId);
            financialApi.deleteBudget(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    public void sync(SyncCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError("UserId is null"));
                    return;
                }
                UUID userId = UUID.fromString(userIdStr);

                // 1. Kéo dữ liệu từ server về trước để so sánh
                Response<List<BudgetDto>> response = financialApi.getAllBudgets().execute();
                if (!response.isSuccessful() || response.body() == null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError("Fetch failed: " + response.code()));
                    return;
                }
                List<BudgetDto> serverBudgets = response.body();
                List<Budget> localBudgets = budgetDao.getAllBudgetsSync();

                // 2. Đẩy các ngân sách local CHƯA CÓ trên server lên server
                for (Budget local : localBudgets) {
                    boolean existsOnServer = false;
                    for (BudgetDto server : serverBudgets) {
                        if (server.getId().toString().equals(local.getId())) {
                            existsOnServer = true;
                            break;
                        }
                    }

                    if (!existsOnServer) {
                        UUID categoryId = resolveCategoryId(local.getCategoryName());
                        if (categoryId != null) {
                            long duration = local.getPeriodEndMs() - local.getPeriodStartMs();
                            String pType = (duration <= 8L * 24 * 60 * 60 * 1000) ? "weekly" : "monthly";

                            FinancialRequests.CreateBudgetRequest req = new FinancialRequests.CreateBudgetRequest(
                                userId, categoryId, new java.math.BigDecimal(local.getLimitAmount()), new java.math.BigDecimal(local.getSpentAmount()),
                                pType, sdf.format(new java.util.Date(local.getPeriodStartMs())),
                                sdf.format(new java.util.Date(local.getPeriodEndMs()))
                            );

                            Response<BudgetDto> res = financialApi.createBudget(req).execute();
                            if (res.isSuccessful() && res.body() != null) {
                                budgetDao.delete(local);
                                budgetDao.insert(convertToModel(res.body()));
                            }
                        }
                    }
                }

                // 3. Cập nhật lại local database bằng dữ liệu mới nhất từ server
                // Fetch server budgets again in case some were just added
                serverBudgets = financialApi.getAllBudgets().execute().body();
                if (serverBudgets != null) {
                    // Xóa sạch budget local (vì ta đã đẩy hết cái chưa có lên rồi)
                    // Hoặc đơn giản là REPLACE các bản ghi trùng ID
                    for (BudgetDto dto : serverBudgets) {
                        budgetDao.insert(convertToModel(dto));
                    }
                }

                new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onSuccess);
            } catch (Exception e) {
                android.util.Log.e("BUDGET_SYNC", "Sync exception: " + e.getMessage(), e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private Budget convertToModel(BudgetDto dto) {
        long start = 0, end = 0;
        try {
            if (dto.getPeriodStart() != null) start = sdf.parse(dto.getPeriodStart()).getTime();
            if (dto.getPeriodEnd() != null) end = sdf.parse(dto.getPeriodEnd()).getTime();
        } catch (Exception ignored) {}

        String categoryName = (dto.getCategoryName() != null && !dto.getCategoryName().isEmpty())
            ? dto.getCategoryName() : "General";

        return new Budget(
            dto.getId().toString(),
            "Ngân sách " + categoryName,
            "💰",
            "#6C5CE7",
            categoryName,
            dto.getAmount().longValue(),
            dto.getSpentAmount() != null ? dto.getSpentAmount().longValue() : 0,
            start,
            end,
            System.currentTimeMillis()
        );
    }

    /**
     * Tìm UUID của danh mục theo tên.
     * Nếu không tìm thấy hoặc UUID không hợp lệ (fake), tự động sync danh mục từ server rồi thử lại.
     * Phương thức này CHỈ gọi từ background thread.
     */
    private UUID resolveCategoryId(String categoryName) {
        // Bước 1: Tìm trong Room (case-insensitive)
        Category category = categoryDao.findByNameNoCase(categoryName);
        if (category != null) {
            try {
                return UUID.fromString(category.getId());
            } catch (IllegalArgumentException e) {
                // ID local (không phải UUID) → Sync lên Neon
                return syncCategoryToNeon(category);
            }
        }

        // Bước 2: Không tìm thấy local → kéo categories mới từ server
        android.util.Log.d("BUDGET_SYNC", "Category '" + categoryName + "' not found locally. Syncing from server...");
        try {
            Response<List<vn.edu.usth.tip.network.responses.FinancialDtos.CategoryDto>> resp =
                    financialApi.getAllCategories().execute();
            if (resp.isSuccessful() && resp.body() != null) {
                for (vn.edu.usth.tip.network.responses.FinancialDtos.CategoryDto dto : resp.body()) {
                    categoryDao.insert(new Category(
                            dto.getId().toString(),
                            dto.getName(),
                            dto.getIcon() != null ? dto.getIcon() : "📂",
                            dto.getColorHex() != null ? dto.getColorHex() : "#6C5CE7",
                            dto.getType() != null ? dto.getType() : "expense",
                            true, false
                    ));
                }
                android.util.Log.d("BUDGET_SYNC", "Synced " + resp.body().size() + " categories from server.");
            }
        } catch (Exception e) {
            android.util.Log.e("BUDGET_SYNC", "Category sync error: " + e.getMessage());
        }

        // Bước 3: Tìm lại sau khi sync
        category = categoryDao.findByNameNoCase(categoryName);
        if (category != null) {
            try {
                return UUID.fromString(category.getId());
            } catch (IllegalArgumentException ignored) {}
        }

        // Bước 4: Vẫn không thấy → Fallback category đầu tiên có UUID
        List<Category> all = categoryDao.getAllCategoriesSync();
        if (all != null) {
            for (Category c : all) {
                try {
                    return UUID.fromString(c.getId());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private UUID syncCategoryToNeon(Category category) {
        try {
            String userIdStr = tokenManager.getUserId();
            if (userIdStr == null) return null;
            UUID userId = UUID.fromString(userIdStr);

            vn.edu.usth.tip.network.requests.FinancialRequests.CreateCategoryRequest req =
                    new vn.edu.usth.tip.network.requests.FinancialRequests.CreateCategoryRequest(
                    userId, category.getName(),
                    category.getType() != null ? category.getType() : "expense",
                    category.getIcon(),
                    category.getColorHex() != null ? category.getColorHex() : "#6C5CE7"
            );

            Response<vn.edu.usth.tip.network.responses.FinancialDtos.CategoryDto> resp = financialApi.createCategory(req).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                vn.edu.usth.tip.network.responses.FinancialDtos.CategoryDto dto = resp.body();
                categoryDao.delete(category);
                category.setId(dto.getId().toString());
                categoryDao.insert(category);
                return dto.getId();
            }
        } catch (Exception e) {
            android.util.Log.e("BUDGET_SYNC", "Failed to sync category: " + category.getName(), e);
        }
        return null;
    }

    /** Đọc UUID hợp lệ từ Room, không phân biệt hoa/thường. Trả null nếu không tìm thấy hoặc UUID giả. */
    private UUID tryGetValidUUID(String categoryName) {
        Category category = categoryDao.findByNameNoCase(categoryName);
        if (category == null) return null;
        try {
            return UUID.fromString(category.getId());
        } catch (IllegalArgumentException e) {
            android.util.Log.w("BUDGET_SYNC", "Category '" + categoryName + "' has fake UUID: " + category.getId());
            return null;
        }
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}
