package vn.edu.usth.tip.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.UUID;

import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.utils.Event;
import vn.edu.usth.tip.network.responses.AccountResponse;
import vn.edu.usth.tip.repositories.AccountRepository;

public class NewTransactionViewModel extends AndroidViewModel {

    // View State representing the UI at any given moment
    public static class UiState {
        public String amountStr = "0";
        public Transaction.Type selectedType = Transaction.Type.EXPENSE;
        public long timestampMs = System.currentTimeMillis();
        public String note = "";

        // For editing mode
        public Transaction editingTx = null;

        // Selected account (from-account for TRANSFER, main account for EXPENSE/INCOME)
        public String selectedAccountId = null;
        public String selectedAccountName = "Chọn ví...";

        // To-account (only used for TRANSFER)
        public String selectedToAccountId = null;
        public String selectedToAccountName = "Chọn ví...";

        // Selected category (only used for EXPENSE/INCOME)
        public String selectedCategoryId = null;
        public String selectedCategoryName = "";

        // Recurring
        public boolean isRecurring = false;
        public String recurInterval = null; // "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY"
    }

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(new UiState());
    private final MutableLiveData<Event<String>> validationError = new MutableLiveData<>();
    private final MutableLiveData<Event<Transaction>> transactionToSave = new MutableLiveData<>();
    private final MutableLiveData<Event<Transaction>> transactionToUpdate = new MutableLiveData<>();
    // Guard against rapid double-tap: set to true when a valid save event is fired,
    // reset to false only on validation failure so the user can correct and retry.
    private boolean isSaving = false;

    // Accounts list loaded from server
    private final MutableLiveData<List<AccountResponse>> accounts = new MutableLiveData<>();
    private final MutableLiveData<String> accountsError = new MutableLiveData<>();

    private final AccountRepository accountRepository;

    public NewTransactionViewModel(@NonNull Application application) {
        super(application);
        accountRepository = new AccountRepository(application);
    }

    public LiveData<UiState> getUiState() { return uiState; }
    public LiveData<Event<String>> getValidationError() { return validationError; }
    public LiveData<Event<Transaction>> getTransactionToSave() { return transactionToSave; }
    public LiveData<Event<Transaction>> getTransactionToUpdate() { return transactionToUpdate; }
    public LiveData<List<AccountResponse>> getAccounts() { return accounts; }
    public LiveData<String> getAccountsError() { return accountsError; }

    public void loadAccounts() {
        accountRepository.fetchAllAccounts(accounts, accountsError, null);
    }

    public void selectAccount(AccountResponse account) {
        UiState state = uiState.getValue();
        if (state != null) {
            state.selectedAccountId = account.getId();
            state.selectedAccountName = account.getName();
            uiState.setValue(state);
        }
    }

    public void selectToAccount(AccountResponse account) {
        UiState state = uiState.getValue();
        if (state != null) {
            state.selectedToAccountId = account.getId();
            state.selectedToAccountName = account.getName();
            uiState.setValue(state);
        }
    }

    public void selectCategory(Category category) {
        UiState state = uiState.getValue();
        if (state != null && category != null) {
            state.selectedCategoryId = category.getId();
            state.selectedCategoryName = category.getName();
            uiState.setValue(state);
        }
    }

    public void initEditMode(Transaction tx) {
        UiState state = uiState.getValue();
        if (state != null && tx != null && state.editingTx == null) {
            state.editingTx = tx;
            state.amountStr = String.valueOf(tx.getAmountVnd());
            state.selectedType = tx.getType();
            state.timestampMs = tx.getTimestampMs();
            state.note = tx.getNote() != null ? tx.getNote() : "";
            state.selectedAccountName = tx.getWalletName() != null ? tx.getWalletName() : "Chọn ví...";
            state.isRecurring = tx.isRecurring();
            state.recurInterval = tx.getRecurInterval();
            // Khôi phục ví đích khi edit TRANSFER: ưu tiên toWalletName, fallback parse title (dữ liệu cũ).
            if (tx.getType() == Transaction.Type.TRANSFER) {
                if (tx.getToWalletName() != null && !tx.getToWalletName().isEmpty()) {
                    state.selectedToAccountName = tx.getToWalletName();
                } else if (tx.getTitle() != null) {
                    int arrowIdx = tx.getTitle().indexOf('→');
                    if (arrowIdx >= 0 && arrowIdx + 1 < tx.getTitle().length()) {
                        state.selectedToAccountName = tx.getTitle().substring(arrowIdx + 1).trim();
                    }
                }
            }
            uiState.setValue(state);
        }
    }

    public void setType(Transaction.Type type) {
        UiState state = uiState.getValue();
        if (state != null && state.selectedType != type) {
            state.selectedType = type;
            uiState.setValue(state);
        }
    }

    public void appendNumpad(String digit) {
        UiState state = uiState.getValue();
        if (state != null) {
            if (state.amountStr.equals("0")) {
                state.amountStr = digit;
            } else if (state.amountStr.length() < 12) {
                state.amountStr += digit;
            }
            uiState.setValue(state);
        }
    }

    public void deleteNumpad() {
        UiState state = uiState.getValue();
        if (state != null) {
            if (state.amountStr.length() > 1) {
                state.amountStr = state.amountStr.substring(0, state.amountStr.length() - 1);
            } else {
                state.amountStr = "0";
            }
            uiState.setValue(state);
        }
    }

