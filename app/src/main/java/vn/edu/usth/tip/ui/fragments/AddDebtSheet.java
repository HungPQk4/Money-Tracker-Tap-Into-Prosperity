package vn.edu.usth.tip.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.DebtLoan;
import vn.edu.usth.tip.viewmodels.AppViewModel;

public class AddDebtSheet extends BottomSheetDialogFragment {

    private AppViewModel viewModel;
    private int selectedType = DebtLoan.TYPE_I_OWE;

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
        TextInputEditText etName = view.findViewById(R.id.et_debt_name);
        TextInputEditText etReason = view.findViewById(R.id.et_debt_reason);
        TextInputEditText etAmount = view.findViewById(R.id.et_debt_amount);
        
        TextInputLayout tilAmount = view.findViewById(R.id.til_debt_amount);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_debt);

        // Toggle Listener
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_type_i_owe) {
                    selectedType = DebtLoan.TYPE_I_OWE;
                    int colorRed = Color.parseColor("#FF7043");
                    tilAmount.setBoxStrokeColor(colorRed);
                    tilAmount.setHintTextColor(ColorStateList.valueOf(colorRed));
                    etAmount.setTextColor(colorRed);
                    btnSave.setBackgroundTintList(ColorStateList.valueOf(colorRed));
                } else if (checkedId == R.id.btn_type_lent) {
                    selectedType = DebtLoan.TYPE_LENT;
                    int colorGreen = Color.parseColor("#26A69A");
                    tilAmount.setBoxStrokeColor(colorGreen);
                    tilAmount.setHintTextColor(ColorStateList.valueOf(colorGreen));
                    etAmount.setTextColor(colorGreen);
                    btnSave.setBackgroundTintList(ColorStateList.valueOf(colorGreen));
                }
            }
        });

        // Save logic
        btnSave.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String reason = etReason.getText() != null ? etReason.getText().toString().trim() : "";
            String amountStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";

            if (name.isEmpty() || reason.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            long amount = Long.parseLong(amountStr);
            
            // Mặc định ngày trả +30 ngày
            long dueDateMs = System.currentTimeMillis() + 86400000L * 30;

            DebtLoan debt = new DebtLoan(
                    UUID.randomUUID().toString(),
                    name,
                    reason,
                    amount,
                    dueDateMs,
                    selectedType
            );

            viewModel.addDebtLoan(debt);
            
            Toast.makeText(requireContext(), "Đã lưu thành công", Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }
}
