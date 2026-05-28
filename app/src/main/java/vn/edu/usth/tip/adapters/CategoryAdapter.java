package vn.edu.usth.tip.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Category;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
        void onAddCategoryClick();
    }

    public interface OnCategoryLongClickListener {
        void onCategoryLongClick(Category category);
    }

    private static final int COLOR_BRAND      = Color.parseColor("#735BF2");
    private static final int COLOR_BRAND_TINT = Color.parseColor("#ECE9FF");
    private static final int COLOR_WHITE      = Color.WHITE;
    private static final int COLOR_TEXT_MUTED = Color.parseColor("#6B6584");
    private static final int COLOR_ICON_DARK  = Color.parseColor("#1A1730");

    private List<Category> categories;
    private final OnCategoryClickListener listener;
    private OnCategoryLongClickListener longClickListener;
    private int selectedPosition = 0;

    public CategoryAdapter(List<Category> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener   = listener;
        setHasStableIds(true);
    }

    public void setOnLongClickListener(OnCategoryLongClickListener l) {
        this.longClickListener = l;
    }

    @Override
    public long getItemId(int position) {
        return categories.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_card, parent, false);
        return new CategoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category cat = categories.get(position);
        holder.tvIcon.setText(cat.getIcon());
        holder.tvName.setText(cat.getName());
        applyChipStyle(holder, cat, position);
        bindClickListeners(holder, cat);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    // ── Selection helpers ──────────────────────────────────────────────

    public Category getSelectedCategory() {
        if (selectedPosition >= 0 && selectedPosition < categories.size()) {
            Category cat = categories.get(selectedPosition);
            return cat.isAddButton() ? null : cat;
        }
        return null;
    }

    public void updateData(List<Category> newCategories) {
        if (newCategories == null) return;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new CategoryDiffCallback(categories, newCategories));
        this.categories = newCategories;
        if (selectedPosition >= categories.size()) selectedPosition = 0;
        result.dispatchUpdatesTo(this);
    }

    public void resetSelection() {
        int prev = selectedPosition;
        selectedPosition = 0;
        if (prev != 0) {
            notifyItemChanged(prev);
            notifyItemChanged(0);
        }
    }

    public void selectCategoryByName(String name) {
        if (name == null) return;
        for (int i = 0; i < categories.size(); i++) {
            if (name.equals(categories.get(i).getName())) {
                int prev = selectedPosition;
                selectedPosition = i;
                if (prev != selectedPosition) {
                    notifyItemChanged(prev);
                    notifyItemChanged(selectedPosition);
                }
                break;
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────

    private void applyChipStyle(CategoryViewHolder holder, Category cat, int position) {
        if (cat.isAddButton()) {
            holder.card.setCardBackgroundColor(COLOR_BRAND_TINT);
            holder.card.setCardElevation(0f);
            holder.tvIcon.setTextColor(COLOR_BRAND);
            holder.tvName.setTextColor(COLOR_BRAND);
        } else if (position == selectedPosition) {
            holder.card.setCardBackgroundColor(COLOR_BRAND);
            holder.card.setCardElevation(4f);
            holder.tvIcon.setTextColor(COLOR_WHITE);
            holder.tvName.setTextColor(COLOR_WHITE);
        } else {
            holder.card.setCardBackgroundColor(COLOR_WHITE);
            holder.card.setCardElevation(2f);
            holder.tvIcon.setTextColor(COLOR_ICON_DARK);
            holder.tvName.setTextColor(COLOR_TEXT_MUTED);
        }
    }

    private void bindClickListeners(CategoryViewHolder holder, Category cat) {
        holder.itemView.setOnClickListener(v -> {
            if (cat.isAddButton()) {
                if (listener != null) listener.onAddCategoryClick();
            } else {
                int previous     = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(previous);
                notifyItemChanged(selectedPosition);
                if (listener != null) listener.onCategoryClick(cat);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!cat.isAddButton() && !cat.isSystem()
                    && cat.getUserId() != null && longClickListener != null) {
                longClickListener.onCategoryLongClick(cat);
            }
            return true;
        });
    }

    // ── DiffCallback ───────────────────────────────────────────────────

    private static final class CategoryDiffCallback extends DiffUtil.Callback {
        private final List<Category> oldList, newList;

        CategoryDiffCallback(List<Category> oldList, List<Category> newList) {
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
            Category o = oldList.get(op), n = newList.get(np);
            return o.isAddButton() == n.isAddButton()
                && o.isSystem()    == n.isSystem()
                && Objects.equals(o.getName(), n.getName())
                && Objects.equals(o.getIcon(), n.getIcon());
        }
    }

    // ── ViewHolder ──────────────────────────────────────────────────────

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        final CardView card;
        final TextView tvIcon, tvName;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            card   = itemView.findViewById(R.id.card_category);
            tvIcon = itemView.findViewById(R.id.tv_category_icon);
            tvName = itemView.findViewById(R.id.tv_category_name);
        }
    }
}
