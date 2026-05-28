package vn.edu.usth.tip.ui.fragments;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.edu.usth.tip.utils.MoneyFormat;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.insights.InsightViewModel;
import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.network.responses.DashboardSummary;
import vn.edu.usth.tip.viewmodels.AppViewModel;
import vn.edu.usth.tip.viewmodels.DashboardViewModel;

public class AnalyticsFragment extends Fragment {

    private static final int BAR_MAX_HEIGHT_DP = 120;
    private static final int BAR_SLIVER_DP     = 4;

    private enum BarFilter { BOTH, INCOME_ONLY, EXPENSE_ONLY }

    private DashboardViewModel dashboardViewModel;
    private AppViewModel appViewModel;
    private InsightViewModel insightViewModel;

    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvTransfer;

    private androidx.cardview.widget.CardView[] cvIncomes = new androidx.cardview.widget.CardView[6];
    private androidx.cardview.widget.CardView[] cvExpenses = new androidx.cardview.widget.CardView[6];
    private TextView[] tvMonths = new TextView[6];

    private BarFilter mBarFilter = BarFilter.BOTH;
    private View mBtnLegendIncome;
    private View mBtnLegendExpense;

    private Handler mClockHandler;
    private Runnable mClockRunnable;
    private boolean mChartInitialized = false;

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
        insightViewModel = new ViewModelProvider(this).get(InsightViewModel.class);

