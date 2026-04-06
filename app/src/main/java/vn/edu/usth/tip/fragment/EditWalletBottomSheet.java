package vn.edu.usth.tip.fragment;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.Wallet;

/**
 * Bottom sheet để chỉnh sửa một Wallet đã tồn tại.
 * Callback trả về wallet đã cập nhật, hoặc thông báo xóa.
 */
public class EditWalletBottomSheet extends BottomSheetDialogFragment {

    // ── Callback ──────────────────────────────────────────────────────
    public interface OnWalletEditListener {
        void onWalletUpdated(Wallet updated, int position);
        void onWalletDeleted(int position);
    }

    // ── Color constants ───────────────────────────────────────────────
    private static final int[] COLORS = {
            Color.parseColor("#735BF2"),
            Color.parseColor("#0EA5E9"),
            Color.parseColor("#D946EF"),
            Color.parseColor("#22C55E"),
            Color.parseColor("#F97316"),
            Color.parseColor("#EF4444"),
            Color.parseColor("#EAB308"),
            Color.parseColor("#14B8A6")
    };

    private static final int[] COLOR_VIEW_IDS = {
            R.id.edit_chip_color_purple, R.id.edit_chip_color_blue,
            R.id.edit_chip_color_pink,   R.id.edit_chip_color_green,
            R.id.edit_chip_color_orange, R.id.edit_chip_color_red,
            R.id.edit_chip_color_yellow, R.id.edit_chip_color_teal
    };

    private static final String[] ICONS = {
            "💵", "💳", "🏦", "💰", "👛", "💜",
            "📈", "🏧", "🪙", "💎", "🏠", "🚗",
            "✈️", "🛒", "🎓", "🏋️", "🎮", "🍕",
            "💊", "🎁", "🐶", "🌴", "⚡", "🔑"
    };

    // ── State ─────────────────────────────────────────────────────────
    private Wallet             wallet;
    private int                position;
    private OnWalletEditListener listener;

    private String        selectedIcon;
    private int           selectedColor;
    private Wallet.Type   selectedType;

    // ── Views ─────────────────────────────────────────────────────────
    private CardView          cardIconPreview;
    private TextView          tvIconPreview;
    private TextInputEditText etName, etBalance;
    private SwitchMaterial    switchInclude;

