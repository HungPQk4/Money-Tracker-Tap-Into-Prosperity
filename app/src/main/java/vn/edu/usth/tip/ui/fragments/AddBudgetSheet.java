package vn.edu.usth.tip.ui.fragments;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Budget;
import vn.edu.usth.tip.viewmodels.AppViewModel;

public class AddBudgetSheet extends BottomSheetDialogFragment {

    // Preset emoji options
    private static final String[] EMOJIS  = {"💰", "🍜", "🛵", "🛒", "🎬", "💊", "⚡", "✈️", "💄", "🏠"};
    private static final String[] COLORS  = {"#735BF2", "#F2C94C", "#2DD3A1", "#E76E60", "#8288D8", "#F97316", "#EC4899", "#0EA5E9"};
    private static final String[] COLOR_LABELS = {"보라", "노랑", "초록", "빨강", "인디고", "오렌지", "핑크", "파랑"};

    private AppViewModel viewModel;
    private String selectedEmoji = "💰";
    private String selectedColor = "#735BF2";
    private boolean periodMonth  = true;  // true = month, false = week

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_budget, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        TextInputEditText etName     = view.findViewById(R.id.et_budget_name);
        TextInputEditText etAmount   = view.findViewById(R.id.et_budget_amount);
        TextInputEditText etCategory = view.findViewById(R.id.et_budget_category);
        TextView          tvEmoji    = view.findViewById(R.id.tv_selected_emoji);
        CardView          cardEmoji  = view.findViewById(R.id.card_pick_emoji);

        // ── Emoji Picker ──────────────────────────────────────────────
        cardEmoji.setOnClickListener(v -> {
            String[] options = EMOJIS;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Chọn biểu tượng")
                    .setItems(options, (d, which) -> {
                        selectedEmoji = EMOJIS[which];
                        tvEmoji.setText(selectedEmoji);
                    })
                    .show();
        });

        // ── Color Chips ───────────────────────────────────────────────
        LinearLayout llColors = view.findViewById(R.id.ll_color_chips);
        buildColorChips(llColors);

        // ── Period Buttons ────────────────────────────────────────────
        CardView btnMonth = view.findViewById(R.id.btn_period_month);
        CardView btnWeek  = view.findViewById(R.id.btn_period_week);
        TextView tvMonth  = view.findViewById(R.id.tv_period_month);
        TextView tvWeek   = view.findViewById(R.id.tv_period_week);

        btnMonth.setOnClickListener(v -> {
            periodMonth = true;
            btnMonth.setCardBackgroundColor(Color.parseColor("#735BF2"));
            btnWeek.setCardBackgroundColor(Color.parseColor("#2A2B3D"));
            tvMonth.setTextColor(Color.WHITE);
            tvWeek.setTextColor(Color.parseColor("#8C8D99"));
        });

        btnWeek.setOnClickListener(v -> {
            periodMonth = false;
            btnWeek.setCardBackgroundColor(Color.parseColor("#735BF2"));
            btnMonth.setCardBackgroundColor(Color.parseColor("#2A2B3D"));
            tvWeek.setTextColor(Color.WHITE);
            tvMonth.setTextColor(Color.parseColor("#8C8D99"));
        });

        // ── Save ──────────────────────────────────────────────────────
        view.findViewById(R.id.btn_save_budget).setOnClickListener(v -> {
            String name     = etName.getText()     != null ? etName.getText().toString().trim()     : "";
            String amountStr= etAmount.getText()   != null ? etAmount.getText().toString().trim()   : "";
            String category = etCategory.getText() != null ? etCategory.getText().toString().trim() : "";

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Nhập tên ngân sách", Toast.LENGTH_SHORT).show();
                return;
            }
            if (amountStr.isEmpty()) {
                Toast.makeText(requireContext(), "Nhập số tiền giới hạn", Toast.LENGTH_SHORT).show();
                return;
            }

            long amount;
            try { amount = Long.parseLong(amountStr); }
            catch (Exception e) {
                Toast.makeText(requireContext(), "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }

            long now = System.currentTimeMillis();
            long periodStart, periodEnd;
            if (periodMonth) {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0);
                periodStart = c.getTimeInMillis();
                c.add(Calendar.MONTH, 1);
                c.add(Calendar.MILLISECOND, -1);
                periodEnd = c.getTimeInMillis();
            } else {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0);
                periodStart = c.getTimeInMillis();
                c.add(Calendar.DAY_OF_WEEK, 7);
                c.add(Calendar.MILLISECOND, -1);
                periodEnd = c.getTimeInMillis();
            }

            Budget budget = new Budget(
                    UUID.randomUUID().toString(),
                    name,
                    selectedEmoji,
                    selectedColor,
                    category,
                    amount,
                    periodStart,
                    periodEnd,
                    now
            );

            viewModel.addBudget(budget);
            Toast.makeText(requireContext(), "✅ Đã tạo ngân sách: " + name, Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    private void buildColorChips(LinearLayout container) {
        int chipSize  = dpToPx(36);
        int chipMargin= dpToPx(8);

        for (int i = 0; i < COLORS.length; i++) {
            final String hex = COLORS[i];
            View chip = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(chipSize, chipSize);
            lp.setMargins(0, 0, chipMargin, 0);
            chip.setLayoutParams(lp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(Color.parseColor(hex));
            chip.setBackground(gd);

            chip.setOnClickListener(v -> {
                selectedColor = hex;
                // Highlight selected (add border to all, remove from others)
                for (int j = 0; j < container.getChildCount(); j++) {
                    View c = container.getChildAt(j);
                    GradientDrawable d = (GradientDrawable) c.getBackground();
                    d.setStroke(0, Color.TRANSPARENT);
                }
                GradientDrawable sel = (GradientDrawable) chip.getBackground();
                sel.setStroke(dpToPx(3), Color.WHITE);
                Toast.makeText(requireContext(), "Màu: " + hex, Toast.LENGTH_SHORT).show();
            });

            // Select first by default
            if (i == 0) {
                GradientDrawable sel = (GradientDrawable) chip.getBackground();
                sel.setStroke(dpToPx(3), Color.WHITE);
            }

            container.addView(chip);
        }
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
