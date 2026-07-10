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
public interface GoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Goal goal);

    @Update
    void update(Goal goal);

    @Delete
    void delete(Goal goal);

    @Query("SELECT * FROM goals WHERE user_id = :userId AND isDeleted = 0 ORDER BY targetDateMs ASC")
    LiveData<List<Goal>> getAllGoalsSorted(String userId);

    @Query("SELECT * FROM goals WHERE user_id = :userId")
    List<Goal> getAllGoalsSync(String userId);

    /** Bản ghi có thay đổi cục bộ chưa đẩy (gồm cả tombstone isDeleted=1) — dùng cho worker push. */
    @Query("SELECT * FROM goals WHERE isSynced = 0 AND user_id = :userId")
    List<Goal> getUnsyncedGoalsSync(String userId);

    /** Soft-delete: giữ tombstone để worker đẩy lệnh xoá lên server (chống zombie-resurrection). */
    @Query("UPDATE goals SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    void softDeleteById(String id);

    /** Đánh dấu đã đồng bộ sau khi PUT update thành công. */
    @Query("UPDATE goals SET isSynced = 1 WHERE id = :id")
    void markSynced(String id);

    @Query("DELETE FROM goals WHERE id = :id")
    void deleteById(String id);
}
