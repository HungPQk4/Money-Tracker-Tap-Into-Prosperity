package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.adapters.SessionAdapter;
import vn.edu.usth.tip.network.responses.SessionResponse;
import vn.edu.usth.tip.viewmodels.SessionViewModel;

/** Màn hình "Thiết bị đăng nhập": liệt kê phiên, thu hồi từ xa, đăng xuất thiết bị khác. */
public class SessionManagementFragment extends Fragment {

    private SessionViewModel viewModel;
    private SessionAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SessionViewModel.class);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_sessions);
        tvEmpty = view.findViewById(R.id.tv_empty_sessions);

        RecyclerView rv = view.findViewById(R.id.rv_sessions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SessionAdapter(this::confirmRevoke);
        rv.setAdapter(adapter);

        view.findViewById(R.id.btn_back)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        view.findViewById(R.id.btn_logout_others)
                .setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Đăng xuất thiết bị khác")
                        .setMessage("Đăng xuất khỏi tất cả thiết bị khác (giữ lại thiết bị này)?")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Đăng xuất", (d, w) -> {
                            swipeRefresh.setRefreshing(true);
                            viewModel.logoutOthers();
                        })
                        .show());

        swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());

        viewModel.getSessions().observe(getViewLifecycleOwner(), list -> {
            swipeRefresh.setRefreshing(false);
            adapter.submit(list);
            boolean empty = list == null || list.isEmpty();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            swipeRefresh.setRefreshing(false);
            if (err != null) Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
        });

        swipeRefresh.setRefreshing(true);
        viewModel.refresh();
    }

    private void confirmRevoke(SessionResponse session) {
        if (session.isCurrent() || session.getId() == null) {
            Toast.makeText(requireContext(),
                    "Đây là thiết bị hiện tại — dùng nút Đăng xuất ở màn hình trước.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String name = session.getDeviceName() != null ? session.getDeviceName() : "thiết bị này";
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Thu hồi phiên")
                .setMessage("Đăng xuất khỏi \"" + name + "\"?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Thu hồi", (d, w) -> {
                    swipeRefresh.setRefreshing(true);
                    viewModel.revoke(session.getId().toString());
                })
                .show();
    }
}
