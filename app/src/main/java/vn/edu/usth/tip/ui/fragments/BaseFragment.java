package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.usth.tip.viewmodels.AppViewModel;

/**
 * Lớp Fragment cơ sở (BaseFragment) áp dụng tính kế thừa trong OOP.
 * Các fragment con sẽ kế thừa lớp này để tái sử dụng mã (ví dụ: khởi tạo ViewModel chung).
 */
public abstract class BaseFragment extends Fragment {

    protected AppViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Khởi tạo ViewModel chung cho tất cả Fragment kế thừa
        viewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
    }
}
