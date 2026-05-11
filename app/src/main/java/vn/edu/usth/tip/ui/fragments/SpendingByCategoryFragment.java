package vn.edu.usth.tip.ui.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.util.ArrayList;
import java.util.List;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.CategorySlice;
import vn.edu.usth.tip.viewmodels.AppViewModel;
import vn.edu.usth.tip.viewmodels.SpendingByCategoryUiState;
import vn.edu.usth.tip.viewmodels.SpendingByCategoryViewModel;

public class SpendingByCategoryFragment extends Fragment {

    private SpendingByCategoryViewModel viewModel;

    private PieChart     pieChart;
    private LinearLayout layoutLegend;
    private View         layoutContent, layoutLoading, layoutEmpty, layoutError;
    private TextView     tvErrorMsg;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spending_by_category, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pieChart      = view.findViewById(R.id.pie_chart);
        layoutLegend  = view.findViewById(R.id.layout_legend);
        layoutContent = view.findViewById(R.id.layout_content);
        layoutLoading = view.findViewById(R.id.layout_loading);
        layoutEmpty   = view.findViewById(R.id.layout_empty);
        layoutError   = view.findViewById(R.id.layout_error);
        tvErrorMsg    = view.findViewById(R.id.tv_error_msg);

        view.findViewById(R.id.btn_retry).setOnClickListener(v ->
                viewModel.init(
                        new ViewModelProvider(requireActivity())
                                .get(AppViewModel.class)
                                .getTransactions()
                ));

        setupPieChart();

        // ViewModel scoped to this fragment; AppViewModel scoped to Activity (shared)
        viewModel = new ViewModelProvider(this).get(SpendingByCategoryViewModel.class);
        AppViewModel appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        viewModel.init(appViewModel.getTransactions());

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
    }

    // ── Cấu hình PieChart ────────────────────────────────────────────────────

    private void setupPieChart() {
        pieChart.setUsePercentValues(false);
        pieChart.getDescription().setEnabled(false);

        // Donut: lỗ 65% — vòng mỏng vừa phải
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(65f);
        pieChart.setTransparentCircleRadius(67f);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleColor(Color.WHITE);
        pieChart.setTransparentCircleAlpha(100);

        // Không hiện label trên slice, không legend mặc định (tự vẽ ở cột phải)
        pieChart.setDrawEntryLabels(false);
        pieChart.getLegend().setEnabled(false);

        // Tâm hiển thị "Tổng + số tiền" — set qua setCenterText() khi có data
        pieChart.setDrawCenterText(true);

        // Tắt tương tác không cần thiết
        pieChart.setRotationEnabled(false);
        pieChart.setHighlightPerTapEnabled(false);
        pieChart.setTouchEnabled(false);
    }

    // ── Render theo trạng thái ────────────────────────────────────────────────

    private void render(SpendingByCategoryUiState state) {
        if (state instanceof SpendingByCategoryUiState.Loading) {
            show(layoutLoading);
        } else if (state instanceof SpendingByCategoryUiState.Empty) {
            show(layoutEmpty);
        } else if (state instanceof SpendingByCategoryUiState.Success) {
            bindSuccess((SpendingByCategoryUiState.Success) state);
            show(layoutContent);
        } else if (state instanceof SpendingByCategoryUiState.Error) {
            tvErrorMsg.setText(((SpendingByCategoryUiState.Error) state).message);
            show(layoutError);
        }
    }

    private void show(View target) {
        for (View v : new View[]{layoutContent, layoutLoading, layoutEmpty, layoutError}) {
            v.setVisibility(v == target ? View.VISIBLE : View.GONE);
        }
    }

    // ── Bind dữ liệu vào chart và legend ────────────────────────────────────

    private void bindSuccess(SpendingByCategoryUiState.Success state) {
        bindPieChart(state.slices, state.total);
        bindLegend(state.slices);
    }

    private void bindPieChart(List<CategorySlice> slices, long total) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer>  colors  = new ArrayList<>();

        for (CategorySlice s : slices) {
            entries.add(new PieEntry(s.amount));
            colors.add(s.color);
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);     // khe hở giữa các slice
        dataSet.setSelectionShift(0f); // không phình khi tap
        dataSet.setDrawValues(false);  // không hiện số trực tiếp trên slice

        pieChart.setData(new PieData(dataSet));

        // Tâm donut: "Tổng" (nhỏ, xám) + số tiền rút gọn (to, đậm, đen)
        String amtStr = formatCompact(total);
        SpannableString center = new SpannableString("Tổng\n" + amtStr);
        center.setSpan(new RelativeSizeSpan(0.73f),                      0, 4, 0);
        center.setSpan(new ForegroundColorSpan(Color.parseColor("#9CA3AF")), 0, 4, 0);
        center.setSpan(new StyleSpan(Typeface.BOLD),                     5, center.length(), 0);

        pieChart.setCenterText(center);
        pieChart.setCenterTextSize(15f);       // base = 15sp (amount); "Tổng" = 15*0.73 ≈ 11sp
        pieChart.setCenterTextColor(Color.parseColor("#1A1A1A"));

        // Animation xoay ease-out 1000ms khi load
        pieChart.animateY(1000, Easing.EaseOutCubic);
        pieChart.invalidate();
    }

    private void bindLegend(List<CategorySlice> slices) {
        layoutLegend.removeAllViews();
        if (getContext() == null) return;

        float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < slices.size(); i++) {
            CategorySlice s = slices.get(i);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowLp.topMargin = (int) (12 * dp);
            row.setLayoutParams(rowLp);

            // Chấm tròn màu danh mục (10dp)
            CardView dot = new CardView(requireContext());
            int dotPx = (int) (10 * dp);
            CardView.LayoutParams dotLp = new CardView.LayoutParams(dotPx, dotPx);
            dotLp.rightMargin = (int) (8 * dp);
            dot.setLayoutParams(dotLp);
            dot.setRadius(dotPx / 2f);
            dot.setCardElevation(0f);
            dot.setCardBackgroundColor(s.color);
            row.addView(dot);

            // Tên danh mục — fill phần còn lại
            TextView tvName = new TextView(requireContext());
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(nameLp);
            tvName.setText(s.categoryName);
            tvName.setTextSize(14f);
            tvName.setTextColor(Color.parseColor("#374151"));
            row.addView(tvName);

            // Phần trăm (làm tròn về số nguyên)
            TextView tvPct = new TextView(requireContext());
            tvPct.setText(Math.round(s.percentage) + "%");
            tvPct.setTextSize(14f);
            tvPct.setTextColor(Color.parseColor("#1A1A1A"));
            tvPct.setTypeface(null, Typeface.BOLD);
            row.addView(tvPct);

            layoutLegend.addView(row);
        }
    }

    // ── Format tiền rút gọn ─────────────────────────────────────────────────

    /**
     * <1tr → đ950K   |   <1tỷ → đ5.5M   |   ≥1tỷ → đ1.2B
     */
    private static String formatCompact(long amount) {
        if (amount >= 1_000_000_000L) {
            double v = amount / 1_000_000_000.0;
            return v == (long) v
                    ? String.format("đ%dB", (long) v)
                    : String.format("đ%.1fB", v);
        } else if (amount >= 1_000_000L) {
            double v = amount / 1_000_000.0;
            return v == (long) v
                    ? String.format("đ%dM", (long) v)
                    : String.format("đ%.1fM", v);
        } else if (amount >= 1_000L) {
            double v = amount / 1_000.0;
            return v == (long) v
                    ? String.format("đ%dK", (long) v)
                    : String.format("đ%.1fK", v);
        } else {
            return "đ" + amount;
        }
    }
}
