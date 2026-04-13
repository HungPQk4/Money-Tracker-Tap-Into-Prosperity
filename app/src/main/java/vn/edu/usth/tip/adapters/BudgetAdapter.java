package vn.edu.usth.tip.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.viewmodels.AppViewModel.BudgetWithSpent;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    public interface OnBudgetLongClickListener {
        void onLongClick(BudgetWithSpent item);
    }

    private List<BudgetWithSpent> data = new ArrayList<>();
    private final OnBudgetLongClickListener listener;

    public BudgetAdapter(OnBudgetLongClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<BudgetWithSpent> newData) {
        this.data = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_budget, parent, false);
        return new BudgetViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder h, int position) {
        BudgetWithSpent item = data.get(position);
        h.bind(item);
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    // ── ViewHolder ─────────────────────────────────────────────────
    static class BudgetViewHolder extends RecyclerView.ViewHolder {

        private final TextView    tvEmoji, tvName, tvCategory, tvPercent;
        private final TextView    tvSpent, tvLimit, tvDaysLeft;
        private final ProgressBar progressBar;
        private final CardView    cardEmojiBg;

        BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji     = itemView.findViewById(R.id.tv_budget_emoji);
            tvName      = itemView.findViewById(R.id.tv_budget_name);
            tvCategory  = itemView.findViewById(R.id.tv_budget_category);
            tvPercent   = itemView.findViewById(R.id.tv_budget_percent);
            tvSpent     = itemView.findViewById(R.id.tv_budget_spent);
            tvLimit     = itemView.findViewById(R.id.tv_budget_limit);
            tvDaysLeft  = itemView.findViewById(R.id.tv_budget_days_left);
            progressBar = itemView.findViewById(R.id.progress_budget);
            cardEmojiBg = itemView.findViewById(R.id.card_budget_emoji);
        }

        void bind(BudgetWithSpent item) {
            long spent   = item.spentAmount;
            long limit   = item.budget.getLimitAmount();
            int  percent = limit > 0 ? (int) Math.min(100, spent * 100L / limit) : 0;

            // Color: đỏ nếu ≥ 90%, vàng nếu ≥ 70%, xanh bình thường
            String hexColor = item.budget.getColor();
            int accentColor;
            try {
                accentColor = Color.parseColor(hexColor);
            } catch (Exception e) {
                accentColor = 0xFF735BF2;
            }
            if (percent >= 90) accentColor = 0xFFE76E60;
            else if (percent >= 70) accentColor = 0xFFF2C94C;

            // Emoji bg
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setColor((accentColor & 0x00FFFFFF) | 0x28000000); // 16% alpha
            bg.setCornerRadius(24f);
            cardEmojiBg.setBackground(bg);

            tvEmoji.setText(item.budget.getEmoji() != null ? item.budget.getEmoji() : "💰");
            tvName.setText(item.budget.getName());

            String cat = item.budget.getCategoryName();
            tvCategory.setText(cat != null && !cat.isEmpty() ? cat : "Tất cả");

            tvPercent.setText(percent + "%");
            tvPercent.setTextColor(accentColor);

            // Progress bar
            progressBar.setProgress(percent);
            progressBar.getProgressDrawable().setColorFilter(
                    accentColor, android.graphics.PorterDuff.Mode.SRC_IN);

            // Số tiền
            tvSpent.setText("₫" + formatVnd(spent));
            tvSpent.setTextColor(accentColor);
            tvLimit.setText("/ ₫" + formatVnd(limit));

            // Days left
            long now  = System.currentTimeMillis();
            long end  = item.budget.getPeriodEndMs();
            long days = Math.max(0, (end - now) / 86_400_000L);
            tvDaysLeft.setText(days + " ngày còn lại");

            // Status warning
            if (percent >= 100) {
                tvDaysLeft.setText("⚠ Vượt ngân sách!");
                tvDaysLeft.setTextColor(0xFFE76E60);
            } else {
                tvDaysLeft.setTextColor(0xFF8C8D99);
            }
        }

        private static String formatVnd(long v) {
            return String.format("%,d", v).replace(",", ".");
        }
    }
}
