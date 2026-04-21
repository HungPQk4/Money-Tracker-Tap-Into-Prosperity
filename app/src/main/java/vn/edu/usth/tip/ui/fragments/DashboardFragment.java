package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.adapters.TransactionAdapter;
import vn.edu.usth.tip.viewmodels.AppViewModel;
import vn.edu.usth.tip.viewmodels.DashboardViewModel;
import vn.edu.usth.tip.repositories.TransactionRepository;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import vn.edu.usth.tip.R;
import androidx.lifecycle.ViewModelProvider;

public class DashboardFragment extends BaseFragment {

    public DashboardFragment() {}

    // ── Tab state ─────────────────────────────────────────────────────
    private static final int TAB_TODAY = 0, TAB_WEEK = 1, TAB_MONTH = 2;
    private int currentTab = TAB_TODAY;

    private DashboardViewModel dashboardViewModel;
    private vn.edu.usth.tip.viewmodels.AccountViewModel accountViewModel;
    private TransactionAdapter txAdapter;
    private View               emptyState;

    // ── Tab views ─────────────────────────────────────────────────────
    private TextView tabToday, tabWeek, tabMonth;

    // ── Theo dõi LiveData hiện tại từ Room để hủy observer khi đổi tab ─
    private LiveData<List<Transaction>> currentTxLiveData = null;
    private Observer<List<Transaction>> txObserver = null;

