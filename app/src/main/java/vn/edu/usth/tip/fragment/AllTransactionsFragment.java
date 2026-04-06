package vn.edu.usth.tip.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.Transaction;
import vn.edu.usth.tip.TransactionAdapter;

/**
 * Màn hình xem tất cả giao dịch với tìm kiếm + bộ lọc theo loại.
 */
public class AllTransactionsFragment extends Fragment {

    private TransactionAdapter adapter;
    private List<Transaction>  allTransactions;
    private List<Transaction>  displayList;

    private TextView tvSummaryIncome, tvSummaryExpense;
    private View     emptyState;

    // Filter state
    private static final int FILTER_ALL      = 0;
    private static final int FILTER_INCOME   = 1;
    private static final int FILTER_EXPENSE  = 2;
    private static final int FILTER_TRANSFER = 3;
    private int    currentFilter = FILTER_ALL;
    private String currentQuery  = "";

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

        // ── Back button ───────────────────────────────────────────────
        view.findViewById(R.id.btn_back_all_tx).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp()
        );

        // ── Summary views ─────────────────────────────────────────────
        tvSummaryIncome  = view.findViewById(R.id.tv_summary_income);
        tvSummaryExpense = view.findViewById(R.id.tv_summary_expense);
        emptyState       = view.findViewById(R.id.layout_all_tx_empty);

        // ── Sample data shared with Dashboard ─────────────────────────
        allTransactions = buildSampleTransactions();
        displayList     = new ArrayList<>(allTransactions);

        // ── Adapter ───────────────────────────────────────────────────
        adapter = new TransactionAdapter(displayList, tx -> {
            TransactionDetailSheet sheet = TransactionDetailSheet.newInstance(tx,
                    new TransactionDetailSheet.OnTransactionActionListener() {
                        @Override
                        public void onEdit(Transaction t) {
                            // TODO: open edit sheet
                        }
                        @Override
                        public void onDelete(Transaction t) {
                            allTransactions.remove(t);
                            applyFilters();
                        }
                    });
            sheet.show(getParentFragmentManager(), "tx_detail");
        });

        RecyclerView rv = view.findViewById(R.id.rv_all_transactions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // ── Search ────────────────────────────────────────────────────
        TextInputEditText etSearch = view.findViewById(R.id.et_search_transactions);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                currentQuery = s.toString().trim().toLowerCase();
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // ── Filter chips ──────────────────────────────────────────────
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

        // ── Initial render ────────────────────────────────────────────
        applyFilters();
    }

    // ── Filter + search logic ─────────────────────────────────────────

    private void applyFilters() {
        List<Transaction> result = new ArrayList<>();
        for (Transaction tx : allTransactions) {
            // Type filter
            boolean typeOk = true;
            if (currentFilter == FILTER_INCOME)   typeOk = tx.getType() == Transaction.Type.INCOME;
            if (currentFilter == FILTER_EXPENSE)  typeOk = tx.getType() == Transaction.Type.EXPENSE;
            if (currentFilter == FILTER_TRANSFER) typeOk = tx.getType() == Transaction.Type.TRANSFER;

            // Search filter
            boolean searchOk = currentQuery.isEmpty()
                    || tx.getTitle().toLowerCase().contains(currentQuery)
                    || tx.getCategory().toLowerCase().contains(currentQuery)
                    || tx.getWalletName().toLowerCase().contains(currentQuery);

            if (typeOk && searchOk) result.add(tx);
        }

        adapter.setData(result);

        // Empty state
        emptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);

        // Update summary
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

    // ── Sample data (same set as Dashboard) ──────────────────────────

    private List<Transaction> buildSampleTransactions() {
        List<Transaction> list = new ArrayList<>();
        long now  = System.currentTimeMillis();
        long hour = 3_600_000L;
        long day  = 86_400_000L;

        list.add(new Transaction(uuid(), "Phở Thìn",        "Ăn uống",    "🍜",
                "Tiền mặt",    75_000L,      Transaction.Type.EXPENSE,  now - 2 * hour));
        list.add(new Transaction(uuid(), "Cà phê Highlands", "Ăn uống",   "☕",
                "MoMo",         55_000L,      Transaction.Type.EXPENSE,  now - 4 * hour));
        list.add(new Transaction(uuid(), "GrabBike",          "Di chuyển", "🛵",
                "MoMo",         48_000L,      Transaction.Type.EXPENSE,  now - 6 * hour));
        list.add(new Transaction(uuid(), "Lương tháng 4",     "Thu nhập",  "💼",
                "Vietcombank",  18_000_000L,  Transaction.Type.INCOME,   now - 8 * hour));
        list.add(new Transaction(uuid(), "Siêu thị VinMart",  "Mua sắm",  "🛒",
                "Tiền mặt",    320_000L,     Transaction.Type.EXPENSE,  now - 1 * day - 2 * hour));
        list.add(new Transaction(uuid(), "Tiền điện nước",    "Hóa đơn",  "⚡",
                "Vietcombank",  450_000L,     Transaction.Type.EXPENSE,  now - 1 * day - 4 * hour));
        list.add(new Transaction(uuid(), "Chuyển tiền mẹ",    "Gia đình", "❤️",
                "Vietcombank",  1_000_000L,   Transaction.Type.EXPENSE,  now - 2 * day));
        list.add(new Transaction(uuid(), "Học phí Anh ngữ",   "Giáo dục", "🎓",
                "Vietcombank",  3_200_000L,   Transaction.Type.EXPENSE,  now - 8 * day));
        list.add(new Transaction(uuid(), "Thu nhập freelance", "Thu nhập", "💻",
                "MoMo",         2_500_000L,   Transaction.Type.INCOME,   now - 9 * day));
        list.add(new Transaction(uuid(), "Shopee – Quần áo",  "Mua sắm",  "👕",
                "MoMo",         890_000L,     Transaction.Type.EXPENSE,  now - 12 * day));
        list.add(new Transaction(uuid(), "Đổ xăng",           "Di chuyển","⛽",
                "Tiền mặt",    120_000L,     Transaction.Type.EXPENSE,  now - 13 * day));
        list.add(new Transaction(uuid(), "Xem phim CGV",      "Giải trí", "🎬",
                "MoMo",         180_000L,     Transaction.Type.EXPENSE,  now - 15 * day));
        list.add(new Transaction(uuid(), "Chuyển sang tiết kiệm","Chuyển khoản","🔄",
                "Vietcombank",  5_000_000L,   Transaction.Type.TRANSFER, now - 20 * day));
        list.add(new Transaction(uuid(), "Thu nhập thêm",      "Thu nhập","💰",
                "Vietcombank",  3_000_000L,   Transaction.Type.INCOME,   now - 22 * day));

        return list;
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
}
