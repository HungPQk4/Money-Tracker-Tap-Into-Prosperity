package vn.edu.usth.tip.models;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);
}

