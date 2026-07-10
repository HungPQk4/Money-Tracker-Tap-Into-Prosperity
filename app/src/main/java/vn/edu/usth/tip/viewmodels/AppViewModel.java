package vn.edu.usth.tip.viewmodels;

import android.app.Application;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.util.concurrent.TimeUnit;

import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.Budget;
import vn.edu.usth.tip.workers.TransactionSyncWorker;
import vn.edu.usth.tip.models.BudgetDao;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.CategoryDao;
import vn.edu.usth.tip.models.Goal;
import vn.edu.usth.tip.models.GoalDao;
import vn.edu.usth.tip.models.DebtLoan;
import vn.edu.usth.tip.models.DebtLoanDao;
import vn.edu.usth.tip.models.TransactionDao;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.models.WalletDao;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import vn.edu.usth.tip.repositories.BudgetsRepository;
import vn.edu.usth.tip.widget.BalanceWidgetProvider;
import vn.edu.usth.tip.repositories.CategoriesRepository;
import vn.edu.usth.tip.repositories.DebtsRepository;
import vn.edu.usth.tip.repositories.GoalsRepository;
import vn.edu.usth.tip.repositories.TransactionRepository;
import vn.edu.usth.tip.repositories.WalletsRepository;
import vn.edu.usth.tip.utils.NetworkUtils;
import vn.edu.usth.tip.utils.TokenManager;

/**
 * ViewModel chia sẻ giữa các Fragment (Financial Engine).
 */
public class AppViewModel extends AndroidViewModel {

    private final TransactionDao transactionDao;
    private final CategoryDao categoryDao;
    private final WalletDao walletDao;
    private final GoalDao goalDao;
    private final BudgetDao budgetDao;
    private final DebtLoanDao debtLoanDao;
    private final TransactionRepository transactionRepository;
    private final WalletsRepository walletsRepository;
    private final CategoriesRepository categoriesRepository;
    private final BudgetsRepository budgetsRepository;
    private final GoalsRepository goalsRepository;
    private final DebtsRepository debtsRepository;

    private final LiveData<List<Transaction>> transactionsLiveData;
    private final LiveData<List<Category>> categoriesLiveData;
    private final LiveData<List<Wallet>> walletsDbLiveData;
    private final LiveData<List<Budget>> budgetsLiveData;
    private final LiveData<List<Goal>> goalsLiveData;

    private final LiveData<List<DebtLoan>> debtsLiveData;
    private final LiveData<Long> totalIOweLiveData;
    private final LiveData<Long> totalOwedToMeLiveData;

    // ── FINANCIAL ENGINE OUTPUTS ────────────────────────────────────
    public static class EngineState {
        public List<Wallet> wallets;
        public long totalAssets; // Tổng giá trị các ví (khớp với Wallet Management)
        public long netWorth; // Tổng tài sản - Nợ + Cho vay
        public long mIncome;
        public long mExpense;
        public long mTransfer;
    }

    /** Budget kèm số tiền đã chi được tính bởi Engine */
    public static class BudgetWithSpent {
        public Budget budget;
        public long spentAmount; // Tổng chi theo categoryName trong kỳ

        public BudgetWithSpent(Budget b, long s) {
            budget = b;
            spentAmount = s;
        }
    }

    private final MediatorLiveData<EngineState> engineStateLiveData = new MediatorLiveData<>();
    private final MediatorLiveData<List<BudgetWithSpent>> budgetStateLiveData = new MediatorLiveData<>();

    // Dùng để truyền dữ liệu giao dịch cần sửa sang Edit Mode
    private Transaction editingTransaction = null;

    // Dùng để quy định tab mặc định (Thu/Chi) khi mở form mới
    private Transaction.Type defaultNewTransactionType = Transaction.Type.EXPENSE;

    private final MutableLiveData<Boolean> isSyncingTransactions = new MutableLiveData<>(false);
    public final MutableLiveData<String> deleteCategoryError = new MutableLiveData<>();

