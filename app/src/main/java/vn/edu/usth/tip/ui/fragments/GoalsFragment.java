package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.adapters.GoalAdapter;
import vn.edu.usth.tip.viewmodels.AppViewModel;

public class GoalsFragment extends Fragment {

    private AppViewModel viewModel;
    private GoalAdapter adapter;

    public GoalsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_goals, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        // Header Add Goal Button
        View btnAddGoal = view.findViewById(R.id.btn_add_goal);
        if (btnAddGoal != null) {
            btnAddGoal.setOnClickListener(v -> {
                AddGoalSheet sheet = new AddGoalSheet();
                sheet.show(getChildFragmentManager(), "add_goal");
            });
        }

        // Setup RecyclerView
        RecyclerView rvGoals = view.findViewById(R.id.rv_goals);
        if (rvGoals != null) {
            rvGoals.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new GoalAdapter();
            rvGoals.setAdapter(adapter);

            adapter.setOnGoalClickListener(goal -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Cập nhật mục tiêu?")
                        .setMessage("Đánh dấu mục tiêu '" + goal.getName() + "' là đã hoàn thành và xoá khỏi hệ thống?")
                        .setPositiveButton("Hoàn thành", (dialog, which) -> {
                            viewModel.deleteGoal(goal);
                            Toast.makeText(requireContext(), "Chúc mừng bạn đã hoàn tất mục tiêu!", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        }

        // Observe Data
        viewModel.getGoals().observe(getViewLifecycleOwner(), goals -> {
            if (adapter != null) {
                adapter.setData(goals != null ? goals : new java.util.ArrayList<>());
            }
        });
    }
}
