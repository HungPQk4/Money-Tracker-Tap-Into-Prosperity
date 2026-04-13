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
public interface WalletDao {

    @Query("SELECT * FROM wallets")
    LiveData<List<Wallet>> getAllWallets();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Wallet wallet);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Wallet> wallets);

    @Update
    void update(Wallet wallet);

    @Delete
    void delete(Wallet wallet);
}
