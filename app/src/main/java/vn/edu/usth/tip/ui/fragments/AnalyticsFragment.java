package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Calendar;
import java.util.List;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.network.responses.DashboardSummary;
import vn.edu.usth.tip.viewmodels.AppViewModel;
import vn.edu.usth.tip.viewmodels.DashboardViewModel;

public class AnalyticsFragment extends Fragment {

    private DashboardViewModel dashboardViewModel;
    private AppViewModel appViewModel;

    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvTransfer;

    private androidx.cardview.widget.CardView[] cvIncomes = new androidx.cardview.widget.CardView[6];
    private androidx.cardview.widget.CardView[] cvExpenses = new androidx.cardview.widget.CardView[6];
    private TextView[] tvMonths = new TextView[6];

    public AnalyticsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_analytics, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvIncome = view.findViewById(R.id.tv_analytics_income);
        tvExpense = view.findViewById(R.id.tv_analytics_expense);
        tvTransfer = view.findViewById(R.id.tv_analytics_transfer);

        cvIncomes[0] = view.findViewById(R.id.cv_bar_income_1);
        cvIncomes[1] = view.findViewById(R.id.cv_bar_income_2);
        cvIncomes[2] = view.findViewById(R.id.cv_bar_income_3);
        cvIncomes[3] = view.findViewById(R.id.cv_bar_income_4);
        cvIncomes[4] = view.findViewById(R.id.cv_bar_income_5);
        cvIncomes[5] = view.findViewById(R.id.cv_bar_income_6);

        cvExpenses[0] = view.findViewById(R.id.cv_bar_expense_1);
        cvExpenses[1] = view.findViewById(R.id.cv_bar_expense_2);
        cvExpenses[2] = view.findViewById(R.id.cv_bar_expense_3);
        cvExpenses[3] = view.findViewById(R.id.cv_bar_expense_4);
        cvExpenses[4] = view.findViewById(R.id.cv_bar_expense_5);
        cvExpenses[5] = view.findViewById(R.id.cv_bar_expense_6);

        tvMonths[0] = view.findViewById(R.id.tv_month_label_1);
        tvMonths[1] = view.findViewById(R.id.tv_month_label_2);
        tvMonths[2] = view.findViewById(R.id.tv_month_label_3);
        tvMonths[3] = view.findViewById(R.id.tv_month_label_4);
        tvMonths[4] = view.findViewById(R.id.tv_month_label_5);
        tvMonths[5] = view.findViewById(R.id.tv_month_label_6);

        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        dashboardViewModel.getSummaryData().observe(getViewLifecycleOwner(), summary -> updateAnalyticsSummary());
        appViewModel.getTransactions().observe(getViewLifecycleOwner(), txs -> {
            updateAnalyticsSummary();
            updateBarChart();
        });
        appViewModel.getEngineState().observe(getViewLifecycleOwner(), state -> updateAnalyticsSummary());

        // Trigger load
        dashboardViewModel.loadDashboardSummary();

        View btnFilterCategory = view.findViewById(R.id.btn_filter_category);
        View btnFilterTime = view.findViewById(R.id.btn_filter_time);

