package vn.edu.usth.tip.ui.fragments;

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
import com.google.android.material.textfield.TextInputEditText;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Random;
import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Goal;
import vn.edu.usth.tip.viewmodels.AppViewModel;

public class AddGoalSheet extends BottomSheetDialogFragment {

    private AppViewModel viewModel;
    private Goal existingGoal;
    
    // Some nice pastel colors for goals
    private static final String[] GOAL_COLORS = {
        "#8288D8", "#2DD3A1", "#F2C94C", "#FF7043", "#42A5F5", "#AB47BC"
    };

    public void setGoal(Goal goal) {
        this.existingGoal = goal;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_goal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        TextView tvTitle = view.findViewById(R.id.tv_sheet_title);
        TextInputEditText etEmoji = view.findViewById(R.id.et_goal_emoji);
        TextInputEditText etName = view.findViewById(R.id.et_goal_name);
        TextInputEditText etTargetAmount = view.findViewById(R.id.et_goal_target_amount);
        TextInputEditText etSavedAmount = view.findViewById(R.id.et_goal_saved_amount);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_goal);
        MaterialButton btnDelete = view.findViewById(R.id.btn_delete_goal);

        if (existingGoal != null) {
            tvTitle.setText("Cập nhật Mục Tiêu");
            etEmoji.setText(existingGoal.getEmoji());
            etName.setText(existingGoal.getName());
            etTargetAmount.setText(String.valueOf(existingGoal.getTargetAmount()));
            etSavedAmount.setText(String.valueOf(existingGoal.getSavedAmount()));
            btnSave.setText("Cập nhật Mục Tiêu");
            btnDelete.setVisibility(View.VISIBLE);

            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Xóa mục tiêu?")
                        .setMessage("Bạn có chắc chắn muốn xóa mục tiêu '" + existingGoal.getName() + "' không?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            viewModel.deleteGoal(existingGoal);
                            Toast.makeText(requireContext(), "Đã xóa mục tiêu", Toast.LENGTH_SHORT).show();
                            dismiss();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        }

        btnSave.setOnClickListener(v -> {
            String emoji = etEmoji.getText() != null ? etEmoji.getText().toString().trim() : "🎯";
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String targetStr = etTargetAmount.getText() != null ? etTargetAmount.getText().toString().trim() : "";
            String savedStr = etSavedAmount.getText() != null ? etSavedAmount.getText().toString().trim() : "0";

            if (name.isEmpty() || targetStr.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập tên và số tiền mục tiêu", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (emoji.isEmpty()) emoji = "🎯";

            try {
                long targetAmount = Long.parseLong(targetStr);
                long savedAmount = Long.parseLong(savedStr);
                
                if (existingGoal != null) {
                    existingGoal.setEmoji(emoji);
                    existingGoal.setName(name);
                    existingGoal.setTargetAmount(targetAmount);
                    existingGoal.setSavedAmount(savedAmount);
                    viewModel.updateGoal(existingGoal);
                    Toast.makeText(requireContext(), "Đã cập nhật mục tiêu", Toast.LENGTH_SHORT).show();
                } else {
                    // Mặc định +6 tháng cho mục tiêu mới
                    long targetDateMs = System.currentTimeMillis() + (86400000L * 30 * 6);
                    String randomHex = GOAL_COLORS[new Random().nextInt(GOAL_COLORS.length)];

                    Goal goal = new Goal(
                            UUID.randomUUID().toString(),
                            name,
                            emoji,
                            targetAmount,
                            savedAmount,
                            targetDateMs,
                            randomHex
                    );
                    viewModel.addGoal(goal);
                    Toast.makeText(requireContext(), "Đã lưu mục tiêu thành công", Toast.LENGTH_SHORT).show();
                }
                
                dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
