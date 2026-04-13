package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Wallet;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.UUID;

import vn.edu.usth.tip.R;

/**
 * Bottom sheet for adding or editing a Wallet.
 * Usage:
 *   AddWalletBottomSheet.newInstance(listener).show(parentFragmentManager, TAG);
 */
public class AddWalletBottomSheet extends BottomSheetDialogFragment {

    // ── Callback ──────────────────────────────────────────────────────
    public interface OnWalletSavedListener {
        void onWalletSaved(Wallet wallet);
    }

    // ── Constants ─────────────────────────────────────────────────────
    private static final String[] ICONS = {
            "💵", "💳", "🏦", "💰", "👛", "💜",
            "📈", "🏧", "🪙", "💎", "🏠", "🚗",
            "✈️", "🛒", "🎓", "🏋️", "🎮", "🍕",
            "💊", "🎁", "🐶", "🌴", "⚡", "🔑"
    };

    private static final int[] COLORS = {
            Color.parseColor("#735BF2"), // purple
            Color.parseColor("#0EA5E9"), // blue
            Color.parseColor("#D946EF"), // pink
            Color.parseColor("#22C55E"), // green
            Color.parseColor("#F97316"), // orange
            Color.parseColor("#EF4444"), // red
            Color.parseColor("#EAB308"), // yellow
            Color.parseColor("#14B8A6")  // teal
    };

    private static final int[] COLOR_VIEW_IDS = {
            R.id.chip_color_purple, R.id.chip_color_blue,
            R.id.chip_color_pink,   R.id.chip_color_green,
            R.id.chip_color_orange, R.id.chip_color_red,
            R.id.chip_color_yellow, R.id.chip_color_teal
    };

    // ── State ─────────────────────────────────────────────────────────
    private OnWalletSavedListener listener;
    private String selectedIcon     = "💳";
    private int    selectedColor    = Color.parseColor("#735BF2");
    private Wallet.Type selectedType = Wallet.Type.CASH;

    // ── Views (late-init) ─────────────────────────────────────────────
    private CardView     cardIconPreview;
    private TextView     tvIconPreview;
    private TextInputEditText etName, etBalance;
    private SwitchMaterial    switchInclude;

    // ── Factory ───────────────────────────────────────────────────────
    public static AddWalletBottomSheet newInstance(OnWalletSavedListener listener) {
        AddWalletBottomSheet sheet = new AddWalletBottomSheet();
        sheet.listener = listener;
        return sheet;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    @Override
    public int getTheme() {
        return com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_wallet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Bind views ────────────────────────────────────────────────
        cardIconPreview = view.findViewById(R.id.card_icon_preview);
        tvIconPreview   = view.findViewById(R.id.tv_icon_preview);
        etName          = view.findViewById(R.id.et_wallet_name);
        etBalance       = view.findViewById(R.id.et_wallet_balance);
        switchInclude   = view.findViewById(R.id.switch_include_in_total);

        // ── Close button ──────────────────────────────────────────────
        view.findViewById(R.id.btn_close_sheet).setOnClickListener(v -> dismiss());

        // ── Icon tap → show icon picker sheet ─────────────────────────
        cardIconPreview.setOnClickListener(v -> showIconPicker());
        view.findViewWithTag("Chọn icon"); // no-op, just label awareness

        // ── Color chips ───────────────────────────────────────────────
        for (int i = 0; i < COLOR_VIEW_IDS.length; i++) {
            final int color = COLORS[i];
            View chip = view.findViewById(COLOR_VIEW_IDS[i]);
            if (chip != null) {
                // Make circles
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(color);
                chip.setBackground(circle);

                chip.setOnClickListener(v -> {
                    selectedColor = color;
                    cardIconPreview.setCardBackgroundColor(selectedColor);
                    refreshColorSelection(view, color);
                });
            }
        }
        // Init first chip as selected
        refreshColorSelection(view, selectedColor);

        // ── Wallet type chips ─────────────────────────────────────────
        setupTypeChip(view, R.id.chip_type_cash,       Wallet.Type.CASH);
        setupTypeChip(view, R.id.chip_type_bank,       Wallet.Type.BANK);
        setupTypeChip(view, R.id.chip_type_ewallet,    Wallet.Type.EWALLET);
        setupTypeChip(view, R.id.chip_type_investment, Wallet.Type.INVESTMENT);
        // Initialize Cash as selected
        highlightTypeChip(view, R.id.chip_type_cash);

        // ── Save button ───────────────────────────────────────────────
        view.findViewById(R.id.btn_save_wallet).setOnClickListener(v -> saveWallet());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void setupTypeChip(View root, int chipId, Wallet.Type type) {
        CardView chip = root.findViewById(chipId);
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            selectedType = type;
            highlightTypeChip(root, chipId);
        });
    }

    private void highlightTypeChip(View root, int selectedChipId) {
        int[] allChipIds = {
                R.id.chip_type_cash, R.id.chip_type_bank,
                R.id.chip_type_ewallet, R.id.chip_type_investment
        };
        for (int id : allChipIds) {
            CardView chip = root.findViewById(id);
            if (chip == null) continue;
            if (id == selectedChipId) {
                chip.setCardBackgroundColor(selectedColor);
            } else {
                chip.setCardBackgroundColor(Color.parseColor("#252545"));
            }
        }
    }

    private void refreshColorSelection(View root, int activeColor) {
        for (int i = 0; i < COLOR_VIEW_IDS.length; i++) {
            View chip = root.findViewById(COLOR_VIEW_IDS[i]);
            if (chip == null) continue;
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(COLORS[i]);
            if (COLORS[i] == activeColor) {
                circle.setStroke(3, Color.WHITE);
            }
            chip.setBackground(circle);
        }
        // Also update the selected type chip highlight color
        highlightTypeChip(requireView(), getSelectedTypeChipId());
    }

    private int getSelectedTypeChipId() {
        switch (selectedType) {
            case BANK:       return R.id.chip_type_bank;
            case EWALLET:    return R.id.chip_type_ewallet;
            case INVESTMENT: return R.id.chip_type_investment;
            default:         return R.id.chip_type_cash;
        }
    }

    private void showIconPicker() {
        IconPickerBottomSheet picker = IconPickerBottomSheet.newInstance(ICONS, icon -> {
            selectedIcon = icon;
            tvIconPreview.setText(selectedIcon);
        });
        picker.show(getParentFragmentManager(), "icon_picker");
    }

    private void saveWallet() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            etName.setError("Vui lòng nhập tên ví");
            etName.requestFocus();
            return;
        }

        long balance = 0;
        String balanceStr = etBalance.getText() != null
                ? etBalance.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(balanceStr)) {
            try {
                balance = (long) Double.parseDouble(balanceStr.replace(",", "").replace(".", ""));
            } catch (NumberFormatException e) {
                balance = 0;
            }
        }

        boolean includeInTotal = switchInclude.isChecked();

        Wallet newWallet = new Wallet(
                UUID.randomUUID().toString(),
                name,
                balance,
                selectedIcon,
                selectedColor,
                selectedType,
                includeInTotal
        );

        if (listener != null) {
            listener.onWalletSaved(newWallet);
        }

        dismiss();
    }
}