    public void clearNumpad() {
        UiState state = uiState.getValue();
        if (state != null) {
            state.amountStr = "0";
            uiState.setValue(state);
        }
    }

    public void updateNote(String newNote) {
        UiState state = uiState.getValue();
        if (state != null) {
            state.note = newNote;
            uiState.setValue(state);
        }
    }

    public void updateDate(long timestampMs) {
        UiState state = uiState.getValue();
        if (state != null) {
            state.timestampMs = timestampMs;
            uiState.setValue(state);
        }
    }

    public void updateRecurring(boolean recurring, String interval) {
        UiState state = uiState.getValue();
        if (state != null) {
            state.isRecurring = recurring;
            state.recurInterval = recurring ? interval : null;
            uiState.setValue(state);
        }
    }

    public void validateAndSave(Category selectedCategory) {
        if (isSaving) return;
        UiState state = uiState.getValue();
        if (state == null) return;

        long amount = 0;
        try {
            amount = Long.parseLong(state.amountStr);
        } catch (NumberFormatException ignored) {}

        if (amount <= 0) {
            validationError.setValue(new Event<>("Vui lòng nhập số tiền hợp lệ"));
            return;
        }

        if (state.selectedType == Transaction.Type.TRANSFER) {
            validateAndSaveTransfer(state, amount);
        } else {
            validateAndSaveNormal(state, selectedCategory, amount);
        }
    }

    private void validateAndSaveTransfer(UiState state, long amount) {
        if (isBlankAccount(state.selectedAccountName)) {
            validationError.setValue(new Event<>("Vui lòng chọn tài khoản nguồn"));
            return;
        }
        if (isBlankAccount(state.selectedToAccountName)) {
            validationError.setValue(new Event<>("Vui lòng chọn tài khoản đích"));
            return;
        }
        if (state.selectedAccountName.equals(state.selectedToAccountName)) {
            validationError.setValue(new Event<>("Tài khoản nguồn và đích không được trùng nhau"));
            return;
        }

        // Title mã hoá cả hai tài khoản để edit mode có thể khôi phục
        String title = state.selectedAccountName + " → " + state.selectedToAccountName;
        String category = "Chuyển khoản";
        String icon = "↔";

        isSaving = true;
        if (state.editingTx != null) {
            Transaction tx = state.editingTx;
            tx.setTitle(title);
            tx.setCategory(category);
            tx.setIcon(icon);
            tx.setWalletName(state.selectedAccountName);
            tx.setToWalletName(state.selectedToAccountName);
            tx.setAmountVnd(amount);
            tx.setType(Transaction.Type.TRANSFER);
            tx.setTimestampMs(state.timestampMs);
            tx.setNote(state.note);
            tx.setRecurring(state.isRecurring);
            tx.setRecurInterval(state.isRecurring ? state.recurInterval : null);
            if (state.selectedAccountId != null) tx.setAccountId(state.selectedAccountId);
            transactionToUpdate.setValue(new Event<>(tx));
        } else {
            Transaction tx = new Transaction(
                    UUID.randomUUID().toString(), title, category, icon,
                    state.selectedAccountName, amount,
                    Transaction.Type.TRANSFER, state.timestampMs, state.note
            );
            tx.setToWalletName(state.selectedToAccountName);
            tx.setRecurring(state.isRecurring);
            tx.setRecurInterval(state.isRecurring ? state.recurInterval : null);
            if (state.selectedAccountId != null) tx.setAccountId(state.selectedAccountId);
            transactionToSave.setValue(new Event<>(tx));
        }
    }

    private void validateAndSaveNormal(UiState state, Category selectedCategory, long amount) {
        if (selectedCategory == null) {
            validationError.setValue(new Event<>("Vui lòng chọn danh mục"));
            return;
        }
        if (isBlankAccount(state.selectedAccountName)) {
            validationError.setValue(new Event<>("Vui lòng chọn ví"));
            return;
        }

        String title = state.note.isEmpty() ? selectedCategory.getName() : state.note;

        isSaving = true;
        if (state.editingTx != null) {
            Transaction tx = state.editingTx;
            tx.setTitle(title);
            tx.setCategory(selectedCategory.getName());
            tx.setIcon(selectedCategory.getIcon());
            tx.setWalletName(state.selectedAccountName);
            tx.setAmountVnd(amount);
            tx.setType(state.selectedType);
            tx.setTimestampMs(state.timestampMs);
            tx.setNote(state.note);
            tx.setRecurring(state.isRecurring);
            tx.setRecurInterval(state.isRecurring ? state.recurInterval : null);
            if (state.selectedAccountId != null) tx.setAccountId(state.selectedAccountId);
            if (state.selectedCategoryId != null) tx.setCategoryId(state.selectedCategoryId);
            transactionToUpdate.setValue(new Event<>(tx));
        } else {
            Transaction tx = new Transaction(
                    UUID.randomUUID().toString(), title,
                    selectedCategory.getName(), selectedCategory.getIcon(),
                    state.selectedAccountName, amount,
                    state.selectedType, state.timestampMs, state.note
            );
            tx.setRecurring(state.isRecurring);
            tx.setRecurInterval(state.isRecurring ? state.recurInterval : null);
            if (state.selectedAccountId != null) tx.setAccountId(state.selectedAccountId);
            if (state.selectedCategoryId != null) tx.setCategoryId(state.selectedCategoryId);
            transactionToSave.setValue(new Event<>(tx));
        }
    }

    private boolean isBlankAccount(String name) {
        return name == null || name.equals("Chọn ví...");
    }
}