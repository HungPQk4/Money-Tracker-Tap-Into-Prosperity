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

    @Query("SELECT * FROM goals WHERE user_id = :userId ORDER BY targetDateMs ASC")
    LiveData<List<Goal>> getAllGoalsSorted(String userId);

    @Query("SELECT * FROM goals WHERE user_id = :userId")
    List<Goal> getAllGoalsSync(String userId);

    @Query("DELETE FROM goals WHERE id = :id")
    void deleteById(String id);
}
