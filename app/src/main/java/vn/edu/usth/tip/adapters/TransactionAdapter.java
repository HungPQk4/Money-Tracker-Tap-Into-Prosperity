package vn.edu.usth.tip.adapters;

import vn.edu.usth.tip.R;

import vn.edu.usth.tip.models.Transaction;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {

    public interface OnTransactionClickListener {
        void onClick(Transaction tx);
    }

    private final List<Transaction>          transactions;
    private final OnTransactionClickListener listener;

    // Icon background colors per category
    private static final int COLOR_EXPENSE  = Color.parseColor("#2A1C1C"); // dark red tint
    private static final int COLOR_INCOME   = Color.parseColor("#1C2A20"); // dark green tint
    private static final int COLOR_TRANSFER = Color.parseColor("#1C1C2A"); // dark blue tint

    // Amount text colors
    private static final int TEXT_EXPENSE  = Color.parseColor("#EF4444");
    private static final int TEXT_INCOME   = Color.parseColor("#22C55E");
    private static final int TEXT_TRANSFER = Color.parseColor("#A78BFA");

    public TransactionAdapter(List<Transaction> transactions,
                              OnTransactionClickListener listener) {
        this.transactions = transactions;
        this.listener     = listener;
    }

    @NonNull
    @Override
    public TxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TxViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TxViewHolder h, int position) {
        Transaction tx = transactions.get(position);

        h.tvIcon.setText(tx.getIcon());
        h.tvTitle.setText(tx.getTitle());
        h.tvCategory.setText(tx.getCategory());
        h.tvTime.setText(tx.getFormattedTime());
        h.tvAmount.setText(tx.getFormattedAmount());
        h.tvWallet.setText(tx.getWalletName());

        // Color icon background by type
        int bgColor = tx.getType() == Transaction.Type.INCOME  ? COLOR_INCOME
                    : tx.getType() == Transaction.Type.EXPENSE ? COLOR_EXPENSE
                    : COLOR_TRANSFER;
        h.cardIcon.setCardBackgroundColor(bgColor);

        // Color amount by type
        int amtColor = tx.getType() == Transaction.Type.INCOME  ? TEXT_INCOME
                     : tx.getType() == Transaction.Type.EXPENSE ? TEXT_EXPENSE
                     : TEXT_TRANSFER;
        h.tvAmount.setTextColor(amtColor);

        h.cardTransaction.setOnClickListener(v -> {
            if (listener != null) listener.onClick(tx);
        });
    }

    @Override
    public int getItemCount() { return transactions.size(); }

    // ── Update helpers ────────────────────────────────────────────────

    public void setData(List<Transaction> newList) {
        transactions.clear();
        transactions.addAll(newList);
        notifyDataSetChanged();
    }

    // ── ViewHolder ────────────────────────────────────────────────────

    static class TxViewHolder extends RecyclerView.ViewHolder {
        CardView cardTransaction, cardIcon;
        TextView tvIcon, tvTitle, tvCategory, tvTime, tvAmount, tvWallet;

        TxViewHolder(@NonNull View v) {
            super(v);
            cardTransaction = v.findViewById(R.id.card_transaction);
            cardIcon        = v.findViewById(R.id.card_tx_icon);
            tvIcon          = v.findViewById(R.id.tv_tx_icon);
            tvTitle         = v.findViewById(R.id.tv_tx_title);
            tvCategory      = v.findViewById(R.id.tv_tx_category);
            tvTime          = v.findViewById(R.id.tv_tx_time);
            tvAmount        = v.findViewById(R.id.tv_tx_amount);
            tvWallet        = v.findViewById(R.id.tv_tx_wallet);
        }
    }
}
