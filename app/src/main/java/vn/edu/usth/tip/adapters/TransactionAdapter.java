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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.utils.MoneyFormat;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {

    public interface OnTransactionClickListener {
        void onClick(Transaction tx);
    }

    private static final int ICON_BG_EXPENSE  = Color.parseColor("#2A1C1C");
    private static final int ICON_BG_INCOME   = Color.parseColor("#1C2A20");
    private static final int ICON_BG_TRANSFER = Color.parseColor("#1C1C2A");

    private static final int AMOUNT_EXPENSE  = Color.parseColor("#EF4444");
    private static final int AMOUNT_INCOME   = Color.parseColor("#22C55E");
    private static final int AMOUNT_TRANSFER = Color.parseColor("#A78BFA");

    private List<Transaction>               transactions;
    private final OnTransactionClickListener listener;

    public TransactionAdapter(List<Transaction> transactions,
                              OnTransactionClickListener listener) {
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        this.listener     = listener;
        setHasStableIds(true);
    }

    public void setData(List<Transaction> newList) {
        List<Transaction> next = newList != null ? newList : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new TxDiffCallback(transactions, next));
        this.transactions = next;
        result.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return transactions.get(position).getId().hashCode();
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
        h.tvTime.setText(tx.getFormattedDateTime());
        h.tvAmount.setText(MoneyFormat.styled(tx.getFormattedAmount()));
        h.tvWallet.setText(tx.getWalletName());

        h.cardIcon.setCardBackgroundColor(iconBgColor(tx.getType()));
        h.tvAmount.setTextColor(amountColor(tx.getType()));

        h.cardRecurring.setVisibility(tx.isRecurring() ? View.VISIBLE : View.GONE);

        h.cardTransaction.setOnClickListener(v -> {
            if (listener != null) listener.onClick(tx);
        });
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static int iconBgColor(Transaction.Type type) {
        switch (type) {
            case INCOME:   return ICON_BG_INCOME;
            case EXPENSE:  return ICON_BG_EXPENSE;
            default:       return ICON_BG_TRANSFER;
        }
    }

    private static int amountColor(Transaction.Type type) {
        switch (type) {
            case INCOME:   return AMOUNT_INCOME;
            case EXPENSE:  return AMOUNT_EXPENSE;
            default:       return AMOUNT_TRANSFER;
        }
    }

    // ── DiffCallback ───────────────────────────────────────────────────

    private static final class TxDiffCallback extends DiffUtil.Callback {
        private final List<Transaction> oldList, newList;

        TxDiffCallback(List<Transaction> oldList, List<Transaction> newList) {
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
            Transaction o = oldList.get(op), n = newList.get(np);
            return o.getAmountVnd()    == n.getAmountVnd()
                && o.getType()         == n.getType()
                && o.isRecurring()     == n.isRecurring()
                && o.getTimestampMs()  == n.getTimestampMs()
                && Objects.equals(o.getTitle(),      n.getTitle())
                && Objects.equals(o.getCategory(),   n.getCategory())
                && Objects.equals(o.getWalletName(), n.getWalletName())
                && Objects.equals(o.getIcon(),       n.getIcon());
        }
    }

    // ── ViewHolder ──────────────────────────────────────────────────────

    static class TxViewHolder extends RecyclerView.ViewHolder {
        final CardView cardTransaction, cardIcon, cardRecurring;
        final TextView tvIcon, tvTitle, tvCategory, tvTime, tvAmount, tvWallet;

        TxViewHolder(@NonNull View v) {
            super(v);
            cardTransaction = v.findViewById(R.id.card_transaction);
            cardIcon        = v.findViewById(R.id.card_tx_icon);
            cardRecurring   = v.findViewById(R.id.card_tx_recurring);
            tvIcon          = v.findViewById(R.id.tv_tx_icon);
            tvTitle         = v.findViewById(R.id.tv_tx_title);
            tvCategory      = v.findViewById(R.id.tv_tx_category);
            tvTime          = v.findViewById(R.id.tv_tx_time);
            tvAmount        = v.findViewById(R.id.tv_tx_amount);
            tvWallet        = v.findViewById(R.id.tv_tx_wallet);
        }
    }
}
