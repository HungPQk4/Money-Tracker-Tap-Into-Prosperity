package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.adapters.WalletAdapter;
import vn.edu.usth.tip.viewmodels.AccountViewModel;
import vn.edu.usth.tip.network.responses.AccountResponse;

import vn.edu.usth.tip.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class WalletManagementFragment extends Fragment
        implements WalletAdapter.WalletActionListener {

    private AccountViewModel accountViewModel;
    private WalletAdapter adapter;
    private List<Wallet> currentWallets = new ArrayList<>();

    private View emptyState;
    private TextView tvSummaryTotal, tvSummaryCount;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wallet_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        emptyState      = view.findViewById(R.id.layout_empty_state);
        tvSummaryTotal  = view.findViewById(R.id.tv_summary_total);
        tvSummaryCount  = view.findViewById(R.id.tv_summary_count);
        
        TextView tvSummaryIncluded = view.findViewById(R.id.tv_summary_included);

        RecyclerView rv = view.findViewById(R.id.rv_wallets);

        adapter = new WalletAdapter(new ArrayList<>(), this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // Lắng nghe AccountViewModel
        accountViewModel.getAccountsData().observe(getViewLifecycleOwner(), accountResponses -> {
            if (accountResponses == null) return;
            
            // Map remote AccountResponse to local Wallet
            List<Wallet> newWallets = new ArrayList<>();
            long totalNetWorth = 0;
            int includedCount = 0;
            
            for (AccountResponse response : accountResponses) {
                int defaultColor = android.graphics.Color.parseColor("#735BF2");
                try {
                    if (response.getColorHex() != null) {
                        defaultColor = android.graphics.Color.parseColor(response.getColorHex());
                    }
                } catch (Exception e) {}

                Wallet.Type mappedType = Wallet.Type.OTHER;
                if(response.getType() != null) {
                   String t = response.getType().toLowerCase();
                   if(t.equals("bank")) mappedType = Wallet.Type.BANK;
                   else if(t.equals("cash")) mappedType = Wallet.Type.CASH;
                   else if(t.equals("e_wallet")) mappedType = Wallet.Type.EWALLET;
                   else if(t.equals("investment")) mappedType = Wallet.Type.INVESTMENT;
                }

                Wallet w = new Wallet(
                        response.getId(),
                        response.getName() != null ? response.getName() : "Ví",
                        response.getBalance(),
                        response.getIcon() != null ? response.getIcon() : "💳",
                        defaultColor,
                        mappedType,
                        response.getIncludeInTotal() != null ? response.getIncludeInTotal() : true
                );
                newWallets.add(w);
                
                if (w.isIncludedInTotal()) {
                    totalNetWorth += w.getBalanceVnd();
                    includedCount++;
                }
            }

            currentWallets = newWallets;
            adapter.updateData(currentWallets);
            
            // Update Summary
            tvSummaryCount.setText(currentWallets.size() + " ví");
            
            if (tvSummaryIncluded != null) {
                tvSummaryIncluded.setText(includedCount + "/" + currentWallets.size());
            }

            String formattedTotal = String.format("₫%,.0f", (double) totalNetWorth).replace(",", ".");
            tvSummaryTotal.setText(formattedTotal);

            // Update toolbar total
            View toolbar = getView();
            if (toolbar != null) {
                TextView tvToolbar = toolbar.findViewById(R.id.tv_total_balance);
                if (tvToolbar != null) tvToolbar.setText(formattedTotal);
            }

            // Update Empty State
            boolean empty = currentWallets.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        // Tải dữ liệu từ server
        accountViewModel.loadAccounts();

        // Lắng nghe kết quả thêm mới để tự động tải lại mà không cần delay "đoán mò"
        accountViewModel.getCreatedAccountData().observe(getViewLifecycleOwner(), accountResponse -> {
            if (accountResponse != null) {
                accountViewModel.loadAccounts();
                accountViewModel.clearCreatedAccountData();
                android.widget.Toast.makeText(getContext(), "Thêm ví thành công!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        // Lắng nghe kết quả cập nhật ví
        accountViewModel.getUpdatedAccountData().observe(getViewLifecycleOwner(), accountResponse -> {
            if (accountResponse != null) {
                accountViewModel.loadAccounts();
                accountViewModel.clearUpdatedAccountData();
                android.widget.Toast.makeText(getContext(), "Cập nhật ví thành công!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        // Lắng nghe kết quả xóa ví
        accountViewModel.getDeleteSuccessData().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                accountViewModel.loadAccounts();
                accountViewModel.clearDeleteSuccessData();
                android.widget.Toast.makeText(getContext(), "Xóa ví thành công chạy!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        // Lắng nghe lỗi để hiển thị nếu tạo ví thất bại
        // Sau – hiển thị lâu hơn và clear error sau khi hiển thị
        accountViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                android.widget.Toast.makeText(
                        getContext(),
                        error,
                        android.widget.Toast.LENGTH_LONG // ✅ Đổi thành LONG để đọc kịp
                ).show();
                accountViewModel.clearErrorMessage(); // ✅ Clear để không hiện lại
            }
        });

        // FAB
        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_add_wallet);
        fab.setOnClickListener(v -> openAddWalletSheet());

        view.findViewById(R.id.btn_empty_add)
                .setOnClickListener(v -> openAddWalletSheet());

        view.findViewById(R.id.btn_back)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    }

    // ── WalletActionListener ──────────────────────────────────────────

    @Override
    public void onEdit(Wallet wallet, int position) {
        EditWalletBottomSheet sheet = EditWalletBottomSheet.newInstance(
                wallet, position,
                new EditWalletBottomSheet.OnWalletEditListener() {
                    @Override
                    public void onWalletUpdated(Wallet updated, int pos) {
                        vn.edu.usth.tip.network.requests.AccountRequest req = new vn.edu.usth.tip.network.requests.AccountRequest();
                        req.setName(updated.getName());
                        String t = "cash";
                        if(updated.getType() == Wallet.Type.BANK) t = "bank";
                        else if(updated.getType() == Wallet.Type.EWALLET) t = "e_wallet";
                        else if(updated.getType() == Wallet.Type.INVESTMENT) t = "investment";
                        req.setType(t);
                        req.setBalance(updated.getBalanceVnd());
                        req.setIcon(updated.getIcon());
                        req.setIncludeInTotal(updated.isIncludedInTotal());
                        req.setColorHex(String.format("#%06X", (0xFFFFFF & updated.getColor())));
                        
                        accountViewModel.updateAccount(updated.getId(), req);
                    }
                    @Override
                    public void onWalletDeleted(int pos) {
                        accountViewModel.deleteAccount(wallet.getId());
                    }
                });
        sheet.show(getParentFragmentManager(), "edit_wallet");
    }

    @Override
    public void onDelete(Wallet wallet, int position) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa ví")
                .setMessage("Bạn có chắc muốn xóa ví \"" + wallet.getName() + "\"?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> {
                    accountViewModel.deleteAccount(wallet.getId());
                })
                .show();
    }

    @Override
    public void onCardClick(Wallet wallet) {
        int position = currentWallets.indexOf(wallet);
        WalletDetailSheet detailSheet = WalletDetailSheet.newInstance(
                wallet,
                new WalletDetailSheet.OnDetailActionListener() {
                    @Override
                    public void onEdit(Wallet w) {
                        WalletManagementFragment.this.onEdit(w, position);
                    }
                    @Override
                    public void onDelete(Wallet w) {
                        WalletManagementFragment.this.onDelete(w, position);
                    }
                });
        detailSheet.show(getParentFragmentManager(), "wallet_detail");
    }

    @Override
    public void onToggleInclude(Wallet wallet, boolean included) {
        wallet.setIncludedInTotal(included);
        vn.edu.usth.tip.network.requests.AccountRequest req = new vn.edu.usth.tip.network.requests.AccountRequest();
        req.setName(wallet.getName());
        String t = "cash";
        if(wallet.getType() == Wallet.Type.BANK) t = "bank";
        else if(wallet.getType() == Wallet.Type.EWALLET) t = "e_wallet";
        else if(wallet.getType() == Wallet.Type.INVESTMENT) t = "investment";
        req.setType(t);
        req.setBalance(wallet.getBalanceVnd());
        req.setIcon(wallet.getIcon());
        req.setIncludeInTotal(included);
        req.setColorHex(String.format("#%06X", (0xFFFFFF & wallet.getColor())));
        
        accountViewModel.updateAccount(wallet.getId(), req);
    }

    private void openAddWalletSheet() {
        AddWalletBottomSheet sheet = AddWalletBottomSheet.newInstance(wallet -> {
            vn.edu.usth.tip.network.requests.AccountRequest req = new vn.edu.usth.tip.network.requests.AccountRequest();
            req.setName(wallet.getName());
            
            String t = "cash";
            if(wallet.getType() == Wallet.Type.BANK) t = "bank";
            else if(wallet.getType() == Wallet.Type.EWALLET) t = "e_wallet";
            else if(wallet.getType() == Wallet.Type.INVESTMENT) t = "investment";
            req.setType(t);
            
            req.setBalance(wallet.getBalanceVnd());
            req.setIcon(wallet.getIcon());
            req.setIncludeInTotal(wallet.isIncludedInTotal());
            req.setColorHex(String.format("#%06X", (0xFFFFFF & wallet.getColor())));
            
            accountViewModel.createAccount(req);
        });
        sheet.show(getParentFragmentManager(), "add_wallet");
    }
}
