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
import vn.edu.usth.tip.network.requests.SyncBatchRequest;
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
        // Bước 1: Push dữ liệu cũ chưa sync lên Neon trước
        AppDatabase.databaseWriteExecutor.execute(() -> {
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
                if (userIdStr == null) {
                    android.util.Log.e("TX_SYNC", "addTransactionOnline: userId is null");
                    return;
                }
                UUID userId = UUID.fromString(userIdStr);

                UUID accountId  = resolveAccountId(tx);
                UUID categoryId = resolveCategoryId(tx);
                if (accountId == null || categoryId == null) {
                    android.util.Log.e("TX_SYNC", "addTransactionOnline: accountId=" + accountId
                            + " categoryId=" + categoryId + " walletName=" + tx.getWalletName()
                            + " category=" + tx.getCategory());
                    return;
                }

                CreateTransactionRequest req = new CreateTransactionRequest(
                        userId, accountId, categoryId,
                        new java.math.BigDecimal(Math.abs(tx.getAmountVnd())), // abs() — type xác định chiều
                        tx.getType().name().toLowerCase(),
                        dateFormat.format(new Date(tx.getTimestampMs()))
                );

                req.setNote(tx.getNote());

                android.util.Log.d("TX_SYNC", "POST /transactions: userId=" + userId
                        + " accountId=" + accountId + " categoryId=" + categoryId
                        + " amount=" + Math.abs(tx.getAmountVnd())
                        + " type=" + tx.getType().name().toLowerCase()
                        + " date=" + dateFormat.format(new Date(tx.getTimestampMs())));

                transactionApi.createTransaction(req).enqueue(new Callback<TransactionDto>() {
                    @Override
                    public void onResponse(Call<TransactionDto> call, Response<TransactionDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            android.util.Log.d("TX_SYNC", "Transaction created on server: " + response.body().getId());
                            AppDatabase.databaseWriteExecutor.execute(() -> {
                                try {
                                    transactionDao.delete(tx);
                                    transactionDao.insert(convertToModel(response.body()));
                                } catch (Exception ignored) {}
                            });
                        } else {
                            String errBody = "";
                            try { if (response.errorBody() != null) errBody = response.errorBody().string(); } catch (Exception ignored) {}
                            android.util.Log.e("TX_SYNC", "Create error: " + response.code() + " " + response.message() + " body=" + errBody);
                        }
                    }
                    @Override public void onFailure(Call<TransactionDto> call, Throwable t) {
                        android.util.Log.e("TX_SYNC", "Create failed: " + t.getMessage(), t);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("TX_SYNC", "addTransactionOnline exception", e);
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

    /**
     * Resolve UUID thật (trên Neon) cho account (wallet) theo tên.
     * Luôn fetch server accounts trước để đảm bảo UUID khớp server.
     */
    private UUID resolveAccountId(Transaction tx) {
        String walletName = tx.getWalletName();
        if (walletName == null || walletName.isEmpty()) walletName = "Ví chính";
        
        android.util.Log.d("TX_SYNC", "Resolving accountId for: '" + walletName + "'");

        java.util.Map<String, UUID> serverAccountMap = new java.util.HashMap<>();
        boolean fetchSuccess = false;
        try {
            Response<List<AccountDto>> resp = financialApi.getAllAccounts().execute();
            if (resp.isSuccessful() && resp.body() != null) {
                fetchSuccess = true;
                for (AccountDto dto : resp.body()) {
                    serverAccountMap.put(dto.getName().trim().toLowerCase(), dto.getId());

                    Wallet local = walletDao.findByNameNoCase(dto.getName());
                    if (local != null && !local.getId().equals(dto.getId().toString())) {
                        walletDao.deleteById(local.getId());
                    }
                    
                    Wallet.Type wType = Wallet.Type.CASH;
                    if (dto.getType() != null) {
                        String t = dto.getType().toLowerCase();
                        if (t.equals("bank")) wType = Wallet.Type.BANK;
                        else if (t.equals("e_wallet")) wType = Wallet.Type.EWALLET;
                        else if (t.equals("investment")) wType = Wallet.Type.INVESTMENT;
                    }

                    walletDao.insert(new Wallet(
                            dto.getId().toString(),
                            dto.getName(),
                            dto.getBalance() != null ? dto.getBalance().longValue() : 0,
                            "💳",
                            android.graphics.Color.parseColor("#4A90E2"),
                            wType,
                            true
                    ));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("TX_SYNC", "Server account fetch error: " + e.getMessage());
        }

        String key = walletName.trim().toLowerCase();
        if (fetchSuccess) {
            if (serverAccountMap.containsKey(key)) {
                return serverAccountMap.get(key);
            }
        } else {
            return null;
        }

        Wallet localWallet = walletDao.findByNameNoCase(walletName);
        if (localWallet != null) {
            UUID created = syncWalletToNeon(localWallet);
            if (created != null) return created;
        } else {
            Wallet newWallet = new Wallet(
                    java.util.UUID.randomUUID().toString(),
                    walletName, 0, "💳", android.graphics.Color.parseColor("#4A90E2"), Wallet.Type.CASH, true
            );
            UUID created = syncWalletToNeon(newWallet);
            if (created != null) return created;
        }

        List<Wallet> all = walletDao.getAllWalletsSync();
        if (all != null) {
            for (Wallet w : all) {
                try {
                    UUID uuid = UUID.fromString(w.getId());
                    if (serverAccountMap.containsValue(uuid)) return uuid;
                } catch (Exception ignored) {}
            }
        }
        return null;
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
                walletDao.delete(wallet);
                wallet.setId(dto.getId().toString());
                walletDao.insert(wallet);
                return dto.getId();
            }
        } catch (Exception e) {
            android.util.Log.e("TX_SYNC", "Failed to sync wallet: " + wallet.getName(), e);
        }
        return null;
    }

    /**
     * Resolve UUID thật (trên Neon) cho category theo tên.
     * Luôn fetch server categories trước để đảm bảo UUID khớp server.
     */
    private UUID resolveCategoryId(Transaction tx) {
        String categoryName = tx.getCategory();
        if (categoryName == null || categoryName.isEmpty()) categoryName = "Khác";

        android.util.Log.d("TX_SYNC", "Resolving categoryId for: '" + categoryName + "'");

        String txType = tx.getType() == Transaction.Type.INCOME ? "income" : "expense";
        java.util.Map<String, UUID> serverCategoryMap = new java.util.HashMap<>();
        boolean fetchSuccess = false;
        try {
            Response<List<CategoryDto>> resp = financialApi.getAllCategories().execute();
            if (resp.isSuccessful() && resp.body() != null) {
                fetchSuccess = true;
                List<Category> localCategories = categoryDao.getAllCategoriesSync();
                for (CategoryDto dto : resp.body()) {
                    if (dto.getType() != null && dto.getType().equalsIgnoreCase(txType)) {
                        serverCategoryMap.put(dto.getName().trim().toLowerCase(), dto.getId());
                    }

                    for (Category local : localCategories) {
                        String localType = local.getType() != null ? local.getType().trim() : "expense";
                        String dtoType = dto.getType() != null ? dto.getType().trim() : "expense";
                        if (local.getName().trim().equalsIgnoreCase(dto.getName().trim()) &&
                            localType.equalsIgnoreCase(dtoType)) {
                            if (!local.getId().equals(dto.getId().toString())) {
                                categoryDao.deleteById(local.getId());
                            }
                        }
                    }
                    categoryDao.insert(new Category(
                            dto.getId().toString(),
                            dto.getName(),
                            dto.getIcon() != null ? dto.getIcon() : "📂",
                            dto.getColorHex() != null ? dto.getColorHex() : "#6C5CE7",
                            dto.getType() != null ? dto.getType() : "expense",
                            true, false
                    ));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("TX_SYNC", "Server category fetch error: " + e.getMessage());
        }

        String key = categoryName.trim().toLowerCase();
        if (fetchSuccess) {
            if (serverCategoryMap.containsKey(key)) {
                return serverCategoryMap.get(key);
            }
        } else {
            return null;
        }

        // Can't easily use findByNameNoCase here since it ignores type, search list manually
        Category localCategory = null;
        List<Category> locals = categoryDao.getAllCategoriesSync();
        if (locals != null) {
            for (Category c : locals) {
                if (c.getName().trim().equalsIgnoreCase(categoryName.trim()) && c.getType().trim().equalsIgnoreCase(txType)) {
                    localCategory = c; break;
                }
            }
        }
        
        if (localCategory != null) {
            UUID created = syncCategoryToNeon(localCategory);
            if (created != null) return created;
        } else {
            Category newCat = new Category(
                    java.util.UUID.randomUUID().toString(),
                    categoryName, "📂", "#6C5CE7", txType, false, false
            );
            UUID created = syncCategoryToNeon(newCat);
            if (created != null) return created;
        }

        List<Category> all = categoryDao.getAllCategoriesSync();
        if (all != null) {
            for (Category c : all) {
                try {
                    UUID uuid = UUID.fromString(c.getId());
                    if (serverCategoryMap.containsValue(uuid)) return uuid;
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

            FinancialRequests.CreateCategoryRequest req = new FinancialRequests.CreateCategoryRequest(
                    userId, category.getName(),
                    category.getType() != null ? category.getType().toLowerCase() : "expense",
                    category.getIcon(),
                    category.getColorHex() != null ? category.getColorHex() : "#735BF2"
            );

            Response<CategoryDto> resp = financialApi.createCategory(req).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                CategoryDto dto = resp.body();
                categoryDao.delete(category);
                category.setId(dto.getId().toString());
                categoryDao.insert(category);
                return dto.getId();
            }
        } catch (Exception e) {
            android.util.Log.e("TX_SYNC", "Failed to sync category: " + category.getName(), e);
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