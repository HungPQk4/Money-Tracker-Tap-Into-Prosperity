package vn.edu.usth.tip.ui.fragments;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.network.responses.AccountResponse;
import vn.edu.usth.tip.viewmodels.AppViewModel;
import vn.edu.usth.tip.viewmodels.NewTransactionViewModel;

public class ExtractInvoiceFragment extends Fragment {

    private AppViewModel appViewModel;
    private NewTransactionViewModel newTxViewModel;

    private EditText etAmount;
    private EditText etNote;
    private TextView tvCategoryIcon, tvCategoryName;
    private TextView tvWalletIcon, tvWalletName;
    private TextView tvDate, tvTime;

    private Category selectedCategory;
    private AccountResponse selectedAccount;
    private long selectedTimestampMs;

    public ExtractInvoiceFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        newTxViewModel = new ViewModelProvider(this).get(NewTransactionViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_extract_invoice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etAmount   = view.findViewById(R.id.et_amount);
        etNote     = view.findViewById(R.id.et_note);
        tvCategoryIcon = view.findViewById(R.id.tv_category_icon);
        tvCategoryName = view.findViewById(R.id.tv_category_name);
        tvWalletIcon   = view.findViewById(R.id.tv_wallet_icon);
        tvWalletName   = view.findViewById(R.id.tv_wallet_name);
        tvDate = view.findViewById(R.id.tv_date);
        tvTime = view.findViewById(R.id.tv_time);

        // Thời điểm hiện tại
        selectedTimestampMs = System.currentTimeMillis();
        updateDateTimeDisplay();

        // Nhận dữ liệu OCR từ ScanReceiptFragment (nếu có)
        Bundle args = getArguments();
        if (args != null) {
            long ocrAmount = args.getLong("ocr_amount", 0);
            String ocrShopName = args.getString("ocr_shop_name", "");
            String ocrDate = args.getString("ocr_date", "");
            String ocrNote = args.getString("ocr_note", "");

            etAmount.setText(ocrAmount > 0 ? String.valueOf(ocrAmount) : "");

            // Ghép shopName + note cho trường ghi chú
            String combinedNote = buildNote(ocrShopName, ocrNote);
            etNote.setText(combinedNote);

            // Nếu LLM trả về ngày, dùng ngày đó thay vì ngày hiện tại
            if (ocrDate != null && !ocrDate.isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                    java.util.Date parsed = sdf.parse(ocrDate);
                    if (parsed != null) {
                        selectedTimestampMs = parsed.getTime();
                        updateDateTimeDisplay();
                    }
                } catch (java.text.ParseException e) {
                    android.util.Log.w("OCR_DEBUG", "Cannot parse LLM date: " + ocrDate);
                }
            }
        } else {
            // Màn hình mở trực tiếp (nhập tay) — để trống
            etAmount.setText("");
            etNote.setText("");
        }