    public LiveData<Boolean> getIsSyncingTransactions() {
        return isSyncingTransactions;
    }

    public AppViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        transactionDao = db.transactionDao();
        categoryDao = db.categoryDao();
        walletDao = db.walletDao();
        budgetDao = db.budgetDao();
        debtLoanDao = db.debtLoanDao();
        goalDao = db.goalDao();
        transactionRepository = new TransactionRepository(application);
        walletsRepository = new WalletsRepository(application);
        categoriesRepository = new CategoriesRepository(application);
        budgetsRepository = new BudgetsRepository(application);
        goalsRepository = new GoalsRepository(application);
        debtsRepository = new DebtsRepository(application);

        String currentUserId = TokenManager.getOrCreate(application).getUserId();
        transactionsLiveData = transactionDao.getAllTransactions(currentUserId);
        categoriesLiveData = (currentUserId != null)
                ? categoryDao.getCategoriesForUser(currentUserId)
                : categoryDao.getAllCategories();
        walletsDbLiveData = walletDao.getAllWallets(currentUserId);
        budgetsLiveData = budgetDao.getAllBudgets(currentUserId);
        goalsLiveData = goalDao.getAllGoalsSorted(currentUserId);

        debtsLiveData = debtLoanDao.getAllSortedByDueDate(currentUserId);
        totalIOweLiveData = debtLoanDao.getTotalIOwe(currentUserId);
        totalOwedToMeLiveData = debtLoanDao.getTotalOwedToMe(currentUserId);

        // Financial Engine: lắng nghe transactions + wallets + debts + loans
        engineStateLiveData.addSource(transactionsLiveData, v -> calculateEngine());
        engineStateLiveData.addSource(walletsDbLiveData, v -> calculateEngine());
        engineStateLiveData.addSource(totalIOweLiveData, v -> calculateEngine());
        engineStateLiveData.addSource(totalOwedToMeLiveData, v -> calculateEngine());

        // Budget Engine: lắng nghe transactions + budgets
        budgetStateLiveData.addSource(transactionsLiveData, v -> calculateBudgets());
        budgetStateLiveData.addSource(budgetsLiveData, v -> calculateBudgets());

        // Tự động khôi phục danh mục nếu bị trống (Self-healing)
        categoriesLiveData.observeForever(categories -> {
            initializeDefaultCategories();
        });

        // Dọn dẹp duplicate categories còn sót trong DB trước khi sync
        cleanupDuplicateCategories();

