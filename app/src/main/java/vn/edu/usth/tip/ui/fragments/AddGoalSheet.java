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

import java.util.Random;
import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Goal;
import vn.edu.usth.tip.viewmodels.AppViewModel;

public class AddGoalSheet extends BottomSheetDialogFragment {

    private AppViewModel viewModel;
    
    // Some nice pastel colors for goals
    private static final String[] GOAL_COLORS = {
        "#8288D8", "#2DD3A1", "#F2C94C", "#FF7043", "#42A5F5", "#AB47BC"
    };

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

        TextInputEditText etEmoji = view.findViewById(R.id.et_goal_emoji);
        TextInputEditText etName = view.findViewById(R.id.et_goal_name);
        TextInputEditText etTargetAmount = view.findViewById(R.id.et_goal_target_amount);
        TextInputEditText etSavedAmount = view.findViewById(R.id.et_goal_saved_amount);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_goal);

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
                
                // Mặc định +6 tháng cho mục tiêu
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
                dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
