package vn.edu.usth.tip.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.DebtLoan;

public class DebtLoanAdapter extends RecyclerView.Adapter<DebtLoanAdapter.ViewHolder> {

    public interface OnDebtClickListener {
        void onDebtClick(DebtLoan debt);
    }

    private List<DebtLoan> data = new ArrayList<>();
    private OnDebtClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    // Colors
    private static final int COLOR_RED    = Color.parseColor("#E53935");
    private static final int COLOR_GREEN  = Color.parseColor("#2E7D32");
    private static final int COLOR_RED_BG = Color.parseColor("#FFEEED");
    private static final int COLOR_GRN_BG = Color.parseColor("#E8F5E9");

    public void setOnDebtClickListener(OnDebtClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<DebtLoan> newData) {
        this.data = newData;
        notifyDataSetChanged();
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

        // Person name and content
        holder.tvPersonName.setText(item.getPersonName());
        holder.tvReason.setText(item.getReason());
        holder.tvDueDate.setText("Đến hạn: " + dateFormat.format(new Date(item.getDueDate())));

        // Format amount: ₫1.500.000
        String formattedAmount = "₫" + String.format("%,d", item.getAmount()).replace(",", ".");
        holder.tvAmount.setText(formattedAmount);

        // Avatar initial: first letter of name
        String name = item.getPersonName();
        String initial = (name != null && !name.isEmpty())
                ? String.valueOf(name.charAt(0)).toUpperCase()
                : "?";
        holder.tvAvatarInitial.setText(initial);

        // Apply color scheme by type
        if (item.getType() == DebtLoan.TYPE_I_OWE) {
            // I owe — red scheme
            holder.tvAvatarInitial.setTextColor(COLOR_RED);
            holder.cvAvatar.setCardBackgroundColor(COLOR_RED_BG);
            holder.tvTag.setText("Mình nợ");
            holder.tvTag.setTextColor(COLOR_RED);
            holder.tvTag.setBackgroundResource(R.drawable.bg_debt_tag_red);
            holder.tvAmount.setTextColor(COLOR_RED);
        } else {
            // Lent — green scheme
            holder.tvAvatarInitial.setTextColor(COLOR_GREEN);
            holder.cvAvatar.setCardBackgroundColor(COLOR_GRN_BG);
            holder.tvTag.setText("Cho vay");
            holder.tvTag.setTextColor(COLOR_GREEN);
            holder.tvTag.setBackgroundResource(R.drawable.bg_debt_tag_green);
            holder.tvAmount.setTextColor(COLOR_GREEN);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDebtClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPersonName;
        TextView tvReason;
        TextView tvAmount;
        TextView tvTag;
        TextView tvDueDate;
        TextView tvAvatarInitial;
        MaterialCardView cvAvatar;

        ViewHolder(View v) {
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
