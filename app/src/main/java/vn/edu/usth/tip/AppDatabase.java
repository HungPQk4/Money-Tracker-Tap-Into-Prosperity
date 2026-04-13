package vn.edu.usth.tip;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.edu.usth.tip.models.Budget;
import vn.edu.usth.tip.models.BudgetDao;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.CategoryDao;
import vn.edu.usth.tip.models.Converters;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.TransactionDao;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.models.WalletDao;
import vn.edu.usth.tip.models.DebtLoan;
import vn.edu.usth.tip.models.DebtLoanDao;
import vn.edu.usth.tip.models.Goal;
import vn.edu.usth.tip.models.GoalDao;

@Database(entities = {Transaction.class, Category.class, Wallet.class, Budget.class, DebtLoan.class, Goal.class}, version = 5, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract WalletDao walletDao();
    public abstract BudgetDao budgetDao();
    public abstract DebtLoanDao debtLoanDao();
    public abstract GoalDao goalDao();

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "money_tracker_database")
                            .fallbackToDestructiveMigration()
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                CategoryDao dao = INSTANCE.categoryDao();
                List<Category> list = new ArrayList<>();
                list.add(new Category("cat_food", "Ăn uống", "🍜"));
                list.add(new Category("cat_transport", "Di chuyển", "🛵"));
                list.add(new Category("cat_shopping", "Mua sắm", "🛒"));
                list.add(new Category("cat_fun", "Giải trí", "🎬"));
                list.add(new Category("cat_health", "Sức khỏe", "💊"));
                list.add(new Category("cat_bills", "Hóa đơn", "⚡"));
                list.add(new Category("cat_family", "Gia đình", "❤️"));
                list.add(new Category("cat_add", "Thêm", "+", true));
                dao.insertAll(list);

                WalletDao walletDao = INSTANCE.walletDao();
                List<Wallet> wallets = new ArrayList<>();
                wallets.add(new Wallet("1", "Tiền mặt",   5_000_000L,   "💵",
                        Color.parseColor("#735BF2"), Wallet.Type.CASH,       true));
                wallets.add(new Wallet("2", "Vietcombank", 110_000_000L, "🏦",
                        Color.parseColor("#0EA5E9"), Wallet.Type.BANK,       true));
                wallets.add(new Wallet("3", "MoMo",        13_450_000L,  "💜",
                        Color.parseColor("#D946EF"), Wallet.Type.EWALLET,    true));
                walletDao.insertAll(wallets);
            });
        }
    };
}