        if (btnFilterCategory != null) {
            btnFilterCategory.setOnClickListener(v -> {
                String[] categories = {"Tất cả", "Ăn uống", "Di chuyển", "Mua sắm", "Giải trí"};
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Chọn danh mục")
                        .setItems(categories, (dialog, which) -> {
                            Toast.makeText(requireContext(), "Lọc: " + categories[which], Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });
        }

        if (btnFilterTime != null) {
            btnFilterTime.setOnClickListener(v -> {
                String[] times = {"Tuần này", "Tháng này", "Tháng trước", "Năm nay"};
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Chọn thời gian")
                        .setItems(times, (dialog, which) -> {
                            Toast.makeText(requireContext(), "Lọc: " + times[which], Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });
        }
    }

    private void updateAnalyticsSummary() {
        DashboardSummary summary = dashboardViewModel.getSummaryData().getValue();
        List<Transaction> txs = appViewModel.getTransactions().getValue();
        AppViewModel.EngineState engineState = appViewModel.getEngineState().getValue();

        if (summary != null) {
            long optIncome = summary.getTotalIncomeThisMonth();
            long optExpense = summary.getTotalExpenseThisMonth();
            long optTransfer = summary.getTotalTransferThisMonth();

            if (txs != null) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfMonth = cal.getTimeInMillis();

                for (Transaction t : txs) {
                    if (!t.isSynced() && t.getTimestampMs() >= startOfMonth) {
                        if (t.getType() == Transaction.Type.INCOME) optIncome += t.getAmountVnd();
                        else if (t.getType() == Transaction.Type.EXPENSE) optExpense += t.getAmountVnd();
                        else if (t.getType() == Transaction.Type.TRANSFER) optTransfer += t.getAmountVnd();
                    }
                }
            }

            if (tvIncome != null) tvIncome.setText("₫" + String.format("%,d", optIncome).replace(",", "."));
            if (tvExpense != null) tvExpense.setText("₫" + String.format("%,d", optExpense).replace(",", "."));
            if (tvTransfer != null) tvTransfer.setText("₫" + String.format("%,d", optTransfer).replace(",", "."));
        } else if (engineState != null) {
            if (tvIncome != null) tvIncome.setText("₫" + String.format("%,d", engineState.mIncome).replace(",", "."));
            if (tvExpense != null) tvExpense.setText("₫" + String.format("%,d", engineState.mExpense).replace(",", "."));
            if (tvTransfer != null) tvTransfer.setText("₫" + String.format("%,d", engineState.mTransfer).replace(",", "."));
        }
    }

    private void updateBarChart() {
        List<Transaction> txs = appViewModel.getTransactions().getValue();
        if (txs == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long[] incomes = new long[6];
        long[] expenses = new long[6];
        String[] monthLabels = new String[6];

        for (int i = 5; i >= 0; i--) {
            int month = cal.get(Calendar.MONTH) + 1; // 1-based month
            monthLabels[i] = "T" + month;

            long startOfMonth = cal.getTimeInMillis();

            // Find end of month
            Calendar endCal = (Calendar) cal.clone();
            endCal.add(Calendar.MONTH, 1);
            long endOfMonth = endCal.getTimeInMillis();

            long mIncome = 0;
            long mExpense = 0;

            for (Transaction t : txs) {
                if (t.getTimestampMs() >= startOfMonth && t.getTimestampMs() < endOfMonth) {
                    if (t.getType() == Transaction.Type.INCOME) mIncome += t.getAmountVnd();
                    else if (t.getType() == Transaction.Type.EXPENSE) mExpense += t.getAmountVnd();
                }
            }

            incomes[i] = mIncome;
            expenses[i] = mExpense;

            // Move to previous month
            cal.add(Calendar.MONTH, -1);
        }

        long maxVal = 0;
        for (int i = 0; i < 6; i++) {
            if (incomes[i] > maxVal) maxVal = incomes[i];
            if (expenses[i] > maxVal) maxVal = expenses[i];
        }

        if (getContext() == null) return;
        float density = getResources().getDisplayMetrics().density;
        int maxDp = 120; // Maximum bar height
        int maxPx = (int) (maxDp * density);

        for (int i = 0; i < 6; i++) {
            if (tvMonths[i] != null) tvMonths[i].setText(monthLabels[i]);

            int incPx = maxVal == 0 ? 0 : (int) ((incomes[i] * maxPx) / maxVal);
            int expPx = maxVal == 0 ? 0 : (int) ((expenses[i] * maxPx) / maxVal);

            // Give a sliver if there's > 0 amount so it's not totally invisible
            if (incomes[i] > 0 && incPx < 4) incPx = 4;
            if (expenses[i] > 0 && expPx < 4) expPx = 4;

            if (cvIncomes[i] != null) {
                ViewGroup.LayoutParams lp = cvIncomes[i].getLayoutParams();
                lp.height = incPx;
                cvIncomes[i].setLayoutParams(lp);
            }
            if (cvExpenses[i] != null) {
                ViewGroup.LayoutParams lp = cvExpenses[i].getLayoutParams();
                lp.height = expPx;
                cvExpenses[i].setLayoutParams(lp);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dashboardViewModel != null) {
            dashboardViewModel.loadDashboardSummary();
        }
    }
}
