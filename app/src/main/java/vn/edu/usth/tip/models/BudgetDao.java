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
public interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Budget budget);

    @Update
    void update(Budget budget);

    @Delete
    void delete(Budget budget);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND isDeleted = 0 ORDER BY createdMs DESC")
    LiveData<List<Budget>> getAllBudgets(String userId);

    @Query("SELECT * FROM budgets WHERE user_id = :userId")
    List<Budget> getAllBudgetsSync(String userId);

    /** Bản ghi có thay đổi cục bộ chưa đẩy (gồm cả tombstone isDeleted=1) — dùng cho worker push. */
    @Query("SELECT * FROM budgets WHERE isSynced = 0 AND user_id = :userId")
    List<Budget> getUnsyncedBudgetsSync(String userId);

    /** Soft-delete: giữ tombstone để worker đẩy lệnh xoá lên server (chống zombie-resurrection). */
    @Query("UPDATE budgets SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    void softDeleteById(String id);

    /** Đánh dấu đã đồng bộ sau khi PUT update thành công. */
    @Query("UPDATE budgets SET isSynced = 1 WHERE id = :id")
    void markSynced(String id);

    @Query("DELETE FROM budgets WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM budgets WHERE user_id = :userId OR user_id IS NULL")
    void deleteAllForUser(String userId);
}
