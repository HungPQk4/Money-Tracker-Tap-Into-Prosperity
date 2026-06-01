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

    /** Tất cả giao dịch chưa xóa của user, mới nhất trước */
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND user_id = :userId ORDER BY timestampMs DESC")
    LiveData<List<Transaction>> getAllTransactions(String userId);

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND user_id = :userId")
    List<Transaction> getAllTransactionsSync(String userId);

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND user_id = :userId ORDER BY timestampMs DESC LIMIT :limit")
    List<Transaction> getRecentTransactionsSync(int limit, String userId);

    /**
     * Lọc giao dịch theo khoảng [fromMs, toMs].
     * Dùng cho filter Hôm nay / Tuần này / Tháng này trên Dashboard.
     */
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND user_id = :userId AND timestampMs >= :fromMs AND timestampMs < :toMs ORDER BY timestampMs DESC")
    LiveData<List<Transaction>> getTransactionsBetween(long fromMs, long toMs, String userId);

    @Query("SELECT * FROM transactions WHERE isSynced = 0 AND user_id = :userId")
    List<Transaction> getUnsyncedTransactionsSync(String userId);

    @Query("DELETE FROM transactions WHERE id = :id")
    void deleteById(String id);

    @Query("UPDATE transactions SET id = :newId WHERE id = :oldId")
    void updateTransactionId(String oldId, String newId);

    @Query("UPDATE transactions SET " +
           "title = :title, category = :category, walletName = :walletName, " +
           "amountVnd = :amountVnd, type = :type, note = :note, " +
           "timestampMs = :timestampMs, updatedAtMs = :serverTs, isSynced = 1 " +
           "WHERE id = :id AND updatedAtMs = :requestTimeMs")
    int fullUpdateIfUnchanged(String id, long requestTimeMs,
                              String title, String category, String walletName,
                              long amountVnd, String type, String note,
                              long timestampMs, long serverTs);

    /** Reassign orphan transactions when a user category is deleted */
    @Query("UPDATE transactions SET category = :newCatName WHERE category = :oldCatName AND isDeleted = 0")
    void reassignCategory(String oldCatName, String newCatName);

    @Query("UPDATE transactions SET isSynced = 0")
    void resetSyncStatus();

    @Query("DELETE FROM transactions WHERE isSynced = 1")
    void deleteSyncedTransactions();

    // ── InsightEngine sync queries ────────────────────────────────────────────
    // Chỉ dùng từ background thread (ExecutorService). Không trả LiveData.

    @Query("SELECT CAST(strftime('%d', timestampMs/1000, 'unixepoch', 'localtime') AS INTEGER) as dayNum, " +
           "SUM(amountVnd) as totalVnd " +
           "FROM transactions WHERE type = 'EXPENSE' AND amountVnd > 0 " +
           "AND user_id = :userId " +
           "AND timestampMs BETWEEN :fromMs AND :toMs " +
           "GROUP BY dayNum ORDER BY dayNum ASC")
    List<DailySpendDTO> getDailyExpensesSync(long fromMs, long toMs, String userId);

    @Query("SELECT CAST(strftime('%d', timestampMs/1000, 'unixepoch', 'localtime') AS INTEGER) as dayNum, " +
           "SUM(amountVnd) as totalVnd " +
           "FROM transactions WHERE type = 'EXPENSE' AND amountVnd > 0 " +
           "AND user_id = :userId " +
           "AND category = :category " +
           "AND timestampMs BETWEEN :fromMs AND :toMs " +
           "GROUP BY dayNum ORDER BY dayNum ASC")
    List<DailySpendDTO> getDailyExpensesByCategorySync(long fromMs, long toMs, String category, String userId);

    @Query("SELECT strftime('%w', timestampMs/1000, 'unixepoch', 'localtime') as dayOfWeek, " +
           "AVG(amountVnd) as avgSpend " +
           "FROM transactions WHERE type = 'EXPENSE' AND amountVnd > 0 " +
           "AND user_id = :userId " +
           "AND timestampMs > :sinceMs " +
           "GROUP BY dayOfWeek")
    List<DayPatternDTO> getAvgSpendByDayOfWeekSync(long sinceMs, String userId);

    @Query("SELECT category, strftime('%Y%m', timestampMs/1000, 'unixepoch', 'localtime') as yearMonth, " +
           "SUM(amountVnd) as totalVnd " +
           "FROM transactions WHERE type = 'EXPENSE' AND amountVnd > 0 " +
           "AND user_id = :userId " +
           "AND timestampMs > :sinceMs " +
           "GROUP BY category, yearMonth")
    List<CategoryMonthlyDTO> getCategoryMonthlyTotalsSync(long sinceMs, String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);
}
