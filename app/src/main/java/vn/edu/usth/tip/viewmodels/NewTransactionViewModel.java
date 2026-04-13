package vn.edu.usth.tip.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.UUID;

import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.Transaction;

public class NewTransactionViewModel extends ViewModel {

    // View State representing the UI at any given moment
    public static class UiState {
        public String amountStr = "0";
        public Transaction.Type selectedType = Transaction.Type.EXPENSE;
        public long timestampMs = System.currentTimeMillis();
        public String note = "";
        
        // For editing mode
        public Transaction editingTx = null;
    }

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(new UiState());
    
    // Using a SingleLiveEvent wrapper pattern conceptually by using simple String messages
    private final MutableLiveData<String> validationError = new MutableLiveData<>();
    private final MutableLiveData<Transaction> transactionToSave = new MutableLiveData<>();

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getValidationError() {
        return validationError;
    }

    public LiveData<Transaction> getTransactionToSave() {
        return transactionToSave;
    }

    public void initEditMode(Transaction tx) {
        UiState state = uiState.getValue();
        if (state != null && tx != null && state.editingTx == null) { // only run once
            state.editingTx = tx;
            state.amountStr = String.valueOf(tx.getAmountVnd());
            state.selectedType = tx.getType();
            state.timestampMs = tx.getTimestampMs();
            state.note = tx.getNote() != null ? tx.getNote() : "";
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

    public void validateAndSave(Category selectedCategory) {
        UiState state = uiState.getValue();
        if (state == null) return;

        long amount = 0;
        try {
            amount = Long.parseLong(state.amountStr);
        } catch (NumberFormatException ignored) {}

        if (amount <= 0) {
            validationError.setValue("Vui lòng nhập số tiền hợp lệ");
            validationError.setValue(null); // clear event
            return;
        }

        if (selectedCategory == null) {
            validationError.setValue("Vui lòng chọn danh mục");
            validationError.setValue(null);
            return;
        }

        Transaction tx;
        if (state.editingTx != null) {
            tx = new Transaction(
                    state.editingTx.getId(),
                    selectedCategory.getName(),
                    selectedCategory.getName(),
                    selectedCategory.getIcon(),
                    state.editingTx.getWalletName(),
                    amount,
                    state.selectedType,
                    state.timestampMs,
                    state.note
            );
        } else {
            tx = new Transaction(
                    UUID.randomUUID().toString(),
                    selectedCategory.getName(),
                    selectedCategory.getName(),
                    selectedCategory.getIcon(),
                    "Ví chính",
                    amount,
                    state.selectedType,
                    state.timestampMs,
                    state.note
            );
        }
        transactionToSave.setValue(tx);
    }
}
