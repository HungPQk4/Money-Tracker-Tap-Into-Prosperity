package vn.edu.usth.tip.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.DebtLoan;
import vn.edu.usth.tip.utils.MoneyFormat;

public class DebtLoanAdapter extends RecyclerView.Adapter<DebtLoanAdapter.ViewHolder> {

    public interface OnDebtClickListener {
        void onDebtClick(DebtLoan debt);
    }

    private static final int COLOR_OWE_TEXT  = Color.parseColor("#E53935");
    private static final int COLOR_OWE_BG    = Color.parseColor("#FFEEED");
    private static final int COLOR_LENT_TEXT = Color.parseColor("#2E7D32");
    private static final int COLOR_LENT_BG   = Color.parseColor("#E8F5E9");

    private List<DebtLoan> data = new ArrayList<>();
    private OnDebtClickListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public DebtLoanAdapter() {
        setHasStableIds(true);
    }

    public void setOnDebtClickListener(OnDebtClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<DebtLoan> newData) {
        List<DebtLoan> next = newData != null ? newData : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DebtDiffCallback(data, next));
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
                .inflate(R.layout.item_debt, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DebtLoan item = data.get(position);

        holder.tvPersonName.setText(item.getPersonName());
        holder.tvReason.setText(item.getReason());
        holder.tvDueDate.setText("Đến hạn: " + dateFormat.format(new Date(item.getDueDate())));
        holder.tvAmount.setText(MoneyFormat.formatShortStyled(item.getAmount()));
        holder.tvAvatarInitial.setText(avatarInitial(item.getPersonName()));

        applyColorScheme(holder, item.getType());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDebtClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static String avatarInitial(String name) {
        return (name != null && !name.isEmpty())
                ? String.valueOf(name.charAt(0)).toUpperCase()
                : "?";
    }

    private static void applyColorScheme(ViewHolder holder, int type) {
        if (type == DebtLoan.TYPE_I_OWE) {
            holder.cvAvatar.setCardBackgroundColor(COLOR_OWE_BG);
            holder.tvAvatarInitial.setTextColor(COLOR_OWE_TEXT);
            holder.tvAmount.setTextColor(COLOR_OWE_TEXT);
            holder.tvTag.setText("Mình nợ");
            holder.tvTag.setTextColor(COLOR_OWE_TEXT);
            holder.tvTag.setBackgroundResource(R.drawable.bg_debt_tag_red);
        } else {
            holder.cvAvatar.setCardBackgroundColor(COLOR_LENT_BG);
            holder.tvAvatarInitial.setTextColor(COLOR_LENT_TEXT);
            holder.tvAmount.setTextColor(COLOR_LENT_TEXT);
            holder.tvTag.setText("Cho vay");
            holder.tvTag.setTextColor(COLOR_LENT_TEXT);
            holder.tvTag.setBackgroundResource(R.drawable.bg_debt_tag_green);
        }
    }

    // ── DiffCallback ───────────────────────────────────────────────────

    private static final class DebtDiffCallback extends DiffUtil.Callback {
        private final List<DebtLoan> oldList, newList;

        DebtDiffCallback(List<DebtLoan> oldList, List<DebtLoan> newList) {
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
            DebtLoan o = oldList.get(op), n = newList.get(np);
            return o.getAmount()  == n.getAmount()
                && o.getDueDate() == n.getDueDate()
                && o.getType()    == n.getType()
                && Objects.equals(o.getPersonName(), n.getPersonName())
                && Objects.equals(o.getReason(),     n.getReason());
        }
    }

    // ── ViewHolder ──────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView       tvPersonName, tvReason, tvAmount, tvTag, tvDueDate, tvAvatarInitial;
        final MaterialCardView cvAvatar;

        ViewHolder(@NonNull View v) {
            super(v);
            tvPersonName    = v.findViewById(R.id.tv_debt_person_name);
            tvReason        = v.findViewById(R.id.tv_debt_reason);
            tvAmount        = v.findViewById(R.id.tv_debt_amount);
            tvTag           = v.findViewById(R.id.tv_debt_tag);
            tvDueDate       = v.findViewById(R.id.tv_debt_due_date);
            tvAvatarInitial = v.findViewById(R.id.tv_avatar_initial);
            cvAvatar        = v.findViewById(R.id.cv_avatar);
        }
    }
}
