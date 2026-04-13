package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.Toast;

import vn.edu.usth.tip.R;

public class AnalyticsFragment extends Fragment {

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

        View btnFilterCategory = view.findViewById(R.id.btn_filter_category);
        View btnFilterTime = view.findViewById(R.id.btn_filter_time);

        if (btnFilterCategory != null) {
            btnFilterCategory.setOnClickListener(v -> {
                String[] categories = {"Tất cả", "Ăn uống", "Di chuyển", "Mua sắm", "Giải trí"};
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Chọn danh mục")
                        .setItems(categories, (dialog, which) -> {
                            Toast.makeText(requireContext(), "Lọc: " + categories[which], Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });
        }

        if (btnFilterTime != null) {
            btnFilterTime.setOnClickListener(v -> {
                String[] times = {"Tuần này", "Tháng này", "Tháng trước", "Năm nay"};
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Chọn thời gian")
                        .setItems(times, (dialog, which) -> {
                            Toast.makeText(requireContext(), "Lọc: " + times[which], Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });
        }
    }
}
