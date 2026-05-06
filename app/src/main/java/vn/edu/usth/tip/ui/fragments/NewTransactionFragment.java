package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.adapters.CategoryAdapter;
import vn.edu.usth.tip.network.responses.AccountResponse;
import vn.edu.usth.tip.viewmodels.AppViewModel;
import vn.edu.usth.tip.viewmodels.NewTransactionViewModel;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import vn.edu.usth.tip.R;

public class NewTransactionFragment extends Fragment {

    private AppViewModel appViewModel;
    private NewTransactionViewModel newTxViewModel;
    private CategoryAdapter categoryAdapter;
    private Transaction.Type currentCategoryType = null;

    // View references
    private TextView tvAmount;
    private CardView btnTypeExpense, btnTypeIncome, btnTypeTransfer;
    private TextView tvNotePreview, tvDatePreview, tvWalletPreview;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        newTxViewModel = new ViewModelProvider(this).get(NewTransactionViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_new_transaction, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind Views
        tvAmount = view.findViewById(R.id.tv_amount);
        btnTypeExpense = view.findViewById(R.id.btn_type_expense);
        btnTypeIncome  = view.findViewById(R.id.btn_type_income);
        btnTypeTransfer = view.findViewById(R.id.btn_type_transfer);
        tvNotePreview = view.findViewById(R.id.tv_note_preview);
        tvDatePreview = view.findViewById(R.id.tv_date_preview);
        tvWalletPreview = view.findViewById(R.id.tv_wallet_preview);

        // Header Back Button
        view.findViewById(R.id.btnBack).setOnClickListener(v -> {
            appViewModel.clearEditingTransaction();
            Navigation.findNavController(v).navigateUp();
        });

        // Initialize Edit Mode if navigating from Details
        Transaction editingTx = appViewModel.getEditingTransaction();
        if (editingTx != null) {
            newTxViewModel.initEditMode(editingTx);
        } else {
            // Check default selected type from Dashboard
            newTxViewModel.setType(appViewModel.getDefaultNewTransactionType());
        }

        // Setup Interaction Listeners
        btnTypeExpense.setOnClickListener(v -> newTxViewModel.setType(Transaction.Type.EXPENSE));
        btnTypeIncome.setOnClickListener(v  -> newTxViewModel.setType(Transaction.Type.INCOME));
        btnTypeTransfer.setOnClickListener(v -> newTxViewModel.setType(Transaction.Type.TRANSFER));

        setupNumpad(view);
        setupNoteAndDate(view);
        setupWalletPicker(view);

        // Setup Categories
        RecyclerView rvCategories = view.findViewById(R.id.rv_categories);
        rvCategories.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        appViewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            refreshCategories();
        });

        view.findViewById(R.id.btn_save_transaction).setOnClickListener(v -> {
            newTxViewModel.validateAndSave(
                    categoryAdapter != null ? categoryAdapter.getSelectedCategory() : null
            );
        });

        // Observe ViewModel State
        newTxViewModel.getUiState().observe(getViewLifecycleOwner(), this::renderUiState);

        // Observe Validation & Save Events
        newTxViewModel.getValidationError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });

        newTxViewModel.getTransactionToSave().observe(getViewLifecycleOwner(), tx -> {
            if (tx != null) {
                if (appViewModel.getEditingTransaction() != null) {
                    appViewModel.updateTransaction(tx);
                    Toast.makeText(requireContext(), "Đã cập nhật giao dịch", Toast.LENGTH_SHORT).show();
                } else {
                    appViewModel.addTransaction(tx);
                    Toast.makeText(requireContext(), "Đã lưu giao dịch", Toast.LENGTH_SHORT).show();
                }
                appViewModel.clearEditingTransaction();
                Navigation.findNavController(requireView()).navigateUp();
            }
        });

        // Load danh sách ví từ backend
        newTxViewModel.loadAccounts();

        newTxViewModel.getAccountsError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && tvWalletPreview != null) {
                tvWalletPreview.setText("Không tải được ví");
            }
        });
    }

    private void setupWalletPicker(View view) {
        CardView btnSelectWallet = view.findViewById(R.id.btn_select_wallet);
        if (btnSelectWallet == null) return;

        btnSelectWallet.setOnClickListener(v -> {
            List<AccountResponse> accounts = newTxViewModel.getAccounts().getValue();
            if (accounts == null || accounts.isEmpty()) {
                Toast.makeText(requireContext(), "Chưa có ví, vui lòng tạo ví trước", Toast.LENGTH_SHORT).show();
                return;
            }

            // Xây dựng danh sách tên ví để hiển thị
            String[] accountNames = new String[accounts.size()];
            for (int i = 0; i < accounts.size(); i++) {
                AccountResponse acc = accounts.get(i);
                String icon = (acc.getIcon() != null && !acc.getIcon().isEmpty()) ? acc.getIcon() + "  " : "💳  ";
                String balance = String.format("%,d₫", acc.getBalance()).replace(",", ".");
                accountNames[i] = icon + acc.getName() + "  (" + balance + ")";
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Chọn ví")
                    .setItems(accountNames, (dialog, which) -> {
                        newTxViewModel.selectAccount(accounts.get(which));
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }

    private void renderUiState(NewTransactionViewModel.UiState state) {
        // Render Amount
        try {
            long val = Long.parseLong(state.amountStr);
            tvAmount.setText(String.format("%,d", val).replace(",", "."));
        } catch (Exception e) {
            tvAmount.setText("0");
        }

        // Reset all
        btnTypeExpense.setCardBackgroundColor(Color.TRANSPARENT);
        btnTypeIncome.setCardBackgroundColor(Color.TRANSPARENT);
        btnTypeTransfer.setCardBackgroundColor(Color.TRANSPARENT);

        btnTypeExpense.setCardElevation(0);
        btnTypeIncome.setCardElevation(0);
        btnTypeTransfer.setCardElevation(0);

        ((TextView)btnTypeExpense.getChildAt(0)).setTextColor(Color.parseColor("#6B6584"));
        ((TextView)btnTypeIncome.getChildAt(0)).setTextColor(Color.parseColor("#6B6584"));
        ((TextView)btnTypeTransfer.getChildAt(0)).setTextColor(Color.parseColor("#6B6584"));

        if (state.selectedType == Transaction.Type.EXPENSE) {
            btnTypeExpense.setCardBackgroundColor(Color.parseColor("#E76E60"));
            btnTypeExpense.setCardElevation(2);
            ((TextView)btnTypeExpense.getChildAt(0)).setTextColor(Color.WHITE);
        } else if (state.selectedType == Transaction.Type.INCOME) {
            btnTypeIncome.setCardBackgroundColor(Color.parseColor("#4ADE80"));
            btnTypeIncome.setCardElevation(2);
            ((TextView)btnTypeIncome.getChildAt(0)).setTextColor(Color.WHITE);
        } else {
            btnTypeTransfer.setCardBackgroundColor(Color.parseColor("#735BF2"));
            btnTypeTransfer.setCardElevation(2);
            ((TextView)btnTypeTransfer.getChildAt(0)).setTextColor(Color.WHITE);
        }

        if (state.note.isEmpty()) {
            tvNotePreview.setText("Thêm ghi chú...");
            tvNotePreview.setTextColor(Color.parseColor("#6B6584"));
        } else {
            tvNotePreview.setText(state.note);
            tvNotePreview.setTextColor(Color.parseColor("#1A1730"));
        }

        tvDatePreview.setText(formatDateDisplay(state.timestampMs));

        // Render wallet name
        if (tvWalletPreview != null) {
            tvWalletPreview.setText(state.selectedAccountName);
        }

        if (currentCategoryType != state.selectedType) {
            currentCategoryType = state.selectedType;
            refreshCategories();
        }
    }

    private void refreshCategories() {
        if (getView() == null) return;
        RecyclerView rvCategories = getView().findViewById(R.id.rv_categories);
        if (rvCategories == null) return;

        List<Category> allCategories = appViewModel.getCategories().getValue();
        if (allCategories == null) return;

        NewTransactionViewModel.UiState state = newTxViewModel.getUiState().getValue();
        if (state == null) return;

        String targetType = "";
        if (state.selectedType == Transaction.Type.EXPENSE) targetType = "expense";
        else if (state.selectedType == Transaction.Type.INCOME) targetType = "income";
        // If transfer, maybe we still show expense or specific? Let's just match exact or show all if empty.

        java.util.List<Category> filtered = new java.util.ArrayList<>();
        for (Category c : allCategories) {
            if (c.isAddButton()) {
                filtered.add(c);
            } else {
                String cType = c.getType() != null ? c.getType().toLowerCase() : "expense";
                if (targetType.isEmpty() || cType.equals(targetType)) {
                    filtered.add(c);
                }
            }
        }

        boolean isEditInit = categoryAdapter == null && appViewModel.getEditingTransaction() != null;
        updateCategoryList(filtered, rvCategories);
        if (isEditInit) {
            categoryAdapter.selectCategoryByName(appViewModel.getEditingTransaction().getCategory());
        }
    }

    private void setupNoteAndDate(View view) {
        view.findViewById(R.id.btn_edit_note).setOnClickListener(v -> {
            EditText input = new EditText(requireContext());
            input.setTextColor(Color.BLACK);
            input.setHint("Nhập nội dung ghi chú");
            NewTransactionViewModel.UiState state = newTxViewModel.getUiState().getValue();
            if (state != null && !state.note.isEmpty()) input.setText(state.note);

            FrameLayout container = new FrameLayout(requireContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(50, 20, 50, 0);
            input.setLayoutParams(params);
            container.addView(input);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Ghi chú")
                    .setView(container)
                    .setPositiveButton("Lưu", (dialog, which) -> {
                        newTxViewModel.updateNote(input.getText().toString().trim());
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        view.findViewById(R.id.btn_select_date).setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Chọn ngày giao dịch")
                    .setSelection(newTxViewModel.getUiState().getValue().timestampMs)// Optional: ensure light theme
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                newTxViewModel.updateDate(selection);
            });

            datePicker.show(getChildFragmentManager(), "transaction_date_picker");
        });
    }

    private String formatDateDisplay(long timestampMs) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestampMs);

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        if (isSameDay(target, today)) {
            return "Hôm nay";
        } else if (isSameDay(target, yesterday)) {
            return "Hôm qua";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd/MM", new Locale("vi", "VN"));
            String formatted = sdf.format(target.getTime());
            return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private void updateCategoryList(List<Category> categories, RecyclerView rv) {
        if (categoryAdapter == null) {
            categoryAdapter = new CategoryAdapter(categories, new CategoryAdapter.OnCategoryClickListener() {
                @Override
                public void onCategoryClick(Category category) {
                    newTxViewModel.selectCategory(category);
                }
                @Override
                public void onAddCategoryClick() {
                    AddCategorySheet sheet = AddCategorySheet.newInstance(cat -> {
                        appViewModel.addCategory(cat);
                        // Khi thêm mới, Room sẽ reload và updateCategoryList sẽ được gọi lại
                        Toast.makeText(requireContext(), "Đã thêm danh mục: " + cat.getName(), Toast.LENGTH_SHORT).show();
                    });
                    sheet.show(getChildFragmentManager(), "add_category");
                }
            });
            rv.setAdapter(categoryAdapter);
        } else {
            categoryAdapter.updateData(categories);
        }
    }

    private void setupNumpad(View view) {
        int[] keys = {R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
                R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9};
        for (int id : keys) {
            TextView key = view.findViewById(id);
            if (key != null) {
                key.setOnClickListener(v -> newTxViewModel.appendNumpad(key.getText().toString()));
            }
        }
        view.findViewById(R.id.key_clear).setOnClickListener(v -> newTxViewModel.clearNumpad());
        view.findViewById(R.id.key_del).setOnClickListener(v -> newTxViewModel.deleteNumpad());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (appViewModel != null && !requireActivity().isChangingConfigurations()) {
            appViewModel.clearEditingTransaction();
        }
    }
}