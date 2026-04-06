package vn.edu.usth.tip.fragment;

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

import vn.edu.usth.tip.AppViewModel;
import vn.edu.usth.tip.R;
import vn.edu.usth.tip.Transaction;
import vn.edu.usth.tip.TransactionAdapter;
import androidx.lifecycle.ViewModelProvider;

public class DashboardFragment extends Fragment {

    public DashboardFragment() {}

    // ── Tab state ─────────────────────────────────────────────────────
    private static final int TAB_TODAY = 0, TAB_WEEK = 1, TAB_MONTH = 2;
    private int currentTab = TAB_TODAY;

    private AppViewModel       viewModel;
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
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Header buttons ────────────────────────────────────────────
        View btnNotification = view.findViewById(R.id.btn_notification);
        if (btnNotification != null) {
            btnNotification.setOnClickListener(v ->
                    Toast.makeText(requireContext(), "Mở Thông báo", Toast.LENGTH_SHORT).show()
            );
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
            btnAddExpense.setOnClickListener(v ->
                    Navigation.findNavController(v)
                            .navigate(R.id.action_dashboard_to_newTransaction)
            );
        }

        // ── Recent Transactions ───────────────────────────────────────
        txAdapter = new TransactionAdapter(new ArrayList<>(), tx -> {
            // Bấm vào item → mở chi tiết giao dịch
            TransactionDetailSheet sheet = TransactionDetailSheet.newInstance(tx,
                    new TransactionDetailSheet.OnTransactionActionListener() {
                        @Override
                        public void onEdit(Transaction t) {
                            Toast.makeText(requireContext(),
                                    "Chỉnh sửa: " + t.getTitle(), Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onDelete(Transaction t) {
                            viewModel.deleteTransaction(t);
                        }
                    });
            sheet.show(getParentFragmentManager(), "tx_detail");
        });

        // Quan trọng: Quan sát dữ liệu từ ViewModel
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