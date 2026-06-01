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

    @Query("SELECT * FROM wallets WHERE user_id = :userId")
    LiveData<List<Wallet>> getAllWallets(String userId);

    @Query("SELECT * FROM wallets WHERE user_id = :userId")
    List<Wallet> getAllWalletsSync(String userId);

    @Query("SELECT * FROM wallets WHERE name = :name AND user_id = :userId LIMIT 1")
    Wallet findByNameSync(String name, String userId);

    @Query("SELECT * FROM wallets WHERE name COLLATE NOCASE = :name AND user_id = :userId LIMIT 1")
    Wallet findByNameNoCase(String name, String userId);

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
