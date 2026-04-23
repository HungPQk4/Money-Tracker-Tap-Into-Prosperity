package vn.edu.usth.tip.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
import vn.edu.usth.tip.network.responses.TransactionDto;
import vn.edu.usth.tip.network.requests.CreateTransactionRequest;
import vn.edu.usth.tip.utils.TokenManager;
import java.util.UUID;

public class TransactionRepository {
    private final TransactionDao transactionDao;
    private final WalletDao walletDao;
    private final CategoryDao categoryDao;
    private final TransactionApi transactionApi;
    private final TokenManager tokenManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public TransactionRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.transactionDao = db.transactionDao();
        this.walletDao = db.walletDao();
        this.categoryDao = db.categoryDao();
        this.tokenManager = new TokenManager(context);
        this.transactionApi = RetrofitClient.createService(TransactionApi.class, tokenManager);
    }

    public void addTransactionOnline(Transaction tx) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);

                // Look up real server UUID for Account
                Wallet wallet = walletDao.findByNameSync(tx.getWalletName());
                if (wallet == null) return;
                UUID accountId = UUID.fromString(wallet.getId());

                // Look up real server UUID for Category
                Category category = categoryDao.findByNameSync(tx.getCategory());
                if (category == null) return;
                UUID categoryId = UUID.fromString(category.getId());

                CreateTransactionRequest req = new CreateTransactionRequest(
                        userId, accountId, categoryId,
                        new java.math.BigDecimal(tx.getAmountVnd()),
                        tx.getType().name().toLowerCase(),
                        dateFormat.format(new java.util.Date(tx.getTimestampMs()))
                );
                req.setNote(tx.getNote());

                transactionApi.createTransaction(req).enqueue(new Callback<TransactionDto>() {
                    @Override
                    public void onResponse(Call<TransactionDto> call, Response<TransactionDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            // Update local sync status
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

    public void updateTransactionOnline(Transaction tx) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userIdStr = tokenManager.getUserId();
                if (userIdStr == null) return;
                UUID userId = UUID.fromString(userIdStr);

                Wallet wallet = walletDao.findByNameSync(tx.getWalletName());
                if (wallet == null) return;
                UUID accountId = UUID.fromString(wallet.getId());

                Category category = categoryDao.findByNameSync(tx.getCategory());
                if (category == null) return;
                UUID categoryId = UUID.fromString(category.getId());

                CreateTransactionRequest req = new CreateTransactionRequest(
                        userId, accountId, categoryId,
                        new java.math.BigDecimal(tx.getAmountVnd()),
                        tx.getType().name().toLowerCase(),
                        dateFormat.format(new java.util.Date(tx.getTimestampMs()))
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

    public void syncTransactions(SyncCallback callback) {
        transactionApi.getAllTransactions().enqueue(new Callback<List<TransactionDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<TransactionDto>> call, @NonNull Response<List<TransactionDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveToLocal(response.body(), callback);
                } else {
                    callback.onError("Không thể lấy dữ liệu: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TransactionDto>> call, @NonNull Throwable t) {
                callback.onError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void saveToLocal(List<TransactionDto> dtos, SyncCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                for (TransactionDto dto : dtos) {
                    Transaction tx = convertToModel(dto);
                    transactionDao.insert(tx); // Sử dụng OnConflictStrategy.REPLACE nếu cần
                }
                callback.onSuccess();
            } catch (Exception e) {
                callback.onError("Lỗi lưu dữ liệu: " + e.getMessage());
            }
        });
    }

    private Transaction convertToModel(TransactionDto dto) throws ParseException {
        long timestamp = 0;
        if (dto.getTransactionDate() != null) {
            Date date = dateFormat.parse(dto.getTransactionDate());
            if (date != null) timestamp = date.getTime();
        }

        Transaction.Type type = Transaction.Type.EXPENSE;
        if (dto.getType() != null) {
            String typeStr = dto.getType().toString().toLowerCase();
            if ("income".equals(typeStr)) type = Transaction.Type.INCOME;
            else if ("transfer".equals(typeStr)) type = Transaction.Type.TRANSFER;
        }

        Transaction tx = new Transaction(
                dto.getId().toString(),
                dto.getNote() != null ? dto.getNote() : "Giao dịch",
                dto.getCategory() != null ? dto.getCategory().getName() : "Khác",
                dto.getCategory() != null ? dto.getCategory().getIcon() : "💰",
                dto.getAccount() != null ? dto.getAccount().getName() : "Ví chính",
                dto.getAmount().longValue(),
                type,
                timestamp,
                dto.getNote()
        );
        tx.setSynced(true); // Dữ liệu từ API mặc định đã đồng bộ
        return tx;
    }

    public interface SyncCallback {
        void onSuccess();
        void onError(String message);
    }
}