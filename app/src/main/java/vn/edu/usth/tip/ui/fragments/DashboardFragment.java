package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.adapters.TransactionAdapter;
import vn.edu.usth.tip.utils.MoneyFormat;
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
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.utils.AnimUtils;
import vn.edu.usth.tip.insights.InsightViewModel;
import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import androidx.lifecycle.ViewModelProvider;

public class DashboardFragment extends BaseFragment {

    public DashboardFragment() {}

    // ── Tab state ─────────────────────────────────────────────────────
    private static final int TAB_TODAY = 0, TAB_WEEK = 1, TAB_MONTH = 2;
    private int currentTab = TAB_TODAY;

    private DashboardViewModel dashboardViewModel;
    private vn.edu.usth.tip.viewmodels.AccountViewModel accountViewModel;
    private InsightViewModel insightViewModel;
    private TransactionAdapter txAdapter;
    private View               emptyState;

    // ── Tab views ─────────────────────────────────────────────────────
    private TextView tabToday, tabWeek, tabMonth;

    private LiveData<List<Transaction>> currentTxLiveData = null;
    private Observer<List<Transaction>> txObserver = null;

    // ── Colors ────────────────────────────────────────────────────────
    private int colorTabActive; // resolved from R.color.brand_primary in onViewCreated
    private static final int COLOR_TAB_TEXT_ON  = Color.WHITE;
    private static final int COLOR_TAB_TEXT_OFF = Color.parseColor("#9CA3AF");
    private static final int COLOR_TAB_BG_OFF   = Color.TRANSPARENT;

