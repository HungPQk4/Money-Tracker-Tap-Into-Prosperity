package vn.edu.usth.tip.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Goal;

public class GoalAdapter extends RecyclerView.Adapter<GoalAdapter.ViewHolder> {

    public interface OnGoalClickListener {
        void onGoalClick(Goal goal);
    }

    private List<Goal> data = new ArrayList<>();
    private OnGoalClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

    public void setOnGoalClickListener(OnGoalClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<Goal> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goal, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Goal item = data.get(position);

        holder.tvEmoji.setText(item.getEmoji());
        holder.tvName.setText(item.getName());
        holder.tvTargetDate.setText("Target: " + dateFormat.format(new Date(item.getTargetDateMs())));

        long target = item.getTargetAmount();
        long saved = item.getSavedAmount();
        if (target == 0) target = 1; // Prevent division by zero
        float percentFloat = (saved * 100.0f) / target;
        if (percentFloat > 100f) percentFloat = 100f;

        String percentText;
        if (percentFloat == 0f || percentFloat >= 100f || percentFloat == (int) percentFloat) {
            percentText = String.format(Locale.US, "%d%%", (int) percentFloat);
        } else if (percentFloat < 0.01f) {
            percentText = "<0.01%";
        } else if (percentFloat < 1f) {
            percentText = String.format(Locale.US, "%.2f%%", percentFloat);
        } else {
            percentText = String.format(Locale.US, "%.1f%%", percentFloat);
        }
        holder.tvPercent.setText(percentText);
        
        String savedStr = "đ" + String.format("%,d", saved).replace(",", ".");
        String targetStr = "of đ" + String.format("%,d", target).replace(",", ".");
        holder.tvSavedAmount.setText(savedStr);
        holder.tvTargetAmount.setText(targetStr);

        // Styling (Colors)
        try {
            int mainColor = Color.parseColor(item.getColorHex() != null ? item.getColorHex() : "#8288D8");
            
            holder.tvPercent.setTextColor(mainColor);
            holder.cvProgressFill.setCardBackgroundColor(mainColor);
            
            // Emoji background color - usually a darker tone of mainColor. Let's just use semi-transparent.
            int alphaMain = Color.argb(40, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor));
            View parentCard = (View) holder.tvEmoji.getParent();
            if (parentCard instanceof CardView) {
                ((CardView) parentCard).setCardBackgroundColor(alphaMain);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Set weight of progress bar
        float visualFill = percentFloat;
        // Đảm bảo thanh trạng thái có hiển thị ít nhất một chút màu nếu đã có tiền
        if (saved > 0 && visualFill < 1.5f) {
            visualFill = 1.5f;
        }
        
        LinearLayout.LayoutParams paramsFill = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, visualFill);
        holder.cvProgressFill.setLayoutParams(paramsFill);
        
        LinearLayout.LayoutParams paramsEmpty = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100f - visualFill);
        // Match the -6dp marginStart from original layout to overlap slightly if needed, or remove it.
        paramsEmpty.setMarginStart((int) (-6 * holder.itemView.getContext().getResources().getDisplayMetrics().density));
        holder.cvProgressEmpty.setLayoutParams(paramsEmpty);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGoalClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmoji;
        TextView tvName;
        TextView tvTargetDate;
        TextView tvPercent;
        CardView cvProgressFill;
        CardView cvProgressEmpty;
        TextView tvSavedAmount;
        TextView tvTargetAmount;

        ViewHolder(View v) {
            super(v);
            tvEmoji = v.findViewById(R.id.tv_goal_emoji);
            tvName = v.findViewById(R.id.tv_goal_name);
            tvTargetDate = v.findViewById(R.id.tv_goal_target_date);
            tvPercent = v.findViewById(R.id.tv_goal_percent);
            cvProgressFill = v.findViewById(R.id.cv_progress_fill);
            cvProgressEmpty = v.findViewById(R.id.cv_progress_empty);
            tvSavedAmount = v.findViewById(R.id.tv_saved_amount);
            tvTargetAmount = v.findViewById(R.id.tv_target_amount);
        }
    }
}
