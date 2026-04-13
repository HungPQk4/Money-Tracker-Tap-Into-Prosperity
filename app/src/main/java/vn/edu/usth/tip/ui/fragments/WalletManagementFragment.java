package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.adapters.WalletAdapter;
import vn.edu.usth.tip.viewmodels.AppViewModel;

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

    private AppViewModel appViewModel;
    private WalletAdapter adapter;
    private List<Wallet> currentWallets = new ArrayList<>();

    private View emptyState;
    private TextView tvSummaryTotal, tvSummaryCount;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
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
        RecyclerView rv = view.findViewById(R.id.rv_wallets);

        adapter = new WalletAdapter(new ArrayList<>(), this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // Lắng nghe Financial Engine!
        appViewModel.getEngineState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            currentWallets = state.wallets;
            adapter.updateData(currentWallets);
            
            // Update Summary from Engine
            tvSummaryCount.setText(currentWallets.size() + " ví");
            String formattedTotal = String.format("₫%,.0f", (double) state.netWorth).replace(",", ".");
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
                        appViewModel.updateWallet(updated);
                    }
                    @Override
                    public void onWalletDeleted(int pos) {
                        appViewModel.deleteWallet(wallet);
                    }
                });
        sheet.show(getParentFragmentManager(), "edit_wallet");
    }

    @Override
    public void onDelete(Wallet wallet, int position) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa ví")
                .setMessage("Bạn có chắc muốn xóa ví \"" + wallet.getName() + "\"? Toàn bộ giao dịch thuộc ví này sẽ mất nơi tham chiếu nhưng vẫn tính vào tổng tài khoản ẩn!")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> {
                    appViewModel.deleteWallet(wallet);
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
        appViewModel.updateWallet(wallet);
    }

    private void openAddWalletSheet() {
        AddWalletBottomSheet sheet = AddWalletBottomSheet.newInstance(wallet -> {
            appViewModel.addWallet(wallet);
        });
        sheet.show(getParentFragmentManager(), "add_wallet");
    }
}
