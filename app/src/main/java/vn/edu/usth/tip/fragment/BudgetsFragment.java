package vn.edu.usth.tip.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.Toast;

import vn.edu.usth.tip.R;

public class BudgetsFragment extends Fragment {

    public BudgetsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_budgets, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View btnNewBudget = view.findViewById(R.id.btn_new_budget);

        if (btnNewBudget != null) {
            btnNewBudget.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "Tạo Ngân sách mới", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
