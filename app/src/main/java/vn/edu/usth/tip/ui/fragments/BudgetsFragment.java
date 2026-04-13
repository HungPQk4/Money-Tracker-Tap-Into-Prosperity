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
            // Long-press: hỏi xóa
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xóa ngân sách")
                    .setMessage("Xóa \"" + item.budget.getName() + "\"?")
                    .setPositiveButton("Xóa", (d, w) -> viewModel.deleteBudget(item.budget))
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        RecyclerView rv = view.findViewById(R.id.rv_budgets);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(budgetAdapter);

        // Observer
        viewModel.getBudgetState().observe(getViewLifecycleOwner(), this::renderBudgets);

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
        int  pct       = totalBudgeted > 0 ? (int) Math.min(100, totalSpent * 100L / totalBudgeted) : 0;

        tvTotalBudgeted.setText("₫" + fmtVnd(totalBudgeted));
        tvTotalSpent.setText("₫"    + fmtVnd(totalSpent));
        tvTotalRemaining.setText("₫" + fmtVnd(Math.max(0, remaining)));

        progressOverall.setProgress(pct);
        tvOverallPercent.setText(pct + "% đã chi");
        tvActiveCount.setText(list.size() + " ngân sách");

        // Color remaining
        if (pct >= 90) {
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
