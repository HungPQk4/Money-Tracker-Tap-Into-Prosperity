package vn.edu.usth.tip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.List;

public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.WalletViewHolder> {

    public interface WalletActionListener {
        void onEdit(Wallet wallet, int position);
        void onDelete(Wallet wallet, int position);
        void onCardClick(Wallet wallet);
        void onToggleInclude(Wallet wallet, boolean included);
    }

    private final List<Wallet> wallets;
    private final WalletActionListener listener;

    public WalletAdapter(List<Wallet> wallets, WalletActionListener listener) {
        this.wallets = wallets;
        this.listener = listener;
    }

    @NonNull
    @Override
    public WalletViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wallet_card, parent, false);
        return new WalletViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WalletViewHolder holder, int position) {
        Wallet wallet = wallets.get(position);

        holder.tvName.setText(wallet.getName());
        holder.tvBalance.setText(wallet.getFormattedBalance());
        holder.tvIcon.setText(wallet.getIcon());
        holder.tvType.setText(wallet.getTypeName());
        holder.cardIcon.setCardBackgroundColor(wallet.getColor());
        holder.switchInclude.setChecked(wallet.isIncludedInTotal());

        // Card click
        holder.cardWallet.setOnClickListener(v -> listener.onCardClick(wallet));

        // Options menu
        holder.btnOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.inflate(R.menu.menu_wallet_options);
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_edit) {
                    listener.onEdit(wallet, holder.getAdapterPosition());
                    return true;
                } else if (id == R.id.action_delete) {
                    listener.onDelete(wallet, holder.getAdapterPosition());
                    return true;
                }
                return false;
            });
            popup.show();
        });

        // Toggle include in total
        holder.switchInclude.setOnCheckedChangeListener((btn, checked) ->
                listener.onToggleInclude(wallet, checked)
        );
    }

    @Override
    public int getItemCount() {
        return wallets.size();
    }

    static class WalletViewHolder extends RecyclerView.ViewHolder {
        CardView cardWallet, cardIcon;
        TextView tvName, tvBalance, tvIcon, tvType;
        SwitchMaterial switchInclude;
        View btnOptions;

        WalletViewHolder(@NonNull View itemView) {
            super(itemView);
            cardWallet   = itemView.findViewById(R.id.card_wallet);
            cardIcon     = itemView.findViewById(R.id.card_icon);
            tvName       = itemView.findViewById(R.id.tv_wallet_name);
            tvBalance    = itemView.findViewById(R.id.tv_balance);
            tvIcon       = itemView.findViewById(R.id.tv_icon);
            tvType       = itemView.findViewById(R.id.tv_wallet_type);
            switchInclude= itemView.findViewById(R.id.switch_include);
            btnOptions   = itemView.findViewById(R.id.btn_options);
        }
    }
}