    // ── Greeting hour ranges ──────────────────────────────────────────
    private static final int GREETING_MORNING_START   = 5;
    private static final int GREETING_NOON_START      = 12;
    private static final int GREETING_AFTERNOON_START = 14;
    private static final int GREETING_EVENING_START   = 18;
    private static final int GREETING_NIGHT_START     = 22;

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
            accountViewModel.loadAccounts();
        }
        if (tabToday != null) {
            selectTab(currentTab);
        }
        // Sync unsynced transactions first, then reload wallet balances to ensure
        // wallet balance = cumulative of all transactions (including prior months).
        if (viewModel != null) {
            viewModel.syncTransactions(new TransactionRepository.SyncCallback() {
                @Override
                public void onSuccess() {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            dashboardViewModel.loadDashboardSummary();
                            accountViewModel.loadAccounts();
                        });
                    }
                }
                @Override
                public void onError(String message) {}
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        colorTabActive   = ContextCompat.getColor(requireContext(), R.color.brand_primary);
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        accountViewModel   = new ViewModelProvider(requireActivity()).get(vn.edu.usth.tip.viewmodels.AccountViewModel.class);
        insightViewModel   = new ViewModelProvider(this).get(InsightViewModel.class);

        setupInsightCard(view);
        setupTransactionList(view);
        setupRefreshLayout(view);
        setupObservers(view);
        setupHeaderAndActions(view);
    }

    private void setupInsightCard(View view) {
        View cardInsight    = view.findViewById(R.id.card_ai_insight_dashboard);
        TextView tvInsightTitle = view.findViewById(R.id.tv_dashboard_insight_title);
        TextView tvInsightBody  = view.findViewById(R.id.tv_dashboard_insight_body);

        if (cardInsight != null) {
            cardInsight.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.action_dashboard_to_insights));
        }

        insightViewModel.getInsights().observe(getViewLifecycleOwner(), insights -> {
            if (insights == null || insights.isEmpty()) return;
            Insight top = null;
            for (Insight ins : insights) {
                if (ins.priority == InsightPriority.HIGH) { top = ins; break; }
            }
            if (top == null) top = insights.get(0);
            if (tvInsightTitle != null) tvInsightTitle.setText(top.title);
            if (tvInsightBody  != null) tvInsightBody.setText(top.body);
        });

        insightViewModel.generateInsights();
    }

    private void setupTransactionList(View view) {
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
    }

    private void setupRefreshLayout(View view) {
        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        if (swipeRefreshLayout == null) return;

        swipeRefreshLayout.setColorSchemeColors(colorTabActive);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (dashboardViewModel != null) dashboardViewModel.loadDashboardSummary();
            if (accountViewModel  != null) accountViewModel.loadAccounts();
            if (tabToday != null) selectTab(currentTab);
            if (insightViewModel  != null) insightViewModel.generateInsights();

            if (viewModel != null) {
                viewModel.syncTransactions(new TransactionRepository.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                dashboardViewModel.loadDashboardSummary();
                                accountViewModel.loadAccounts();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    }
                    @Override
                    public void onError(String message) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
                        }
                    }
                });
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void setupObservers(View view) {
        accountViewModel.getAccountsData().observe(getViewLifecycleOwner(), accounts ->
                updateOptimisticDashboard(view));

        viewModel.getTransactions().observe(getViewLifecycleOwner(), transactions ->
                updateOptimisticDashboard(view));

        viewModel.getTotalIOwe().observe(getViewLifecycleOwner(), total ->
                updateOptimisticDashboard(view));

        viewModel.getTotalOwedToMe().observe(getViewLifecycleOwner(), total ->
                updateOptimisticDashboard(view));

        dashboardViewModel.getSummaryData().observe(getViewLifecycleOwner(), summary -> {
            if (summary == null) return;
            TextView tvGrowth = view.findViewById(R.id.tv_net_worth_growth);
            if (tvGrowth != null) tvGrowth.setText("Dữ liệu đồng bộ từ đám mây");
            updateOptimisticDashboard(view);
        });
    }

    private void setupHeaderAndActions(View view) {
        TextView tvGreeting = view.findViewById(R.id.tv_greeting);
        if (tvGreeting != null) tvGreeting.setText(buildGreeting());

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
                    Navigation.findNavController(v).navigate(R.id.action_dashboard_to_walletManagement));
        }

        View btnAddExpense = view.findViewById(R.id.btn_add_expense);
        if (btnAddExpense != null) {
            btnAddExpense.setOnClickListener(v -> AnimUtils.bounceClick(v, () -> {
                viewModel.setDefaultNewTransactionType(Transaction.Type.EXPENSE);
                Navigation.findNavController(v).navigate(R.id.action_dashboard_to_newTransaction);
            }));
        }

        View btnAddIncome = view.findViewById(R.id.btn_add_income);
        if (btnAddIncome != null) {
            btnAddIncome.setOnClickListener(v -> AnimUtils.bounceClick(v, () -> {
                viewModel.setDefaultNewTransactionType(Transaction.Type.INCOME);
                Navigation.findNavController(v).navigate(R.id.action_dashboard_to_newTransaction);
            }));
        }

        View btnScanReceipt = view.findViewById(R.id.btn_scan_receipt);
        if (btnScanReceipt != null) {
            btnScanReceipt.setOnClickListener(v ->
                    AnimUtils.bounceClick(v, () ->
                            Navigation.findNavController(v).navigate(R.id.action_dashboard_to_scanReceipt)));
        }

        View btnBudgets = view.findViewById(R.id.btn_budgets);
        if (btnBudgets != null) {
            btnBudgets.setOnClickListener(v ->
                    AnimUtils.bounceClick(v, () ->
                            Navigation.findNavController(v).navigate(R.id.action_dashboard_to_budgets)));
        }

        tabToday = view.findViewById(R.id.tab_today);
        tabWeek  = view.findViewById(R.id.tab_week);
        tabMonth = view.findViewById(R.id.tab_month);

        tabToday.setOnClickListener(v -> AnimUtils.bounceClick(v, () -> selectTab(TAB_TODAY)));
        tabWeek.setOnClickListener(v  -> AnimUtils.bounceClick(v, () -> selectTab(TAB_WEEK)));
        tabMonth.setOnClickListener(v -> AnimUtils.bounceClick(v, () -> selectTab(TAB_MONTH)));

        selectTab(TAB_TODAY);

        View tvSeeAll = view.findViewById(R.id.tv_see_all);
        if (tvSeeAll != null) {
            tvSeeAll.setOnClickListener(v ->
                    AnimUtils.bounceClick(v, () ->
                            Navigation.findNavController(v).navigate(R.id.action_dashboard_to_allTransactions)));
        }
        View btnSeeAll = view.findViewById(R.id.btn_see_all_transactions);
        if (btnSeeAll != null) {
            btnSeeAll.setOnClickListener(v ->
                    AnimUtils.bounceClick(v, () ->
                            Navigation.findNavController(v).navigate(R.id.action_dashboard_to_allTransactions)));
        }
    }

    private void updateOptimisticDashboard(View view) {
        if (view == null) return;

        List<vn.edu.usth.tip.network.responses.AccountResponse> accounts =
                accountViewModel.getAccountsData().getValue();
        List<Transaction> transactions = viewModel.getTransactions().getValue();
        vn.edu.usth.tip.network.responses.DashboardSummary summary =
                dashboardViewModel.getSummaryData().getValue();

        updateNetWorthDisplay(view, accounts, transactions);
        updateMonthlySummaryDisplay(view, summary, transactions);
    }

    private void updateNetWorthDisplay(View view,
            List<vn.edu.usth.tip.network.responses.AccountResponse> accounts,
            List<Transaction> transactions) {
        TextView tvTotalAssets    = view.findViewById(R.id.tv_total_assets);
        TextView tvNetWorthInside = view.findViewById(R.id.tv_net_worth);

        long totalAssets = 0;
        if (accounts != null && !accounts.isEmpty()) {
            for (vn.edu.usth.tip.network.responses.AccountResponse acc : accounts) {
                if (acc.getIncludeInTotal() != null && acc.getIncludeInTotal()) {
                    long balance = acc.getBalance();
                    if (transactions != null) {
                        for (Transaction t : transactions) {
                            if (!t.isSynced() && t.getWalletName() != null
                                    && t.getWalletName().equals(acc.getName())) {
                                if (t.getType() == Transaction.Type.INCOME)       balance += t.getAmountVnd();
                                else if (t.getType() == Transaction.Type.EXPENSE) balance -= t.getAmountVnd();
                                // TRANSFER: net effect on total assets = 0 (deduct one wallet, add another)
                                // Not adjusted here because only one walletName is known.
                            }
                        }
                    }
                    totalAssets += balance;
                }
            }
        } else {
            // Fallback to Room data (AppViewModel EngineState) when API not yet loaded.
            AppViewModel.EngineState engineState = viewModel.getEngineState().getValue();
            if (engineState != null && engineState.wallets != null) {
                for (vn.edu.usth.tip.models.Wallet w : engineState.wallets) {
                    if (w.isIncludedInTotal()) {
                        long balance = w.getBalanceVnd();
                        // Add unsynced transactions on top of the already-synced Room balance.
                        if (transactions != null) {
                            for (Transaction t : transactions) {
                                if (!t.isSynced() && t.getWalletName() != null
                                        && t.getWalletName().equals(w.getName())) {
                                    if (t.getType() == Transaction.Type.INCOME)       balance += t.getAmountVnd();
                                    else if (t.getType() == Transaction.Type.EXPENSE) balance -= t.getAmountVnd();
                                    // TRANSFER: net effect = 0 → skip
                                }
                            }
                        }
                        totalAssets += balance;
                    }
                }
            }
        }

        Long iOwe     = viewModel.getTotalIOwe().getValue();
        Long owedToMe = viewModel.getTotalOwedToMe().getValue();
        long netWorth = totalAssets - (iOwe != null ? iOwe : 0) + (owedToMe != null ? owedToMe : 0);

        if (tvTotalAssets != null) {
            tvTotalAssets.setText(MoneyFormat.formatShortStyled(totalAssets));
        }
        if (tvNetWorthInside != null) {
            tvNetWorthInside.setText(MoneyFormat.formatShortStyled(netWorth));
        }
    }

    private void updateMonthlySummaryDisplay(View view,
            vn.edu.usth.tip.network.responses.DashboardSummary summary,
            List<Transaction> transactions) {
        TextView tvMonthlyIncome   = view.findViewById(R.id.tv_monthly_income);
        TextView tvMonthlyExpense  = view.findViewById(R.id.tv_monthly_expense);
        TextView tvMonthlyTransfer = view.findViewById(R.id.tv_monthly_transfer);

        if (summary != null) {
            long optIncome   = summary.getTotalIncomeThisMonth();
            long optExpense  = summary.getTotalExpenseThisMonth();
            long optTransfer = summary.getTotalTransferThisMonth();

            if (transactions != null) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                clearTime(cal);
                long startOfMonth = cal.getTimeInMillis();

                for (Transaction t : transactions) {
                    if (!t.isSynced() && t.getTimestampMs() >= startOfMonth) {
                        if (t.getType() == Transaction.Type.INCOME)        optIncome   += t.getAmountVnd();
                        else if (t.getType() == Transaction.Type.EXPENSE)  optExpense  += t.getAmountVnd();
                        else if (t.getType() == Transaction.Type.TRANSFER) optTransfer += t.getAmountVnd();
                    }
                }
            }

            if (tvMonthlyIncome   != null) tvMonthlyIncome.setText(MoneyFormat.formatShortStyled(optIncome));
            if (tvMonthlyExpense  != null) tvMonthlyExpense.setText(MoneyFormat.formatShortStyled(optExpense));
            if (tvMonthlyTransfer != null) tvMonthlyTransfer.setText(MoneyFormat.formatShortStyled(optTransfer));
        } else {
            AppViewModel.EngineState engineState = viewModel.getEngineState().getValue();
            if (engineState != null) {
                if (tvMonthlyIncome   != null) tvMonthlyIncome.setText(MoneyFormat.formatShortStyled(engineState.mIncome));
                if (tvMonthlyExpense  != null) tvMonthlyExpense.setText(MoneyFormat.formatShortStyled(engineState.mExpense));
                if (tvMonthlyTransfer != null) tvMonthlyTransfer.setText(MoneyFormat.formatShortStyled(engineState.mTransfer));
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
            active.setBackgroundColor(colorTabActive);
            active.setTextColor(COLOR_TAB_TEXT_ON);
        }

        Calendar from = Calendar.getInstance();
        Calendar to   = Calendar.getInstance();
        clearTime(from);
        clearTime(to);

        if (tab == TAB_TODAY) {
            to.add(Calendar.DAY_OF_MONTH, 1);
        } else if (tab == TAB_WEEK) {
            int dayOfWeek    = from.get(Calendar.DAY_OF_WEEK);
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

        // Clear stale data and show loading spinner to prevent UI flicker between tabs.
        if (txAdapter != null) txAdapter.setData(new ArrayList<>());
        if (emptyState != null) emptyState.setVisibility(View.GONE);
        View pbTransactions = getView() != null ? getView().findViewById(R.id.pb_transactions) : null;
        if (pbTransactions != null) pbTransactions.setVisibility(View.VISIBLE);

        currentTxLiveData = viewModel.getTransactionsBetween(fromMs, toMs);
        txObserver = txList -> {
            if (pbTransactions != null) pbTransactions.setVisibility(View.GONE);
            if (txAdapter != null) txAdapter.setData(txList != null ? txList : new ArrayList<>());
            if (emptyState != null) {
                emptyState.setVisibility((txList == null || txList.isEmpty()) ? View.VISIBLE : View.GONE);
            }
        };
        currentTxLiveData.observe(getViewLifecycleOwner(), txObserver);
    }

    private static String buildGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= GREETING_MORNING_START   && hour < GREETING_NOON_START)      return "Bắt đầu ngày tài chính thật sáng suốt ✨";
        if (hour >= GREETING_NOON_START      && hour < GREETING_AFTERNOON_START) return "Kiểm tra chi tiêu buổi sáng của bạn 📊";
        if (hour >= GREETING_AFTERNOON_START && hour < GREETING_EVENING_START)   return "Quản lý tài chính thông minh 💼";
        if (hour >= GREETING_EVENING_START   && hour < GREETING_NIGHT_START)     return "Tổng kết tài chính hôm nay 🌆";
        return "Lên kế hoạch tài chính cho ngày mai 🌙";
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
