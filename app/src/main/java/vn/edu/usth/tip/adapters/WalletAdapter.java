package vn.edu.usth.tip.adapters;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.utils.MoneyFormat;

public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.WalletViewHolder> {

    public interface WalletActionListener {
        void onEdit(Wallet wallet, int position);
        void onDelete(Wallet wallet, int position);
        void onCardClick(Wallet wallet);
        void onToggleInclude(Wallet wallet, boolean included);
    }

    private List<Wallet> wallets;
    private final WalletActionListener listener;

    public WalletAdapter(List<Wallet> wallets, WalletActionListener listener) {
        this.wallets  = wallets != null ? wallets : new ArrayList<>();
        this.listener = listener;
        setHasStableIds(true);
    }

    public void updateData(List<Wallet> newWallets) {
        List<Wallet> next = newWallets != null ? newWallets : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new WalletDiffCallback(wallets, next));
        this.wallets = next;
        result.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return wallets.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public WalletViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wallet_card, parent, false);
        return new WalletViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull WalletViewHolder holder, int position) {
        Wallet wallet = wallets.get(position);

        holder.tvName.setText(wallet.getName());
        holder.tvBalance.setText(MoneyFormat.styled(wallet.getFormattedBalance()));
        holder.tvIcon.setText(wallet.getIcon());
        holder.tvType.setText(wallet.getTypeName());
        holder.cardIcon.setCardBackgroundColor(wallet.getColor());
        holder.switchInclude.setChecked(wallet.isIncludedInTotal());

        holder.cardWallet.setOnClickListener(v -> listener.onCardClick(wallet));

        holder.btnOptions.setOnClickListener(v -> showOptionsMenu(v, wallet, holder));

        holder.switchInclude.setOnCheckedChangeListener((btn, checked) ->
                listener.onToggleInclude(wallet, checked));
    }

    @Override
    public int getItemCount() {
        return wallets.size();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void showOptionsMenu(View anchor, Wallet wallet, WalletViewHolder holder) {
        Context themed = new ContextThemeWrapper(anchor.getContext(), R.style.LightPopupMenuStyle);
        PopupMenu popup = new PopupMenu(themed, anchor);
        popup.inflate(R.menu.menu_wallet_options);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                listener.onEdit(wallet, holder.getAdapterPosition());
                return true;
            }
            if (id == R.id.action_delete) {
                listener.onDelete(wallet, holder.getAdapterPosition());
                return true;
            }
            return false;
        });
        popup.show();
    }

    // ── DiffCallback ───────────────────────────────────────────────────

    private static final class WalletDiffCallback extends DiffUtil.Callback {
        private final List<Wallet> oldList, newList;

        WalletDiffCallback(List<Wallet> oldList, List<Wallet> newList) {
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
            Wallet o = oldList.get(op), n = newList.get(np);
            return o.getBalanceVnd()      == n.getBalanceVnd()
                && o.getColor()           == n.getColor()
                && o.getType()            == n.getType()
                && o.isIncludedInTotal()  == n.isIncludedInTotal()
                && Objects.equals(o.getName(), n.getName())
                && Objects.equals(o.getIcon(), n.getIcon());
        }
    }

    // ── ViewHolder ──────────────────────────────────────────────────────

    static class WalletViewHolder extends RecyclerView.ViewHolder {
        final CardView       cardWallet, cardIcon;
        final TextView       tvName, tvBalance, tvIcon, tvType;
        final SwitchMaterial switchInclude;
        final View           btnOptions;

        WalletViewHolder(@NonNull View itemView) {
            super(itemView);
            cardWallet    = itemView.findViewById(R.id.card_wallet);
            cardIcon      = itemView.findViewById(R.id.card_icon);
            tvName        = itemView.findViewById(R.id.tv_wallet_name);
            tvBalance     = itemView.findViewById(R.id.tv_balance);
            tvIcon        = itemView.findViewById(R.id.tv_icon);
            tvType        = itemView.findViewById(R.id.tv_wallet_type);
            switchInclude = itemView.findViewById(R.id.switch_include);
            btnOptions    = itemView.findViewById(R.id.btn_options);
        }
    }
}
