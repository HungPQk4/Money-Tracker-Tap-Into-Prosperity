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

    @Query("SELECT * FROM wallets")
    List<Wallet> getAllWalletsSync();

    @Query("SELECT * FROM wallets WHERE name = :name LIMIT 1")
    Wallet findByNameSync(String name);

    @Query("SELECT * FROM wallets WHERE name COLLATE NOCASE = :name LIMIT 1")
    Wallet findByNameNoCase(String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Wallet wallet);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Wallet> wallets);

    @Update
    void update(Wallet wallet);

    @Delete
    void delete(Wallet wallet);

    @Query("DELETE FROM wallets WHERE id = :id")
    void deleteById(String id);
}
