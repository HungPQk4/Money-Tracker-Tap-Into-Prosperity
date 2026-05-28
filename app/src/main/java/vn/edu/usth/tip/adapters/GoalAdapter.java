package vn.edu.usth.tip.adapters;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Goal;
import vn.edu.usth.tip.utils.MoneyFormat;

public class GoalAdapter extends RecyclerView.Adapter<GoalAdapter.ViewHolder> {

    public interface OnGoalClickListener {
        void onGoalClick(Goal goal);
    }

    private static final String TAG           = "GoalAdapter";
    private static final String DEFAULT_COLOR = "#8288D8";
    private static final float  MIN_FILL_PCT  = 1.5f;

    private List<Goal> data = new ArrayList<>();
    private OnGoalClickListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM yyyy", Locale.getDefault());

    public GoalAdapter() {
        setHasStableIds(true);
    }

    public void setOnGoalClickListener(OnGoalClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<Goal> newData) {
        List<Goal> next = newData != null ? newData : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new GoalDiffCallback(data, next));
        this.data = next;
        result.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_goal, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Goal goal = data.get(position);

        long  target = goal.getTargetAmount() == 0 ? 1 : goal.getTargetAmount();
        long  saved  = goal.getSavedAmount();
        float pct    = Math.min(100f, (saved * 100.0f) / target);

        holder.tvEmoji.setText(goal.getEmoji());
        holder.tvName.setText(goal.getName());
        holder.tvTargetDate.setText("Target: " + dateFormat.format(new Date(goal.getTargetDateMs())));
        holder.tvPercent.setText(formatPercent(pct));
        holder.tvSavedAmount.setText(MoneyFormat.formatShortStyled(saved));

        SpannableStringBuilder targetLabel = new SpannableStringBuilder("/");
        targetLabel.append(MoneyFormat.formatShortStyled(target));
        holder.tvTargetAmount.setText(targetLabel);

        applyColor(holder, goal, pct);
        applyProgressBar(holder, saved, pct);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onGoalClick(goal);
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static void applyColor(ViewHolder holder, Goal goal, float pct) {
        try {
            String hex   = goal.getColorHex() != null ? goal.getColorHex() : DEFAULT_COLOR;
            int    color = Color.parseColor(hex);
            holder.tvPercent.setTextColor(color);
            holder.cvProgressFill.setCardBackgroundColor(color);
            View emojiParent = (View) holder.tvEmoji.getParent();
            if (emojiParent instanceof CardView) {
                ((CardView) emojiParent).setCardBackgroundColor(emojiTintColor(color));
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid color for goal '" + goal.getName() + "': " + e.getMessage());
        }
    }

    private static void applyProgressBar(ViewHolder holder, long saved, float pct) {
        float fill = (saved > 0 && pct < MIN_FILL_PCT) ? MIN_FILL_PCT : pct;

        LinearLayout.LayoutParams fillParams =
                (LinearLayout.LayoutParams) holder.cvProgressFill.getLayoutParams();
        fillParams.weight = fill;
        holder.cvProgressFill.setLayoutParams(fillParams);

        LinearLayout.LayoutParams emptyParams =
                (LinearLayout.LayoutParams) holder.cvProgressEmpty.getLayoutParams();
        emptyParams.weight = 100f - fill;
        holder.cvProgressEmpty.setLayoutParams(emptyParams);
    }

    private static int emojiTintColor(int color) {
        return Color.argb(40, Color.red(color), Color.green(color), Color.blue(color));
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

    private static final class GoalDiffCallback extends DiffUtil.Callback {
        private final List<Goal> oldList, newList;

        GoalDiffCallback(List<Goal> oldList, List<Goal> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int op, int np) {
            return oldList.get(op).getId().equals(newList.get(np).getId());
        }

        @Override
        public boolean areContentsTheSame(int op, int np) {
            Goal o = oldList.get(op), n = newList.get(np);
            return o.getSavedAmount()  == n.getSavedAmount()
                && o.getTargetAmount() == n.getTargetAmount()
                && o.getTargetDateMs() == n.getTargetDateMs()
                && Objects.equals(o.getName(),     n.getName())
                && Objects.equals(o.getEmoji(),    n.getEmoji())
                && Objects.equals(o.getColorHex(), n.getColorHex());
        }
    }

    // ── ViewHolder ──────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvEmoji, tvName, tvTargetDate, tvPercent;
        final TextView tvSavedAmount, tvTargetAmount;
        final CardView cvProgressFill, cvProgressEmpty;

        ViewHolder(@NonNull View v) {
            super(v);
            tvEmoji         = v.findViewById(R.id.tv_goal_emoji);
            tvName          = v.findViewById(R.id.tv_goal_name);
            tvTargetDate    = v.findViewById(R.id.tv_goal_target_date);
            tvPercent       = v.findViewById(R.id.tv_goal_percent);
            cvProgressFill  = v.findViewById(R.id.cv_progress_fill);
            cvProgressEmpty = v.findViewById(R.id.cv_progress_empty);
            tvSavedAmount   = v.findViewById(R.id.tv_saved_amount);
            tvTargetAmount  = v.findViewById(R.id.tv_target_amount);

            float density = v.getContext().getResources().getDisplayMetrics().density;
            LinearLayout.LayoutParams emptyParams =
                    (LinearLayout.LayoutParams) cvProgressEmpty.getLayoutParams();
            emptyParams.setMarginStart((int)(-6 * density));
            cvProgressEmpty.setLayoutParams(emptyParams);
        }
    }
}
