package vn.edu.usth.tip.insights;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.insights.models.ForecastPoint;
import vn.edu.usth.tip.insights.models.Insight;

public class InsightsFragment extends Fragment {

    private InsightViewModel viewModel;
    private InsightAdapter adapter;

    private ProgressBar progressBar;
    private RecyclerView rvInsights;
    private TextView tvEmpty;
    private View cardForecastChart;
    private LineChart lineChart;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_insights, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Toolbar toolbar = view.findViewById(R.id.toolbar_insights);
        toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        progressBar = view.findViewById(R.id.progress_insights);
        rvInsights = view.findViewById(R.id.rv_insights);
        tvEmpty = view.findViewById(R.id.tv_insights_empty);
        cardForecastChart = view.findViewById(R.id.card_forecast_chart);
        lineChart = view.findViewById(R.id.chart_forecast);

        setupRecyclerView();
        setupChart();

        viewModel = new ViewModelProvider(this).get(InsightViewModel.class);
        observeViewModel();
        viewModel.generateInsights();
    }

    private void setupRecyclerView() {
        adapter = new InsightAdapter();
        rvInsights.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvInsights.setAdapter(adapter);
        rvInsights.setNestedScrollingEnabled(false);
    }

    private void setupChart() {
        lineChart.setDescription(null);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(false);
        lineChart.setDrawGridBackground(false);

        lineChart.getAxisRight().setEnabled(false);

        lineChart.getAxisLeft().setTextColor(Color.parseColor("#888888"));
        lineChart.getAxisLeft().setGridColor(Color.parseColor("#F0F0F0"));
        lineChart.getAxisLeft().setTextSize(10f);
        // Hiển thị đơn vị "K₫" (nghìn đồng) thay vì số thô
        lineChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value >= 1_000f) return String.format("%.0ftr", value / 1_000f);
                return String.format("%.0fK", value);
            }
        });

        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.getXAxis().setTextColor(Color.parseColor("#888888"));
        lineChart.getXAxis().setGridColor(Color.parseColor("#F0F0F0"));
        lineChart.getXAxis().setLabelCount(6, true);
        lineChart.getXAxis().setTextSize(10f);
        // Trục X hiển thị "Ngày N"
        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "Ng " + (int) value;
            }
        });

        lineChart.setExtraBottomOffset(8f);
        lineChart.setExtraLeftOffset(4f);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            progressBar.setVisibility(loading != null && loading ? View.VISIBLE : View.GONE);
        });

        viewModel.getInsights().observe(getViewLifecycleOwner(), insights -> {
            if (insights == null || insights.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                rvInsights.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                rvInsights.setVisibility(View.VISIBLE);
                adapter.setData(insights);
            }
        });

        viewModel.getForecastPoints().observe(getViewLifecycleOwner(), points -> {
            if (points != null && !points.isEmpty()) {
                cardForecastChart.setVisibility(View.VISIBLE);
                updateChart(points);
            }
        });
    }

    private void updateChart(List<ForecastPoint> points) {
        List<Entry> actualEntries = new ArrayList<>();
        List<Entry> projectedEntries = new ArrayList<>();

        for (ForecastPoint p : points) {
            float x = p.day;
            float y = p.amountVnd / 1_000f;
            if (p.isProjected) {
                projectedEntries.add(new Entry(x, y));
            } else {
                actualEntries.add(new Entry(x, y));
            }
        }

        // Connect the two lines: prepend the last actual point to projected so they join visually
        if (!actualEntries.isEmpty() && !projectedEntries.isEmpty()) {
            Entry last = actualEntries.get(actualEntries.size() - 1);
            projectedEntries.add(0, new Entry(last.getX(), last.getY()));
        }

        LineDataSet actualSet = new LineDataSet(actualEntries, "Thực tế");
        actualSet.setColor(Color.parseColor("#4A90D9"));
        actualSet.setLineWidth(2f);
        actualSet.setDrawCircles(false);
        actualSet.setDrawValues(false);
        actualSet.setMode(LineDataSet.Mode.LINEAR);

        LineDataSet projectedSet = new LineDataSet(projectedEntries, "Dự báo");
        projectedSet.setColor(Color.parseColor("#E53935"));
        projectedSet.setLineWidth(2f);
        projectedSet.setDrawCircles(false);
        projectedSet.setDrawValues(false);
        projectedSet.enableDashedLine(10f, 6f, 0f);
        projectedSet.setMode(LineDataSet.Mode.LINEAR);

        lineChart.setData(new LineData(actualSet, projectedSet));
        lineChart.invalidate();
    }
}