        // Nhúng SpendingByCategoryFragment vào container (chỉ lần đầu, không nhân đôi khi rotate)
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.container_donut_chart, new SpendingByCategoryFragment())
                    .commit();
        }

        dashboardViewModel.getSummaryData().observe(getViewLifecycleOwner(), summary -> updateAnalyticsSummary());
        appViewModel.getTransactions().observe(getViewLifecycleOwner(), txs -> {
            updateAnalyticsSummary();
            updateBarChart(true);
        });
        appViewModel.getEngineState().observe(getViewLifecycleOwner(), state -> updateAnalyticsSummary());

        // Trigger load
        dashboardViewModel.loadDashboardSummary();

        // Real-time clock
        TextView tvCurrentDatetime = view.findViewById(R.id.tv_current_datetime);
        mClockHandler = new Handler(Looper.getMainLooper());
        mClockRunnable = new Runnable() {
            @Override
            public void run() {
                if (tvCurrentDatetime != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
                    tvCurrentDatetime.setText(sdf.format(new Date()));
                }
                mClockHandler.postDelayed(this, 60000);
            }
        };
        mClockRunnable.run();

        // Interactive legend buttons
        mBtnLegendIncome = view.findViewById(R.id.btn_legend_income);
        mBtnLegendExpense = view.findViewById(R.id.btn_legend_expense);

        if (mBtnLegendIncome != null) {
            mBtnLegendIncome.setOnClickListener(v -> {
                animateLegendClick(v);
                mBarFilter = (mBarFilter == BarFilter.INCOME_ONLY) ? BarFilter.BOTH : BarFilter.INCOME_ONLY;
                updateBarChart(false);
                updateLegendState();
            });
        }
        if (mBtnLegendExpense != null) {
            mBtnLegendExpense.setOnClickListener(v -> {
                animateLegendClick(v);
                mBarFilter = (mBarFilter == BarFilter.EXPENSE_ONLY) ? BarFilter.BOTH : BarFilter.EXPENSE_ONLY;
                updateBarChart(false);
                updateLegendState();
            });
        }

        // Card AI → navigate + cập nhật nội dung từ InsightViewModel
        View cardAiInsight = view.findViewById(R.id.card_ai_insight);
        TextView tvAiTitle = view.findViewById(R.id.tv_analytics_ai_title);
        TextView tvAiBody  = view.findViewById(R.id.tv_analytics_ai_body);

        if (cardAiInsight != null) {
            cardAiInsight.setOnClickListener(v ->
                Navigation.findNavController(v)
                    .navigate(R.id.action_analytics_to_insights));
        }

        insightViewModel.getInsights().observe(getViewLifecycleOwner(), insights -> {
            if (insights == null || insights.isEmpty()) return;
            Insight top = null;
            for (Insight ins : insights) {
                if (ins.priority == InsightPriority.HIGH) { top = ins; break; }
            }
            if (top == null) top = insights.get(0);
            if (tvAiTitle != null) tvAiTitle.setText(top.title);
            if (tvAiBody  != null) tvAiBody.setText(top.body);
        });

        insightViewModel.generateInsights();

        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_analytics);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.brand_primary));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                mChartInitialized = false;
                if (dashboardViewModel != null) dashboardViewModel.loadDashboardSummary();
                if (insightViewModel != null) insightViewModel.generateInsights();
                swipeRefreshLayout.setRefreshing(false);
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

            if (tvIncome != null) tvIncome.setText(MoneyFormat.formatShortStyled(optIncome));
            if (tvExpense != null) tvExpense.setText(MoneyFormat.formatShortStyled(optExpense));
            if (tvTransfer != null) tvTransfer.setText(MoneyFormat.formatShortStyled(optTransfer));
        } else if (engineState != null) {
            if (tvIncome != null) tvIncome.setText(MoneyFormat.formatShortStyled(engineState.mIncome));
            if (tvExpense != null) tvExpense.setText(MoneyFormat.formatShortStyled(engineState.mExpense));
            if (tvTransfer != null) tvTransfer.setText(MoneyFormat.formatShortStyled(engineState.mTransfer));
        }
    }

    private void updateBarChart(boolean staggered) {
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
            int month = cal.get(Calendar.MONTH) + 1;
            monthLabels[i] = "T" + month;

            long startOfMonth = cal.getTimeInMillis();
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
            cal.add(Calendar.MONTH, -1);
        }

        long maxVal = 0;
        for (int i = 0; i < 6; i++) {
            if (incomes[i] > maxVal) maxVal = incomes[i];
            if (expenses[i] > maxVal) maxVal = expenses[i];
        }

        if (getContext() == null) return;
        float density = getResources().getDisplayMetrics().density;
        int maxPx = (int) (BAR_MAX_HEIGHT_DP * density);

        // On first data load use stagger; on filter toggle animate all at once
        boolean useStagger = staggered && !mChartInitialized;
        if (staggered) mChartInitialized = true;

        for (int i = 0; i < 6; i++) {
            if (tvMonths[i] != null) tvMonths[i].setText(monthLabels[i]);

            int incPx = maxVal == 0 ? 0 : (int) ((incomes[i] * maxPx) / maxVal);
            int expPx = maxVal == 0 ? 0 : (int) ((expenses[i] * maxPx) / maxVal);

            if (incomes[i]  > 0 && incPx < BAR_SLIVER_DP) incPx = BAR_SLIVER_DP;
            if (expenses[i] > 0 && expPx < BAR_SLIVER_DP) expPx = BAR_SLIVER_DP;

            if (mBarFilter == BarFilter.EXPENSE_ONLY) incPx = 0;
            if (mBarFilter == BarFilter.INCOME_ONLY) expPx = 0;

            long delay = useStagger ? i * 70L : 0L;
            animateBar(cvIncomes[i], incPx, delay);
            animateBar(cvExpenses[i], expPx, delay + (useStagger ? 25L : 0L));
        }
    }

    private void animateBar(View bar, int targetHeight, long startDelay) {
        if (bar == null) return;
        int fromHeight = bar.getLayoutParams().height;
        if (fromHeight == targetHeight) return;
        ValueAnimator anim = ValueAnimator.ofInt(fromHeight, targetHeight);
        anim.setDuration(380);
        anim.setStartDelay(startDelay);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = (int) a.getAnimatedValue();
            bar.setLayoutParams(lp);
        });
        anim.start();
    }

    private void animateLegendClick(View v) {
        v.animate()
            .scaleX(0.90f).scaleY(0.90f)
            .setDuration(90)
            .withEndAction(() -> v.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .start())
            .start();
    }

    private void updateLegendState() {
        if (mBtnLegendIncome != null) {
            float a = mBarFilter == BarFilter.EXPENSE_ONLY ? 0.4f : 1.0f;
            mBtnLegendIncome.animate().alpha(a).setDuration(200).start();
        }
        if (mBtnLegendExpense != null) {
            float a = mBarFilter == BarFilter.INCOME_ONLY ? 0.4f : 1.0f;
            mBtnLegendExpense.animate().alpha(a).setDuration(200).start();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mClockHandler != null) {
            mClockHandler.removeCallbacks(mClockRunnable);
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
