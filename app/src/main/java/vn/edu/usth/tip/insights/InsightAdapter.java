package vn.edu.usth.tip.insights;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;

public class InsightAdapter extends RecyclerView.Adapter<InsightAdapter.ViewHolder> {

    private static final int COLOR_HIGH   = Color.parseColor("#E53935"); // đỏ
    private static final int COLOR_MEDIUM = Color.parseColor("#F57C00"); // cam
    private static final int COLOR_LOW    = Color.parseColor("#43A047"); // xanh lá

    private List<Insight> items = Collections.emptyList();

    public void setData(List<Insight> newItems) {
        items = newItems != null ? newItems : Collections.emptyList();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_insight_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Insight insight = items.get(position);
        holder.tvTitle.setText(insight.title);
        holder.tvBody.setText(insight.body);

        int color;
        if (insight.priority == InsightPriority.HIGH) color = COLOR_HIGH;
        else if (insight.priority == InsightPriority.MEDIUM) color = COLOR_MEDIUM;
        else color = COLOR_LOW;

        holder.viewPriorityBar.setBackgroundColor(color);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View viewPriorityBar;
        final TextView tvTitle;
        final TextView tvBody;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewPriorityBar = itemView.findViewById(R.id.view_priority_bar);
            tvTitle = itemView.findViewById(R.id.tv_insight_title);
            tvBody = itemView.findViewById(R.id.tv_insight_body);
        }
    }
}
