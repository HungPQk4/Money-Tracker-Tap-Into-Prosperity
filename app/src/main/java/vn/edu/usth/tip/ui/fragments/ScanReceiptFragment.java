package vn.edu.usth.tip.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import vn.edu.usth.tip.R;

public class ScanReceiptFragment extends Fragment {

    private View layoutLoading;
    private View scanLaser;

    public ScanReceiptFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan_receipt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutLoading = view.findViewById(R.id.layout_loading);
        scanLaser = view.findViewById(R.id.scan_laser);

        // Start Laser Animation (Mockup up and down)
        if (scanLaser != null) {
            TranslateAnimation animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_PARENT, 0f,
                    Animation.RELATIVE_TO_PARENT, 0f,
                    Animation.RELATIVE_TO_PARENT, -0.2f,
                    Animation.RELATIVE_TO_PARENT, 0.7f
            );
            animation.setDuration(1500);
            animation.setRepeatCount(Animation.INFINITE);
            animation.setRepeatMode(Animation.REVERSE);
            scanLaser.startAnimation(animation);
        }

        // Back button
        View btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        }

        // Capture logic
        View btnCapture = view.findViewById(R.id.btn_capture);
        if (btnCapture != null) {
            btnCapture.setOnClickListener(v -> {
                // Show loading
                if (layoutLoading != null) {
                    layoutLoading.setVisibility(View.VISIBLE);
                }
                
                // Simulate AI Processing Delay (1.5 seconds)
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded() && getView() != null) {
                        Navigation.findNavController(getView())
                                .navigate(R.id.action_scanReceipt_to_extractInvoice);
                    }
                }, 1500);
            });
        }
    }
}