    // ── Factory ───────────────────────────────────────────────────────
    public static EditWalletBottomSheet newInstance(
            Wallet wallet, int position, OnWalletEditListener listener) {
        EditWalletBottomSheet sheet = new EditWalletBottomSheet();
        sheet.wallet   = wallet;
        sheet.position = position;
        sheet.listener = listener;
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
        return inflater.inflate(R.layout.bottom_sheet_edit_wallet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Init state from existing wallet ───────────────────────────
        selectedIcon  = wallet.getIcon();
        selectedColor = wallet.getColor();
        selectedType  = wallet.getType();

        // ── Bind views ────────────────────────────────────────────────
        cardIconPreview = view.findViewById(R.id.card_edit_icon_preview);
        tvIconPreview   = view.findViewById(R.id.tv_edit_icon_preview);
        etName          = view.findViewById(R.id.et_edit_wallet_name);
        etBalance       = view.findViewById(R.id.et_edit_wallet_balance);
        switchInclude   = view.findViewById(R.id.switch_edit_include_in_total);

        // ── Pre-fill with existing data ───────────────────────────────
        cardIconPreview.setCardBackgroundColor(selectedColor);
        tvIconPreview.setText(selectedIcon);
        etName.setText(wallet.getName());
        etBalance.setText(String.valueOf(wallet.getBalanceVnd()));
        switchInclude.setChecked(wallet.isIncludedInTotal());

        // ── Close ─────────────────────────────────────────────────────
        view.findViewById(R.id.btn_close_edit_sheet).setOnClickListener(v -> dismiss());

        // ── Icon tap → icon picker ────────────────────────────────────
        cardIconPreview.setOnClickListener(v -> {
            IconPickerBottomSheet picker = IconPickerBottomSheet.newInstance(ICONS, icon -> {
                selectedIcon = icon;
                tvIconPreview.setText(selectedIcon);
            });
            picker.show(getParentFragmentManager(), "icon_picker_edit");
        });

        // ── Color chips ───────────────────────────────────────────────
        for (int i = 0; i < COLOR_VIEW_IDS.length; i++) {
            final int color = COLORS[i];
            View chip = view.findViewById(COLOR_VIEW_IDS[i]);
            if (chip == null) continue;
            applyCircleDrawable(chip, color, false);
            chip.setOnClickListener(v -> {
                selectedColor = color;
                cardIconPreview.setCardBackgroundColor(selectedColor);
                refreshColorChips(view);
                highlightTypeChip(view, getSelectedTypeChipId());
            });
        }
        refreshColorChips(view);

        // ── Type chips ────────────────────────────────────────────────
        setupTypeChip(view, R.id.edit_chip_type_cash,       Wallet.Type.CASH);
        setupTypeChip(view, R.id.edit_chip_type_bank,       Wallet.Type.BANK);
        setupTypeChip(view, R.id.edit_chip_type_ewallet,    Wallet.Type.EWALLET);
        setupTypeChip(view, R.id.edit_chip_type_investment, Wallet.Type.INVESTMENT);
        highlightTypeChip(view, getSelectedTypeChipId());

        // ── Save ──────────────────────────────────────────────────────
        view.findViewById(R.id.btn_edit_wallet_save).setOnClickListener(v -> saveChanges());

        // ── Delete ────────────────────────────────────────────────────
        view.findViewById(R.id.btn_edit_wallet_delete).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Xóa ví")
                        .setMessage("Bạn có chắc muốn xóa ví \"" + wallet.getName() + "\"?")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            if (listener != null) listener.onWalletDeleted(position);
                            dismiss();
                        })
                        .show()
        );
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

    private void highlightTypeChip(View root, int selectedId) {
        int[] all = { R.id.edit_chip_type_cash, R.id.edit_chip_type_bank,
                      R.id.edit_chip_type_ewallet, R.id.edit_chip_type_investment };
        for (int id : all) {
            CardView chip = root.findViewById(id);
            if (chip == null) continue;
            chip.setCardBackgroundColor(id == selectedId ? selectedColor
                                                         : Color.parseColor("#252545"));
        }
    }

    private int getSelectedTypeChipId() {
        switch (selectedType) {
            case BANK:       return R.id.edit_chip_type_bank;
            case EWALLET:    return R.id.edit_chip_type_ewallet;
            case INVESTMENT: return R.id.edit_chip_type_investment;
            default:         return R.id.edit_chip_type_cash;
        }
    }

    private void refreshColorChips(View root) {
        for (int i = 0; i < COLOR_VIEW_IDS.length; i++) {
            View chip = root.findViewById(COLOR_VIEW_IDS[i]);
            if (chip == null) continue;
            applyCircleDrawable(chip, COLORS[i], COLORS[i] == selectedColor);
        }
        highlightTypeChip(root, getSelectedTypeChipId());
    }

    private void applyCircleDrawable(View view, int color, boolean selected) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        if (selected) circle.setStroke(3, Color.WHITE);
        view.setBackground(circle);
    }

    private void saveChanges() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            etName.setError("Vui lòng nhập tên ví");
            etName.requestFocus();
            return;
        }
        long balance = 0;
        String bStr = etBalance.getText() != null ? etBalance.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(bStr)) {
            try { balance = (long) Double.parseDouble(bStr.replace(",", "").replace(".", "")); }
            catch (NumberFormatException ignored) {}
        }

        // Mutate in-place so the list reference stays the same
        wallet.setName(name);
        wallet.setBalanceVnd(balance);
        wallet.setIncludedInTotal(switchInclude.isChecked());
        // icon & color require setter — add them to Wallet
        wallet.setIcon(selectedIcon);
        wallet.setColor(selectedColor);
        wallet.setType(selectedType);

        if (listener != null) listener.onWalletUpdated(wallet, position);
        dismiss();
    }
}
