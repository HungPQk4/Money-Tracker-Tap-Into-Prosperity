package vn.edu.usth.tip;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
        void onAddCategoryClick();
    }

    private final List<Category> categories;
    private final OnCategoryClickListener listener;
    private int selectedPosition = 0; // "Ăn uống" is first by default

    public CategoryAdapter(List<Category> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_card, parent, false);
        return new CategoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category cat = categories.get(position);
        holder.tvIcon.setText(cat.getIcon());
        holder.tvName.setText(cat.getName());

        // Highlight selection
        if (cat.isAddButton()) {
            holder.tvIcon.setTextColor(Color.parseColor("#735BF2"));
            holder.tvName.setTextColor(Color.parseColor("#735BF2"));
            holder.card.setCardBackgroundColor(Color.parseColor("#1A1B29"));
            holder.card.setCardElevation(0f);
        } else {
            if (position == selectedPosition) {
                holder.card.setCardBackgroundColor(Color.parseColor("#2D2E45"));
                holder.card.setCardElevation(4f);
                holder.tvName.setTextColor(Color.WHITE);
            } else {
                holder.card.setCardBackgroundColor(Color.parseColor("#1A1B29"));
                holder.card.setCardElevation(0f);
                holder.tvName.setTextColor(Color.parseColor("#8C8D99"));
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (cat.isAddButton()) {
                if (listener != null) listener.onAddCategoryClick();
            } else {
                int previous = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(previous);
                notifyItemChanged(selectedPosition);
                if (listener != null) listener.onCategoryClick(cat);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public Category getSelectedCategory() {
        if (selectedPosition >= 0 && selectedPosition < categories.size()) {
            return categories.get(selectedPosition);
        }
        return null;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvIcon, tvName;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_category);
            tvIcon = itemView.findViewById(R.id.tv_category_icon);
            tvName = itemView.findViewById(R.id.tv_category_name);
        }
    }
}
