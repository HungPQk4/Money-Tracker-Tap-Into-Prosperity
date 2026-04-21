package vn.edu.usth.tip.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.DebtLoan;
import vn.edu.usth.tip.viewmodels.AppViewModel;

public class AddDebtSheet extends BottomSheetDialogFragment {

    private AppViewModel viewModel;
    private int selectedType = DebtLoan.TYPE_I_OWE;

    // Default due date: 30 days from today
    private long selectedDueDateMs = System.currentTimeMillis() + 86400000L * 30;
    private boolean dateChosen = false;

    private final SimpleDateFormat displayFormat =
            new SimpleDateFormat("EEEE, dd 'tháng' M, yyyy", new Locale("vi", "VN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_debt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggle_debt_type);
        TextInputEditText etName    = view.findViewById(R.id.et_debt_name);
        TextInputEditText etReason  = view.findViewById(R.id.et_debt_reason);
        TextInputEditText etAmount  = view.findViewById(R.id.et_debt_amount);
        TextInputLayout   tilAmount = view.findViewById(R.id.til_debt_amount);
        MaterialButton    btnSave   = view.findViewById(R.id.btn_save_debt);

        // Due date card
        LinearLayout cardDueDate     = view.findViewById(R.id.card_due_date);
        TextView     tvDueDateDisplay = view.findViewById(R.id.tv_due_date_display);

        // ── Type Toggle ───────────────────────────────────────────────────────
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_type_i_owe) {
                    selectedType = DebtLoan.TYPE_I_OWE;
                    applyAmountColor(tilAmount, etAmount, btnSave, "#E53935");
                } else if (checkedId == R.id.btn_type_lent) {
                    selectedType = DebtLoan.TYPE_LENT;
                    applyAmountColor(tilAmount, etAmount, btnSave, "#2E7D32");
                }
            }
        });

        // ── Date Selector Card ────────────────────────────────────────────────
        cardDueDate.setOnClickListener(v -> {
            // Build picker: only allow today and future dates
            CalendarConstraints constraints = new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build();

            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Chọn ngày hẹn trả")
                    .setSelection(selectedDueDateMs)
                    .setCalendarConstraints(constraints)
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                // selection is UTC midnight; shift to local timezone for display
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(selection);
                selectedDueDateMs = cal.getTimeInMillis();
                dateChosen = true;

                // Update display text
                tvDueDateDisplay.setText(capitalize(displayFormat.format(cal.getTime())));

                // Highlight card with purple border
                cardDueDate.setBackgroundResource(R.drawable.bg_date_selector);
            });

            datePicker.show(getChildFragmentManager(), "debt_date_picker");
        });

        // ── Save ──────────────────────────────────────────────────────────────
        btnSave.setOnClickListener(v -> {
            String name      = etName.getText()   != null ? etName.getText().toString().trim()   : "";
            String reason    = etReason.getText() != null ? etReason.getText().toString().trim() : "";
            String amountStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";

            if (name.isEmpty() || reason.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            long amount = Long.parseLong(amountStr);

            DebtLoan debt = new DebtLoan(
                    UUID.randomUUID().toString(),
                    name,
                    reason,
                    amount,
                    selectedDueDateMs,
                    selectedType
            );

            viewModel.addDebtLoan(debt);
            Toast.makeText(requireContext(), "Đã lưu thành công", Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyAmountColor(TextInputLayout til, TextInputEditText et,
                                   MaterialButton btn, String hex) {
        int color = Color.parseColor(hex);
        til.setBoxStrokeColor(color);
        til.setHintTextColor(ColorStateList.valueOf(color));
        et.setTextColor(color);
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    /** Capitalize first char of a string (for Vietnamese day names). */
    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
