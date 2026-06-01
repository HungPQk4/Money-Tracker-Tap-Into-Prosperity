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

    @Query("SELECT * FROM budgets WHERE user_id = :userId ORDER BY createdMs DESC")
    LiveData<List<Budget>> getAllBudgets(String userId);

    @Query("SELECT * FROM budgets WHERE user_id = :userId")
    List<Budget> getAllBudgetsSync(String userId);

    @Query("DELETE FROM budgets WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM budgets WHERE user_id = :userId OR user_id IS NULL")
    void deleteAllForUser(String userId);
}