        // Sync dữ liệu từ server ngay khi khởi động
        syncAllData();
    }

    private void initializeDefaultCategories() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Category> current = categoryDao.getAllCategoriesSync();
            boolean hasAddBtn = false;
            if (current != null) {
                for (Category c : current) {
                    if (c.isAddButton()) {
                        hasAddBtn = true;
                        break;
                    }
                }
            }
            if (!hasAddBtn) {
                // Chỉ đảm bảo nút "Thêm" cục bộ luôn tồn tại
                categoryDao.insert(new Category(UUID.randomUUID().toString(), "Thêm", "+", "#DFE6E9", "expense", true, true));
            }
        });
    }

    // ── Financial Engine ────────────────────────────────────────────
    private void calculateEngine() {
        List<Transaction> txs = transactionsLiveData.getValue();
        List<Wallet> ws = walletsDbLiveData.getValue();
        if (txs == null || ws == null)
            return;

        long totalAssets = 0, mIncome = 0, mExpense = 0, mTransfer = 0;

        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long startOfMonth = c.getTimeInMillis();

        List<Wallet> calculatedWallets = new ArrayList<>();
        for (Wallet w : ws) {
            // KHÔNG cộng trừ giao dịch thủ công ở đây để khớp với Wallet Management (vốn đã
            // được đồng bộ từ API/Room)
            Wallet dw = new Wallet(w.getId(), w.getName(), w.getBalanceVnd(),
                    w.getIcon(), w.getColor(), w.getType(), w.isIncludedInTotal());
            calculatedWallets.add(dw);
            if (dw.isIncludedInTotal())
                totalAssets += dw.getBalanceVnd();
        }

        // Lấy dữ liệu nợ/cho vay để tính Net Worth
        Long iOwe = totalIOweLiveData.getValue();
        Long owedToMe = totalOwedToMeLiveData.getValue();
        long totalDebts = (iOwe != null) ? iOwe : 0;
        long totalLoans = (owedToMe != null) ? owedToMe : 0;

        // Net Worth = Tài sản - Nợ + Cho vay
        long netWorth = totalAssets - totalDebts + totalLoans;

        for (Transaction t : txs) {
            if (t.getTimestampMs() >= startOfMonth) {
                if (t.getType() == Transaction.Type.INCOME)
                    mIncome += t.getAmountVnd();
                else if (t.getType() == Transaction.Type.EXPENSE)
                    mExpense += t.getAmountVnd();
                else if (t.getType() == Transaction.Type.TRANSFER)
                    mTransfer += t.getAmountVnd();
            }
        }

        EngineState state = new EngineState();
        state.wallets = calculatedWallets;
        state.totalAssets = totalAssets;
        state.netWorth = netWorth;
        state.mIncome = mIncome;
        state.mExpense = mExpense;
        state.mTransfer = mTransfer;
        engineStateLiveData.postValue(state);
    }

    // ── Budget Engine ───────────────────────────────────────────────
    private void calculateBudgets() {
        List<Budget> budgets = budgetsLiveData.getValue();
        // budgets == null: query chưa được thực thi, chưa thể tính → đợi
        if (budgets == null) return;

        // budgets rỗng: user chưa có ngân sách nào → emit empty ngay, không cần txs
        if (budgets.isEmpty()) {
            budgetStateLiveData.postValue(new ArrayList<>());
            return;
        }

        List<Transaction> txs = transactionsLiveData.getValue();
        List<BudgetWithSpent> result = new ArrayList<>();
        for (Budget budget : budgets) {
            long spent = budget.getSpentAmount();
            // Cộng thêm giao dịch CHƯA sync nếu txs đã được deliver
            if (txs != null) {
                for (Transaction t : txs) {
                    if (t.isSynced()) continue;
                    if (t.getType() != Transaction.Type.EXPENSE) continue;
                    if (t.getTimestampMs() < budget.getPeriodStartMs()) continue;
                    if (t.getTimestampMs() > budget.getPeriodEndMs()) continue;
                    String cat = t.getCategory();
                    if (cat != null && cat.equalsIgnoreCase(budget.getCategoryName())) {
                        spent += t.getAmountVnd();
                    }
                }
            }
            result.add(new BudgetWithSpent(budget, spent));
        }
        budgetStateLiveData.postValue(result);
    }

    // ── Public LiveData ─────────────────────────────────────────────
    public LiveData<EngineState> getEngineState() {
        return engineStateLiveData;
    }

    public LiveData<List<BudgetWithSpent>> getBudgetState() {
        return budgetStateLiveData;
    }

    public LiveData<List<Transaction>> getTransactions() {
        return transactionsLiveData;
    }

    public LiveData<List<Transaction>> getTransactionsBetween(long fromMs, long toMs) {
        String userId = TokenManager.getOrCreate(getApplication()).getUserId();
        return transactionDao.getTransactionsBetween(fromMs, toMs, userId);
    }

    public LiveData<List<Category>> getCategories() {
        return categoriesLiveData;
    }

    public LiveData<List<DebtLoan>> getDebts() {
        return debtsLiveData;
    }

    public LiveData<List<Goal>> getGoals() {
        return goalsLiveData;
    }

    public LiveData<Long> getTotalIOwe() {
        return totalIOweLiveData;
    }

    public LiveData<Long> getTotalOwedToMe() {
        return totalOwedToMeLiveData;
    }

    public String formatCurrency(long amount) {
        return vn.edu.usth.tip.utils.MoneyFormat.formatShort(amount);
    }

    public void setEditingTransaction(Transaction tx) {
        this.editingTransaction = tx;
    }

    public Transaction getEditingTransaction() {
        return editingTransaction;
    }

    public void clearEditingTransaction() {
        this.editingTransaction = null;
    }

    public Transaction.Type getDefaultNewTransactionType() {
        return defaultNewTransactionType;
    }

    public void setDefaultNewTransactionType(Transaction.Type t) {
        this.defaultNewTransactionType = t;
    }

    // ── DAO Operations ──────────────────────────────────────────────
    public void addCategory(Category category) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Chặn trùng tên — không cho phép 2 danh mục cùng tên tồn tại trong DB
            Category existing = categoryDao.findByNameNoCase(category.getName());
            if (existing != null && !existing.isAddButton()) return;
            categoryDao.insert(category);
            categoriesRepository.addOnline(category);
        });
    }

    public void deleteCategory(Category category) {
        if (category.isSystem() || category.getUserId() == null) return;
        if (!NetworkUtils.isConnected(getApplication())) {
            deleteCategoryError.postValue("Cần kết nối internet để xóa danh mục");
            return;
        }
        categoriesRepository.deleteOnline(category.getId(), new CategoriesRepository.DeleteCallback() {
            @Override public void onSuccess() {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    transactionDao.reassignCategory(category.getName(), "Khác");
                    categoryDao.delete(category);
                });
            }
            @Override public void onError(String msg) {
                deleteCategoryError.postValue("Xóa thất bại: " + msg);
            }
        });
    }

    private void cleanupDuplicateCategories() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Category> all = categoryDao.getAllCategoriesSync();
            if (all == null) return;
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (Category c : all) {
                if (c.isAddButton()) continue;
                String key = (c.getName() != null ? c.getName().trim().toLowerCase() : c.getId())
                        + "|" + (c.getType() != null ? c.getType().trim().toLowerCase() : "expense");
                if (!seen.add(key)) {
                    categoryDao.deleteById(c.getId());
                }
            }
        });
    }

    public void addTransaction(Transaction tx) {
        tx.setUpdatedAtMs(System.currentTimeMillis());
        tx.setClientUpdatedAtMs(System.currentTimeMillis()); // LWW edit-time
        tx.setSynced(false);
        tx.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.insert(tx);
            enqueueSync();
            BalanceWidgetProvider.sendUpdateBroadcast(getApplication());
        });
    }

    public void updateTransaction(Transaction tx) {
        tx.setUpdatedAtMs(System.currentTimeMillis());
        tx.setClientUpdatedAtMs(System.currentTimeMillis()); // LWW edit-time
        tx.setSynced(false);
        if (tx.getUserId() == null) tx.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.insert(tx); // INSERT OR REPLACE
            enqueueSync();
            BalanceWidgetProvider.sendUpdateBroadcast(getApplication());
        });
    }

    public void deleteTransaction(Transaction tx) {
        tx.setDeleted(true);
        tx.setSynced(false);
        tx.setUpdatedAtMs(System.currentTimeMillis());
        tx.setClientUpdatedAtMs(System.currentTimeMillis()); // LWW edit-time
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.insert(tx); // soft delete — push lên server trước khi hard delete
            enqueueSync();
            BalanceWidgetProvider.sendUpdateBroadcast(getApplication());
        });
    }

    private void enqueueSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest syncWork = new OneTimeWorkRequest.Builder(TransactionSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "TxSync",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                syncWork
        );
    }

    public void addWallet(Wallet w) {
        w.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            walletDao.insert(w);
            walletsRepository.addOnline(w);
            BalanceWidgetProvider.sendUpdateBroadcast(getApplication());
        });
    }

    public void updateWallet(Wallet w) {
        if (w.getUserId() == null) w.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            walletDao.update(w);
            walletsRepository.updateOnline(w);
            BalanceWidgetProvider.sendUpdateBroadcast(getApplication());
        });
    }

    public void deleteWallet(Wallet w) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            walletDao.delete(w);
            walletsRepository.deleteOnline(w.getId());
            BalanceWidgetProvider.sendUpdateBroadcast(getApplication());
        });
    }

    // Budget/Goal/Debt theo offline-first: ghi Room (isSynced=false) rồi để TransactionSyncWorker
    // đẩy lên server (push create/update/delete) — an toàn khi offline, không mất dữ liệu.

    public void addBudget(Budget b) {
        b.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        b.setSynced(false);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            budgetDao.insert(b);
            enqueueSync();
        });
    }

    public void updateBudget(Budget b) {
        if (b.getUserId() == null) b.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        b.setSynced(false);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            budgetDao.insert(b); // INSERT OR REPLACE
            enqueueSync();
        });
    }

    public void deleteBudget(Budget b) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            budgetDao.softDeleteById(b.getId()); // tombstone → worker đẩy lệnh xoá lên server
            enqueueSync();
        });
    }

    public void addDebtLoan(DebtLoan debtLoan) {
        debtLoan.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        debtLoan.setSynced(false);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            debtLoanDao.insert(debtLoan);
            enqueueSync();
        });
    }

    public void deleteDebtLoan(DebtLoan debtLoan) {
        // Debt chưa có soft-delete: xoá cứng cục bộ + gửi lệnh xoá online (xoá offline là hạn chế đã biết).
        AppDatabase.databaseWriteExecutor.execute(() -> {
            debtLoanDao.delete(debtLoan);
            debtsRepository.deleteOnline(debtLoan.getId());
        });
    }

    public void addGoal(Goal goal) {
        goal.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        goal.setSynced(false);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            goalDao.insert(goal);
            enqueueSync();
        });
    }

    public void updateGoal(Goal goal) {
        if (goal.getUserId() == null) goal.setUserId(TokenManager.getOrCreate(getApplication()).getUserId());
        goal.setSynced(false);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            goalDao.insert(goal); // INSERT OR REPLACE
            enqueueSync();
        });
    }

    public void deleteGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            goalDao.softDeleteById(goal.getId()); // tombstone → worker đẩy lệnh xoá lên server
            enqueueSync();
        });
    }

    public List<Transaction> getCurrentList() {
        return transactionsLiveData.getValue() != null ? transactionsLiveData.getValue() : new ArrayList<>();
    }

    public void syncBudgets(BudgetsRepository.SyncCallback callback) {
        budgetsRepository.sync(callback);
    }

    public void syncTransactions(TransactionRepository.SyncCallback callback) {
        isSyncingTransactions.postValue(true);
        transactionRepository.syncTransactions(new TransactionRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                isSyncingTransactions.postValue(false);
                callback.onSuccess();
            }

            @Override
            public void onError(String msg) {
                isSyncingTransactions.postValue(false);
                callback.onError(msg);
            }
        });
    }

    public void syncAllData() {
        // Đồng bộ song song tất cả các mục
        transactionRepository.syncTransactions(new TransactionRepository.SyncCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String msg) {
            }
        });
        walletsRepository.sync(new WalletsRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                BalanceWidgetProvider.sendUpdateBroadcast(getApplication());
            }

            @Override
            public void onError(String msg) {
            }
        });
        categoriesRepository.sync(new CategoriesRepository.SyncCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String msg) {
            }
        });
        goalsRepository.sync(new GoalsRepository.SyncCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String msg) {
            }
        });
        debtsRepository.sync(new DebtsRepository.SyncCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String msg) {
            }
        });
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}