        // ── Danh mục từ Room DB — giống NewTransactionFragment ──────────────
        appViewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories == null || categories.isEmpty()) return;
            if (selectedCategory == null) {
                for (Category c : categories) {
                    if (!c.isAddButton()) {
                        selectedCategory = c;
                        break;
                    }
                }
                updateCategoryUI();
            }
        });

        // ── Ví thanh toán: một lần fetch API — giống NewTransactionFragment ─
        newTxViewModel.loadAccounts();
        newTxViewModel.getAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts == null || accounts.isEmpty()) return;
            if (selectedAccount == null) {
                selectedAccount = accounts.get(0);
                updateWalletUI();
            }
        });
        newTxViewModel.getAccountsError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) tvWalletName.setText("Không tải được ví");
        });

        // ── Click listeners ──────────────────────────────────────────────────
        view.findViewById(R.id.btn_back)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        view.findViewById(R.id.card_date).setOnClickListener(v -> showDatePicker());
        view.findViewById(R.id.card_time).setOnClickListener(v -> showTimePicker());

        view.findViewById(R.id.card_category).setOnClickListener(v -> {
            List<Category> all = appViewModel.getCategories().getValue();
            if (all == null) return;
            List<Category> filtered = new ArrayList<>();
            for (Category c : all) {
                if (!c.isAddButton()) filtered.add(c);
            }
            if (filtered.isEmpty()) return;
            String[] names = new String[filtered.size()];
            for (int i = 0; i < filtered.size(); i++) {
                names[i] = filtered.get(i).getIcon() + " " + filtered.get(i).getName();
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Chọn danh mục")
                    .setItems(names, (dialog, which) -> {
                        selectedCategory = filtered.get(which);
                        updateCategoryUI();
                    }).show();
        });

        view.findViewById(R.id.card_wallet).setOnClickListener(v -> {
            List<AccountResponse> accounts = newTxViewModel.getAccounts().getValue();
            if (accounts == null || accounts.isEmpty()) {
                Toast.makeText(requireContext(), "Chưa có ví, vui lòng tạo ví trước",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[accounts.size()];
            for (int i = 0; i < accounts.size(); i++) {
                AccountResponse acc = accounts.get(i);
                String icon = (acc.getIcon() != null && !acc.getIcon().isEmpty())
                        ? acc.getIcon() + " " : "💳 ";
                names[i] = icon + acc.getName();
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Chọn ví thanh toán")
                    .setItems(names, (dialog, which) -> {
                        selectedAccount = accounts.get(which);
                        updateWalletUI();
                    }).show();
        });

        view.findViewById(R.id.btn_save).setOnClickListener(this::validateAndSave);
    }

    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Chọn ngày giao dịch")
                .setSelection(selectedTimestampMs)
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(utcMs -> {
            // MaterialDatePicker trả về UTC midnight — chuyển sang local, giữ giờ:phút cũ
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(utcMs);

            Calendar local = Calendar.getInstance();
            local.set(Calendar.YEAR, utc.get(Calendar.YEAR));
            local.set(Calendar.MONTH, utc.get(Calendar.MONTH));
            local.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH));

            Calendar oldTime = Calendar.getInstance();
            oldTime.setTimeInMillis(selectedTimestampMs);
            local.set(Calendar.HOUR_OF_DAY, oldTime.get(Calendar.HOUR_OF_DAY));
            local.set(Calendar.MINUTE, oldTime.get(Calendar.MINUTE));
            local.set(Calendar.SECOND, 0);
            local.set(Calendar.MILLISECOND, 0);

            selectedTimestampMs = local.getTimeInMillis();
            updateDateTimeDisplay();
        });
        picker.show(getParentFragmentManager(), "extract_date_picker");
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedTimestampMs);
        new TimePickerDialog(requireContext(), (tp, hour, minute) -> {
            Calendar updated = Calendar.getInstance();
            updated.setTimeInMillis(selectedTimestampMs);
            updated.set(Calendar.HOUR_OF_DAY, hour);
            updated.set(Calendar.MINUTE, minute);
            updated.set(Calendar.SECOND, 0);
            updated.set(Calendar.MILLISECOND, 0);
            selectedTimestampMs = updated.getTimeInMillis();
            updateDateTimeDisplay();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
    }

    private void updateDateTimeDisplay() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedTimestampMs);
        tvDate.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d",
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR)));
        tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)));
    }

    private void updateCategoryUI() {
        if (selectedCategory != null) {
            tvCategoryIcon.setText(selectedCategory.getIcon());
            tvCategoryName.setText(selectedCategory.getName());
        }
    }

    private void updateWalletUI() {
        if (selectedAccount != null) {
            String icon = (selectedAccount.getIcon() != null && !selectedAccount.getIcon().isEmpty())
                    ? selectedAccount.getIcon() : "💳";
            tvWalletIcon.setText(icon);
            tvWalletName.setText(selectedAccount.getName());
        }
    }

    // ── Guard chống double-tap (tham khảo NewTransactionViewModel) ────────────
    private boolean isSaving = false;

    private void validateAndSave(View v) {
        // Guard: nếu đang lưu → bỏ qua click thừa
        if (isSaving) return;

        String amountStr = etAmount.getText().toString().trim()
                .replace(".", "").replace(",", "");
        long amount = 0;
        try {
            amount = Long.parseLong(amountStr);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(getContext(), "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCategory == null) {
            Toast.makeText(getContext(), "Chưa có danh mục", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedAccount == null) {
            Toast.makeText(getContext(), "Vui lòng chọn ví", Toast.LENGTH_SHORT).show();
            return;
        }

        // ── Lock: chặn double-tap từ đây trở đi ──
        isSaving = true;
        v.setEnabled(false);

        String note = etNote.getText().toString().trim();

        Transaction tx = new Transaction(
                UUID.randomUUID().toString(),
                "Hóa đơn AI",
                selectedCategory.getName(),
                selectedCategory.getIcon(),
                selectedAccount.getName(),
                amount,
                Transaction.Type.EXPENSE,
                selectedTimestampMs,
                note
        );
        tx.setAccountId(selectedAccount.getId());
        tx.setCategoryId(selectedCategory.getId());

        appViewModel.addTransaction(tx);
        Toast.makeText(getContext(), "Lưu hóa đơn thành công", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(v).popBackStack(R.id.dashboardFragment, false);
    }

    /**
     * Ghép shopName và note thành một chuỗi ghi chú hoàn chỉnh.
     * VD: "Highlands Coffee — Cà phê sữa đá" hoặc chỉ shopName / note nếu 1 trong 2 rỗng.
     */
    private String buildNote(String shopName, String note) {
        boolean hasShop = shopName != null && !shopName.trim().isEmpty();
        boolean hasNote = note != null && !note.trim().isEmpty();

        if (hasShop && hasNote) {
            return shopName.trim() + " — " + note.trim();
        } else if (hasShop) {
            return shopName.trim();
        } else if (hasNote) {
            return note.trim();
        }
        return "";
    }
}
