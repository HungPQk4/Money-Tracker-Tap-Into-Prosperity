package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import vn.edu.usth.tip.R;
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
    private View        emptyState, listLabel;

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
            btnNew.setOnClickListener(v -> {
                AddBudgetSheet sheet = new AddBudgetSheet();
                sheet.show(getChildFragmentManager(), "add_budget");
            });
        }
    }

    private void renderBudgets(List<BudgetWithSpent> list) {
        if (list == null || list.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            listLabel.setVisibility(View.GONE);
            resetSummary();
            budgetAdapter.setData(null);
            return;
        }

        emptyState.setVisibility(View.GONE);
        listLabel.setVisibility(View.VISIBLE);
        budgetAdapter.setData(list);

        // Calculate totals
        long totalBudgeted = 0, totalSpent = 0;
        for (BudgetWithSpent b : list) {
            totalBudgeted += b.budget.getLimitAmount();
            totalSpent    += b.spentAmount;
        }
        long remaining = totalBudgeted - totalSpent;

        if (totalBudgeted == 0) totalBudgeted = 1;
        float pctFloat = (totalSpent * 100.0f) / totalBudgeted;
        int pctInt = (int) Math.min(100, pctFloat);

        tvTotalBudgeted.setText("₫" + fmtVnd(totalBudgeted));
        tvTotalSpent.setText("₫"    + fmtVnd(totalSpent));
        tvTotalRemaining.setText("₫" + fmtVnd(Math.max(0, remaining)));

        progressOverall.setProgress(pctInt);

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
        tvOverallPercent.setText(percentText);
        
        tvActiveCount.setText(list.size() + " ngân sách");

        // Color remaining
        if (pctInt >= 90) {
            tvTotalRemaining.setTextColor(0xFFE76E60);
        } else {
            tvTotalRemaining.setTextColor(0xFF2DD3A1);
        }
    }

    private void resetSummary() {
        tvTotalBudgeted.setText("₫0");
        tvTotalSpent.setText("₫0");
        tvTotalRemaining.setText("₫0");
        progressOverall.setProgress(0);
        tvOverallPercent.setText("0% đã chi");
        tvActiveCount.setText("0 ngân sách");
    }

    private static String fmtVnd(long v) {
        return String.format("%,d", v).replace(",", ".");
    }
}
