package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Wallet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import vn.edu.usth.tip.R;

/**
 * BottomSheet hiển thị chi tiết ví.
 */
public class WalletDetailSheet extends BottomSheetDialogFragment {

    public interface OnDetailActionListener {
        void onEdit(Wallet wallet);
        void onDelete(Wallet wallet);
    }

    private Wallet wallet;
    private OnDetailActionListener listener;

    public WalletDetailSheet() {}

    public static WalletDetailSheet newInstance(Wallet wallet, OnDetailActionListener listener) {
        WalletDetailSheet fragment = new WalletDetailSheet();
        fragment.wallet = wallet;
        fragment.listener = listener;
        return fragment;
    }

    @Override
    public int getTheme() {
        return com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_wallet_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (wallet == null) {
            dismiss();
            return;
        }

        // Bind Views
        TextView tvIcon     = view.findViewById(R.id.tv_detail_icon);
        CardView cardIconBg = view.findViewById(R.id.card_detail_icon_bg);
        TextView tvBalance  = view.findViewById(R.id.tv_detail_balance);
        TextView tvName     = view.findViewById(R.id.tv_detail_name);
        TextView tvType     = view.findViewById(R.id.tv_detail_type);
        TextView tvIncluded = view.findViewById(R.id.tv_detail_included);

        // Pre-fill
        tvIcon.setText(wallet.getIcon());
        cardIconBg.setCardBackgroundColor(wallet.getColor());
        tvBalance.setText(String.format("₫%,.0f", (double) wallet.getBalanceVnd())
                .replace(",", "."));
        tvName.setText(wallet.getName());
        tvType.setText(getWalletTypeName(wallet.getType()));
        tvIncluded.setText(wallet.isIncludedInTotal() ? "Có" : "Không");

        // Action Buttons
        view.findViewById(R.id.btn_detail_close).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_detail_edit).setOnClickListener(v -> {
            if (listener != null) listener.onEdit(wallet);
            dismiss();
        });
    }

    private String getWalletTypeName(Wallet.Type type) {
        switch (type) {
            case BANK:       return "Ngân hàng";
            case EWALLET:    return "Ví điện tử";
            case INVESTMENT: return "Đầu tư";
            case CASH:
            default:         return "Tiền mặt";
        }
    }
}
