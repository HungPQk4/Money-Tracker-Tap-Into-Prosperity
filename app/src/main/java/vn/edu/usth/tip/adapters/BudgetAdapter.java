package vn.edu.usth.tip.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.utils.MoneyFormat;
import vn.edu.usth.tip.viewmodels.AppViewModel.BudgetWithSpent;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    public interface OnBudgetClickListener {
        void onClick(BudgetWithSpent item);
    }

    private static final int THRESHOLD_CRITICAL = 90;
    private static final int THRESHOLD_WARNING  = 70;

    private static final int COLOR_CRITICAL  = 0xFFE76E60;
    private static final int COLOR_WARNING   = 0xFFF2C94C;
    private static final int COLOR_DEFAULT   = 0xFF735BF2;
    private static final int COLOR_OVERBUDGET = 0xFFE76E60;
    private static final int COLOR_MUTED      = 0xFF8C8D99;

    private List<BudgetWithSpent> data = new ArrayList<>();
    private final OnBudgetClickListener listener;

    public BudgetAdapter(OnBudgetClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setData(List<BudgetWithSpent> newData) {
        List<BudgetWithSpent> next = newData != null ? newData : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new BudgetDiffCallback(data, next));
        this.data = next;
        result.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).budget.getId().hashCode();
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_budget, parent, false);
        return new BudgetViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        BudgetWithSpent item = data.get(position);
        holder.bind(item);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static int resolveAccentColor(int percentInt, String hexColor) {
        if (percentInt >= THRESHOLD_CRITICAL) return COLOR_CRITICAL;
        if (percentInt >= THRESHOLD_WARNING)  return COLOR_WARNING;
        try {
            return Color.parseColor(hexColor);
        } catch (Exception e) {
            return COLOR_DEFAULT;
        }
    }

    private static String formatPercent(float pct) {
        if (pct == 0f || pct >= 100f || pct == (int) pct) {
            return String.format(Locale.US, "%d%%", (int) pct);
        }
        if (pct < 0.01f) return "<0.01%";
        if (pct < 1f)    return String.format(Locale.US, "%.2f%%", pct);
        return                  String.format(Locale.US, "%.1f%%", pct);
    }

    // ── DiffCallback ───────────────────────────────────────────────────

    private static final class BudgetDiffCallback extends DiffUtil.Callback {
        private final List<BudgetWithSpent> oldList, newList;

        BudgetDiffCallback(List<BudgetWithSpent> oldList, List<BudgetWithSpent> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int op, int np) {
            return oldList.get(op).budget.getId().equals(newList.get(np).budget.getId());
        }

        @Override
        public boolean areContentsTheSame(int op, int np) {
            BudgetWithSpent o = oldList.get(op), n = newList.get(np);
            return o.spentAmount == n.spentAmount
                && o.budget.getLimitAmount() == n.budget.getLimitAmount()
                && o.budget.getPeriodEndMs()  == n.budget.getPeriodEndMs()
                && Objects.equals(o.budget.getName(),         n.budget.getName())
                && Objects.equals(o.budget.getEmoji(),        n.budget.getEmoji())
                && Objects.equals(o.budget.getColor(),        n.budget.getColor())
                && Objects.equals(o.budget.getCategoryName(), n.budget.getCategoryName());
        }
    }

    // ── ViewHolder ──────────────────────────────────────────────────────

    static class BudgetViewHolder extends RecyclerView.ViewHolder {

        private final TextView     tvEmoji, tvName, tvCategory, tvPercent;
        private final TextView     tvSpent, tvLimit, tvDaysLeft;
        private final CardView     cvProgressFill, cvProgressEmpty;
        private final CardView     cardEmojiBg;
        private final LinearLayout layoutBudgetInner;

        private final float            density;
        private final GradientDrawable borderDrawable;

        BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji           = itemView.findViewById(R.id.tv_budget_emoji);
            tvName            = itemView.findViewById(R.id.tv_budget_name);
            tvCategory        = itemView.findViewById(R.id.tv_budget_category);
            tvPercent         = itemView.findViewById(R.id.tv_budget_percent);
            tvSpent           = itemView.findViewById(R.id.tv_budget_spent);
            tvLimit           = itemView.findViewById(R.id.tv_budget_limit);
            tvDaysLeft        = itemView.findViewById(R.id.tv_budget_days_left);
            cvProgressFill    = itemView.findViewById(R.id.cv_progress_fill);
            cvProgressEmpty   = itemView.findViewById(R.id.cv_progress_empty);
            cardEmojiBg       = itemView.findViewById(R.id.card_budget_emoji);
            layoutBudgetInner = itemView.findViewById(R.id.layout_budget_inner);

            density = itemView.getContext().getResources().getDisplayMetrics().density;

            borderDrawable = new GradientDrawable();
            borderDrawable.setShape(GradientDrawable.RECTANGLE);
            borderDrawable.setColor(Color.WHITE);
            borderDrawable.setCornerRadius(16 * density);
            layoutBudgetInner.setBackground(borderDrawable);
        }

        void bind(BudgetWithSpent item) {
            long  spent  = item.spentAmount;
            long  limit  = item.budget.getLimitAmount();
            if (limit == 0) limit = 1;
            float pct    = (spent * 100.0f) / limit;
            int   pctInt = (int) Math.min(100, pct);
            int   accent = resolveAccentColor(pctInt, item.budget.getColor());

            applyTheme(accent, pctInt);
            bindHeader(item, pct, accent);
            bindProgressBar(pctInt, accent);
            bindAmounts(spent, limit, accent);
            bindDaysLeft(item.budget.getPeriodEndMs(), pctInt);
        }

        private void applyTheme(int accent, int percentInt) {
            cardEmojiBg.setCardBackgroundColor((accent & 0x00FFFFFF) | 0x28000000);
            int alpha, strokePx;
            if (percentInt >= THRESHOLD_CRITICAL) {
                alpha    = 0xFF;
                strokePx = Math.max(2, (int)(2f * density));
            } else if (percentInt >= THRESHOLD_WARNING) {
                alpha    = 0xCC;
                strokePx = Math.max(2, (int)(1.5f * density));
            } else {
                alpha    = 0x66;
                strokePx = Math.max(2, (int)(1.5f * density));
            }
            borderDrawable.setStroke(strokePx, (accent & 0x00FFFFFF) | (alpha << 24));
        }

        private void bindHeader(BudgetWithSpent item, float pct, int accent) {
            tvEmoji.setText(item.budget.getEmoji() != null ? item.budget.getEmoji() : "💰");
            tvName.setText(item.budget.getName());
            String cat = item.budget.getCategoryName();
            tvCategory.setText(cat != null && !cat.isEmpty() ? cat : "Tất cả");
            tvPercent.setText(formatPercent(pct));
            tvPercent.setTextColor(accent);
        }

        private void bindProgressBar(int pctInt, int accent) {
            LinearLayout.LayoutParams fillParams =
                    (LinearLayout.LayoutParams) cvProgressFill.getLayoutParams();
            fillParams.weight = pctInt;
            cvProgressFill.setLayoutParams(fillParams);
            cvProgressFill.setCardBackgroundColor(accent);

            LinearLayout.LayoutParams emptyParams =
                    (LinearLayout.LayoutParams) cvProgressEmpty.getLayoutParams();
            emptyParams.weight = 100 - pctInt;
            cvProgressEmpty.setLayoutParams(emptyParams);

            if (pctInt == 0) {
                cvProgressFill.setVisibility(View.GONE);
                ((ViewGroup.MarginLayoutParams) cvProgressEmpty.getLayoutParams()).setMarginStart(0);
            } else {
                cvProgressFill.setVisibility(View.VISIBLE);
                ((ViewGroup.MarginLayoutParams) cvProgressEmpty.getLayoutParams())
                        .setMarginStart((int)(-6 * density));
            }
        }

        private void bindAmounts(long spent, long limit, int accent) {
            tvSpent.setText(MoneyFormat.formatShortStyled(spent));
            tvSpent.setTextColor(accent);
            SpannableStringBuilder limitLabel = new SpannableStringBuilder("/ ");
            limitLabel.append(MoneyFormat.formatShortStyled(limit));
            tvLimit.setText(limitLabel);
        }

        private void bindDaysLeft(long periodEndMs, int pctInt) {
            long daysLeft = (periodEndMs - System.currentTimeMillis()) / 86_400_000L;
            if (pctInt >= 100) {
                tvDaysLeft.setText("⚠ Vượt ngân sách!");
                tvDaysLeft.setTextColor(COLOR_OVERBUDGET);
            } else if (daysLeft <= 0) {
                tvDaysLeft.setText("Đã kết thúc");
                tvDaysLeft.setTextColor(COLOR_MUTED);
            } else {
                tvDaysLeft.setText(daysLeft + " ngày còn lại");
                tvDaysLeft.setTextColor(COLOR_MUTED);
            }
        }
    }
}
