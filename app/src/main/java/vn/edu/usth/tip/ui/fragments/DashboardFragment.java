package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.Wallet;
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
import androidx.fragment.app.Fragment;
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
    private TransactionAdapter txAdapter;
    private View               emptyState;

    // ── Tab views ─────────────────────────────────────────────────────
    private TextView tabToday, tabWeek, tabMonth;

    // ── Colors ────────────────────────────────────────────────────────
    private static final int COLOR_TAB_ACTIVE  = Color.parseColor("#735BF2");
    private static final int COLOR_TAB_TEXT_ON = Color.WHITE;
    private static final int COLOR_TAB_TEXT_OFF = Color.parseColor("#9CA3AF");
    private static final int COLOR_TAB_BG_OFF  = Color.TRANSPARENT;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // viewModel initialized in BaseFragment
    }

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
            selectTab(currentTab);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Kích hoạt đồng bộ hóa TOÀN BỘ dữ liệu từ Backend khi mở Dashboard
        viewModel.syncAllData();
        
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        // Ta vẫn giữ callback của syncTransactions để hiển thị Toast thông báo cho người dùng
        viewModel.syncTransactions(new TransactionRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Đã cập nhật dữ liệu từ đám mây", Toast.LENGTH_SHORT).show();
                        dashboardViewModel.loadDashboardSummary();
                        dashboardViewModel.loadRecentTransactions("month");
                    });
                }
            }

            @Override
            public void onError(String message) {}
        });

        // Tải dữ liệu ban đầu
        dashboardViewModel.loadDashboardSummary();

        // ── Header buttons ────────────────────────────────────────────
        View btnNotification = view.findViewById(R.id.btn_notification);
        if (btnNotification != null) {
            btnNotification.setOnClickListener(v -> {
                NotificationBottomSheet sheet = new NotificationBottomSheet();
                sheet.show(getChildFragmentManager(), "notifications");
            });
        }

        // Avatar / Profile → mở Wallet Management
        View btnProfile = view.findViewById(R.id.btn_profile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v ->
                    Navigation.findNavController(v)
                            .navigate(R.id.action_dashboard_to_walletManagement)
            );
        }

        // Nút "Thêm chi tiêu" → mở NewTransactionFragment
        View btnAddExpense = view.findViewById(R.id.btn_add_expense);
        if (btnAddExpense != null) {
            btnAddExpense.setOnClickListener(v -> {
                viewModel.setDefaultNewTransactionType(Transaction.Type.EXPENSE);
                Navigation.findNavController(v).navigate(R.id.action_dashboard_to_newTransaction);
            });
        }

        // Nút "Thêm thu nhập" → mở NewTransactionFragment
        View btnAddIncome = view.findViewById(R.id.btn_add_income);
        if (btnAddIncome != null) {
            btnAddIncome.setOnClickListener(v -> {
                viewModel.setDefaultNewTransactionType(Transaction.Type.INCOME);
                Navigation.findNavController(v).navigate(R.id.action_dashboard_to_newTransaction);
            });
        }

        // Nút "Quét hóa đơn" → mở ScanReceiptFragment
        View btnScanReceipt = view.findViewById(R.id.btn_scan_receipt);
        if (btnScanReceipt != null) {
            btnScanReceipt.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_dashboard_to_scanReceipt)
            );
        }

        // ── Recent Transactions ───────────────────────────────────────
        txAdapter = new TransactionAdapter(new ArrayList<>(), tx -> {
            // Bấm vào item → mở chi tiết giao dịch
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

        // Quan trọng: Quan sát dữ liệu từ DashboardViewModel
        dashboardViewModel.getSummaryData().observe(getViewLifecycleOwner(), summary -> {
            if (summary == null) return;
            
            TextView tvNetWorth = view.findViewById(R.id.tv_net_worth);
            if (tvNetWorth != null) {
                tvNetWorth.setText(String.format("%,d", summary.getNetWorth()).replace(",", "."));
            }

            TextView tvGrowth = view.findViewById(R.id.tv_net_worth_growth);
            if (tvGrowth != null) {
                tvGrowth.setText("Dữ liệu trực tiếp từ API");
            }

            TextView tvIncome = view.findViewById(R.id.tv_monthly_income);
            if (tvIncome != null) {
                tvIncome.setText("₫" + String.format("%,d", summary.getTotalIncomeThisMonth()).replace(",", "."));
            }

            TextView tvExpense = view.findViewById(R.id.tv_monthly_expense);
            if (tvExpense != null) {
                tvExpense.setText("₫" + String.format("%,d", summary.getTotalExpenseThisMonth()).replace(",", "."));
            }

            TextView tvTransfer = view.findViewById(R.id.tv_monthly_transfer);
            if (tvTransfer != null) {
                tvTransfer.setText("₫" + String.format("%,d", summary.getTotalTransferThisMonth()).replace(",", "."));
            }
        });

        dashboardViewModel.getRecentTransactionsData().observe(getViewLifecycleOwner(), responseList -> {
            List<Transaction> localTxList = new ArrayList<>();
            if (responseList != null) {
                for (vn.edu.usth.tip.network.responses.TransactionResponse r : responseList) {
                    localTxList.add(mapToLocalTransaction(r));
                }
            }
            txAdapter.setData(localTxList);
            if (emptyState != null) {
                emptyState.setVisibility(localTxList.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        RecyclerView rv = view.findViewById(R.id.rv_recent_transactions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(txAdapter);

        emptyState = view.findViewById(R.id.layout_tx_empty);

        // ── Filter Tabs ───────────────────────────────────────────────
        tabToday = view.findViewById(R.id.tab_today);
        tabWeek  = view.findViewById(R.id.tab_week);
        tabMonth = view.findViewById(R.id.tab_month);

        tabToday.setOnClickListener(v -> selectTab(TAB_TODAY));
        tabWeek.setOnClickListener(v  -> selectTab(TAB_WEEK));
        tabMonth.setOnClickListener(v -> selectTab(TAB_MONTH));

        // Init
        selectTab(TAB_TODAY);

        // ── See All (header link) ──────────────────────────────────────
        View tvSeeAll = view.findViewById(R.id.tv_see_all);
        if (tvSeeAll != null) {
            tvSeeAll.setOnClickListener(v ->
                    Navigation.findNavController(v)
                            .navigate(R.id.action_dashboard_to_allTransactions)
            );
        }

        // ── See All (bottom button) ───────────────────────────────────
        View btnSeeAll = view.findViewById(R.id.btn_see_all_transactions);
        if (btnSeeAll != null) {
            btnSeeAll.setOnClickListener(v ->
                    Navigation.findNavController(v)
                            .navigate(R.id.action_dashboard_to_allTransactions)
            );
        }
    }

    // ── Tab logic ─────────────────────────────────────────────────────

    private void selectTab(int tab) {
        currentTab = tab;

        // Reset all tabs
        resetTab(tabToday);
        resetTab(tabWeek);
        resetTab(tabMonth);

        // Highlight selected
        TextView active = (tab == TAB_TODAY) ? tabToday
                        : (tab == TAB_WEEK)  ? tabWeek
                        : tabMonth;
        active.setBackgroundColor(COLOR_TAB_ACTIVE);
        active.setTextColor(COLOR_TAB_TEXT_ON);

        String period = "today";
        if (tab == TAB_WEEK) period = "week";
        if (tab == TAB_MONTH) period = "month";
        
        // Gọi API lọc
        dashboardViewModel.loadRecentTransactions(period);
    }

    private void resetTab(TextView tab) {
        if (tab == null) return;
        tab.setBackgroundColor(COLOR_TAB_BG_OFF);
        tab.setTextColor(COLOR_TAB_TEXT_OFF);
    }

    private Transaction mapToLocalTransaction(vn.edu.usth.tip.network.responses.TransactionResponse remoteTx) {
        long timestamp = 0;
        try {
            if (remoteTx.getTransactionDate() != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                java.util.Date d = sdf.parse(remoteTx.getTransactionDate());
                if (d != null) timestamp = d.getTime();
            }
        } catch (Exception e) {
            // fallback to current time
            timestamp = System.currentTimeMillis();
        }
        
        Transaction.Type type = Transaction.Type.EXPENSE;
        if ("income".equalsIgnoreCase(remoteTx.getType())) type = Transaction.Type.INCOME;
        if ("transfer".equalsIgnoreCase(remoteTx.getType())) type = Transaction.Type.TRANSFER;

        return new Transaction(
                remoteTx.getId() != null ? remoteTx.getId().toString() : uuid(),
                remoteTx.getNote() != null ? remoteTx.getNote() : "Giao dịch",
                remoteTx.getCategoryName() != null ? remoteTx.getCategoryName() : "Khác",
                "💰", // dummy icon cho đến khi API hỗ trợ icon 
                remoteTx.getAccountName() != null ? remoteTx.getAccountName() : "Ví chính",
                remoteTx.getAmount(),
                type,
                timestamp,
                remoteTx.getNote()
        );
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
}