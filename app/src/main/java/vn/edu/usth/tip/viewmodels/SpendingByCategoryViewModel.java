package vn.edu.usth.tip.viewmodels;

import android.app.Application;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vn.edu.usth.tip.models.CategorySlice;
import vn.edu.usth.tip.models.Transaction;

public class SpendingByCategoryViewModel extends AndroidViewModel {

    // ── Bảng màu cố định theo tên danh mục (key = lowercase, không dấu chấp nhận có dấu) ──
    private static final Map<String, Integer> CATEGORY_COLORS = new HashMap<>();
    static {
        CATEGORY_COLORS.put("ăn uống",   Color.parseColor("#FF6B6B")); // đỏ san hô
        CATEGORY_COLORS.put("di chuyển", Color.parseColor("#735BF2")); // tím chủ đạo
        CATEGORY_COLORS.put("sức khỏe",  Color.parseColor("#10B981")); // xanh ngọc
        CATEGORY_COLORS.put("mua sắm",   Color.parseColor("#FBBF24")); // vàng
        CATEGORY_COLORS.put("giải trí",  Color.parseColor("#3B82F6")); // xanh dương
        CATEGORY_COLORS.put("hóa đơn",   Color.parseColor("#8B5CF6")); // tím nhạt
        CATEGORY_COLORS.put("khác",      Color.parseColor("#9CA3AF")); // xám
    }

    // Màu fallback tuần tự cho danh mục chưa có trong bảng cố định
    private static final int[] FALLBACK_COLORS = {
        Color.parseColor("#FF6B6B"), Color.parseColor("#735BF2"),
        Color.parseColor("#10B981"), Color.parseColor("#FBBF24"),
        Color.parseColor("#3B82F6"), Color.parseColor("#8B5CF6"),
    };

    // targetYearMonth = year*100 + month(1-12), ví dụ tháng 5/2026 = 202605
    private final MutableLiveData<Integer> targetYearMonth = new MutableLiveData<>();
    private final MediatorLiveData<SpendingByCategoryUiState> uiState = new MediatorLiveData<>();
    private LiveData<List<Transaction>> transactionsSource;

    public SpendingByCategoryViewModel(@NonNull Application application) {
        super(application);
        Calendar cal = Calendar.getInstance();
        targetYearMonth.setValue(cal.get(Calendar.YEAR) * 100 + (cal.get(Calendar.MONTH) + 1));
    }

    /**
     * Kết nối với nguồn dữ liệu giao dịch từ AppViewModel.
     * Phải gọi một lần duy nhất trong onViewCreated của Fragment.
     */
    public void init(LiveData<List<Transaction>> transactionsLiveData) {
        if (transactionsSource != null) return;
        transactionsSource = transactionsLiveData;

        uiState.setValue(new SpendingByCategoryUiState.Loading());

        // Tái tính khi transactions thay đổi hoặc khi đổi tháng
        uiState.addSource(transactionsLiveData, txs -> compute());
        uiState.addSource(targetYearMonth, ym -> compute());
    }

    public void setTargetMonth(int year, int month) {
        targetYearMonth.setValue(year * 100 + month);
    }

    public LiveData<SpendingByCategoryUiState> getUiState() {
        return uiState;
    }

    // ── Engine tính toán ──────────────────────────────────────────────────────

    private void compute() {
        List<Transaction> txs = transactionsSource == null ? null : transactionsSource.getValue();
        Integer ym = targetYearMonth.getValue();
        if (txs == null || ym == null) return;

        int year  = ym / 100;
        int month = ym % 100; // 1-12

        // Tính start/end của tháng đích
        Calendar start = Calendar.getInstance();
        start.set(year, month - 1, 1, 0, 0, 0);
        start.set(Calendar.MILLISECOND, 0);
        long startMs = start.getTimeInMillis();

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        long endMs = end.getTimeInMillis();

        // Lọc EXPENSE trong tháng, group theo categoryName
        Map<String, Long> totals = new HashMap<>();
        long grandTotal = 0;

        for (Transaction t : txs) {
            if (t.getType() != Transaction.Type.EXPENSE) continue;
            if (t.getTimestampMs() < startMs || t.getTimestampMs() >= endMs) continue;

            String cat = t.getCategory() != null ? t.getCategory().trim() : "";
            if (cat.isEmpty()) cat = "Khác";

            totals.put(cat, totals.getOrDefault(cat, 0L) + t.getAmountVnd());
            grandTotal += t.getAmountVnd();
        }

        if (grandTotal == 0) {
            uiState.postValue(new SpendingByCategoryUiState.Empty());
            return;
        }

        // Sort giảm dần theo amount
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(totals.entrySet());
        Collections.sort(sorted, (a, b) -> Long.compare(b.getValue(), a.getValue()));

        // Nếu >6 danh mục → gộp các danh mục nhỏ nhất (từ index 5 trở đi) vào "Khác"
        List<Map.Entry<String, Long>> display;
        if (sorted.size() > 6) {
            long othersTotal = 0;
            for (int i = 5; i < sorted.size(); i++) {
                othersTotal += sorted.get(i).getValue();
            }
            display = new ArrayList<>(sorted.subList(0, 5));

            // Tìm nếu "Khác" đã có trong top 5, cộng vào; không thì thêm mới
            boolean merged = false;
            for (int i = 0; i < display.size(); i++) {
                if (display.get(i).getKey().equalsIgnoreCase("Khác")) {
                    final long prev = display.get(i).getValue();
                    final long added = othersTotal;
                    display.set(i, new java.util.AbstractMap.SimpleEntry<>(
                            display.get(i).getKey(), prev + added));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                display.add(new java.util.AbstractMap.SimpleEntry<>("Khác", othersTotal));
            }
        } else {
            display = sorted;
        }

        // Xây dựng danh sách CategorySlice
        final long total = grandTotal;
        List<CategorySlice> slices = new ArrayList<>();
        int fallbackIdx = 0;
        for (Map.Entry<String, Long> entry : display) {
            float pct   = (entry.getValue() * 100f) / total;
            int   color = resolveColor(entry.getKey(), fallbackIdx++);
            slices.add(new CategorySlice(entry.getKey(), entry.getValue(), pct, color));
        }

        uiState.postValue(new SpendingByCategoryUiState.Success(slices, total));
    }

    // Tra bảng màu cố định, fallback theo thứ tự nếu không tìm thấy
    private int resolveColor(String name, int fallbackIndex) {
        Integer color = CATEGORY_COLORS.get(name.trim().toLowerCase());
        return color != null ? color : FALLBACK_COLORS[fallbackIndex % FALLBACK_COLORS.length];
    }
}
