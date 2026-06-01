package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import vn.edu.usth.tip.utils.MoneyFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.utils.AnimUtils;
import vn.edu.usth.tip.adapters.BudgetAdapter;
import vn.edu.usth.tip.viewmodels.AppViewModel;
import vn.edu.usth.tip.viewmodels.AppViewModel.BudgetWithSpent;

public class BudgetsFragment extends Fragment {

    private AppViewModel  viewModel;
    private BudgetAdapter budgetAdapter;

    // Summary views
    private TextView    tvTotalBudgeted, tvTotalSpent, tvTotalRemaining;
    private TextView    tvOverallPercent, tvActiveCount;
    private ProgressBar progressOverall;
    private View        emptyState, listLabel, summaryCard;

    public BudgetsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_budgets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        // Bind summary views
        tvTotalBudgeted  = view.findViewById(R.id.tv_total_budgeted);
        tvTotalSpent     = view.findViewById(R.id.tv_total_spent);
        tvTotalRemaining = view.findViewById(R.id.tv_total_remaining);
        tvOverallPercent = view.findViewById(R.id.tv_overall_percent);
        tvActiveCount    = view.findViewById(R.id.tv_active_count);
        progressOverall  = view.findViewById(R.id.progress_overall);
        emptyState       = view.findViewById(R.id.layout_budget_empty);
        listLabel        = view.findViewById(R.id.tv_budget_list_label);
        summaryCard      = view.findViewById(R.id.card_budget_summary);

        // RecyclerView
        budgetAdapter = new BudgetAdapter(item -> {
            AddBudgetSheet sheet = new AddBudgetSheet();
            sheet.setExistingBudget(item.budget);
            sheet.show(getChildFragmentManager(), "edit_budget");
        });

        RecyclerView rv = view.findViewById(R.id.rv_budgets);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(budgetAdapter);

        // Observer
        viewModel.getBudgetState().observe(getViewLifecycleOwner(), this::renderBudgets);

        // Back button
        View btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    androidx.navigation.Navigation.findNavController(v).popBackStack()
            );
        }

        // Header button: Add Budget
        View btnNew = view.findViewById(R.id.btn_new_budget);
        if (btnNew != null) {
            btnNew.setOnClickListener(v -> AnimUtils.bounceClick(v, () -> {
                AddBudgetSheet sheet = new AddBudgetSheet();
                sheet.show(getChildFragmentManager(), "add_budget");
            }));
        }

        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_budgets);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.brand_primary));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (viewModel != null) {
                    viewModel.syncBudgets(new vn.edu.usth.tip.repositories.BudgetsRepository.SyncCallback() {
                        @Override public void onSuccess() {
                            if (getActivity() != null)
                                getActivity().runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
                        }
                        @Override public void onError(String msg) {
                            if (getActivity() != null)
                                getActivity().runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
                        }
                    });
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }
    }

    private void renderBudgets(List<BudgetWithSpent> list) {
        if (list == null || list.isEmpty()) {
            if (emptyState   != null) emptyState.setVisibility(View.VISIBLE);
            if (summaryCard  != null) summaryCard.setVisibility(View.GONE);
            if (listLabel    != null) listLabel.setVisibility(View.GONE);
            if (budgetAdapter != null) budgetAdapter.setData(null);
            return;
        }

        if (emptyState  != null) emptyState.setVisibility(View.GONE);
        if (summaryCard != null) summaryCard.setVisibility(View.VISIBLE);
        if (listLabel   != null) listLabel.setVisibility(View.VISIBLE);
        if (budgetAdapter != null) budgetAdapter.setData(list);

        long totalBudgeted = 0, totalSpent = 0;
        long now = System.currentTimeMillis();
        int  activeCount = 0;
        for (BudgetWithSpent b : list) {
            if (b == null || b.budget == null) continue;
            totalBudgeted += b.budget.getLimitAmount();
            totalSpent    += b.spentAmount;
            if (b.budget.getPeriodEndMs() >= now) activeCount++;
        }
        long remaining = totalBudgeted - totalSpent;

        float pctFloat = totalBudgeted > 0 ? (totalSpent * 100.0f) / totalBudgeted : 0f;
        int   pctInt   = (int) Math.min(100, pctFloat);

        if (tvTotalBudgeted  != null) tvTotalBudgeted.setText(MoneyFormat.formatShortStyled(totalBudgeted));
        if (tvTotalSpent     != null) tvTotalSpent.setText(MoneyFormat.formatShortStyled(totalSpent));
        if (tvTotalRemaining != null) {
            tvTotalRemaining.setText(MoneyFormat.formatShortStyled(Math.max(0, remaining)));
            tvTotalRemaining.setTextColor(pctInt >= 90 ? 0xFFE76E60 : 0xFF2DD3A1);
        }
        if (progressOverall  != null) progressOverall.setProgress(pctInt);

        String percentText;
        if (pctFloat == 0f || pctFloat >= 100f || pctFloat == (int) pctFloat) {
            percentText = String.format(java.util.Locale.US, "%d%% đã chi", (int) pctFloat);
        } else if (pctFloat < 0.01f) {
            percentText = "<0.01% đã chi";
        } else if (pctFloat < 1f) {
            percentText = String.format(java.util.Locale.US, "%.2f%% đã chi", pctFloat);
        } else {
            percentText = String.format(java.util.Locale.US, "%.1f%% đã chi", pctFloat);
        }
        if (tvOverallPercent != null) tvOverallPercent.setText(percentText);
        if (tvActiveCount    != null) tvActiveCount.setText(activeCount + " đang hoạt động");
    }

    private void resetSummary() {
        if (tvTotalBudgeted  != null) tvTotalBudgeted.setText(MoneyFormat.formatShortStyled(0));
        if (tvTotalSpent     != null) tvTotalSpent.setText(MoneyFormat.formatShortStyled(0));
        if (tvTotalRemaining != null) tvTotalRemaining.setText(MoneyFormat.formatShortStyled(0));
        if (progressOverall  != null) progressOverall.setProgress(0);
        if (tvOverallPercent != null) tvOverallPercent.setText("0% đã chi");
        if (tvActiveCount    != null) tvActiveCount.setText("0 ngân sách");
    }

}
