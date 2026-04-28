package vn.edu.usth.tip.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.CategoryDao;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.TransactionDao;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.models.WalletDao;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.TransactionApi;
import vn.edu.usth.tip.network.requests.CreateTransactionRequest;
import vn.edu.usth.tip.network.FinancialApi;
import vn.edu.usth.tip.network.requests.FinancialRequests;
import vn.edu.usth.tip.network.responses.FinancialDtos.AccountDto;
import vn.edu.usth.tip.network.responses.FinancialDtos.CategoryDto;
import vn.edu.usth.tip.network.responses.SyncBatchResponse;
import vn.edu.usth.tip.network.responses.TransactionDto;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class TransactionRepository {
    private final TransactionDao transactionDao;
    private final WalletDao walletDao;
    private final CategoryDao categoryDao;
    private final TransactionApi transactionApi;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;

    // Format ngày cho transactionDate (YYYY-MM-DD)
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    // Format ISO-8601 với timezone cho createdAt (server cần đúng định dạng này)
    private final SimpleDateFormat isoFormat;

    public TransactionRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.transactionDao = db.transactionDao();
        this.walletDao      = db.walletDao();
        this.categoryDao    = db.categoryDao();
        this.tokenManager   = new TokenManager(context);
        this.transactionApi = RetrofitClient.createService(TransactionApi.class, tokenManager);
        this.financialApi   = RetrofitClient.createService(FinancialApi.class, tokenManager);

        // ISO-8601 với offset timezone (ví dụ: 2025-03-15T19:30:00+07:00)
        isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // ← sửa ở đây
    }

    // =========================================================================
    //  PUSH DỮ LIỆU CŨ TRONG ROOM LÊN NEON (dùng API batch /sync)
    //  Đây là giải pháp cho vấn đề: dữ liệu "ăn uống", "di chuyển"...
    //  đã nhập offline nhưng chưa bao giờ lên được Neon.
    // =========================================================================

    /**
     * Đẩy TẤT CẢ giao dịch chưa sync (isSynced=false) trong Room lên Neon.
     * Giữ nguyên createdAt gốc — server không ghi đè bằng thời gian hiện tại.
     *
     * Luồng xử lý:
     * 1. Lấy toàn bộ record isSynced=false từ Room
     * 2. Map sang SyncBatchRequest.SyncItem (kèm createdAt gốc)
     * 3. Gọi POST /api/transactions/sync
     * 4. Với mỗi record server trả về (có UUID Neon): xóa bản nháp cũ, insert bản mới
     * 5. Gọi callback khi hoàn tất
     */
    public void pushUnsyncedToServer(PushCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) {
                    runOnMain(() -> callback.onComplete(0, 0, "Chưa đăng nhập"));
                    return;
                }
                UUID userId = UUID.fromString(userIdStr);

                // ── 1. Lấy tất cả giao dịch chưa sync ───────────────────────
                List<Transaction> unsynced = transactionDao.getUnsyncedTransactionsSync();
                if (unsynced == null || unsynced.isEmpty()) {
                    runOnMain(() -> callback.onComplete(0, 0, null));
                    return;
                }

                // ── 2. Map sang SyncItem, resolve accountId & categoryId ──────
                List<SyncBatchRequest.SyncItem> items     = new ArrayList<>();
                List<Transaction>               skippedTx = new ArrayList<>();

                for (Transaction tx : unsynced) {
                    UUID accountId  = resolveAccountId(tx);
                    UUID categoryId = resolveCategoryId(tx);

                    if (accountId == null || categoryId == null) {
                        android.util.Log.w("SYNC", "Skip tx id=" + tx.getId()
                                + " accountId=" + tx.getAccountId()
                                + " category=" + tx.getCategory());
                        skippedTx.add(tx); // thiếu account/category → bỏ qua
                        continue;
                    }

                    // Chuyển timestampMs → ISO-8601 string giữ offset múi giờ địa phương
                    String createdAtStr = isoFormat.format(new Date(tx.getTimestampMs()));

                    // QUAN TRỌNG: amount LUÔN gửi giá trị dương (abs).
                    // type="expense"/"income"/"transfer" đã xác định chiều hướng.
                    // Nếu gửi số âm → server từ chối do CHECK(amount > 0) trên Neon.
                    long rawAmount = tx.getAmountVnd();
                    if (rawAmount == 0) {
                        android.util.Log.w("SYNC", "Skip tx id=" + tx.getId() + ": amount = 0");
                        skippedTx.add(tx);
                        continue;
                    }
                    java.math.BigDecimal positiveAmount = new java.math.BigDecimal(Math.abs(rawAmount));

                    SyncBatchRequest.SyncItem item = new SyncBatchRequest.SyncItem(
                            accountId,
                            categoryId,
                            positiveAmount,
                            tx.getType().name().toLowerCase(),
                            tx.getNote(),
                            dateFormat.format(new Date(tx.getTimestampMs())),
                            createdAtStr
                    );
                    items.add(item);

                }

                if (items.isEmpty()) {
                    int skipped = skippedTx.size();
                    runOnMain(() -> callback.onComplete(0, skipped, null));
                    return;
                }

                // ── 3. Gọi API batch sync (synchronous vì đang trên background) ──
                SyncBatchRequest batchReq = new SyncBatchRequest(userId, items);
                Response<SyncBatchResponse> response = transactionApi.syncBatch(batchReq).execute();

                if (!response.isSuccessful() || response.body() == null) {
                    String err = "Lỗi server: " + response.code();
                    runOnMain(() -> callback.onComplete(0, unsynced.size(), err));
                    return;
                }

                SyncBatchResponse result = response.body();

                // ── 4. Cập nhật Room: xóa bản nháp, insert bản từ server ──────
                if (result.getSavedTransactions() != null) {
                    // Map theo thứ tự: items[i] tương ứng unsynced[j] (đã bỏ skipped)
                    int serverIdx = 0;
                    int localIdx  = 0;
                    for (Transaction tx : unsynced) {
                        UUID accountId  = resolveAccountId(tx);
                        UUID categoryId = resolveCategoryId(tx);
                        if (accountId == null || categoryId == null) continue; // bản đã skip

                        if (serverIdx < result.getSavedTransactions().size()) {
                            TransactionDto dto = result.getSavedTransactions().get(serverIdx++);
                            try {
                                transactionDao.delete(tx);
                                transactionDao.insert(convertToModel(dto));
                            } catch (Exception ignored) {}
                        }
                    }
                }

                int saved   = result.getSavedCount();
                int skipped = result.getSkippedCount() + skippedTx.size();
                runOnMain(() -> callback.onComplete(saved, skipped, null));

            } catch (Exception e) {
                runOnMain(() -> callback.onComplete(0, 0, "Lỗi đẩy dữ liệu: " + e.getMessage()));
            }
        });
    }

    // =========================================================================
    //  SYNC ĐẦY ĐỦ: push cũ lên trước → pull mới về sau
    //  Được gọi từ DashboardFragment và AllTransactionsFragment.
    // =========================================================================

    public void syncTransactions(SyncCallback callback) {
        // ÉP APP RESET TOÀN BỘ VỀ CHƯA SYNC (isSynced = 0)
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                transactionDao.resetSyncStatus();
            } catch (Exception ignored) {}

            // Bước 1: Push dữ liệu cũ chưa sync lên Neon trước
            pushUnsyncedToServer(new PushCallback() {
                @Override
                public void onComplete(int saved, int skipped, String error) {
                    // Dù push có lỗi hay không, vẫn tiếp tục pull để UI hiển thị đúng
                    pullFromServer(callback);
                }
            });
        });
    }

    // ─── Pull: lấy 30 ngày gần nhất từ Neon về Room ──────────────────────────
    private void pullFromServer(SyncCallback callback) {
        transactionApi.getRecentTransactions(30).enqueue(new Callback<List<TransactionDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<TransactionDto>> call,
                                   @NonNull Response<List<TransactionDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveToLocal(response.body(), callback);
                } else {
                    runOnMain(() -> callback.onError("Không thể lấy dữ liệu: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TransactionDto>> call, @NonNull Throwable t) {
                runOnMain(() -> callback.onError("Lỗi kết nối: " + t.getMessage()));
            }
        });
    }

    // =========================================================================
    //  THÊM / SỬA / XÓA ĐƠN LẺ (real-time)
    // =========================================================================

    public void addTransactionOnline(Transaction tx) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);

                UUID accountId  = resolveAccountId(tx);
                UUID categoryId = resolveCategoryId(tx);
                if (accountId == null || categoryId == null) return;

                CreateTransactionRequest req = new CreateTransactionRequest(
                        userId, accountId, categoryId,
                        new java.math.BigDecimal(Math.abs(tx.getAmountVnd())), // abs() — type xác định chiều
                        tx.getType().name().toLowerCase(),
                        dateFormat.format(new Date(tx.getTimestampMs()))
                );

                req.setNote(tx.getNote());

                transactionApi.createTransaction(req).enqueue(new Callback<TransactionDto>() {
                    @Override
                    public void onResponse(Call<TransactionDto> call, Response<TransactionDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            AppDatabase.databaseWriteExecutor.execute(() -> {
                                try {
                                    transactionDao.delete(tx);
                                    transactionDao.insert(convertToModel(response.body()));
                                } catch (Exception ignored) {}
                            });
                        }
                    }
                    @Override public void onFailure(Call<TransactionDto> call, Throwable t) {}
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void updateTransactionOnline(Transaction tx) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);

                UUID accountId  = resolveAccountId(tx);
                UUID categoryId = resolveCategoryId(tx);
                if (accountId == null || categoryId == null) return;

                CreateTransactionRequest req = new CreateTransactionRequest(
                        userId, accountId, categoryId,
                        new java.math.BigDecimal(Math.abs(tx.getAmountVnd())), // abs() — type xác định chiều
                        tx.getType().name().toLowerCase(),
                        dateFormat.format(new Date(tx.getTimestampMs()))
                );
                req.setNote(tx.getNote());

                UUID id = UUID.fromString(tx.getId());
                transactionApi.updateTransaction(id, req).enqueue(new Callback<TransactionDto>() {
                    @Override
                    public void onResponse(Call<TransactionDto> call, Response<TransactionDto> response) {
                        if (response.isSuccessful()) {
                            AppDatabase.databaseWriteExecutor.execute(() -> {
                                tx.setSynced(true);
                                transactionDao.update(tx);
                            });
                        }
                    }
                    @Override public void onFailure(Call<TransactionDto> call, Throwable t) {}
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void deleteTransactionOnline(String txId) {
        try {
            UUID id = UUID.fromString(txId);
            transactionApi.deleteTransaction(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        } catch (Exception ignored) {}
    }

    // =========================================================================
    //  HELPER METHODS
    // =========================================================================

    /**
     * Lưu danh sách TransactionDto từ server vào Room (upsert).
     * Xóa các record đã sync mà không còn tồn tại trên server.
     */
    private void saveToLocal(List<TransactionDto> dtos, SyncCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                java.util.Set<String> serverIds = new java.util.HashSet<>();
                for (TransactionDto dto : dtos) {
                    if (dto.getId() != null) serverIds.add(dto.getId().toString());
                }

                // Xóa record đã sync nhưng server không còn nữa
                List<Transaction> localAll = transactionDao.getAllTransactionsSync();
                if (localAll != null) {
                    for (Transaction local : localAll) {
                        if (local.isSynced() && !serverIds.contains(local.getId())) {
                            transactionDao.delete(local);
                        }
                    }
                }

                // Upsert từng record từ server
                for (TransactionDto dto : dtos) {
                    try {
                        transactionDao.insert(convertToModel(dto));
                    } catch (Exception ignored) {}
                }

                runOnMain(callback::onSuccess);
            } catch (Exception e) {
                runOnMain(() -> callback.onError("Lỗi lưu dữ liệu: " + e.getMessage()));
            }
        });
    }

    /** Resolve UUID account: ưu tiên accountId field, fallback lookup tên ví.
     * Nếu ví có ID local (không phải UUID), sync lên Neon ngay lập tức. */
    private UUID resolveAccountId(Transaction tx) {
        String aid = tx.getAccountId();
        if (aid != null && !aid.isEmpty()) {
            try { return UUID.fromString(aid); } catch (IllegalArgumentException ignored) {}
        }

        Wallet wallet = walletDao.findByNameSync(tx.getWalletName());
        if (wallet == null) {
            List<Wallet> all = walletDao.getAllWalletsSync();
            if (all != null && !all.isEmpty()) wallet = all.get(0);
        }
        if (wallet == null) return null;

        // Kiểm tra xem ID có phải UUID không
        try {
            return UUID.fromString(wallet.getId());
        } catch (IllegalArgumentException e) {
            // ID local → Sync lên Neon
            return syncWalletToNeon(wallet);
        }
    }

    private UUID syncWalletToNeon(Wallet wallet) {
        try {
            String userIdStr = tokenManager.getUserId();
            if (userIdStr == null) return null;
            UUID userId = UUID.fromString(userIdStr);

            String neonType = switch (wallet.getType()) {
                case CASH -> "cash";
                case BANK -> "bank";
                case EWALLET -> "e_wallet";
                case INVESTMENT -> "investment";
                default -> "cash";
            };

            FinancialRequests.CreateAccountRequest req = new FinancialRequests.CreateAccountRequest(
                    userId, wallet.getName(), neonType, new java.math.BigDecimal(wallet.getBalanceVnd())
            );

            Response<AccountDto> resp = financialApi.createAccount(req).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                AccountDto dto = resp.body();
                // Xóa bản local cũ, insert bản mới có UUID từ Neon
                walletDao.delete(wallet);
                wallet.setId(dto.getId().toString());
                walletDao.insert(wallet);
                return dto.getId();
            }
        } catch (Exception e) {
            android.util.Log.e("SYNC", "Failed to sync wallet: " + wallet.getName(), e);
        }
        return null;
    }

    /** Resolve UUID category: ưu tiên categoryId field, fallback lookup tên danh mục.
     * Nếu category có ID local (không phải UUID), sync lên Neon ngay lập tức. */
    private UUID resolveCategoryId(Transaction tx) {
        String cid = tx.getCategoryId();
        if (cid != null && !cid.isEmpty()) {
            try { return UUID.fromString(cid); } catch (IllegalArgumentException ignored) {}
        }

        Category category = categoryDao.findByNameSync(tx.getCategory());
        if (category == null) {
            List<Category> all = categoryDao.getAllCategoriesSync();
            if (all != null && !all.isEmpty()) category = all.get(0);
        }
        if (category == null) return null;

        try {
            return UUID.fromString(category.getId());
        } catch (IllegalArgumentException e) {
            // ID local → Sync lên Neon
            return syncCategoryToNeon(category);
        }
    }

    private UUID syncCategoryToNeon(Category category) {
        try {
            String userIdStr = tokenManager.getUserId();
            if (userIdStr == null) return null;
            UUID userId = UUID.fromString(userIdStr);

            FinancialRequests.CreateCategoryRequest req = new FinancialRequests.CreateCategoryRequest(
                    userId, category.getName(),
                    category.getType() != null ? category.getType() : "expense",
                    category.getIcon(),
                    category.getColorHex() != null ? category.getColorHex() : "#735BF2"
            );

            Response<CategoryDto> resp = financialApi.createCategory(req).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                CategoryDto dto = resp.body();
                // Xóa bản local cũ, insert bản mới có UUID từ Neon
                categoryDao.delete(category);
                category.setId(dto.getId().toString());
                categoryDao.insert(category);
                return dto.getId();
            }
        } catch (Exception e) {
            android.util.Log.e("SYNC", "Failed to sync category: " + category.getName(), e);
        }
        return null;
    }

    /** Chuyển TransactionDto từ server sang Transaction Room entity */
    private Transaction convertToModel(TransactionDto dto) throws ParseException {
        long timestamp = 0;
        if (dto.getTransactionDate() != null) {
            Date date = dateFormat.parse(dto.getTransactionDate());
            if (date != null) timestamp = date.getTime();
        }

        Transaction.Type type = Transaction.Type.EXPENSE;
        if (dto.getType() != null) {
            String typeStr = dto.getType().toString().toLowerCase();
            if ("income".equals(typeStr))        type = Transaction.Type.INCOME;
            else if ("transfer".equals(typeStr)) type = Transaction.Type.TRANSFER;
        }

        Transaction tx = new Transaction(
                dto.getId().toString(),
                dto.getNote() != null ? dto.getNote() : "Giao dịch",
                dto.getCategoryName() != null ? dto.getCategoryName() : "Khác",
                "💰",
                dto.getAccountName() != null ? dto.getAccountName() : "Ví chính",
                dto.getAmount().longValue(),
                type,
                timestamp,
                dto.getNote()
        );
        tx.setSynced(true);
        return tx;
    }

    /** Post runnable về Main thread an toàn */
    private void runOnMain(Runnable action) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(action);
    }

    // =========================================================================
    //  CALLBACK INTERFACES
    // =========================================================================

    public interface SyncCallback {
        void onSuccess();
        void onError(String message);
    }

    /** Callback riêng cho bước push dữ liệu cũ lên server */
    public interface PushCallback {
        /**
         * @param saved   Số bản ghi lưu thành công lên Neon
         * @param skipped Số bản ghi bị bỏ qua (trùng hoặc thiếu account/category)
         * @param error   null nếu OK, mô tả lỗi nếu thất bại
         */
        void onComplete(int saved, int skipped, String error);
    }
}