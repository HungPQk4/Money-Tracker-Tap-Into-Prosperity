package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.adapters.TransactionAdapter;
import vn.edu.usth.tip.viewmodels.AppViewModel;

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

    // viewModel is inherited from BaseFragment
    private TransactionAdapter txAdapter;
    private List<Transaction>  allTransactions = new ArrayList<>();
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Kích hoạt đồng bộ hóa dữ liệu từ Backend khi mở Dashboard
        viewModel.syncTransactions(new TransactionRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                // LiveData sẽ tự động trigger UI update khi dữ liệu vào Room
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getContext(), "Đồng bộ thành công", Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onError(String message) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });

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

        // Quan trọng: Quan sát dữ liệu từ ViewModel
        // Báo AppViewModel quan sát State chung
        viewModel.getEngineState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            
            // Xử lý Tài sản ròng
            TextView tvNetWorth = view.findViewById(R.id.tv_net_worth);
            if (tvNetWorth != null) {
                tvNetWorth.setText(String.format("%,d", state.netWorth).replace(",", "."));
            }

            // Xử lý Phần trăm (Cố định ở mức Demo hiện tại -> TODO: Tích hợp Engine thời gian)
            TextView tvGrowth = view.findViewById(R.id.tv_net_worth_growth);
            if (tvGrowth != null) {
                tvGrowth.setText("Dựa trên số liệu giao dịch...");
            }

            // Xử lý Khối Thu thập tháng
            TextView tvIncome = view.findViewById(R.id.tv_monthly_income);
            if (tvIncome != null) {
                tvIncome.setText("₫" + String.format("%,d", state.mIncome).replace(",", "."));
            }

            // Xử lý Khối Chi tiêu tháng
            TextView tvExpense = view.findViewById(R.id.tv_monthly_expense);
            if (tvExpense != null) {
                tvExpense.setText("₫" + String.format("%,d", state.mExpense).replace(",", "."));
            }

            // Xử lý Khối Chuyển khoản tháng
            TextView tvTransfer = view.findViewById(R.id.tv_monthly_transfer);
            if (tvTransfer != null) {
                tvTransfer.setText("₫" + String.format("%,d", state.mTransfer).replace(",", "."));
            }
        });

        viewModel.getTransactions().observe(getViewLifecycleOwner(), transactions -> {
            allTransactions = transactions;
            selectTab(currentTab); // Cập nhật lại list theo tab hiện tại
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

        // Filter data
        List<Transaction> filtered = filterByTab(tab);
        txAdapter.setData(filtered);

        if (emptyState != null) {
            emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void resetTab(TextView tab) {
        if (tab == null) return;
        tab.setBackgroundColor(COLOR_TAB_BG_OFF);
        tab.setTextColor(COLOR_TAB_TEXT_OFF);
    }

    private List<Transaction> filterByTab(int tab) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();

        // Start of today
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        // Start of this week (Monday)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        long startOfWeek = cal.getTimeInMillis();

        // Start of this month
        cal.set(Calendar.DAY_OF_MONTH, 1);
        long startOfMonth = cal.getTimeInMillis();

        long cutoff = (tab == TAB_TODAY) ? startOfDay
                    : (tab == TAB_WEEK)  ? startOfWeek
                    : startOfMonth;

        List<Transaction> result = new ArrayList<>();
        for (Transaction tx : allTransactions) {
            if (tx.getTimestampMs() >= cutoff && tx.getTimestampMs() <= now) {
                result.add(tx);
            }
        }
        return result;
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
}