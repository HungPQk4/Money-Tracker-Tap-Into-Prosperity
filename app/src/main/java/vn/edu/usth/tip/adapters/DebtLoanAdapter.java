package vn.edu.usth.tip.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        // Assume layout item_debt.xml exists
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_debt, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DebtLoan item = data.get(position);

        // Hiển thị tên người giao dịch và lý do
        holder.tvPersonName.setText(item.getPersonName());
        holder.tvReason.setText(item.getReason());

        // Định dạng ngày hẹn
        holder.tvDueDate.setText("Đến hạn: " + dateFormat.format(new Date(item.getDueDate())));

        // Định dạng tiền tệ: đ1,500,000
        String formattedAmount = "đ" + String.format("%,d", item.getAmount()).replace(",", ".");
        holder.tvAmount.setText(formattedAmount);

        // Logic màu sắc và Tag (Type)
        GradientDrawable tagBackground = (GradientDrawable) holder.tvTag.getBackground();

        if (item.getType() == DebtLoan.TYPE_I_OWE) {
            // Mình nợ
            holder.tvTag.setText("I owe");
            holder.tvTag.setTextColor(Color.parseColor("#FF7043")); // Đỏ cam
            tagBackground.setStroke(2, Color.parseColor("#FF7043"));
            holder.tvAmount.setTextColor(Color.parseColor("#FF7043"));
        } else {
            // Cho vay
            holder.tvTag.setText("Lent");
            holder.tvTag.setTextColor(Color.parseColor("#26A69A")); // Xanh lục
            tagBackground.setStroke(2, Color.parseColor("#26A69A"));
            holder.tvAmount.setTextColor(Color.parseColor("#26A69A"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDebtClick(item);
            }
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

        ViewHolder(View v) {
            super(v);
            // Giả định các ID này sẽ tồn tại trong item_debt.xml
            tvPersonName = v.findViewById(R.id.tv_debt_person_name);
            tvReason = v.findViewById(R.id.tv_debt_reason);
            tvAmount = v.findViewById(R.id.tv_debt_amount);
            tvTag = v.findViewById(R.id.tv_debt_tag);
            tvDueDate = v.findViewById(R.id.tv_debt_due_date);
        }
    }
}
