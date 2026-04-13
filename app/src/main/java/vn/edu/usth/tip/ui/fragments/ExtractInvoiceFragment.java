package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Category;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.viewmodels.AppViewModel;

public class ExtractInvoiceFragment extends Fragment {

    private AppViewModel appViewModel;

    private EditText etAmount;
    private EditText etNote;
    private TextView tvCategoryIcon, tvCategoryName;
    private TextView tvWalletIcon, tvWalletName;

    private Category selectedCategory;
    private Wallet selectedWallet;

    public ExtractInvoiceFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_extract_invoice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etAmount = view.findViewById(R.id.et_amount);
        etNote = view.findViewById(R.id.et_note);
        tvCategoryIcon = view.findViewById(R.id.tv_category_icon);
        tvCategoryName = view.findViewById(R.id.tv_category_name);
        tvWalletIcon = view.findViewById(R.id.tv_wallet_icon);
        tvWalletName = view.findViewById(R.id.tv_wallet_name);

        // Pre-fill mock AI Extract data
        etAmount.setText("350000");
        etNote.setText("Thanh toán Bách Hóa Xanh (Bill #8281)");

        // Bind data from AppViewModel for Categories and Wallets
        appViewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null && !categories.isEmpty()) {
                if (selectedCategory == null) {
                    // Try to guess "Ăn uống"
                    for (Category c : categories) {
                        if (c.getName().contains("Ăn uống") || c.getName().contains("Mua sắm")) {
                            selectedCategory = c;
                            break;
                        }
                    }
                    if (selectedCategory == null) selectedCategory = categories.get(0);
                    updateCategoryUI();
                }
            }
        });

        appViewModel.getEngineState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state.wallets != null && !state.wallets.isEmpty()) {
                if (selectedWallet == null) {
                    selectedWallet = state.wallets.get(0);
                    updateWalletUI();
                }
            }
        });

        // Set up interactions
        view.findViewById(R.id.btn_back).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        
        view.findViewById(R.id.card_category).setOnClickListener(v -> {
            List<Category> list = appViewModel.getCategories().getValue();
            if (list == null || list.isEmpty()) return;
            String[] names = new String[list.size()];
            for (int i = 0; i < list.size(); i++) names[i] = list.get(i).getIcon() + " " + list.get(i).getName();
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Chọn danh mục")
                    .setItems(names, (dialog, which) -> {
                        selectedCategory = list.get(which);
                        updateCategoryUI();
                    }).show();
        });

        view.findViewById(R.id.card_wallet).setOnClickListener(v -> {
            AppViewModel.EngineState state = appViewModel.getEngineState().getValue();
            if (state == null || state.wallets == null || state.wallets.isEmpty()) return;
            List<Wallet> list = state.wallets;
            String[] names = new String[list.size()];
            for (int i = 0; i < list.size(); i++) names[i] = list.get(i).getIcon() + " " + list.get(i).getName();
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Chọn ví thanh toán")
                    .setItems(names, (dialog, which) -> {
                        selectedWallet = list.get(which);
                        updateWalletUI();
                    }).show();
        });

        view.findViewById(R.id.btn_save).setOnClickListener(this::validateAndSave);
    }

    private void updateCategoryUI() {
        if (selectedCategory != null) {
            tvCategoryIcon.setText(selectedCategory.getIcon());
            tvCategoryName.setText(selectedCategory.getName());
        }
    }

    private void updateWalletUI() {
        if (selectedWallet != null) {
            tvWalletIcon.setText(selectedWallet.getIcon());
            tvWalletName.setText(selectedWallet.getName());
        }
    }

    private void validateAndSave(View v) {
        String amountStr = etAmount.getText().toString().trim();
        long amount = 0;
        try {
            amount = Long.parseLong(amountStr);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount <= 0) {
            Toast.makeText(getContext(), "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCategory == null) {
            Toast.makeText(getContext(), "Chưa có danh mục", Toast.LENGTH_SHORT).show();
            return;
        }

        String note = etNote.getText().toString().trim();
        String walletName = selectedWallet != null ? selectedWallet.getName() : "Tiền mặt";

        Transaction tx = new Transaction(
                UUID.randomUUID().toString(),
                "Hóa đơn AI",
                selectedCategory.getName(),
                selectedCategory.getIcon(),
                walletName,
                amount,
                Transaction.Type.EXPENSE,
                System.currentTimeMillis(),
                note
        );

        appViewModel.addTransaction(tx);
        Toast.makeText(getContext(), "Lưu hóa đơn thành công", Toast.LENGTH_SHORT).show();
        
        // Pop back out to Dashboard
        Navigation.findNavController(v).popBackStack(R.id.dashboardFragment, false);
    }
}
