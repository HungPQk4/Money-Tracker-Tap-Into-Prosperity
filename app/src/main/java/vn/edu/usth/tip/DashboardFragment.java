package vn.edu.usth.tip;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class DashboardFragment extends Fragment {

    public DashboardFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Header buttons
        View btnNotification = view.findViewById(R.id.btn_notification);
        View btnProfile = view.findViewById(R.id.btn_profile);

        if (btnNotification != null) {
            btnNotification.setOnClickListener(v ->
                    Toast.makeText(requireContext(), "Mở Thông báo", Toast.LENGTH_SHORT).show()
            );
        }

        if (btnProfile != null) {
            btnProfile.setOnClickListener(v ->
                    Toast.makeText(requireContext(), "Mở Hồ sơ Minh Tuấn", Toast.LENGTH_SHORT).show()
            );
        }

        // Nút "Thêm chi tiêu" → mở NewTransactionFragment
        View btnAddExpense = view.findViewById(R.id.btn_add_expense);
        if (btnAddExpense != null) {
            btnAddExpense.setOnClickListener(v ->
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.nav_host_fragment, new NewTransactionFragment())
                            .addToBackStack(null)
                            .commit()
            );
        }
    }
}