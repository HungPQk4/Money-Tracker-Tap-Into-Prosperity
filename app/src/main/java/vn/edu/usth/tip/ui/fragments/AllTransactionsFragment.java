package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.adapters.TransactionAdapter;
import vn.edu.usth.tip.viewmodels.AppViewModel;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import vn.edu.usth.tip.repositories.TransactionRepository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import vn.edu.usth.tip.R;

public class AllTransactionsFragment extends Fragment {

    private AppViewModel viewModel;
    private TransactionAdapter adapter;
    private List<Transaction>  allTransactions = new ArrayList<>();
    private List<Transaction>  displayList;

    private TextView tvSummaryIncome, tvSummaryExpense;
    private View     emptyState;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Filter state
    private static final int FILTER_ALL      = 0;
    private static final int FILTER_INCOME   = 1;
    private static final int FILTER_EXPENSE  = 2;
    private static final int FILTER_TRANSFER = 3;
    private int    currentFilter = FILTER_ALL;
    private String currentQuery  = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_all_transactions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back_all_tx).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp()
        );

        tvSummaryIncome  = view.findViewById(R.id.tv_summary_income);
        tvSummaryExpense = view.findViewById(R.id.tv_summary_expense);
        emptyState       = view.findViewById(R.id.layout_all_tx_empty);

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setColorSchemeColors(0xFF7B5CE7);
        swipeRefreshLayout.setOnRefreshListener(this::triggerSync);

        viewModel.getIsSyncingTransactions().observe(getViewLifecycleOwner(), isSyncing -> {
            swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(isSyncing));
        });

        displayList = new ArrayList<>();

        adapter = new TransactionAdapter(displayList, tx -> {
            TransactionDetailSheet sheet = TransactionDetailSheet.newInstance(tx,
                    new TransactionDetailSheet.OnTransactionActionListener() {
                        @Override
                        public void onEdit(Transaction t) {
                            viewModel.setEditingTransaction(t);
                            // Navigate using the same action we use in Dashboard if possible,
                            // but check if there's a specific nav action.
                            // Assuming global action or simple R.id.newTransaction works:
                            Navigation.findNavController(requireView()).navigate(R.id.newTransactionFragment);
                        }
                        @Override
                        public void onDelete(Transaction t) {
                            viewModel.deleteTransaction(t);
                        }
                    });
            sheet.show(getParentFragmentManager(), "tx_detail");
        });

        RecyclerView rv = view.findViewById(R.id.rv_all_transactions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        viewModel.getTransactions().observe(getViewLifecycleOwner(), transactions -> {
            allTransactions = transactions;
            applyFilters();
        });

        TextInputEditText etSearch = view.findViewById(R.id.et_search_transactions);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                currentQuery = s.toString().trim().toLowerCase();
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        ChipGroup chipGroup = view.findViewById(R.id.chip_group_filter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int chipId = checkedIds.get(0);
            if      (chipId == R.id.chip_income)   currentFilter = FILTER_INCOME;
            else if (chipId == R.id.chip_expense)  currentFilter = FILTER_EXPENSE;
            else if (chipId == R.id.chip_transfer) currentFilter = FILTER_TRANSFER;
            else                                    currentFilter = FILTER_ALL;
            applyFilters();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        triggerSync();
    }

    private void triggerSync() {
        viewModel.syncTransactions(new TransactionRepository.SyncCallback() {
            @Override public void onSuccess() {
                if (getContext() != null)
                    Toast.makeText(getContext(), "Đã cập nhật giao dịch", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                if (getContext() != null)
                    Toast.makeText(getContext(), "Lỗi đồng bộ: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilters() {
        if (allTransactions == null) return;
        List<Transaction> result = new ArrayList<>();
        for (Transaction tx : allTransactions) {
            boolean typeOk = true;
            if (currentFilter == FILTER_INCOME)   typeOk = tx.getType() == Transaction.Type.INCOME;
            if (currentFilter == FILTER_EXPENSE)  typeOk = tx.getType() == Transaction.Type.EXPENSE;
            if (currentFilter == FILTER_TRANSFER) typeOk = tx.getType() == Transaction.Type.TRANSFER;

            boolean searchOk = currentQuery.isEmpty()
                    || tx.getTitle().toLowerCase().contains(currentQuery)
                    || tx.getCategory().toLowerCase().contains(currentQuery)
                    || tx.getWalletName().toLowerCase().contains(currentQuery);

            if (typeOk && searchOk) result.add(tx);
        }

        adapter.setData(result);
        emptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
        updateSummary(result);
    }

    private void updateSummary(List<Transaction> list) {
        long totalIncome = 0, totalExpense = 0;
        for (Transaction tx : list) {
            if (tx.getType() == Transaction.Type.INCOME)   totalIncome  += tx.getAmountVnd();
            if (tx.getType() == Transaction.Type.EXPENSE)  totalExpense += tx.getAmountVnd();
        }
        tvSummaryIncome.setText(formatVnd(totalIncome));
        tvSummaryExpense.setText(formatVnd(totalExpense));
    }

    private String formatVnd(long amount) {
        return String.format("₫%,.0f", (double) amount).replace(",", ".");
    }
}