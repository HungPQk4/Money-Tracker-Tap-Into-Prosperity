package vn.edu.usth.tip.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import vn.edu.usth.tip.R;

/**
 * Simple bottom sheet with an emoji grid for picking wallet icons.
 */
public class IconPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnIconSelectedListener {
        void onIconSelected(String icon);
    }

    private String[] icons;
    private OnIconSelectedListener listener;

    public static IconPickerBottomSheet newInstance(String[] icons,
                                                    OnIconSelectedListener listener) {
        IconPickerBottomSheet sheet = new IconPickerBottomSheet();
        sheet.icons   = icons;
        sheet.listener = listener;
        return sheet;
    }

    @Override
    public int getTheme() {
        return com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_icon_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GridLayout grid = view.findViewById(R.id.grid_icons);
        if (grid == null || icons == null) return;

        int cellSizePx = (int) (56 * requireContext().getResources().getDisplayMetrics().density);

        for (String emoji : icons) {
            TextView tv = new TextView(requireContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width  = cellSizePx;
            params.height = cellSizePx;
            params.setMargins(8, 8, 8, 8);
            tv.setLayoutParams(params);
            tv.setText(emoji);
            tv.setTextSize(26);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setBackground(buildRoundedBg(Color.parseColor("#252545"), cellSizePx / 4));

            tv.setOnClickListener(v -> {
                if (listener != null) listener.onIconSelected(emoji);
                dismiss();
            });

            grid.addView(tv);
        }
    }

    private android.graphics.drawable.GradientDrawable buildRoundedBg(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(color);
        return d;
    }
}
