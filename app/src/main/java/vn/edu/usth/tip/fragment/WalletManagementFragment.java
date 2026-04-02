package vn.edu.usth.tip.fragment;
import vn.edu.usth.tip.R;
import vn.edu.usth.tip.Wallet;
import vn.edu.usth.tip.WalletAdapter;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class WalletManagementFragment extends Fragment
        implements WalletAdapter.WalletActionListener {

    private WalletAdapter adapter;
    private List<Wallet> walletList;
    private View emptyState;
    private TextView tvSummaryTotal, tvSummaryCount;

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

        // Sample data
        walletList = getSampleWallets();

        adapter = new WalletAdapter(walletList, this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // FAB
        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_add_wallet);
        fab.setOnClickListener(v -> openAddWalletSheet());

        // Empty state button
        view.findViewById(R.id.btn_empty_add)
                .setOnClickListener(v -> openAddWalletSheet());

        // Back button
        view.findViewById(R.id.btn_back)
                .setOnClickListener(v -> requireActivity()
                        .getSupportFragmentManager().popBackStack());

        updateSummary();
        updateEmptyState();
    }

    // ── WalletActionListener ──────────────────────────────────────────

    @Override
    public void onEdit(Wallet wallet, int position) {
        // TODO: mở AddEditWalletBottomSheet với dữ liệu wallet
        Toast.makeText(requireContext(),
                "Sửa ví: " + wallet.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDelete(Wallet wallet, int position) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa ví")
                .setMessage("Bạn có chắc muốn xóa ví \"" + wallet.getName() + "\"?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> {
                    walletList.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateSummary();
                    updateEmptyState();
                })
                .show();
    }

    @Override
    public void onCardClick(Wallet wallet) {
        Toast.makeText(requireContext(),
                "Xem chi tiết: " + wallet.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onToggleInclude(Wallet wallet, boolean included) {
        wallet.setIncludedInTotal(included);
        updateSummary();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void openAddWalletSheet() {
        // TODO: hiển thị AddEditWalletBottomSheet
        Toast.makeText(requireContext(),
                "Mở thêm ví mới", Toast.LENGTH_SHORT).show();
    }

    private void updateSummary() {
        long total = 0;
        for (Wallet w : walletList) {
            if (w.isIncludedInTotal()) total += w.getBalanceVnd();
        }
        String formatted = String.format("₫%,.0f", (double) total)
                .replace(",", ".");
        tvSummaryTotal.setText(formatted);
        tvSummaryCount.setText(walletList.size() + " ví");

        // Update toolbar total
        View toolbar = getView();
        if (toolbar != null) {
            TextView tvToolbar = toolbar.findViewById(R.id.tv_total_balance);
            if (tvToolbar != null) tvToolbar.setText(formatted);
        }
    }

    private void updateEmptyState() {
        boolean empty = walletList.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        View rv = requireView().findViewById(R.id.rv_wallets);
        if (rv != null) rv.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private List<Wallet> getSampleWallets() {
        List<Wallet> list = new ArrayList<>();
        list.add(new Wallet("1", "Tiền mặt",   5_000_000L,   "💵",
                Color.parseColor("#735BF2"), Wallet.Type.CASH,       true));
        list.add(new Wallet("2", "Vietcombank", 110_000_000L, "🏦",
                Color.parseColor("#0EA5E9"), Wallet.Type.BANK,       true));
        list.add(new Wallet("3", "MoMo",        13_450_000L,  "💜",
                Color.parseColor("#D946EF"), Wallet.Type.EWALLET,    true));
        return list;
    }
}

