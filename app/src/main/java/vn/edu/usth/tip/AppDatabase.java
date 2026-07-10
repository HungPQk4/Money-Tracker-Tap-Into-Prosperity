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
        version = 21,
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

    // Expense catch-all
    public static final String CAT_OTHER_EXP  = "a1000000-0000-0000-0000-000000000008";

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
                            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
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

    // 16→17: thêm user_id vào tất cả bảng còn lại để cô lập dữ liệu theo từng tài khoản
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN user_id TEXT");
            database.execSQL("ALTER TABLE wallets ADD COLUMN user_id TEXT");
            database.execSQL("ALTER TABLE budgets ADD COLUMN user_id TEXT");
            database.execSQL("ALTER TABLE goals ADD COLUMN user_id TEXT");
            database.execSQL("ALTER TABLE debt_loans ADD COLUMN user_id TEXT");
        }
    };

    // 17→18: dọn các dòng mồ côi (user_id IS NULL) còn sót sau migration 16→17.
    // Các dòng này là dữ liệu cũ tạo trước khi có user_id — không thể gán cho user nào
    // một cách an toàn (có thể thuộc bất kỳ tài khoản nào từng đăng nhập trên máy).
    // Xóa đi rồi để sync kéo lại từ server với user_id đúng.
    // KHÔNG xóa NULL trong 'categories' — system categories hợp lệ có user_id IS NULL.
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DELETE FROM transactions WHERE user_id IS NULL");
            database.execSQL("DELETE FROM wallets WHERE user_id IS NULL");
            database.execSQL("DELETE FROM budgets WHERE user_id IS NULL");
            database.execSQL("DELETE FROM goals WHERE user_id IS NULL");
            database.execSQL("DELETE FROM debt_loans WHERE user_id IS NULL");
        }
    };

    // 18→19: thêm clientUpdatedAtMs (LWW edit-time — biến thể hai-mốc; updatedAtMs = version server)
    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN clientUpdatedAtMs INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 19→20: thêm toWalletName (ví đích cho giao dịch chuyển khoản — sửa lỗi transfer mất tiền)
    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN toWalletName TEXT");
        }
    };

    // 20→21: offline-first cho budget & goal — thêm isSynced (dòng cũ = đã sync = 1, không bị re-push)
    // và isDeleted (soft-delete/tombstone) để worker đẩy create/update/delete + chống zombie-resurrection.
    static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE budgets ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE budgets ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE goals ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE goals ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 15→16: thêm user_id vào categories để phân biệt system vs user categories
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE categories ADD COLUMN user_id TEXT");
        }
    };

    // 14→15: cập nhật icon đúng cho "Hóa đơn" và "Gia đình"
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("UPDATE categories SET icon = '🧾' WHERE name = 'Hóa đơn'");
            database.execSQL("UPDATE categories SET icon = '👨‍👩‍👧' WHERE name = 'Gia đình'");
        }
    };

    // 13→14: thêm isRecurring + recurInterval vào transactions cho tính năng giao dịch định kỳ
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE transactions ADD COLUMN recurInterval TEXT");
        }
    };

    // 12→13: thêm updatedAtMs + isDeleted vào transactions cho LWW conflict resolution
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // db.execSQL() chỉ chấp nhận 1 câu SQL mỗi lần — KHÔNG gộp với dấu ";"
            database.execSQL("ALTER TABLE transactions ADD COLUMN updatedAtMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE transactions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 11→12: thêm createdMs vào goals (DEFAULT 0 = data cũ, GoalAdvisor fallback 90 ngày)
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE goals ADD COLUMN createdMs INTEGER NOT NULL DEFAULT 0"
            );
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
        insertCat(db, CAT_BILLS_EXP,  "Hóa đơn",       "🧾", "#F59E0B", "expense");
        insertCat(db, CAT_FAMILY_EXP, "Gia đình",      "👨‍👩‍👧", "#EC4899", "expense");
        insertCat(db, CAT_OTHER_EXP,  "Khác",          "📦", "#6B7280", "expense");

        // Thu nhập (income)
        insertCat(db, CAT_SALARY,     "Lương",         "💰", "#22C55E", "income");
        insertCat(db, CAT_BONUS,      "Thưởng",        "🎁", "#F59E0B", "income");
        insertCat(db, CAT_BILLS_INC,  "Hóa đơn",       "🧾", "#3B82F6", "income");
        insertCat(db, CAT_FAMILY_INC, "Gia đình",      "👨‍👩‍👧", "#EC4899", "income");
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