package vn.edu.usth.tip.viewmodels;

import android.app.Application;

import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.models.Budget;
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

/**
 * ViewModel chia sẻ giữa các Fragment (Financial Engine).
 */
public class AppViewModel extends AndroidViewModel {

    private final TransactionDao transactionDao;
    private final CategoryDao    categoryDao;
    private final WalletDao      walletDao;
    private final BudgetDao      budgetDao;
    private final DebtLoanDao    debtLoanDao;
    private final GoalDao        goalDao;

    private final LiveData<List<Transaction>> transactionsLiveData;
    private final LiveData<List<Category>>    categoriesLiveData;
    private final LiveData<List<Wallet>>      walletsDbLiveData;
    private final LiveData<List<Budget>>      budgetsLiveData;
    private final LiveData<List<Goal>>        goalsLiveData;

    private final LiveData<List<DebtLoan>>    debtsLiveData;
    private final LiveData<Long>              totalIOweLiveData;
    private final LiveData<Long>              totalOwedToMeLiveData;

    // ── FINANCIAL ENGINE OUTPUTS ────────────────────────────────────
    public static class EngineState {
        public List<Wallet> wallets;
        public long netWorth;
        public long mIncome;
        public long mExpense;
        public long mTransfer;
    }

    /** Budget kèm số tiền đã chi được tính bởi Engine */
    public static class BudgetWithSpent {
        public Budget budget;
        public long   spentAmount;   // Tổng chi theo categoryName trong kỳ
        public BudgetWithSpent(Budget b, long s) { budget = b; spentAmount = s; }
    }

    private final MediatorLiveData<EngineState>         engineStateLiveData  = new MediatorLiveData<>();
    private final MediatorLiveData<List<BudgetWithSpent>> budgetStateLiveData = new MediatorLiveData<>();

    // Dùng để truyền dữ liệu giao dịch cần sửa sang Edit Mode
    private Transaction editingTransaction = null;

    // Dùng để quy định tab mặc định (Thu/Chi) khi mở form mới
    private Transaction.Type defaultNewTransactionType = Transaction.Type.EXPENSE;

    public AppViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        transactionDao = db.transactionDao();
        categoryDao    = db.categoryDao();
        walletDao      = db.walletDao();
        budgetDao      = db.budgetDao();
        debtLoanDao    = db.debtLoanDao();
        goalDao        = db.goalDao();

        transactionsLiveData = transactionDao.getAllTransactions();
        categoriesLiveData   = categoryDao.getAllCategories();
        walletsDbLiveData    = walletDao.getAllWallets();
        budgetsLiveData      = budgetDao.getAllBudgets();
        goalsLiveData        = goalDao.getAllGoalsSorted();

        debtsLiveData         = debtLoanDao.getAllSortedByDueDate();
        totalIOweLiveData     = debtLoanDao.getTotalIOwe();
        totalOwedToMeLiveData = debtLoanDao.getTotalOwedToMe();

        // Financial Engine: lắng nghe transactions + wallets
        engineStateLiveData.addSource(transactionsLiveData, v -> calculateEngine());
        engineStateLiveData.addSource(walletsDbLiveData,    v -> calculateEngine());

