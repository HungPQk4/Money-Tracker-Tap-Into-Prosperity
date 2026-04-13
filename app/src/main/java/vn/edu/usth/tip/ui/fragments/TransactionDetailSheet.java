package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import vn.edu.usth.tip.R;

/**
 * BottomSheet hiển thị chi tiết một giao dịch.
 * Dùng: TransactionDetailSheet.newInstance(tx, listener).show(...)
 */
public class TransactionDetailSheet extends BottomSheetDialogFragment {

    public interface OnTransactionActionListener {
        void onEdit(Transaction tx);
        void onDelete(Transaction tx);
    }

    private Transaction tx;
    private OnTransactionActionListener actionListener;

    // Colors
    private static final int COLOR_EXPENSE_BG   = Color.parseColor("#2D1C1C");
    private static final int COLOR_INCOME_BG    = Color.parseColor("#1C2A20");
    private static final int COLOR_TRANSFER_BG  = Color.parseColor("#1C1C2A");
    private static final int COLOR_EXPENSE_TEXT = Color.parseColor("#EF4444");
    private static final int COLOR_INCOME_TEXT  = Color.parseColor("#22C55E");
    private static final int COLOR_TRANSFER_TEXT= Color.parseColor("#A78BFA");
    private static final int COLOR_ICON_EXPENSE = Color.parseColor("#2A1C1C");
    private static final int COLOR_ICON_INCOME  = Color.parseColor("#1C2A20");
    private static final int COLOR_ICON_XFER    = Color.parseColor("#1C1C2A");

    public static TransactionDetailSheet newInstance(
            Transaction tx,
            OnTransactionActionListener listener) {
        TransactionDetailSheet sheet = new TransactionDetailSheet();
        sheet.tx = tx;
        sheet.actionListener = listener;
        return sheet;
    }

    @Override
    public int getTheme() {
        return com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_transaction_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (tx == null) { dismiss(); return; }

        // ── Determine colors for type ─────────────────────────────────
        int amountColor, badgeBg, iconBg;
        String typeLabel;
        if (tx.getType() == Transaction.Type.INCOME) {
            amountColor = COLOR_INCOME_TEXT;
            badgeBg     = COLOR_INCOME_BG;
            iconBg      = COLOR_ICON_INCOME;
            typeLabel   = "Thu nhập";
        } else if (tx.getType() == Transaction.Type.TRANSFER) {
            amountColor = COLOR_TRANSFER_TEXT;
            badgeBg     = COLOR_TRANSFER_BG;
            iconBg      = COLOR_ICON_XFER;
            typeLabel   = "Chuyển khoản";
        } else {
            amountColor = COLOR_EXPENSE_TEXT;
            badgeBg     = COLOR_EXPENSE_BG;
            iconBg      = COLOR_ICON_EXPENSE;
            typeLabel   = "Chi tiêu";
        }

        // ── Bind hero section ─────────────────────────────────────────
        CardView cardIcon = view.findViewById(R.id.card_detail_icon);
        cardIcon.setCardBackgroundColor(iconBg);

        ((TextView) view.findViewById(R.id.tv_detail_icon)).setText(tx.getIcon());

        TextView tvAmount = view.findViewById(R.id.tv_detail_amount);
        tvAmount.setText(tx.getFormattedAmount());
        tvAmount.setTextColor(amountColor);

        ((TextView) view.findViewById(R.id.tv_detail_title)).setText(tx.getTitle());

        CardView cardType = view.findViewById(R.id.card_detail_type);
        cardType.setCardBackgroundColor(badgeBg);
        TextView tvType = view.findViewById(R.id.tv_detail_type);
        tvType.setText(typeLabel);
        tvType.setTextColor(amountColor);

        // ── Bind detail rows ──────────────────────────────────────────
        ((TextView) view.findViewById(R.id.tv_detail_category)).setText(tx.getCategory());
        ((TextView) view.findViewById(R.id.tv_detail_wallet)).setText(tx.getWalletName());

        // Format full date-time
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault());
        String dateStr = sdf.format(new Date(tx.getTimestampMs()));
        ((TextView) view.findViewById(R.id.tv_detail_datetime)).setText(dateStr);

        // Note (placeholder – will be real field once notes are added to Transaction model)
        String note = tx.getNote();
        ((TextView) view.findViewById(R.id.tv_detail_note)).setText(
                (note == null || note.isEmpty()) ? "Không có ghi chú" : note);

        // ── Action buttons ────────────────────────────────────────────
        view.findViewById(R.id.btn_detail_edit).setOnClickListener(v -> {
            dismiss();
            if (actionListener != null) actionListener.onEdit(tx);
        });

        view.findViewById(R.id.btn_detail_delete).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Xóa giao dịch")
                        .setMessage("Bạn có chắc muốn xóa giao dịch \"" + tx.getTitle() + "\"?")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            if (actionListener != null) actionListener.onDelete(tx);
                            dismiss();
                        })
                        .show()
        );
    }
}
