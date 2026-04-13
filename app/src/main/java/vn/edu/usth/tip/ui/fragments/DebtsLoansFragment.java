package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.adapters.DebtLoanAdapter;
import vn.edu.usth.tip.viewmodels.AppViewModel;

public class DebtsLoansFragment extends Fragment {

    private AppViewModel viewModel;
    private DebtLoanAdapter adapter;

    public DebtsLoansFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_debts, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        // Add Button
        View btnAddDebt = view.findViewById(R.id.btn_add_debt);
        if (btnAddDebt != null) {
            btnAddDebt.setOnClickListener(v -> {
                AddDebtSheet sheet = new AddDebtSheet();
                sheet.show(getChildFragmentManager(), "add_debt");
            });
        }

        // Setup RecyclerView
        RecyclerView rvDebts = view.findViewById(R.id.rv_debts);
        if (rvDebts != null) {
            rvDebts.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new DebtLoanAdapter();
            rvDebts.setAdapter(adapter);

            adapter.setOnDebtClickListener(debt -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Giao dịch hoàn tất?")
                        .setMessage("Đánh dấu khoản " + (debt.getType() == vn.edu.usth.tip.models.DebtLoan.TYPE_I_OWE ? "nợ" : "vay") + " này là đã hoàn thành và xoá khỏi hệ thống?")
                        .setPositiveButton("Hoàn thành", (dialog, which) -> {
                            viewModel.deleteDebtLoan(debt);
                            android.widget.Toast.makeText(requireContext(), "Đã hoàn thành giao dịch", android.widget.Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        }

        // Setup Observers
        viewModel.getDebts().observe(getViewLifecycleOwner(), debts -> {
            if (adapter != null) {
                adapter.setData(debts != null ? debts : new java.util.ArrayList<>());
            }
        });

        TextView tvTotalIOwe = view.findViewById(R.id.tv_total_i_owe);
        if (tvTotalIOwe != null) {
            viewModel.getTotalIOwe().observe(getViewLifecycleOwner(), total -> {
                tvTotalIOwe.setText(viewModel.formatCurrency(total != null ? total : 0));
            });
        }

        TextView tvTotalOwedToMe = view.findViewById(R.id.tv_total_owed_to_me);
        if (tvTotalOwedToMe != null) {
            viewModel.getTotalOwedToMe().observe(getViewLifecycleOwner(), total -> {
                tvTotalOwedToMe.setText(viewModel.formatCurrency(total != null ? total : 0));
            });
        }
    }
}