    // ── Colors ────────────────────────────────────────────────────────
    private static final int COLOR_TAB_ACTIVE   = Color.parseColor("#735BF2");
    private static final int COLOR_TAB_TEXT_ON  = Color.WHITE;
    private static final int COLOR_TAB_TEXT_OFF = Color.parseColor("#9CA3AF");
    private static final int COLOR_TAB_BG_OFF   = Color.TRANSPARENT;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dashboardViewModel != null) {
            dashboardViewModel.loadDashboardSummary();
        }
        if (accountViewModel != null) {
            accountViewModel.loadAccounts(); // Đồng bộ ví từ PostgreSQL
        }
        if (tabToday != null) {
            selectTab(currentTab);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        accountViewModel   = new ViewModelProvider(requireActivity()).get(vn.edu.usth.tip.viewmodels.AccountViewModel.class);

        // ── Setup RecyclerView & Adapter ─────────────────────────────────
        txAdapter = new TransactionAdapter(new ArrayList<>(), tx -> {
            TransactionDetailSheet sheet = TransactionDetailSheet.newInstance(tx,
                    new TransactionDetailSheet.OnTransactionActionListener() {
                        @Override
                        public void onEdit(Transaction t) {
                            viewModel.setEditingTransaction(t);
                            Navigation.findNavController(requireView())
                                    .navigate(R.id.action_dashboard_to_newTransaction);
                        }
                        @Override
                        public void onDelete(Transaction t) {
                            viewModel.deleteTransaction(t);
                        }
                    });
            sheet.show(getParentFragmentManager(), "tx_detail");
        });

        RecyclerView rv = view.findViewById(R.id.rv_recent_transactions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(txAdapter);
        emptyState = view.findViewById(R.id.layout_tx_empty);

        // ── Observe Wallet Data (PostgreSQL Source + Optimistic Local Tx) ──
        // ── Observe Wallet Data (PostgreSQL Source + Optimistic Local Tx) ──
        accountViewModel.getAccountsData().observe(getViewLifecycleOwner(), accounts -> {
            updateOptimisticDashboard(view);
        });

        viewModel.getTransactions().observe(getViewLifecycleOwner(), transactions -> {
            updateOptimisticDashboard(view);
        });

        // ── Observe Summary từ API (Thu chi tháng) ─────────────
        dashboardViewModel.getSummaryData().observe(getViewLifecycleOwner(), summary -> {
            if (summary == null) return;
            
            TextView tvGrowth = view.findViewById(R.id.tv_net_worth_growth);
            if (tvGrowth != null) tvGrowth.setText("Dữ liệu đồng bộ từ đám mây");

            updateOptimisticDashboard(view);
        });

        // ── Header buttons ────────────────────────────────────────────
        View btnNotification = view.findViewById(R.id.btn_notification);
        if (btnNotification != null) {
            btnNotification.setOnClickListener(v -> {
                NotificationBottomSheet sheet = new NotificationBottomSheet();
                sheet.show(getChildFragmentManager(), "notifications");
            });
        }

        View btnProfile = view.findViewById(R.id.btn_profile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.action_dashboard_to_walletManagement)
            );
        }

        // ── Quick Actions ─────────────────────────────────────────────
        View btnAddExpense = view.findViewById(R.id.btn_add_expense);
        if (btnAddExpense != null) {
            btnAddExpense.setOnClickListener(v -> {
                viewModel.setDefaultNewTransactionType(Transaction.Type.EXPENSE);
                Navigation.findNavController(v).navigate(R.id.action_dashboard_to_newTransaction);
            });
        }

        View btnAddIncome = view.findViewById(R.id.btn_add_income);
        if (btnAddIncome != null) {
            btnAddIncome.setOnClickListener(v -> {
                viewModel.setDefaultNewTransactionType(Transaction.Type.INCOME);
                Navigation.findNavController(v).navigate(R.id.action_dashboard_to_newTransaction);
            });
        }

        View btnScanReceipt = view.findViewById(R.id.btn_scan_receipt);
        if (btnScanReceipt != null) {
            btnScanReceipt.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.action_dashboard_to_scanReceipt)
            );
        }

        // ── Filter Tabs ───────────────────────────────────────────────
        tabToday = view.findViewById(R.id.tab_today);
        tabWeek  = view.findViewById(R.id.tab_week);
        tabMonth = view.findViewById(R.id.tab_month);

        tabToday.setOnClickListener(v -> selectTab(TAB_TODAY));
        tabWeek.setOnClickListener(v  -> selectTab(TAB_WEEK));
        tabMonth.setOnClickListener(v -> selectTab(TAB_MONTH));

        selectTab(TAB_TODAY);

        // ── See All buttons ───────────────────────────────────────────
        View tvSeeAll = view.findViewById(R.id.tv_see_all);
        if (tvSeeAll != null) {
            tvSeeAll.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.action_dashboard_to_allTransactions)
            );
        }
        View btnSeeAll = view.findViewById(R.id.btn_see_all_transactions);
        if (btnSeeAll != null) {
            btnSeeAll.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.action_dashboard_to_allTransactions)
            );
        }

        // ── Sync API ngầm ─────────────────────────────
        viewModel.syncTransactions(new TransactionRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> dashboardViewModel.loadDashboardSummary());
                }
            }
            @Override
            public void onError(String message) {}
        });
    }

    private void updateOptimisticDashboard(View view) {
        List<vn.edu.usth.tip.network.responses.AccountResponse> accounts = accountViewModel.getAccountsData().getValue();
        List<Transaction> transactions = viewModel.getTransactions().getValue();
        vn.edu.usth.tip.network.responses.DashboardSummary summary = dashboardViewModel.getSummaryData().getValue();

        if (accounts == null || view == null) return;
        
        TextView tvTotalAssets = view.findViewById(R.id.tv_total_assets);
        TextView tvNetWorthInside = view.findViewById(R.id.tv_net_worth);
        TextView tvMonthlyIncome = view.findViewById(R.id.tv_monthly_income);
        TextView tvMonthlyExpense = view.findViewById(R.id.tv_monthly_expense);
        TextView tvMonthlyTransfer = view.findViewById(R.id.tv_monthly_transfer);

        // 1. Tính Tài sản ròng lạc quan (Optimistic Net Worth)
        long totalAssets = 0;
        for (vn.edu.usth.tip.network.responses.AccountResponse acc : accounts) {
            if (acc.getIncludeInTotal() != null && acc.getIncludeInTotal()) {
                long balance = acc.getBalance();
                if (transactions != null) {
                    for (Transaction t : transactions) {
                        if (!t.isSynced() && t.getWalletName() != null && t.getWalletName().equals(acc.getName())) {
                            if (t.getType() == Transaction.Type.INCOME)   balance += t.getAmountVnd();
                            else if (t.getType() == Transaction.Type.EXPENSE) balance -= t.getAmountVnd();
                            else if (t.getType() == Transaction.Type.TRANSFER) balance -= t.getAmountVnd();
                        }
                    }
                }
                totalAssets += balance;
            }
        }

        Long iOwe      = viewModel.getTotalIOwe().getValue();
        Long owedToMe  = viewModel.getTotalOwedToMe().getValue();
        long netWorth  = totalAssets - (iOwe != null ? iOwe : 0) + (owedToMe != null ? owedToMe : 0);

        if (tvTotalAssets != null) {
            tvTotalAssets.setText(String.format("%,d", totalAssets).replace(",", "."));
        }
        if (tvNetWorthInside != null) {
            tvNetWorthInside.setText(String.format("%,d", netWorth).replace(",", "."));
        }

        // 2. Tính Tổng hợp tháng lạc quan (Optimistic Monthly Summary)
        if (summary != null) {
            long optIncome = summary.getTotalIncomeThisMonth();
            long optExpense = summary.getTotalExpenseThisMonth();
            long optTransfer = summary.getTotalTransferThisMonth();

            if (transactions != null) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                clearTime(cal);
                long startOfMonth = cal.getTimeInMillis();

                for (Transaction t : transactions) {
                    if (!t.isSynced() && t.getTimestampMs() >= startOfMonth) {
                        if (t.getType() == Transaction.Type.INCOME) optIncome += t.getAmountVnd();
                        else if (t.getType() == Transaction.Type.EXPENSE) optExpense += t.getAmountVnd();
                        else if (t.getType() == Transaction.Type.TRANSFER) optTransfer += t.getAmountVnd();
                    }
                }
            }

            if (tvMonthlyIncome != null) {
                tvMonthlyIncome.setText("₫" + String.format("%,d", optIncome).replace(",", "."));
            }
            if (tvMonthlyExpense != null) {
                tvMonthlyExpense.setText("₫" + String.format("%,d", optExpense).replace(",", "."));
            }
            if (tvMonthlyTransfer != null) {
                tvMonthlyTransfer.setText("₫" + String.format("%,d", optTransfer).replace(",", "."));
            }
        }
    }

    private void selectTab(int tab) {
        currentTab = tab;
        resetTab(tabToday);
        resetTab(tabWeek);
        resetTab(tabMonth);

        TextView active = (tab == TAB_TODAY) ? tabToday
                        : (tab == TAB_WEEK)  ? tabWeek
                        : tabMonth;
        if (active != null) {
            active.setBackgroundColor(COLOR_TAB_ACTIVE);
            active.setTextColor(COLOR_TAB_TEXT_ON);
        }

        Calendar from = Calendar.getInstance();
        Calendar to   = Calendar.getInstance();
        clearTime(from);
        clearTime(to);

        if (tab == TAB_TODAY) {
            to.add(Calendar.DAY_OF_MONTH, 1);
        } else if (tab == TAB_WEEK) {
            int dayOfWeek = from.get(Calendar.DAY_OF_WEEK);
            int daysToMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - Calendar.MONDAY);
            from.add(Calendar.DAY_OF_MONTH, -daysToMonday);
            to.set(Calendar.YEAR, from.get(Calendar.YEAR));
            to.set(Calendar.DAY_OF_YEAR, from.get(Calendar.DAY_OF_YEAR));
            to.add(Calendar.DAY_OF_MONTH, 7);
        } else {
            from.set(Calendar.DAY_OF_MONTH, 1);
            to.set(Calendar.DAY_OF_MONTH, 1);
            to.add(Calendar.MONTH, 1);
        }

        long fromMs = from.getTimeInMillis();
        long toMs   = to.getTimeInMillis();

        if (currentTxLiveData != null && txObserver != null) {
            currentTxLiveData.removeObserver(txObserver);
        }

        currentTxLiveData = viewModel.getTransactionsBetween(fromMs, toMs);
        txObserver = txList -> {
            if (txAdapter != null) {
                txAdapter.setData(txList != null ? txList : new ArrayList<>());
            }
            if (emptyState != null) {
                emptyState.setVisibility((txList == null || txList.isEmpty()) ? View.VISIBLE : View.GONE);
            }
        };
        currentTxLiveData.observe(getViewLifecycleOwner(), txObserver);
    }

    private void clearTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE,      0);
        c.set(Calendar.SECOND,      0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private void resetTab(TextView tab) {
        if (tab == null) return;
        tab.setBackgroundColor(COLOR_TAB_BG_OFF);
        tab.setTextColor(COLOR_TAB_TEXT_OFF);
    }
}