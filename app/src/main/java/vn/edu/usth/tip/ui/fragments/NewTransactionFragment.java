package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.adapters.CategoryAdapter;
import vn.edu.usth.tip.network.responses.AccountResponse;
import vn.edu.usth.tip.viewmodels.AppViewModel;
import vn.edu.usth.tip.viewmodels.NewTransactionViewModel;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.utils.Event;

public class NewTransactionFragment extends Fragment {

    private AppViewModel appViewModel;
    private NewTransactionViewModel newTxViewModel;
    private CategoryAdapter categoryAdapter;
    private Transaction.Type currentCategoryType = null;
    private String pendingCategorySelection = null;

    // View references
    private TextView tvAmount;
    private CardView btnTypeExpense, btnTypeIncome, btnTypeTransfer;
    private TextView tvTypeExpense, tvTypeIncome, tvTypeTransfer;
    private TextView tvNotePreview, tvDatePreview, tvWalletPreview;
    private LinearLayout sectionCategory, sectionTransfer;
    private View cardSelectWallet;
    private TextView tvFromAccountPreview, tvToAccountPreview;

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
        tvTypeExpense = view.findViewById(R.id.tv_type_expense);
        tvTypeIncome  = view.findViewById(R.id.tv_type_income);
        tvTypeTransfer = view.findViewById(R.id.tv_type_transfer);
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
            pendingCategorySelection = editingTx.getCategory();
            newTxViewModel.initEditMode(editingTx);
        } else {
            // Check default selected type from Dashboard
            newTxViewModel.setType(appViewModel.getDefaultNewTransactionType());
        }

        // Setup Interaction Listeners
        btnTypeExpense.setOnClickListener(v -> newTxViewModel.setType(Transaction.Type.EXPENSE));
        btnTypeIncome.setOnClickListener(v  -> newTxViewModel.setType(Transaction.Type.INCOME));
        btnTypeTransfer.setOnClickListener(v -> newTxViewModel.setType(Transaction.Type.TRANSFER));

        sectionCategory = view.findViewById(R.id.section_category);
        sectionTransfer = view.findViewById(R.id.section_transfer);
        cardSelectWallet = view.findViewById(R.id.btn_select_wallet);
        tvFromAccountPreview = view.findViewById(R.id.tv_from_account);
        tvToAccountPreview = view.findViewById(R.id.tv_to_account);

        setupNumpad(view);
        setupNoteAndDate(view);
        setupWalletPicker(view);
        setupTransferAccountPickers(view);
        setupRecurring(view);

        // Setup Categories
        RecyclerView rvCategories = view.findViewById(R.id.rv_categories);
        rvCategories.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        appViewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            refreshCategories(false);
        });

        view.findViewById(R.id.btn_save_transaction).setOnClickListener(v -> {
            newTxViewModel.validateAndSave(
                    categoryAdapter != null ? categoryAdapter.getSelectedCategory() : null
            );
        });

        // Observe ViewModel State
        newTxViewModel.getUiState().observe(getViewLifecycleOwner(), this::renderUiState);

        // Observe Validation & Save Events
        newTxViewModel.getValidationError().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            String msg = event.getContentIfNotHandled();
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });

        newTxViewModel.getTransactionToSave().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            Transaction tx = event.getContentIfNotHandled();
            if (tx != null) {
                appViewModel.addTransaction(tx);
                Toast.makeText(requireContext(), "Đã lưu giao dịch", Toast.LENGTH_SHORT).show();
                appViewModel.clearEditingTransaction();
                Navigation.findNavController(requireView()).navigateUp();
            }
        });

        newTxViewModel.getTransactionToUpdate().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            Transaction tx = event.getContentIfNotHandled();
            if (tx != null) {
                appViewModel.updateTransaction(tx);
                Toast.makeText(requireContext(), "Đã cập nhật giao dịch", Toast.LENGTH_SHORT).show();
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

        appViewModel.deleteCategoryError.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                appViewModel.deleteCategoryError.setValue(null); // reset
            }
        });
    }

    private void updateSectionVisibility(Transaction.Type type) {
        if (sectionCategory == null || sectionTransfer == null) return;
        ViewGroup parent = (ViewGroup) sectionCategory.getParent();
        if (parent != null) TransitionManager.beginDelayedTransition(parent);
        if (type == Transaction.Type.TRANSFER) {
            sectionCategory.setVisibility(View.GONE);
            sectionTransfer.setVisibility(View.VISIBLE);
            if (cardSelectWallet != null) cardSelectWallet.setVisibility(View.GONE);
        } else {
            sectionCategory.setVisibility(View.VISIBLE);
            sectionTransfer.setVisibility(View.GONE);
            if (cardSelectWallet != null) cardSelectWallet.setVisibility(View.VISIBLE);
        }
    }

    private void setupTransferAccountPickers(View view) {
        CardView btnFromAccount = view.findViewById(R.id.btn_select_from_account);
        CardView btnToAccount = view.findViewById(R.id.btn_select_to_account);
        if (btnFromAccount == null || btnToAccount == null) return;

        btnFromAccount.setOnClickListener(v -> showAccountPicker(false));
        btnToAccount.setOnClickListener(v -> showAccountPicker(true));
    }

    private void showAccountPicker(boolean isToAccount) {
        List<AccountResponse> accounts = newTxViewModel.getAccounts().getValue();
        if (accounts == null || accounts.isEmpty()) {
            Toast.makeText(requireContext(), "Chưa có ví, vui lòng tạo ví trước", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            AccountResponse acc = accounts.get(i);
            String icon = (acc.getIcon() != null && !acc.getIcon().isEmpty()) ? acc.getIcon() + "  " : "💳  ";
            String balance = vn.edu.usth.tip.utils.MoneyFormat.formatShort(acc.getBalance());
            names[i] = icon + acc.getName() + "  (" + balance + ")";
        }
        String title = isToAccount ? "Chọn tài khoản đích" : "Chọn tài khoản nguồn";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setItems(names, (dialog, which) -> {
                    if (isToAccount) {
                        newTxViewModel.selectToAccount(accounts.get(which));
                    } else {
                        newTxViewModel.selectAccount(accounts.get(which));
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
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
                String balance = vn.edu.usth.tip.utils.MoneyFormat.formatShort(acc.getBalance());
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
            float sp = val < 1_000_000_000L ? 52f : (val < 1_000_000_000_000L ? 36f : 26f);
            tvAmount.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        } catch (Exception e) {
            tvAmount.setText("0");
            tvAmount.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 52f);
        }

        // Reset all tabs to inactive state — use container color, not transparent
        int inactiveBg = Color.parseColor("#E9E9F0");
        int inactiveText = Color.parseColor("#6B6584");
        float elevationActive = 4 * getResources().getDisplayMetrics().density;

        btnTypeExpense.setCardBackgroundColor(inactiveBg);
        btnTypeIncome.setCardBackgroundColor(inactiveBg);
        btnTypeTransfer.setCardBackgroundColor(inactiveBg);

        btnTypeExpense.setCardElevation(0f);
        btnTypeIncome.setCardElevation(0f);
        btnTypeTransfer.setCardElevation(0f);

        tvTypeExpense.setTextColor(inactiveText);
        tvTypeIncome.setTextColor(inactiveText);
        tvTypeTransfer.setTextColor(inactiveText);

        if (state.selectedType == Transaction.Type.EXPENSE) {
            btnTypeExpense.setCardBackgroundColor(Color.parseColor("#E76E60"));
            btnTypeExpense.setCardElevation(elevationActive);
            tvTypeExpense.setTextColor(Color.WHITE);
        } else if (state.selectedType == Transaction.Type.INCOME) {
            btnTypeIncome.setCardBackgroundColor(Color.parseColor("#4ADE80"));
            btnTypeIncome.setCardElevation(elevationActive);
            tvTypeIncome.setTextColor(Color.WHITE);
        } else if (state.selectedType == Transaction.Type.TRANSFER) {
            btnTypeTransfer.setCardBackgroundColor(Color.parseColor("#735BF2"));
            btnTypeTransfer.setCardElevation(elevationActive);
            tvTypeTransfer.setTextColor(Color.WHITE);
        }

        if (state.note.isEmpty()) {
            tvNotePreview.setText("Thêm ghi chú...");
            tvNotePreview.setTextColor(Color.parseColor("#6B6584"));
        } else {
            tvNotePreview.setText(state.note);
            tvNotePreview.setTextColor(Color.parseColor("#1A1730"));
        }

        tvDatePreview.setText(formatDateDisplay(state.timestampMs));

        // Render wallet name (EXPENSE / INCOME)
        if (tvWalletPreview != null) {
            tvWalletPreview.setText(state.selectedAccountName);
        }
        // Render from/to accounts (TRANSFER)
        if (tvFromAccountPreview != null) {
            tvFromAccountPreview.setText(state.selectedAccountName);
        }
        if (tvToAccountPreview != null) {
            tvToAccountPreview.setText(state.selectedToAccountName);
        }

        updateSectionVisibility(state.selectedType);

        if (currentCategoryType != state.selectedType) {
            currentCategoryType = state.selectedType;
            refreshCategories(true);
        }
    }

    private void refreshCategories(boolean typeChanged) {
        if (getView() == null) return;
        RecyclerView rvCategories = getView().findViewById(R.id.rv_categories);
        if (rvCategories == null) return;

        List<Category> allCategories = appViewModel.getCategories().getValue();
        if (allCategories == null) return;

        NewTransactionViewModel.UiState state = newTxViewModel.getUiState().getValue();
        if (state == null) return;

        // Dedup by name: loại bỏ các category trùng tên còn sót trong DB
        // (nút "Thêm" dùng ID làm key vì nó không có tên duy nhất)
        java.util.LinkedHashMap<String, Category> dedupMap = new java.util.LinkedHashMap<>();
        for (Category c : allCategories) {
            String key = c.isAddButton()
                    ? c.getId()
                    : (c.getName() != null ? c.getName().trim().toLowerCase() : c.getId());
            dedupMap.putIfAbsent(key, c);
        }
        List<Category> filtered = new ArrayList<>(dedupMap.values());

        updateCategoryList(filtered, rvCategories);

        // Reset về item đầu tiên khi người dùng đổi tab loại giao dịch
        if (typeChanged && categoryAdapter != null) {
            categoryAdapter.resetSelection();
        }

        // Áp dụng danh mục đang chờ chọn (chế độ chỉnh sửa)
        if (pendingCategorySelection != null && categoryAdapter != null) {
            categoryAdapter.selectCategoryByName(pendingCategorySelection);
            pendingCategorySelection = null;
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
            NewTransactionViewModel.UiState currentState = newTxViewModel.getUiState().getValue();
            long currentTs = currentState != null ? currentState.timestampMs : System.currentTimeMillis();

            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Chọn ngày giao dịch")
                    .setSelection(currentTs)
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                // selection là UTC midnight; tách ra year/month/day theo UTC
                java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
                Calendar utcCal = Calendar.getInstance(utc);
                utcCal.setTimeInMillis(selection);
                int year  = utcCal.get(Calendar.YEAR);
                int month = utcCal.get(Calendar.MONTH);
                int day   = utcCal.get(Calendar.DAY_OF_MONTH);

                Calendar existingCal = Calendar.getInstance();
                existingCal.setTimeInMillis(currentTs);
                int initHour   = existingCal.get(Calendar.HOUR_OF_DAY);
                int initMinute = existingCal.get(Calendar.MINUTE);

                new TimePickerDialog(requireContext(), (tp, hour, minute) -> {
                    Calendar finalCal = Calendar.getInstance();
                    finalCal.set(year, month, day, hour, minute, 0);
                    finalCal.set(Calendar.MILLISECOND, 0);
                    newTxViewModel.updateDate(finalCal.getTimeInMillis());
                }, initHour, initMinute, true).show();
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

        String time = String.format(Locale.getDefault(), "%02d:%02d",
                target.get(Calendar.HOUR_OF_DAY),
                target.get(Calendar.MINUTE));

        if (isSameDay(target, today)) {
            return "Hôm nay  " + time;
        } else if (isSameDay(target, yesterday)) {
            return "Hôm qua  " + time;
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd/MM", new Locale("vi", "VN"));
            String formatted = sdf.format(target.getTime());
            String date = Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
            return date + "  " + time;
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
            categoryAdapter.setOnLongClickListener(category -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Xóa danh mục")
                    .setMessage("Xóa \"" + category.getName() + "\"?\nCác giao dịch liên quan sẽ được chuyển sang \"Khác\".")
                    .setPositiveButton("Xóa", (d, w) -> appViewModel.deleteCategory(category))
                    .setNegativeButton("Huỷ", null)
                    .show();
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

    private void setupRecurring(View view) {
        SwitchMaterial switchRecurring = view.findViewById(R.id.switch_recurring);
        LinearLayout layoutInterval    = view.findViewById(R.id.layout_recur_interval);
        Spinner spinnerInterval        = view.findViewById(R.id.spinner_recur_interval);

        if (switchRecurring == null || layoutInterval == null || spinnerInterval == null) return;

        String[] intervalLabels = {"Hàng ngày", "Hàng tuần", "Hàng tháng", "Hàng năm"};
        String[] intervalValues = {"DAILY",     "WEEKLY",    "MONTHLY",    "YEARLY"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, intervalLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInterval.setAdapter(adapter);

        // Khôi phục trạng thái khi edit mode
        NewTransactionViewModel.UiState state = newTxViewModel.getUiState().getValue();
        if (state != null && state.isRecurring) {
            switchRecurring.setChecked(true);
            layoutInterval.setVisibility(View.VISIBLE);
            if (state.recurInterval != null) {
                for (int i = 0; i < intervalValues.length; i++) {
                    if (intervalValues[i].equals(state.recurInterval)) {
                        spinnerInterval.setSelection(i);
                        break;
                    }
                }
            }
        }

        switchRecurring.setOnCheckedChangeListener((btn, isChecked) -> {
            layoutInterval.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            String selected = isChecked ? intervalValues[spinnerInterval.getSelectedItemPosition()] : null;
            newTxViewModel.updateRecurring(isChecked, selected);
        });

        spinnerInterval.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                if (switchRecurring.isChecked()) {
                    newTxViewModel.updateRecurring(true, intervalValues[pos]);
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.app.Activity activity = getActivity();
        if (appViewModel != null && activity != null && !activity.isChangingConfigurations()) {
            appViewModel.clearEditingTransaction();
        }
    }
}