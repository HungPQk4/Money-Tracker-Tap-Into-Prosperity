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
public interface DebtLoanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DebtLoan debtLoan);

    @Update
    void update(DebtLoan debtLoan);

    @Delete
    void delete(DebtLoan debtLoan);

    @Query("SELECT * FROM debt_loans WHERE user_id = :userId ORDER BY dueDate ASC")
    LiveData<List<DebtLoan>> getAllSortedByDueDate(String userId);

    @Query("SELECT * FROM debt_loans WHERE isSynced = 0 AND user_id = :userId")
    List<DebtLoan> getUnsyncedDebtsSync(String userId);

    /** Xoá cứng theo id — dùng khi delta pull nhận tombstone (server đã xoá). */
    @Query("DELETE FROM debt_loans WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM debt_loans WHERE isSynced = 1 AND user_id = :userId")
    void deleteSyncedDebts(String userId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_loans WHERE type = " + DebtLoan.TYPE_I_OWE + " AND user_id = :userId")
    LiveData<Long> getTotalIOwe(String userId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_loans WHERE type = " + DebtLoan.TYPE_LENT + " AND user_id = :userId")
    LiveData<Long> getTotalOwedToMe(String userId);

    @Query("SELECT * FROM debt_loans WHERE user_id = :userId ORDER BY dueDate ASC")
    List<DebtLoan> getAllDebtLoansSync(String userId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_loans WHERE type = " + DebtLoan.TYPE_I_OWE + " AND user_id = :userId")
    long getTotalIOweSync(String userId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_loans WHERE type = " + DebtLoan.TYPE_LENT + " AND user_id = :userId")
    long getTotalOwedToMeSync(String userId);
}
