package vn.edu.usth.tip.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.DebtLoan;
import vn.edu.usth.tip.models.DebtLoanDao;
import vn.edu.usth.tip.network.FinancialApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.responses.DeltaResponse;
import vn.edu.usth.tip.network.responses.FinancialDtos.DebtDto;
import vn.edu.usth.tip.network.requests.FinancialRequests;
import vn.edu.usth.tip.utils.NetworkUtils;
import vn.edu.usth.tip.utils.SyncPrefs;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class DebtsRepository {
    private final AppDatabase db;
    private final Context appContext;
    private final DebtLoanDao debtLoanDao;
    private final FinancialApi financialApi;
    private final TokenManager tokenManager;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public DebtsRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AppDatabase.getDatabase(appContext);
        this.debtLoanDao = db.debtLoanDao();
        this.tokenManager = TokenManager.getOrCreate(appContext);
        this.financialApi = RetrofitClient.createService(FinancialApi.class, tokenManager);
    }

    public void addOnline(DebtLoan d) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);

                FinancialRequests.CreateDebtRequest req = new FinancialRequests.CreateDebtRequest(
                    userId, d.getPersonName(), new java.math.BigDecimal(d.getAmount()),
                    (d.getType() == DebtLoan.TYPE_LENT) ? "lend" : "borrow", 
                    sdf.format(new java.util.Date(d.getDueDate()))
                );
                req.setNote(d.getReason());

                financialApi.createDebt(req).enqueue(new Callback<DebtDto>() {
                    @Override
                    public void onResponse(Call<DebtDto> call, Response<DebtDto> response) {
                        if (response.isSuccessful()) {
                            AppDatabase.databaseWriteExecutor.execute(() -> {
                                d.setSynced(true);
                                debtLoanDao.update(d);
                            });
                        } else {
                            android.util.Log.e("DEBT_SYNC", "Add error: " + response.code() + " " + response.message());
                        }
                    }
                    @Override public void onFailure(Call<DebtDto> call, Throwable t) {
                        android.util.Log.e("DEBT_SYNC", "Add failed: " + t.getMessage());
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("DEBT_SYNC", "addOnline exception", e);
            }
        });
    }

    public void updateOnline(DebtLoan d) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);

                FinancialRequests.CreateDebtRequest req = new FinancialRequests.CreateDebtRequest(
                    userId, d.getPersonName(), new java.math.BigDecimal(d.getAmount()),
                    (d.getType() == DebtLoan.TYPE_LENT) ? "lend" : "borrow", 
                    sdf.format(new java.util.Date(d.getDueDate()))
                );
                req.setNote(d.getReason());

                try {
                    UUID id = UUID.fromString(d.getId());
                    financialApi.updateDebt(id, req).enqueue(new Callback<DebtDto>() {
                        @Override public void onResponse(Call<DebtDto> call, Response<DebtDto> response) {
                            if (!response.isSuccessful()) {
                                android.util.Log.e("DEBT_SYNC", "Update error: " + response.code() + " " + response.message());
                                // 404: server deleted the record (cross-device delete) or local UUID never reached server.
                                // Do NOT recreate — that would resurrect a deleted debt on other devices.
                                // The next full sync() will reconcile the final state.
                            } else {
                                android.util.Log.d("DEBT_SYNC", "Update success!");
                            }
                        }
                        @Override public void onFailure(Call<DebtDto> call, Throwable t) {
                            android.util.Log.e("DEBT_SYNC", "Update failure: " + t.getMessage());
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("DEBT_SYNC", "Update UUID parse error: " + e.getMessage());
                }
            } catch (Exception e) {
                android.util.Log.e("DEBT_SYNC", "updateOnline exception", e);
            }
        });
    }

    public void deleteOnline(String debtId) {
        try {
            UUID id = UUID.fromString(debtId);
            financialApi.deleteDebt(id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        android.util.Log.d("DEBT_SYNC", "Delete success!");
                    } else {
                        android.util.Log.e("DEBT_SYNC", "Delete error: " + response.code() + " " + response.message());
                    }
                }
                @Override public void onFailure(Call<Void> call, Throwable t) {
                    android.util.Log.e("DEBT_SYNC", "Delete failed: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            android.util.Log.e("DEBT_SYNC", "Delete UUID parse error: " + e.getMessage());
        }
    }

    public void sync(SyncCallback callback) {
        if (!NetworkUtils.isConnected(appContext)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError("Không có kết nối internet"));
            return;
        }
        // Capture userId trước toàn bộ sync — dùng cho cả push lẫn pull
        final String userIdStr = tokenManager.getUserId();
        if (userIdStr == null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError("Not logged in"));
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 1. Đẩy các khoản vay/nợ chưa đồng bộ lên server trước
                List<DebtLoan> unsynced = debtLoanDao.getUnsyncedDebtsSync(userIdStr);
                if (unsynced != null && !unsynced.isEmpty()) {
                    UUID userId = UUID.fromString(userIdStr);
                    for (DebtLoan d : unsynced) {
                        FinancialRequests.CreateDebtRequest req = new FinancialRequests.CreateDebtRequest(
                            userId, d.getPersonName(), new java.math.BigDecimal(d.getAmount()),
                            (d.getType() == DebtLoan.TYPE_LENT) ? "lend" : "borrow",
                            sdf.format(new java.util.Date(d.getDueDate()))
                        );
                        req.setNote(d.getReason());
                        try {
                            Response<DebtDto> res = financialApi.createDebt(req).execute();
                            if (res.isSuccessful() && res.body() != null) {
                                debtLoanDao.delete(d);
                                DebtLoan synced = convertToModel(res.body());
                                synced.setUserId(userIdStr);
                                debtLoanDao.insert(synced);
                            }
                        } catch (Exception e) {
                            android.util.Log.e("DEBT_SYNC", "sync push failed for '" + d.getPersonName() + "': " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("DEBT_SYNC", "sync exception", e);
            }

            // 2. Kéo tất cả dữ liệu từ server về Room
            financialApi.getAllDebts().enqueue(new Callback<List<DebtDto>>() {
                @Override
                public void onResponse(@NonNull Call<List<DebtDto>> call, @NonNull Response<List<DebtDto>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            if (!userIdStr.equals(tokenManager.getUserId())) return;
                            final List<DebtDto> serverDebts = response.body();
                            db.runInTransaction(() -> {
                                debtLoanDao.deleteSyncedDebts(userIdStr);
                                for (DebtDto dto : serverDebts) {
                                    DebtLoan model = convertToModel(dto);
                                    model.setUserId(userIdStr);
                                    debtLoanDao.insert(model);
                                }
                            });
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onSuccess);
                        });
                    } else {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError("Error: " + response.code()));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<DebtDto>> call, @NonNull Throwable t) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(t.getMessage()));
                }
            });
        });
    }

    // ─── Đồng bộ nền (worker): push unsynced + delta pull ──────────────────────

    /** Đẩy các khoản nợ/cho vay tạo offline (isSynced=0) lên server. Trả true nếu cần retry (5xx). */
    public boolean pushUnsyncedBlocking() throws IOException {
        String userIdStr = tokenManager.getUserId();
        if (userIdStr == null) return false;
        List<DebtLoan> unsynced = debtLoanDao.getUnsyncedDebtsSync(userIdStr);
        if (unsynced == null || unsynced.isEmpty()) return false;
        UUID userId = UUID.fromString(userIdStr);
        for (DebtLoan d : unsynced) {
            FinancialRequests.CreateDebtRequest req = new FinancialRequests.CreateDebtRequest(
                userId, d.getPersonName(), new java.math.BigDecimal(d.getAmount()),
                (d.getType() == DebtLoan.TYPE_LENT) ? "lend" : "borrow",
                sdf.format(new java.util.Date(d.getDueDate())));
            req.setNote(d.getReason());
            Response<DebtDto> res = financialApi.createDebt(req).execute();
            if (res.code() >= 500) return true; // retry theo backoff của worker
            if (res.isSuccessful() && res.body() != null) {
                debtLoanDao.delete(d);
                DebtLoan synced = convertToModel(res.body()); // setSynced(true)
                synced.setUserId(userIdStr);
                debtLoanDao.insert(synced);
            }
        }
        return false;
    }

    /** Delta pull nợ/cho vay (cursor-based, có tombstone). Trả true nếu cần retry (5xx). */
    public boolean syncDeltaBlocking() throws IOException {
        String cursor      = SyncPrefs.getCursor(appContext, SyncPrefs.KEY_DEBT);
        String untilTs     = null;
        String lastUpdAt   = null;
        String lastIdStr   = null;
        String finalCursor = null;
        boolean completed  = false; // chỉ advance cursor khi đã kéo HẾT các trang trong window

        while (true) {
            Response<DeltaResponse<DebtDto>> resp = financialApi
                    .getDebtsDelta(cursor, untilTs, lastUpdAt, lastIdStr, 500)
                    .execute();
            if (!resp.isSuccessful() || resp.body() == null) {
                if (resp.code() >= 500) return true;
                break;
            }
            DeltaResponse<DebtDto> page = resp.body();
            if (untilTs == null) untilTs = page.getSyncTimestamp();
            finalCursor = page.getSyncTimestamp();

            List<DebtDto> items = page.getItems();
            if (items != null && !items.isEmpty()) {
                String userId = tokenManager.getUserId();
                List<DebtLoan> toInsert = new ArrayList<>();
                List<String>   toDelete = new ArrayList<>();
                for (DebtDto dto : items) {
                    if (dto.isDeleted()) {
                        if (dto.getId() != null) toDelete.add(dto.getId().toString());
                        continue;
                    }
                    DebtLoan m = convertToModel(dto);
                    m.setUserId(userId);
                    toInsert.add(m);
                }
                db.runInTransaction(() -> {
                    for (String id : toDelete)  debtLoanDao.deleteById(id);
                    for (DebtLoan m : toInsert) debtLoanDao.insert(m);
                });
            }

            if (!page.isHasMore()) { completed = true; break; }
            if (items != null && !items.isEmpty()) {
                DebtDto last = items.get(items.size() - 1);
                lastUpdAt = last.getUpdatedAt();
                lastIdStr = last.getId() != null ? last.getId().toString() : null;
            }
        }

        if (completed && finalCursor != null)
            SyncPrefs.setCursor(appContext, SyncPrefs.KEY_DEBT, finalCursor);
        return false;
    }

    private DebtLoan convertToModel(DebtDto dto) {
        long dueDate = 0;
        try {
            if (dto.getDueDate() != null) dueDate = sdf.parse(dto.getDueDate()).getTime();
        } catch (Exception ignored) {}

        int type = "lend".equalsIgnoreCase(dto.getType()) ? DebtLoan.TYPE_LENT : DebtLoan.TYPE_I_OWE;

        DebtLoan model = new DebtLoan(
            dto.getId().toString(),
            dto.getContactName(),
            dto.getNote(),
            dto.getAmount().longValue(),
            dueDate,
            type
        );
        model.setSynced(true);
        return model;
    }

    public interface SyncCallback { void onSuccess(); void onError(String msg); }
}