        // Budget Engine: lắng nghe transactions + budgets
        budgetStateLiveData.addSource(transactionsLiveData, v -> calculateBudgets());
        budgetStateLiveData.addSource(budgetsLiveData,      v -> calculateBudgets());
    }

    // ── Financial Engine ────────────────────────────────────────────
    private void calculateEngine() {
        List<Transaction> txs = transactionsLiveData.getValue();
        List<Wallet>      ws  = walletsDbLiveData.getValue();
        if (txs == null || ws == null) return;

        long netWorth = 0, mIncome = 0, mExpense = 0, mTransfer = 0;

        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY,  0);
        c.set(Calendar.MINUTE,       0);
        c.set(Calendar.SECOND,       0);
        long startOfMonth = c.getTimeInMillis();

        List<Wallet> calculatedWallets = new ArrayList<>();
        for (Wallet w : ws) {
            Wallet dw = new Wallet(w.getId(), w.getName(), w.getBalanceVnd(),
                    w.getIcon(), w.getColor(), w.getType(), w.isIncludedInTotal());
            for (Transaction t : txs) {
                if (t.getWalletName() != null && t.getWalletName().equals(w.getName())) {
                    if      (t.getType() == Transaction.Type.INCOME)   dw.setBalanceVnd(dw.getBalanceVnd() + t.getAmountVnd());
                    else if (t.getType() == Transaction.Type.EXPENSE)  dw.setBalanceVnd(dw.getBalanceVnd() - t.getAmountVnd());
                    else if (t.getType() == Transaction.Type.TRANSFER) dw.setBalanceVnd(dw.getBalanceVnd() - t.getAmountVnd());
                }
            }
            calculatedWallets.add(dw);
            if (dw.isIncludedInTotal()) netWorth += dw.getBalanceVnd();
        }

        for (Transaction t : txs) {
            if (t.getTimestampMs() >= startOfMonth) {
                if      (t.getType() == Transaction.Type.INCOME)   mIncome   += t.getAmountVnd();
                else if (t.getType() == Transaction.Type.EXPENSE)  mExpense  += t.getAmountVnd();
                else if (t.getType() == Transaction.Type.TRANSFER) mTransfer += t.getAmountVnd();
            }
        }

        EngineState state = new EngineState();
        state.wallets   = calculatedWallets;
        state.netWorth  = netWorth;
        state.mIncome   = mIncome;
        state.mExpense  = mExpense;
        state.mTransfer = mTransfer;
        engineStateLiveData.postValue(state);
    }

    // ── Budget Engine ───────────────────────────────────────────────
    private void calculateBudgets() {
        List<Transaction>  txs     = transactionsLiveData.getValue();
        List<Budget>       budgets = budgetsLiveData.getValue();
        if (txs == null || budgets == null) return;

        List<BudgetWithSpent> result = new ArrayList<>();
        for (Budget budget : budgets) {
            long spent = 0;
            for (Transaction t : txs) {
                if (t.getType() != Transaction.Type.EXPENSE) continue;
                if (t.getTimestampMs() < budget.getPeriodStartMs()) continue;
                if (t.getTimestampMs() > budget.getPeriodEndMs())   continue;
                String cat = t.getCategory();
                if (cat != null && cat.equalsIgnoreCase(budget.getCategoryName())) {
                    spent += t.getAmountVnd();
                }
            }
            result.add(new BudgetWithSpent(budget, spent));
        }
        budgetStateLiveData.postValue(result);
    }

    // ── Public LiveData ─────────────────────────────────────────────
    public LiveData<EngineState>             getEngineState()  { return engineStateLiveData;  }
    public LiveData<List<BudgetWithSpent>>   getBudgetState()  { return budgetStateLiveData;  }
    public LiveData<List<Transaction>>       getTransactions() { return transactionsLiveData; }
    public LiveData<List<Category>>          getCategories()   { return categoriesLiveData;   }
    public LiveData<List<DebtLoan>>          getDebts()        { return debtsLiveData;        }
    public LiveData<List<Goal>>              getGoals()        { return goalsLiveData;        }
    public LiveData<Long>                    getTotalIOwe()    { return totalIOweLiveData;    }
    public LiveData<Long>                    getTotalOwedToMe(){ return totalOwedToMeLiveData;}

    public String formatCurrency(long amount) {
        return "đ" + String.format("%,d", amount).replace(",", ".");
    }

    public void setEditingTransaction(Transaction tx) { this.editingTransaction = tx; }
    public Transaction getEditingTransaction()        { return editingTransaction; }
    public void clearEditingTransaction()             { this.editingTransaction = null; }

    public Transaction.Type getDefaultNewTransactionType()                         { return defaultNewTransactionType; }
    public void setDefaultNewTransactionType(Transaction.Type t)                   { this.defaultNewTransactionType = t; }

    // ── DAO Operations ──────────────────────────────────────────────
    public void addCategory(Category category) {
        AppDatabase.databaseWriteExecutor.execute(() -> categoryDao.insert(category));
    }

    public void addTransaction(Transaction tx) {
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.insert(tx));
    }

    public void updateTransaction(Transaction tx) {
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.update(tx));
    }

    public void deleteTransaction(Transaction tx) {
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.delete(tx));
    }

    public void addWallet(Wallet w) {
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.insert(w));
    }

    public void updateWallet(Wallet w) {
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.update(w));
    }

    public void deleteWallet(Wallet w) {
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.delete(w));
    }

    public void addBudget(Budget b) {
        AppDatabase.databaseWriteExecutor.execute(() -> budgetDao.insert(b));
    }

    public void deleteBudget(Budget b) {
        AppDatabase.databaseWriteExecutor.execute(() -> budgetDao.delete(b));
    }

    public void addDebtLoan(DebtLoan debtLoan) {
        AppDatabase.databaseWriteExecutor.execute(() -> debtLoanDao.insert(debtLoan));
    }
    
    public void deleteDebtLoan(DebtLoan debtLoan) {
        AppDatabase.databaseWriteExecutor.execute(() -> debtLoanDao.delete(debtLoan));
    }

    public void addGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> goalDao.insert(goal));
    }

    public void deleteGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> goalDao.delete(goal));
    }

    public List<Transaction> getCurrentList() {
        return transactionsLiveData.getValue() != null ? transactionsLiveData.getValue() : new ArrayList<>();
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
}
