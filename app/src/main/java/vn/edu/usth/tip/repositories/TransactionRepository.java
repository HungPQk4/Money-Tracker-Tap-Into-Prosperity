package vn.edu.usth.tip.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import vn.edu.usth.tip.utils.NetworkUtils;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import vn.edu.usth.tip.network.responses.DeltaResponse;
import vn.edu.usth.tip.network.responses.FinancialDtos.AccountDto;
import vn.edu.usth.tip.network.responses.FinancialDtos.CategoryDto;
import vn.edu.usth.tip.network.responses.SyncBatchResponse;
import vn.edu.usth.tip.network.responses.TransactionDto;
import vn.edu.usth.tip.utils.SyncPrefs;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class TransactionRepository {
    private final AppDatabase db;
    private final Context appContext;
    private final TransactionDao transactionDao;
    private final WalletDao walletDao;
    private final CategoryDao categoryDao;
    private final TransactionApi transactionApi;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;

    // Format ngày cho transactionDate (YYYY-MM-DD) — chỉ dùng trên databaseWriteExecutor, an toàn
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    // Thread-safe ISO-8601 formatter cho updatedAt/createdAt (dùng thay SimpleDateFormat isoFormat cũ)
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public TransactionRepository(Context context) {
        this.appContext     = context.getApplicationContext();
        this.db             = AppDatabase.getDatabase(appContext);
        this.transactionDao = db.transactionDao();
        this.walletDao      = db.walletDao();
        this.categoryDao    = db.categoryDao();
        this.tokenManager   = TokenManager.getOrCreate(appContext);
        this.transactionApi = RetrofitClient.createService(TransactionApi.class, tokenManager);
        this.financialApi   = RetrofitClient.createService(FinancialApi.class, tokenManager);
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
                List<Transaction> unsynced = transactionDao.getUnsyncedTransactionsSync(userIdStr);
                if (unsynced == null || unsynced.isEmpty()) {
                    runOnMain(() -> callback.onComplete(0, 0, null));
                    return;
                }

                // ── 2. Map sang SyncItem, resolve accountId & categoryId ──────
                List<SyncBatchRequest.SyncItem> items     = new ArrayList<>();
                List<Transaction>               skippedTx = new ArrayList<>();
                // Track local tx keyed by ID — dùng khi ghép kết quả server (ID-based, không phải positional).
                Map<String, Transaction>        localById = new HashMap<>();

                for (Transaction tx : unsynced) {
                    // Soft-deleted: chỉ cần UUID để ra lệnh xóa, không cần account/category.
                    if (tx.isDeleted()) {
                        UUID serverTxId = null;
                        try { serverTxId = UUID.fromString(tx.getId()); } catch (IllegalArgumentException ignored) {}
                        if (serverTxId == null) {
                            // Legacy ID chưa bao giờ lên server — xóa thẳng, không cần push.
                            transactionDao.deleteById(tx.getId());
                            continue;
                        }
                        SyncBatchRequest.SyncItem deleteItem = new SyncBatchRequest.SyncItem(
                                serverTxId, null, null, null, null, null, null, null, null);
                        deleteItem.setDeleted(true);
                        items.add(deleteItem);
                        localById.put(tx.getId(), tx);
                        continue;
                    }

                    long rawAmount = tx.getAmountVnd();
                    if (rawAmount == 0) {
                        android.util.Log.w("SYNC", "Skip tx id=" + tx.getId() + ": amount = 0");
                        skippedTx.add(tx);
                        continue;
                    }

                    UUID accountId  = resolveAccountId(tx);
                    UUID categoryId = resolveCategoryId(tx);

                    if (accountId == null || categoryId == null) {
                        android.util.Log.w("SYNC", "Skip tx id=" + tx.getId()
                                + " accountId=" + tx.getAccountId()
                                + " category=" + tx.getCategory());
                        skippedTx.add(tx);
                        continue;
                    }

                    String createdAtStr = ISO_UTC.format(Instant.ofEpochMilli(tx.getTimestampMs()));
                    java.math.BigDecimal positiveAmount = new java.math.BigDecimal(Math.abs(rawAmount));

                    UUID serverTxId = null;
                    try { serverTxId = UUID.fromString(tx.getId()); } catch (IllegalArgumentException ignored) {}

                    long editMs = tx.getClientUpdatedAtMs() > 0 ? tx.getClientUpdatedAtMs()
                            : (tx.getUpdatedAtMs() > 0 ? tx.getUpdatedAtMs() : tx.getTimestampMs());
                    String clientUpdatedAt = ISO_UTC.format(Instant.ofEpochMilli(editMs));

                    SyncBatchRequest.SyncItem item = new SyncBatchRequest.SyncItem(
                            serverTxId,
                            accountId,
                            categoryId,
                            positiveAmount,
                            tx.getType().name().toLowerCase(),
                            tx.getNote(),
                            dateFormat.format(new Date(tx.getTimestampMs())),
                            createdAtStr,
                            clientUpdatedAt
                    );
                    item.setRecurring(tx.isRecurring());
                    if (tx.isRecurring()) item.setRecurInterval(tx.getRecurInterval());
                    items.add(item);
                    localById.put(tx.getId(), tx);
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

                // ── 4. Cập nhật Room: match bằng ID (không phải positional) ──
                if (result.getSavedTransactions() != null) {
                    for (TransactionDto dto : result.getSavedTransactions()) {
                        if (dto.getId() == null) continue;
                        String dtoId = dto.getId().toString();
                        Transaction local = localById.get(dtoId);

                        if (dto.isDeleted()) {
                            // Server xác nhận xóa — hard delete khỏi Room
                            transactionDao.deleteById(dtoId);
                            continue;
                        }

                        if (local == null) continue;

                        try {
                            transactionDao.delete(local);
                            Transaction newTx = convertToModel(dto);
                            newTx.setPhotoUri(local.getPhotoUri());
                            // Server chỉ lưu ngày (YYYY-MM-DD), không lưu giờ.
                            // Giữ lại timestampMs gốc của local để không bị reset về 00:00.
                            newTx.setTimestampMs(local.getTimestampMs());
                            transactionDao.insert(newTx);
                        } catch (Exception e) {
                            android.util.Log.e("TX_SYNC", "import DB replace failed for id=" + dto.getId() + ": " + e.getMessage());
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
        if (!NetworkUtils.isConnected(appContext)) {
            runOnMain(() -> callback.onError("Không có kết nối internet"));
            return;
        }
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

    // =========================================================================
    //  SYNCHRONOUS BATCH SYNC — dùng bởi TransactionSyncWorker
    //  Trả về true nếu cần retry (5xx), false nếu thành công.
    //  Throws IOException nếu mất mạng giữa chừng (Worker sẽ retry theo backoff).
    // =========================================================================

    public boolean pushUnsyncedBatchSync() throws IOException {
        String userIdStr = tokenManager.getUserId();
        if (userIdStr == null) return false; // chưa đăng nhập — không retry

        UUID userId = UUID.fromString(userIdStr);
        List<Transaction> unsynced = transactionDao.getUnsyncedTransactionsSync(userIdStr);
        if (unsynced == null || unsynced.isEmpty()) return false;

        final int BATCH_SIZE = 100;
        for (int i = 0; i < unsynced.size(); i += BATCH_SIZE) {
            List<Transaction> chunk = unsynced.subList(i, Math.min(i + BATCH_SIZE, unsynced.size()));

            // Snapshot TRƯỚC khi build SyncItems — ngăn Late Callback Overwrite race condition
            Map<String, Long> requestTimeMap = new HashMap<>();
            List<SyncBatchRequest.SyncItem> items = new ArrayList<>();

            for (Transaction tx : chunk) {
                requestTimeMap.put(tx.getId(), tx.getUpdatedAtMs());

                // Soft-deleted transaction — chỉ gửi lệnh xóa, không cần resolve account/category
                if (tx.isDeleted()) {
                    UUID serverTxId = null;
                    try { serverTxId = UUID.fromString(tx.getId()); } catch (IllegalArgumentException ignored) {}
                    if (serverTxId == null) {
                        // Legacy ID chưa bao giờ lên server — xóa local ngay
                        transactionDao.deleteById(tx.getId());
                        requestTimeMap.remove(tx.getId());
                        continue;
                    }
                    SyncBatchRequest.SyncItem deleteItem = new SyncBatchRequest.SyncItem(
                            serverTxId, null, null, null, null, null, null, null, null);
                    deleteItem.setDeleted(true);
                    items.add(deleteItem);
                    continue;
                }

                long rawAmount = tx.getAmountVnd();
                if (rawAmount == 0) { requestTimeMap.remove(tx.getId()); continue; }

                // UUID healing — legacy ID không phải UUID chuẩn
                UUID serverTxId;
                try {
                    serverTxId = UUID.fromString(tx.getId());
                } catch (IllegalArgumentException e) {
                    serverTxId = UUID.randomUUID();
                    final String oldId = tx.getId();
                    final String newId = serverTxId.toString();
                    tx.setId(newId);
                    transactionDao.updateTransactionId(oldId, newId);
                    // Cập nhật snapshot key sang ID mới
                    Long snap = requestTimeMap.remove(oldId);
                    requestTimeMap.put(newId, snap != null ? snap : tx.getUpdatedAtMs());
                    android.util.Log.w("SYNC", "Legacy ID healed: " + oldId + " → " + newId);
                }

                UUID accountId  = resolveAccountId(tx);
                UUID categoryId = resolveCategoryId(tx);
                if (accountId == null || categoryId == null) {
                    requestTimeMap.remove(tx.getId());
                    continue;
                }

                String createdAt     = ISO_UTC.format(Instant.ofEpochMilli(tx.getTimestampMs()));
                long editMs = tx.getClientUpdatedAtMs() > 0 ? tx.getClientUpdatedAtMs()
                        : (tx.getUpdatedAtMs() > 0 ? tx.getUpdatedAtMs() : tx.getTimestampMs());
                String clientUpdated = ISO_UTC.format(Instant.ofEpochMilli(editMs));

                SyncBatchRequest.SyncItem syncItem = new SyncBatchRequest.SyncItem(
                        serverTxId, accountId, categoryId,
                        new java.math.BigDecimal(Math.abs(rawAmount)),
                        tx.getType().name().toLowerCase(),
                        tx.getNote(),
                        dateFormat.format(new Date(tx.getTimestampMs())),
                        createdAt, clientUpdated);
                syncItem.setRecurring(tx.isRecurring());
                if (tx.isRecurring()) syncItem.setRecurInterval(tx.getRecurInterval());
                items.add(syncItem);
            }

            if (items.isEmpty()) continue;

            Response<SyncBatchResponse> response =
                    transactionApi.syncBatch(new SyncBatchRequest(userId, items)).execute();

            if (response.code() >= 500) return true; // 5xx → retry
            if (!response.isSuccessful() || response.body() == null) continue; // 4xx → skip

            List<TransactionDto> saved = response.body().getSavedTransactions();
            if (saved == null) continue;

            for (TransactionDto serverTx : saved) {
                if (serverTx.getId() == null) continue;
                String id = serverTx.getId().toString();
                Long requestTimeMs = requestTimeMap.get(id);
                if (requestTimeMs == null) continue;

                if (serverTx.isDeleted()) {
                    transactionDao.deleteById(id); // server confirm xóa → hard delete Room
                } else {
                    long serverTs = parseServerUpdatedAt(serverTx.getUpdatedAt());
                    String title    = serverTx.getNote() != null ? serverTx.getNote() : "Giao dịch";
                    String category = serverTx.getCategoryName() != null ? serverTx.getCategoryName() : "Khác";
                    String wallet   = serverTx.getAccountName() != null ? serverTx.getAccountName() : "Ví chính";
                    long amount     = serverTx.getAmount() != null ? serverTx.getAmount().longValue() : 0;
                    String type     = mapTypeStr(serverTx.getType());
                    String note     = serverTx.getNote();
                    long tsMs       = parseDateToMs(serverTx.getTransactionDate());

                    transactionDao.fullUpdateIfUnchanged(
                            id, requestTimeMs,
                            title, category, wallet,
                            amount, type, note,
                            tsMs, serverTs);
                }
            }
        }
        return false; // success
    }

    private long parseServerUpdatedAt(String updatedAt) {
        if (updatedAt == null) return 0;
        try { return Instant.parse(updatedAt).toEpochMilli(); } catch (Exception e) { return 0; }
    }

    private long parseDateToMs(String dateStr) {
        if (dateStr == null) return 0;
        try { return dateFormat.parse(dateStr).getTime(); } catch (Exception e) { return 0; }
    }

    private String mapTypeStr(String serverType) {
        if (serverType == null) return "expense";
        switch (serverType.toLowerCase()) {
            case "income":   return "INCOME";
            case "transfer": return "TRANSFER";
            default:         return "EXPENSE";
        }
    }

    // ─── Pull: lấy 180 ngày gần nhất từ Neon về Room ──────────────────────────
    private void pullFromServer(SyncCallback callback) {
        transactionApi.getRecentTransactions(180).enqueue(new Callback<List<TransactionDto>>() {
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
                // Mark synced=true BEFORE sending to prevent pushUnsyncedToServer()
                // from picking up the same transaction and double-sending it.
                tx.setSynced(true);
                transactionDao.insert(tx); // INSERT OR REPLACE — an toàn hơn update nếu record bị xóa

                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) {
                    android.util.Log.e("TX_SYNC", "addTransactionOnline: userId is null");
                    revertToUnsynced(tx);
                    return;
                }
                UUID userId = UUID.fromString(userIdStr);

                UUID accountId  = resolveAccountId(tx);
                UUID categoryId = resolveCategoryId(tx);
                if (accountId == null || categoryId == null) {
                    android.util.Log.e("TX_SYNC", "addTransactionOnline: accountId=" + accountId
                            + " categoryId=" + categoryId + " walletName=" + tx.getWalletName()
                            + " category=" + tx.getCategory());
                    revertToUnsynced(tx);
                    return;
                }

                CreateTransactionRequest req = new CreateTransactionRequest(
                        userId, accountId, categoryId,
                        new java.math.BigDecimal(Math.abs(tx.getAmountVnd())),
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
                            long localTimestamp = tx.getTimestampMs();
                            AppDatabase.databaseWriteExecutor.execute(() -> {
                                try {
                                    transactionDao.delete(tx);
                                    Transaction newTx = convertToModel(response.body());
                                    newTx.setPhotoUri(tx.getPhotoUri());
                                    newTx.setTimestampMs(localTimestamp);
                                    transactionDao.insert(newTx);
                                } catch (Exception e) {
                                    android.util.Log.e("TX_SYNC", "Local replace failed after create: " + e.getMessage());
                                }
                            });
                        } else {
                            String errBody = "";
                            try { if (response.errorBody() != null) errBody = response.errorBody().string(); } catch (Exception ignored) {}
                            android.util.Log.e("TX_SYNC", "Create error: " + response.code() + " " + response.message() + " body=" + errBody);
                            revertToUnsynced(tx);
                        }
                    }
                    @Override public void onFailure(Call<TransactionDto> call, Throwable t) {
                        android.util.Log.e("TX_SYNC", "Create failed: " + t.getMessage(), t);
                        revertToUnsynced(tx);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("TX_SYNC", "addTransactionOnline exception", e);
                revertToUnsynced(tx);
            }
        });
    }

    private void revertToUnsynced(Transaction tx) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            tx.setSynced(false);
            transactionDao.insert(tx); // INSERT OR REPLACE — đảm bảo record không bị mất nếu sync đã xóa nó
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
                        } else {
                            android.util.Log.e("TX_SYNC", "Update error: " + response.code() + " " + response.message());
                            revertToUnsynced(tx);
                        }
                    }
                    @Override public void onFailure(Call<TransactionDto> call, Throwable t) {
                        android.util.Log.e("TX_SYNC", "Update failed: " + t.getMessage());
                        revertToUnsynced(tx);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("TX_SYNC", "updateTransactionOnline exception", e);
            }
        });
    }

    public void deleteTransactionOnline(String txId) {
        try {
            UUID id = UUID.fromString(txId);
            transactionApi.deleteTransaction(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        android.util.Log.d("TX_SYNC", "Delete success: " + txId);
                    } else {
                        android.util.Log.e("TX_SYNC", "Delete error: " + response.code() + " " + response.message());
                    }
                }
                @Override public void onFailure(Call<Void> call, Throwable t) {
                    android.util.Log.e("TX_SYNC", "Delete failed: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            android.util.Log.e("TX_SYNC", "deleteTransactionOnline UUID parse error: " + e.getMessage());
        }
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
                // Dedup server list: nếu server trả về 2 record cùng nội dung (do double-send
                // trước khi fix), chỉ giữ record đầu tiên theo composite key.
                java.util.Map<String, TransactionDto> deduped = new java.util.LinkedHashMap<>();
                for (TransactionDto dto : dtos) {
                    String key = (dto.getTransactionDate() != null ? dto.getTransactionDate() : "") + "|"
                            + (dto.getAmount() != null ? dto.getAmount().toPlainString() : "0") + "|"
                            + (dto.getType() != null ? dto.getType().toString().toLowerCase() : "") + "|"
                            + (dto.getAccountName() != null ? dto.getAccountName().toLowerCase() : "") + "|"
                            + (dto.getCategoryName() != null ? dto.getCategoryName().toLowerCase() : "") + "|"
                            + (dto.getNote() != null ? dto.getNote().toLowerCase() : "");
                    deduped.putIfAbsent(key, dto);
                }
                List<TransactionDto> uniqueDtos = new ArrayList<>(deduped.values());

                java.util.Set<String> serverIds = new java.util.HashSet<>();
                for (TransactionDto dto : uniqueDtos) {
                    if (dto.getId() != null) serverIds.add(dto.getId().toString());
                }

                // Snapshot local DB once (outside transaction — read-only)
                String userId = tokenManager.getUserId();
                final List<Transaction> localAll = transactionDao.getAllTransactionsSync(userId);

                // All deletes + inserts run as a single atomic transaction:
                // if any uncaught error occurs mid-way, Room rolls back everything.
                db.runInTransaction(() -> {
                    // Xóa record đã sync nhưng server không còn nữa
                    if (localAll != null) {
                        for (Transaction local : localAll) {
                            if (local.isSynced() && !serverIds.contains(local.getId())) {
                                transactionDao.delete(local);
                            }
                        }
                    }

                    // Upsert các record đã dedup từ server (với LWW guard)
                    for (TransactionDto dto : uniqueDtos) {
                        try {
                            Transaction newTx = convertToModel(dto);
                            Transaction existingLocal = null;
                            if (localAll != null) {
                                for (Transaction local : localAll) {
                                    if (local.getId().equals(newTx.getId())) {
                                        existingLocal = local;
                                        newTx.setPhotoUri(local.getPhotoUri());
                                        if (hasActualTime(local.getTimestampMs())) {
                                            newTx.setTimestampMs(local.getTimestampMs());
                                        }
                                        break;
                                    }
                                }
                            }
                            if (existingLocal != null && shouldSkipServerVersion(existingLocal, newTx)) {
                                continue;
                            }
                            transactionDao.insert(newTx);
                        } catch (Exception e) {
                            android.util.Log.e("TX_SYNC", "saveToLocal insert failed for id=" + dto.getId() + ": " + e.getMessage());
                        }
                    }

                    // Clean up isSynced=false orphans whose data was already pushed to server
                    if (localAll != null) {
                        for (Transaction local : localAll) {
                            if (local.isSynced()) continue;
                            for (TransactionDto dto : uniqueDtos) {
                                if (isMatchingRecord(local, dto)) {
                                    transactionDao.delete(local);
                                    break;
                                }
                            }
                        }
                    }
                });

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
        String userId = tokenManager.getUserId();

        android.util.Log.d("TX_SYNC", "Resolving accountId for: '" + walletName + "'");

        java.util.Map<String, UUID> serverAccountMap = new java.util.HashMap<>();
        boolean fetchSuccess = false;
        try {
            Response<List<AccountDto>> resp = financialApi.getAllAccounts().execute();
            if (resp.isSuccessful() && resp.body() != null) {
                fetchSuccess = true;
                for (AccountDto dto : resp.body()) {
                    serverAccountMap.put(dto.getName().trim().toLowerCase(), dto.getId());

                    Wallet local = walletDao.findByNameNoCase(dto.getName(), userId);
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

                    Wallet serverWallet = new Wallet(
                            dto.getId().toString(),
                            dto.getName(),
                            dto.getBalance() != null ? dto.getBalance().longValue() : 0,
                            "💳",
                            android.graphics.Color.parseColor("#4A90E2"),
                            wType,
                            true
                    );
                    serverWallet.setUserId(userId);
                    walletDao.insert(serverWallet);
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

        Wallet localWallet = walletDao.findByNameNoCase(walletName, userId);
        if (localWallet != null) {
            UUID created = syncWalletToNeon(localWallet);
            if (created != null) return created;
        } else {
            Wallet newWallet = new Wallet(
                    java.util.UUID.randomUUID().toString(),
                    walletName, 0, "💳", android.graphics.Color.parseColor("#4A90E2"), Wallet.Type.CASH, true
            );
            newWallet.setUserId(userId);
            UUID created = syncWalletToNeon(newWallet);
            if (created != null) return created;
        }

        List<Wallet> all = walletDao.getAllWalletsSync(userId);
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

            String neonType = vn.edu.usth.tip.utils.WalletTypeConverter.toApiString(wallet.getType());

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
                    Category cat = new Category(
                            dto.getId().toString(),
                            dto.getName(),
                            dto.getIcon() != null ? dto.getIcon() : "📂",
                            dto.getColorHex() != null ? dto.getColorHex() : "#6C5CE7",
                            dto.getType() != null ? dto.getType() : "expense",
                            Boolean.TRUE.equals(dto.getIsSystem()), false
                    );
                    cat.setUserId(dto.getUserId() != null ? dto.getUserId().toString() : null);
                    categoryDao.insert(cat);
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
        tx.setRecurring(dto.isRecurring());
        tx.setRecurInterval(dto.getRecurInterval());
        tx.setUserId(tokenManager.getUserId());
        // Server updatedAt → updatedAtMs (version/cursor); clientUpdatedAt → clientUpdatedAtMs (LWW guard)
        if (dto.getUpdatedAt() != null) {
            tx.setUpdatedAtMs(parseServerUpdatedAt(dto.getUpdatedAt()));
        }
        if (dto.getClientUpdatedAt() != null) {
            tx.setClientUpdatedAtMs(parseServerUpdatedAt(dto.getClientUpdatedAt()));
        }
        return tx;
    }

    /**
     * Trả về LiveData danh sách giao dịch EXPENSE trong một tháng cụ thể.
     * Dùng nguồn Room cục bộ (luôn đồng bộ qua syncTransactions).
     */
    public androidx.lifecycle.LiveData<List<Transaction>> getMonthlyExpenses(int year, int month) {
        java.util.Calendar start = java.util.Calendar.getInstance();
        start.set(year, month - 1, 1, 0, 0, 0);
        start.set(java.util.Calendar.MILLISECOND, 0);
        long startMs = start.getTimeInMillis();

        java.util.Calendar end = (java.util.Calendar) start.clone();
        end.add(java.util.Calendar.MONTH, 1);
        long endMs = end.getTimeInMillis();

        return transactionDao.getTransactionsBetween(startMs, endMs, tokenManager.getUserId());
    }

    /**
     * LWW pull guard: returns true when the local copy has an unsynced edit that is
     * at least as recent as the server version — server version should NOT overwrite it.
     *
     * Fallback: if updatedAtMs == 0 (pre-migration record), use timestampMs as a proxy
     * so we don't accidentally overwrite data from users who just upgraded the app.
     */
    private boolean shouldSkipServerVersion(Transaction local, Transaction serverTx) {
        if (local.isSynced()) return false; // already in sync — let server win
        // LWW on the client EDIT-time (mirrors the server). updatedAtMs is the server version/cursor.
        long localEdit = local.getClientUpdatedAtMs() > 0 ? local.getClientUpdatedAtMs()
                : (local.getUpdatedAtMs() > 0 ? local.getUpdatedAtMs() : local.getTimestampMs());
        return localEdit >= serverTx.getClientUpdatedAtMs();
    }

    /** Returns true if a local unsynced record matches a server DTO (same date/amount/type/account/category). */
    private boolean isMatchingRecord(Transaction local, TransactionDto dto) {
        String localDate = dateFormat.format(new Date(local.getTimestampMs()));
        if (!localDate.equals(dto.getTransactionDate())) return false;
        if (dto.getAmount() == null || local.getAmountVnd() != dto.getAmount().longValue()) return false;
        String localType = local.getType().name().toLowerCase();
        String dtoType = dto.getType() != null ? dto.getType().toString().toLowerCase() : "expense";
        if (!localType.equals(dtoType)) return false;
        String localWallet = local.getWalletName() != null ? local.getWalletName().trim().toLowerCase() : "";
        String dtoAccount = dto.getAccountName() != null ? dto.getAccountName().trim().toLowerCase() : "";
        if (!localWallet.equals(dtoAccount)) return false;
        String localCat = local.getCategory() != null ? local.getCategory().trim().toLowerCase() : "";
        String dtoCat = dto.getCategoryName() != null ? dto.getCategoryName().trim().toLowerCase() : "";
        return localCat.equals(dtoCat);
    }

    /** Trả về true nếu timestamp có giờ:phút khác 00:00 (tức là đã được set thủ công). */
    private boolean hasActualTime(long timestampMs) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        return cal.get(java.util.Calendar.HOUR_OF_DAY) != 0
            || cal.get(java.util.Calendar.MINUTE)      != 0;
    }

    /** Post runnable về Main thread an toàn */
    private void runOnMain(Runnable action) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(action);
    }

    // =========================================================================
    //  FULL SYNC — dùng bởi TransactionSyncWorker
    //  Thứ tự FK: categories → accounts → transactions
    //  Trả về true nếu cần retry (5xx), false nếu thành công.
    // =========================================================================

    /**
     * Phase 1 of full sync: push unsynced local data, then pull master data (categories + accounts).
     * The Worker calls pullDeltaTransactionsSync() last so budgets/goals (which depend on categories
     * and accounts) can be pulled in between by their own repositories.
     */
    public boolean fullSyncPhase1() throws IOException {
        boolean retryNeeded = pushUnsyncedBatchSync();
        if (retryNeeded) return true;
        refreshCategoriesSync();
        refreshAccountsSync();
        return false;
    }

    private void refreshCategoriesSync() throws IOException {
        String cursor      = SyncPrefs.getCursor(appContext, SyncPrefs.KEY_CATEGORY);
        String untilTs     = null;
        String lastUpdAt   = null;
        String lastIdStr   = null;
        String finalCursor = null;

        while (true) {
            Response<DeltaResponse<CategoryDto>> resp = financialApi
                    .getCategoriesDelta(cursor, untilTs, lastUpdAt, lastIdStr, 500)
                    .execute();
            if (!resp.isSuccessful() || resp.body() == null) {
                if (resp.code() >= 500) throw new IOException("Categories delta server error: " + resp.code());
                break;
            }
            DeltaResponse<CategoryDto> page = resp.body();
            if (untilTs == null) untilTs = page.getSyncTimestamp();
            finalCursor = page.getSyncTimestamp();

            List<CategoryDto> items = page.getItems();
            if (items != null && !items.isEmpty()) {
                db.runInTransaction(() -> {
                    for (CategoryDto dto : items) {
                        if (dto.isDeleted()) {
                            if (dto.getId() != null) categoryDao.deleteById(dto.getId().toString());
                            continue;
                        }
                        Category cat = new Category(
                                dto.getId().toString(),
                                dto.getName(),
                                dto.getIcon()     != null ? dto.getIcon()     : "📂",
                                dto.getColorHex() != null ? dto.getColorHex() : "#6C5CE7",
                                dto.getType()     != null ? dto.getType()     : "expense",
                                Boolean.TRUE.equals(dto.getIsSystem()), false
                        );
                        cat.setUserId(dto.getUserId() != null ? dto.getUserId().toString() : null);
                        categoryDao.insert(cat);
                    }
                });
            }

            if (!page.isHasMore()) break;
            if (items != null && !items.isEmpty()) {
                CategoryDto last = items.get(items.size() - 1);
                lastUpdAt = last.getUpdatedAt();
                lastIdStr = last.getId() != null ? last.getId().toString() : null;
            }
        }

        if (finalCursor != null)
            SyncPrefs.setCursor(appContext, SyncPrefs.KEY_CATEGORY, finalCursor);
    }

    private void refreshAccountsSync() throws IOException {
        String cursor      = SyncPrefs.getCursor(appContext, SyncPrefs.KEY_ACCOUNT);
        String untilTs     = null;
        String lastUpdAt   = null;
        String lastIdStr   = null;
        String finalCursor = null;

        while (true) {
            Response<DeltaResponse<AccountDto>> resp = financialApi
                    .getAccountsDelta(cursor, untilTs, lastUpdAt, lastIdStr, 500)
                    .execute();
            if (!resp.isSuccessful() || resp.body() == null) {
                if (resp.code() >= 500) throw new IOException("Accounts delta server error: " + resp.code());
                break;
            }
            DeltaResponse<AccountDto> page = resp.body();
            if (untilTs == null) untilTs = page.getSyncTimestamp();
            finalCursor = page.getSyncTimestamp();

            List<AccountDto> items = page.getItems();
            if (items != null && !items.isEmpty()) {
                final String syncUserId = tokenManager.getUserId();
                db.runInTransaction(() -> {
                    for (AccountDto dto : items) {
                        if (dto.isDeleted()) {
                            if (dto.getId() != null) walletDao.deleteById(dto.getId().toString());
                            continue;
                        }
                        Wallet.Type wType = vn.edu.usth.tip.utils.WalletTypeConverter.fromApiString(dto.getType(), Wallet.Type.CASH);
                        Wallet w = new Wallet(
                                dto.getId().toString(),
                                dto.getName(),
                                dto.getBalance() != null ? dto.getBalance().longValue() : 0,
                                "💳",
                                android.graphics.Color.parseColor("#4A90E2"),
                                wType,
                                true
                        );
                        w.setUserId(syncUserId);
                        walletDao.insert(w);
                    }
                });
            }

            if (!page.isHasMore()) break;
            if (items != null && !items.isEmpty()) {
                AccountDto last = items.get(items.size() - 1);
                lastUpdAt = last.getUpdatedAt();
                lastIdStr = last.getId() != null ? last.getId().toString() : null;
            }
        }

        if (finalCursor != null)
            SyncPrefs.setCursor(appContext, SyncPrefs.KEY_ACCOUNT, finalCursor);
    }

    public boolean pullDeltaTransactionsSync() throws IOException {
        String cursor      = SyncPrefs.getCursor(appContext, SyncPrefs.KEY_TX);
        String untilTs     = null;
        String lastUpdAt   = null;
        String lastIdStr   = null;
        String finalCursor = null;

        while (true) {
            Response<DeltaResponse<TransactionDto>> resp = transactionApi
                    .getDelta(cursor, untilTs, lastUpdAt, lastIdStr, 500)
                    .execute();
            if (!resp.isSuccessful() || resp.body() == null) {
                if (resp.code() >= 500) return true; // 5xx → retry
                break; // 4xx → skip
            }
            DeltaResponse<TransactionDto> page = resp.body();
            if (untilTs == null) untilTs = page.getSyncTimestamp();
            finalCursor = page.getSyncTimestamp();

            List<TransactionDto> items = page.getItems();
            if (items != null && !items.isEmpty()) {
                // Pre-convert outside runInTransaction (convertToModel throws ParseException)
                List<Transaction>  toUpsert = new ArrayList<>();
                Map<String, Long>  toDelete = new HashMap<>(); // id → serverUpdatedAtMs
                for (TransactionDto dto : items) {
                    if (dto.isDeleted()) {
                        if (dto.getId() != null)
                            toDelete.put(dto.getId().toString(), parseServerUpdatedAt(dto.getUpdatedAt()));
                        continue;
                    }
                    try {
                        toUpsert.add(convertToModel(dto));
                    } catch (Exception e) {
                        android.util.Log.w("TX_DELTA", "Skip id=" + dto.getId() + ": " + e.getMessage());
                    }
                }

                // Snapshot local for LWW (read-only, outside transaction)
                String userId = tokenManager.getUserId();
                List<Transaction> localAll = transactionDao.getAllTransactionsSync(userId);
                Map<String, Transaction> localById = new HashMap<>();
                if (localAll != null) {
                    for (Transaction t : localAll) localById.put(t.getId(), t);
                }

                db.runInTransaction(() -> {
                    // Soft-delete propagation with LWW
                    for (Map.Entry<String, Long> entry : toDelete.entrySet()) {
                        String delId    = entry.getKey();
                        long   serverTs = entry.getValue();
                        Transaction existing = localById.get(delId);
                        if (existing == null) continue;
                        if (!existing.isSynced()) {
                            long localTs = existing.getUpdatedAtMs() > 0
                                    ? existing.getUpdatedAtMs() : existing.getTimestampMs();
                            if (serverTs >= localTs) transactionDao.deleteById(delId);
                            // else: local is newer — keep it; PUSH will reconcile
                        } else {
                            transactionDao.deleteById(delId);
                        }
                    }

                    // Upsert with LWW
                    for (Transaction serverTx : toUpsert) {
                        Transaction local = localById.get(serverTx.getId());
                        if (local != null) {
                            serverTx.setPhotoUri(local.getPhotoUri());
                            if (hasActualTime(local.getTimestampMs()))
                                serverTx.setTimestampMs(local.getTimestampMs());
                            if (!local.isSynced()) {
                                long localTs = local.getUpdatedAtMs() > 0
                                        ? local.getUpdatedAtMs() : local.getTimestampMs();
                                if (serverTx.getUpdatedAtMs() <= localTs) continue; // local wins
                            }
                        }
                        transactionDao.insert(serverTx);
                    }
                });
            }

            if (!page.isHasMore()) break;
            if (items != null && !items.isEmpty()) {
                TransactionDto last = items.get(items.size() - 1);
                lastUpdAt = last.getUpdatedAt();
                lastIdStr = last.getId() != null ? last.getId().toString() : null;
            }
        }

        if (finalCursor != null)
            SyncPrefs.setCursor(appContext, SyncPrefs.KEY_TX, finalCursor);
        return false; // success
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