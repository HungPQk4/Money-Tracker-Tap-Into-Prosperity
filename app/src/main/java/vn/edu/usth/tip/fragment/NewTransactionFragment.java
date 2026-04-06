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
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import vn.edu.usth.tip.AppViewModel;
import vn.edu.usth.tip.Category;
import vn.edu.usth.tip.CategoryAdapter;
import vn.edu.usth.tip.R;
import vn.edu.usth.tip.Transaction;

public class NewTransactionFragment extends Fragment {

    private AppViewModel viewModel;
    private CategoryAdapter categoryAdapter;

    // State
    private String currentAmount = "0";
    private Transaction.Type selectedType = Transaction.Type.EXPENSE;

    // Views
    private TextView tvAmount;
    private CardView btnTypeExpense;
    private TextView btnTypeIncome, btnTypeTransfer;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_new_transaction, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Header ────────────────────────────────────────────────────
        view.findViewById(R.id.btnBack).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp()
        );

        // ── Main Displays ─────────────────────────────────────────────
        tvAmount = view.findViewById(R.id.tv_amount);

        // ── Type Selectors ───────────────────────────────────────────
        btnTypeExpense = view.findViewById(R.id.btn_type_expense);
        btnTypeIncome  = view.findViewById(R.id.btn_type_income);
        btnTypeTransfer = view.findViewById(R.id.btn_type_transfer);

        btnTypeExpense.setOnClickListener(v -> selectType(Transaction.Type.EXPENSE));
        btnTypeIncome.setOnClickListener(v  -> selectType(Transaction.Type.INCOME));
        btnTypeTransfer.setOnClickListener(v -> selectType(Transaction.Type.TRANSFER));

        // ── Dynamic Category List ─────────────────────────────────────
        RecyclerView rvCategories = view.findViewById(R.id.rv_categories);
        rvCategories.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        
        // Setup initial adapter
        updateCategoryList(new ArrayList<>(), rvCategories);

        // Observe Categories from ViewModel
        viewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            updateCategoryList(categories, rvCategories);
        });

        // ── Numeric Keypad ────────────────────────────────────────────
        setupNumpad(view);

        // ── Save Button ───────────────────────────────────────────────
        view.findViewById(R.id.btn_save_transaction).setOnClickListener(v -> saveTransaction());
    }

    private void updateCategoryList(List<Category> categories, RecyclerView rv) {
        CategoryAdapter adapter = new CategoryAdapter(categories, new CategoryAdapter.OnCategoryClickListener() {
            @Override
            public void onCategoryClick(Category category) {
                // Selection handled by adapter visual state
            }

            @Override
            public void onAddCategoryClick() {
                AddCategorySheet sheet = AddCategorySheet.newInstance(cat -> {
                    viewModel.addCategory(cat);
                    Toast.makeText(requireContext(), "Đã thêm: " + cat.getName(), Toast.LENGTH_SHORT).show();
                });
                sheet.show(getChildFragmentManager(), "add_category");
            }
        });
        rv.setAdapter(adapter);
        categoryAdapter = adapter;
    }

    private void selectType(Transaction.Type type) {
        selectedType = type;
        
        // Reset colors
        btnTypeExpense.setCardBackgroundColor(Color.TRANSPARENT);
        ((TextView)btnTypeExpense.getChildAt(0)).setTextColor(Color.parseColor("#8C8D99"));
        
        btnTypeIncome.setTextColor(Color.parseColor("#8C8D99"));
        btnTypeTransfer.setTextColor(Color.parseColor("#8C8D99"));

        // Highlight selected
        if (type == Transaction.Type.EXPENSE) {
            btnTypeExpense.setCardBackgroundColor(Color.parseColor("#E76E60"));
            ((TextView)btnTypeExpense.getChildAt(0)).setTextColor(Color.WHITE);
        } else if (type == Transaction.Type.INCOME) {
            btnTypeIncome.setTextColor(Color.parseColor("#4ADE80"));
        } else {
            btnTypeTransfer.setTextColor(Color.parseColor("#735BF2"));
        }
    }

    private void setupNumpad(View view) {
        int[] keys = {R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4, 
                      R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9};
        for (int id : keys) {
            TextView key = view.findViewById(id);
            if (key != null) {
                key.setOnClickListener(v -> {
                    String val = key.getText().toString();
                    if (currentAmount.equals("0")) currentAmount = val;
                    else if (currentAmount.length() < 12) currentAmount += val;
                    updateAmountDisplay();
                });
            }
        }

        view.findViewById(R.id.key_clear).setOnClickListener(v -> {
            currentAmount = "0";
            updateAmountDisplay();
        });

        view.findViewById(R.id.key_del).setOnClickListener(v -> {
            if (currentAmount.length() > 1) {
                currentAmount = currentAmount.substring(0, currentAmount.length() - 1);
            } else {
                currentAmount = "0";
            }
            updateAmountDisplay();
        });
    }

    private void updateAmountDisplay() {
        try {
            long val = Long.parseLong(currentAmount);
            tvAmount.setText(String.format("%,d", val).replace(",", "."));
        } catch (Exception e) {
            tvAmount.setText(currentAmount);
        }
    }

    private void saveTransaction() {
        long amount = Long.parseLong(currentAmount);
        if (amount <= 0) {
            Toast.makeText(requireContext(), "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
            return;
        }

        Category selected = categoryAdapter.getSelectedCategory();
        if (selected == null) {
            Toast.makeText(requireContext(), "Vui lòng chọn danh mục", Toast.LENGTH_SHORT).show();
            return;
        }

        Transaction tx = new Transaction(
                UUID.randomUUID().toString(),
                selected.getName(),
                selected.getName(),
                selected.getIcon(),
                "Ví chính",
                amount,
                selectedType,
                System.currentTimeMillis()
        );

        viewModel.addTransaction(tx);
        Toast.makeText(requireContext(), "Đã lưu giao dịch", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView()).navigateUp();
    }
}