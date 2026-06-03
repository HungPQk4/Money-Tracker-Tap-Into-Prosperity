package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.adapters.WalletAdapter;
import vn.edu.usth.tip.utils.MoneyFormat;
import vn.edu.usth.tip.viewmodels.AccountViewModel;
import vn.edu.usth.tip.network.responses.AccountResponse;

import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.R;
import vn.edu.usth.tip.ui.activities.LoginActivity;
import vn.edu.usth.tip.utils.AnimUtils;
import vn.edu.usth.tip.utils.SessionManager;
import vn.edu.usth.tip.utils.SyncPrefs;
import vn.edu.usth.tip.utils.TokenManager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WalletManagementFragment extends Fragment
        implements WalletAdapter.WalletActionListener {

    private enum SortOrder { DEFAULT, NAME_ASC, NAME_DESC, BALANCE_DESC, BALANCE_ASC }

    private AccountViewModel accountViewModel;
    private WalletAdapter adapter;
    private List<Wallet> currentWallets = new ArrayList<>();
    private SortOrder currentSort = SortOrder.DEFAULT;

    private View emptyState;
    private TextView tvSummaryTotal, tvSummaryCount, tvSort;

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

        emptyState     = view.findViewById(R.id.layout_empty_state);
        tvSummaryTotal = view.findViewById(R.id.tv_summary_total);
        tvSummaryCount = view.findViewById(R.id.tv_summary_count);
        tvSort         = view.findViewById(R.id.tv_sort_wallets);
        tvSort.setOnClickListener(this::showSortMenu);
        
        TextView tvSummaryIncluded = view.findViewById(R.id.tv_summary_included);

        RecyclerView rv = view.findViewById(R.id.rv_wallets);

        adapter = new WalletAdapter(new ArrayList<>(), this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        accountViewModel.getAccountsData().observe(getViewLifecycleOwner(), accountResponses -> {
            if (accountResponses == null) return;

            List<Wallet> newWallets = new ArrayList<>();
            long totalNetWorth = 0;
            int includedCount = 0;
            
            for (AccountResponse response : accountResponses) {
                int defaultColor = ContextCompat.getColor(requireContext(), R.color.brand_primary);
                try {
                    if (response.getColorHex() != null) {
                        defaultColor = android.graphics.Color.parseColor(response.getColorHex());
                    }
                } catch (Exception e) {
                    android.util.Log.w("WalletManagement", "Invalid color hex '" + response.getColorHex() + "', using default: " + e.getMessage());
                }

                Wallet.Type mappedType = vn.edu.usth.tip.utils.WalletTypeConverter.fromApiString(response.getType(), Wallet.Type.OTHER);

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
            adapter.updateData(applySorted(currentWallets));

            tvSummaryCount.setText(currentWallets.size() + " ví");
            if (tvSummaryIncluded != null) {
                tvSummaryIncluded.setText(includedCount + "/" + currentWallets.size());
            }

            CharSequence formattedTotal = MoneyFormat.formatShortStyled(totalNetWorth);
            tvSummaryTotal.setText(formattedTotal);

            boolean empty = currentWallets.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        accountViewModel.loadAccounts();

        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_wallet);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.brand_primary));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                accountViewModel.loadAccounts();
                swipeRefreshLayout.setRefreshing(false);
            });
        }

        // Lắng nghe kết quả thêm mới để tự động tải lại mà không cần delay "đoán mò"
        accountViewModel.getCreatedAccountData().observe(getViewLifecycleOwner(), accountResponse -> {
            if (accountResponse != null) {
                accountViewModel.loadAccounts();
                accountViewModel.clearCreatedAccountData();
                android.widget.Toast.makeText(getContext(), "Thêm ví thành công!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        accountViewModel.getUpdatedAccountData().observe(getViewLifecycleOwner(), accountResponse -> {
            if (accountResponse != null) {
                accountViewModel.loadAccounts();
                accountViewModel.clearUpdatedAccountData();
                android.widget.Toast.makeText(getContext(), "Cập nhật ví thành công!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        accountViewModel.getDeleteSuccessData().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                accountViewModel.loadAccounts();
                accountViewModel.clearDeleteSuccessData();
                android.widget.Toast.makeText(getContext(), "Xóa ví thành công chạy!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        accountViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                android.widget.Toast.makeText(getContext(), error, android.widget.Toast.LENGTH_LONG).show();
                accountViewModel.clearErrorMessage(); // clear so the same error doesn't re-appear on next observe
            }
        });

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_add_wallet);
        fab.setOnClickListener(v -> AnimUtils.bounceClick(v, this::openAddWalletSheet));

        view.findViewById(R.id.btn_empty_add)
                .setOnClickListener(v -> AnimUtils.bounceClick(v, this::openAddWalletSheet));

        view.findViewById(R.id.btn_back)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        view.findViewById(R.id.btn_logout)
                .setOnClickListener(v -> showLogoutConfirmation());
    }

    private void showLogoutConfirmation() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất khỏi tài khoản?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất", (dialog, which) -> performLogout())
                .show();
    }

    private void performLogout() {
        // Hiện loading để chặn user tương tác trong khi xóa DB (tránh race condition).
        android.app.ProgressDialog pd = new android.app.ProgressDialog(requireContext());
        pd.setMessage("Đang đăng xuất...");
        pd.setCancelable(false);
        pd.show();

        Context appCtx = requireContext().getApplicationContext();
        // Huỷ tất cả request đang bay ngay lập tức (tránh zombie response).
        vn.edu.usth.tip.network.RetrofitClient.cancelAllRequests();
        // Cancel pending sync worker trước khi xóa DB.
        androidx.work.WorkManager.getInstance(appCtx).cancelUniqueWork("TxSync");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(appCtx).clearAllTables();
            SyncPrefs.clearAll(appCtx);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                // Guard: Fragment có thể đã bị detach (xoay màn hình, thu hồi tài nguyên)
                // trong khoảng thời gian clearAllTables chạy trên background thread.
                if (pd.isShowing()) pd.dismiss();
                if (!isAdded() || isDetached() || getActivity() == null) return;
                TokenManager.getOrCreate(appCtx).clear();
                SessionManager.getInstance().clearSessionExpired();
                Intent intent = new Intent(appCtx, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                appCtx.startActivity(intent);
            });
        });
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
                        req.setType(vn.edu.usth.tip.utils.WalletTypeConverter.toApiString(updated.getType()));
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
        req.setType(vn.edu.usth.tip.utils.WalletTypeConverter.toApiString(wallet.getType()));
        req.setBalance(wallet.getBalanceVnd());
        req.setIcon(wallet.getIcon());
        req.setIncludeInTotal(included);
        req.setColorHex(String.format("#%06X", (0xFFFFFF & wallet.getColor())));
        
        accountViewModel.updateAccount(wallet.getId(), req);
    }

    private List<Wallet> applySorted(List<Wallet> source) {
        List<Wallet> copy = new ArrayList<>(source);
        switch (currentSort) {
            case NAME_ASC:
                Collections.sort(copy, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                break;
            case NAME_DESC:
                Collections.sort(copy, (a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                break;
            case BALANCE_DESC:
                Collections.sort(copy, (a, b) -> Long.compare(b.getBalanceVnd(), a.getBalanceVnd()));
                break;
            case BALANCE_ASC:
                Collections.sort(copy, (a, b) -> Long.compare(a.getBalanceVnd(), b.getBalanceVnd()));
                break;
            default:
                break;
        }
        return copy;
    }

    private void showSortMenu(android.view.View anchor) {
        android.view.ContextThemeWrapper wrapper =
                new android.view.ContextThemeWrapper(requireContext(), R.style.LightPopupMenuStyle);
        android.widget.PopupMenu popup = new android.widget.PopupMenu(wrapper, anchor);
        popup.inflate(R.menu.menu_wallet_sort);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.sort_default)           currentSort = SortOrder.DEFAULT;
            else if (id == R.id.sort_name_asc)     currentSort = SortOrder.NAME_ASC;
            else if (id == R.id.sort_name_desc)    currentSort = SortOrder.NAME_DESC;
            else if (id == R.id.sort_balance_desc) currentSort = SortOrder.BALANCE_DESC;
            else if (id == R.id.sort_balance_asc)  currentSort = SortOrder.BALANCE_ASC;
            else return false;

            updateSortLabel();
            adapter.updateData(applySorted(currentWallets));
            return true;
        });
        popup.show();
    }

    private void updateSortLabel() {
        String label;
        switch (currentSort) {
            case NAME_ASC:      label = "Tên A→Z ▴";    break;
            case NAME_DESC:     label = "Tên Z→A ▾";    break;
            case BALANCE_DESC:  label = "Cao nhất ▾";   break;
            case BALANCE_ASC:   label = "Thấp nhất ▴";  break;
            default:            label = "Sắp xếp";      break;
        }
        tvSort.setText(label);
    }

    private void openAddWalletSheet() {
        AddWalletBottomSheet sheet = AddWalletBottomSheet.newInstance(wallet -> {
            vn.edu.usth.tip.network.requests.AccountRequest req = new vn.edu.usth.tip.network.requests.AccountRequest();
            req.setName(wallet.getName());
            req.setType(vn.edu.usth.tip.utils.WalletTypeConverter.toApiString(wallet.getType()));
            req.setBalance(wallet.getBalanceVnd());
            req.setIcon(wallet.getIcon());
            req.setIncludeInTotal(wallet.isIncludedInTotal());
            req.setColorHex(String.format("#%06X", (0xFFFFFF & wallet.getColor())));
            
            accountViewModel.createAccount(req);
        });
        sheet.show(getParentFragmentManager(), "add_wallet");
    }
}
