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

    // Lấy danh sách tất cả, sắp xếp theo ngày hẹn gần nhất
    @Query("SELECT * FROM debt_loans ORDER BY dueDate ASC")
    LiveData<List<DebtLoan>> getAllSortedByDueDate();

    // Tính tổng tiền 'I OWE' (những mục mình nợ)
    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_loans WHERE type = " + DebtLoan.TYPE_I_OWE)
    LiveData<Long> getTotalIOwe();

    // Tính tổng tiền 'OWED TO ME' (những mục mình cho vay)
    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_loans WHERE type = " + DebtLoan.TYPE_LENT)
    LiveData<Long> getTotalOwedToMe();
}
