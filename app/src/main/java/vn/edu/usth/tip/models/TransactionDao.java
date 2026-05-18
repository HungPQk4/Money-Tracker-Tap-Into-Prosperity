package vn.edu.usth.tip.models;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import vn.edu.usth.tip.models.dto.CategoryMonthlyDTO;
import vn.edu.usth.tip.models.dto.DailySpendDTO;
import vn.edu.usth.tip.models.dto.DayPatternDTO;

@Dao
public interface TransactionDao {

    /** Tất cả giao dịch, mới nhất trước */
    @Query("SELECT * FROM transactions ORDER BY timestampMs DESC")
    LiveData<List<Transaction>> getAllTransactions();

    @Query("SELECT * FROM transactions")
    List<Transaction> getAllTransactionsSync();

    /**
     * Lọc giao dịch theo khoảng [fromMs, toMs].
     * Dùng cho filter Hôm nay / Tuần này / Tháng này trên Dashboard.
     */
    @Query("SELECT * FROM transactions WHERE timestampMs >= :fromMs AND timestampMs < :toMs ORDER BY timestampMs DESC")
    LiveData<List<Transaction>> getTransactionsBetween(long fromMs, long toMs);

    @Query("SELECT * FROM transactions WHERE isSynced = 0")
    List<Transaction> getUnsyncedTransactionsSync();

    @Query("UPDATE transactions SET isSynced = 0")
    void resetSyncStatus();

    @Query("DELETE FROM transactions WHERE isSynced = 1")
    void deleteSyncedTransactions();

    // ── InsightEngine sync queries ────────────────────────────────────────────
    // Chỉ dùng từ background thread (ExecutorService). Không trả LiveData.

    // BudgetForecaster: chi tiêu mỗi ngày trong khoảng thời gian (chỉ EXPENSE dương)
    // CAST sang INTEGER vì strftime trả về String — Room không tự ép kiểu int
    // 'localtime' bắt buộc: tránh giao dịch 00:00-06:59 VN bị xếp sang ngày trước (UTC)
    @Query("SELECT CAST(strftime('%d', timestampMs/1000, 'unixepoch', 'localtime') AS INTEGER) as dayNum, " +
           "SUM(amountVnd) as totalVnd " +
           "FROM transactions WHERE type = 'EXPENSE' AND amountVnd > 0 " +
           "AND timestampMs BETWEEN :fromMs AND :toMs " +
           "GROUP BY dayNum ORDER BY dayNum ASC")
    List<DailySpendDTO> getDailyExpensesSync(long fromMs, long toMs);

    @Query("SELECT CAST(strftime('%d', timestampMs/1000, 'unixepoch', 'localtime') AS INTEGER) as dayNum, " +
           "SUM(amountVnd) as totalVnd " +
           "FROM transactions WHERE type = 'EXPENSE' AND amountVnd > 0 " +
           "AND category = :category " +
           "AND timestampMs BETWEEN :fromMs AND :toMs " +
           "GROUP BY dayNum ORDER BY dayNum ASC")
    List<DailySpendDTO> getDailyExpensesByCategorySync(long fromMs, long toMs, String category);

    // PatternAnalyzer: trung bình chi tiêu theo thứ trong tuần (7 dòng tối đa)
    @Query("SELECT strftime('%w', timestampMs/1000, 'unixepoch', 'localtime') as dayOfWeek, " +
           "AVG(amountVnd) as avgSpend " +
           "FROM transactions WHERE type = 'EXPENSE' AND amountVnd > 0 " +
           "AND timestampMs > :sinceMs " +
           "GROUP BY dayOfWeek")
    List<DayPatternDTO> getAvgSpendByDayOfWeekSync(long sinceMs);

    // AnomalyDetector: tổng chi tiêu theo tháng, phân nhóm category (4 tháng gần nhất)
    @Query("SELECT category, strftime('%Y%m', timestampMs/1000, 'unixepoch', 'localtime') as yearMonth, " +
           "SUM(amountVnd) as totalVnd " +
           "FROM transactions WHERE type = 'EXPENSE' AND amountVnd > 0 " +
           "AND timestampMs > :sinceMs " +
           "GROUP BY category, yearMonth")
    List<CategoryMonthlyDTO> getCategoryMonthlyTotalsSync(long sinceMs);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);
}

