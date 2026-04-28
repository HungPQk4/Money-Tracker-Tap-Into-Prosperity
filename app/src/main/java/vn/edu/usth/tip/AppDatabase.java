package vn.edu.usth.tip;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

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

@Database(
        entities = {Transaction.class, Category.class, Wallet.class,
                Budget.class, DebtLoan.class, Goal.class},
        version = 10,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    // =========================================================================
    //  UUID CỐ ĐỊNH — PHẢI KHỚP HOÀN TOÀN VỚI NEON DATABASE
    // =========================================================================

    // Chi tiêu (expense)
    public static final String CAT_FOOD       = "a1000000-0000-0000-0000-000000000001";
    public static final String CAT_TRANSPORT  = "a1000000-0000-0000-0000-000000000002";
    public static final String CAT_SHOPPING   = "a1000000-0000-0000-0000-000000000003";
    public static final String CAT_FUN        = "a1000000-0000-0000-0000-000000000004";
    public static final String CAT_HEALTH     = "a1000000-0000-0000-0000-000000000005";
    public static final String CAT_BILLS_EXP  = "a1000000-0000-0000-0000-000000000006";
    public static final String CAT_FAMILY_EXP = "a1000000-0000-0000-0000-000000000007";

    // Thu nhập (income)
    public static final String CAT_SALARY     = "b1000000-0000-0000-0000-000000000001";
    public static final String CAT_BONUS      = "b1000000-0000-0000-0000-000000000002";
    public static final String CAT_BILLS_INC  = "b1000000-0000-0000-0000-000000000003";
    public static final String CAT_FAMILY_INC = "b1000000-0000-0000-0000-000000000004";
    public static final String CAT_OTHER_INC  = "b1000000-0000-0000-0000-000000000005";

    // Wallets
    public static final String WALLET_CASH    = "w1000000-0000-0000-0000-000000000001";
    public static final String WALLET_VCB     = "w1000000-0000-0000-0000-000000000002";
    public static final String WALLET_MOMO    = "w1000000-0000-0000-0000-000000000003";

    // =========================================================================

    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract WalletDao walletDao();
    public abstract BudgetDao budgetDao();
    public abstract DebtLoanDao debtLoanDao();
    public abstract GoalDao goalDao();

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(4);

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "money_tracker_database")
                            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                            .fallbackToDestructiveMigration()
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // =========================================================================
    //  MIGRATIONS
    // =========================================================================

    // 7→8: thêm spentAmount vào budgets
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE budgets ADD COLUMN spentAmount INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    // 8→9: chỉ xóa data cũ, KHÔNG seed
    //       (các cột mới chưa được ALTER TABLE ở bước này)
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DELETE FROM categories");
            database.execSQL("DELETE FROM wallets");
        }
    };

    // 9→10: ALTER TABLE thêm cột mới → rồi seed
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // ── Categories: thêm color_hex, type, is_system ──────────
            try { database.execSQL("ALTER TABLE categories ADD COLUMN color_hex TEXT"); }
            catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE categories ADD COLUMN type TEXT"); }
            catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE categories ADD COLUMN is_system INTEGER NOT NULL DEFAULT 0"); }
            catch (Exception ignored) {}

            // ── Wallets: thêm balanceVnd, color, type, includedInTotal ─
            // (schema cũ có thể chỉ có id, name, icon)
            try { database.execSQL("ALTER TABLE wallets ADD COLUMN balanceVnd INTEGER NOT NULL DEFAULT 0"); }
            catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE wallets ADD COLUMN color INTEGER NOT NULL DEFAULT 0"); }
            catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE wallets ADD COLUMN type TEXT"); }
            catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE wallets ADD COLUMN includedInTotal INTEGER NOT NULL DEFAULT 1"); }
            catch (Exception ignored) {}

            // Xóa data rác còn sót
            database.execSQL("DELETE FROM categories");
            database.execSQL("DELETE FROM wallets");

            // Seed với UUID chuẩn — tất cả cột đã tồn tại
            seedCategories(database);
            seedWallets(database);
        }
    };

    // =========================================================================
    //  CALLBACK: cài app lần đầu — Room tạo table từ Entity nên đủ cột sẵn
    // =========================================================================

    private static final RoomDatabase.Callback sRoomDatabaseCallback =
            new RoomDatabase.Callback() {
                @Override
                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                    super.onCreate(db);
                    databaseWriteExecutor.execute(() -> {
                        seedCategories(db);
                        seedWallets(db);
                    });
                }
            };

    // =========================================================================
    //  SEED CATEGORIES
    //  Columns từ Category.java: id, name, icon, color_hex, type, is_system
    // =========================================================================

    private static void seedCategories(@NonNull SupportSQLiteDatabase db) {
        // Chi tiêu (expense)
        insertCat(db, CAT_FOOD,       "Ăn uống",       "🍜", "#A855F7", "expense");
        insertCat(db, CAT_TRANSPORT,  "Di chuyển",     "🛵", "#FFA500", "expense");
        insertCat(db, CAT_SHOPPING,   "Mua sắm",       "🛒", "#3B82F6", "expense");
        insertCat(db, CAT_FUN,        "Giải trí",      "🎬", "#6B7280", "expense");
        insertCat(db, CAT_HEALTH,     "Sức khỏe",      "💊", "#EF4444", "expense");
        insertCat(db, CAT_BILLS_EXP,  "Hóa đơn",       "⚡", "#F59E0B", "expense");
        insertCat(db, CAT_FAMILY_EXP, "Gia đình",      "❤️", "#EC4899", "expense");

        // Thu nhập (income)
        insertCat(db, CAT_SALARY,     "Lương",         "💰", "#22C55E", "income");
        insertCat(db, CAT_BONUS,      "Thưởng",        "🎁", "#F59E0B", "income");
        insertCat(db, CAT_BILLS_INC,  "Hóa đơn",       "⚡", "#3B82F6", "income");
        insertCat(db, CAT_FAMILY_INC, "Gia đình",      "❤️", "#EC4899", "income");
        insertCat(db, CAT_OTHER_INC,  "Thu nhập khác", "💵", "#6B7280", "income");
    }

    private static void insertCat(@NonNull SupportSQLiteDatabase db,
                                  String id, String name, String icon, String color, String type) {
        db.execSQL(
                "INSERT OR IGNORE INTO categories " +
                        "(id, name, icon, color_hex, type, is_system) VALUES (?, ?, ?, ?, ?, 1)",
                new Object[]{id, name, icon, color, type}
        );
    }

    // =========================================================================
    //  SEED WALLETS
    //  Columns từ Wallet.java:
    //    id, name, balanceVnd, icon, color (ARGB int), type (enum name), includedInTotal
    //
    //  Lưu ý: Room lưu enum Wallet.Type bằng tên string: "CASH", "BANK", "EWALLET"
    //  Khi sync lên Neon, TransactionRepository cần convert sang: "cash","bank","e_wallet"
    // =========================================================================

    private static void seedWallets(@NonNull SupportSQLiteDatabase db) {
        insertWallet(db,
                WALLET_CASH, "Tiền mặt",    5_000_000L,   "💵",
                Color.parseColor("#735BF2"), "CASH"        // enum name, khớp Wallet.Type.CASH
        );
        insertWallet(db,
                WALLET_VCB,  "Vietcombank", 110_000_000L, "🏦",
                Color.parseColor("#0EA5E9"), "BANK"        // enum name, khớp Wallet.Type.BANK
        );
        insertWallet(db,
                WALLET_MOMO, "MoMo",        13_450_000L,  "💜",
                Color.parseColor("#D946EF"), "EWALLET"     // enum name, khớp Wallet.Type.EWALLET
        );
    }

    private static void insertWallet(@NonNull SupportSQLiteDatabase db,
                                     String id, String name, long balanceVnd, String icon, int color, String type) {
        db.execSQL(
                "INSERT OR IGNORE INTO wallets " +
                        "(id, name, balanceVnd, icon, color, type, includedInTotal) VALUES (?, ?, ?, ?, ?, ?, 1)",
                new Object[]{id, name, balanceVnd, icon, color, type}
        );
    }
}