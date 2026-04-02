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
                Toast.makeText(requireContext(), "Bộ lọc danh mục", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnFilterTime != null) {
            btnFilterTime.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "Bộ lọc thời gian", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
