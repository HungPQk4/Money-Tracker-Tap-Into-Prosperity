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

    /**
     * Lọc giao dịch theo khoảng [fromMs, toMs].
     * Dùng cho filter Hôm nay / Tuần này / Tháng này trên Dashboard.
     */
    @Query("SELECT * FROM transactions WHERE timestampMs >= :fromMs AND timestampMs < :toMs ORDER BY timestampMs DESC")
    LiveData<List<Transaction>> getTransactionsBetween(long fromMs, long toMs);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);
}

