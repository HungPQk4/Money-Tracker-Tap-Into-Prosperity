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
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.TransactionDao;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.TransactionApi;
import vn.edu.usth.tip.network.responses.TransactionDto;
import vn.edu.usth.tip.utils.TokenManager;

public class TransactionRepository {
    private final TransactionDao transactionDao;
    private final TransactionApi transactionApi;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public TransactionRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.transactionDao = db.transactionDao();
        TokenManager tokenManager = new TokenManager(context);
        this.transactionApi = RetrofitClient.createService(TransactionApi.class, tokenManager);
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
        if ("INCOME".equalsIgnoreCase(dto.getType())) type = Transaction.Type.INCOME;
        else if ("TRANSFER".equalsIgnoreCase(dto.getType())) type = Transaction.Type.TRANSFER;

        return new Transaction(
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
    }

    public interface SyncCallback {
        void onSuccess();
        void onError(String message);
    }
}
