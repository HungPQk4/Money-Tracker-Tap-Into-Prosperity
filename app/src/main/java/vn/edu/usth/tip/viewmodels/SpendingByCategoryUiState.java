package vn.edu.usth.tip.viewmodels;

import java.util.List;
import vn.edu.usth.tip.models.CategorySlice;

/** Trạng thái UI cho biểu đồ donut "Chi tiêu theo danh mục". */
public abstract class SpendingByCategoryUiState {

    /** Đang tải lần đầu — hiện skeleton. */
    public static final class Loading extends SpendingByCategoryUiState {}

    /** Không có chi tiêu trong tháng — hiện empty state. */
    public static final class Empty extends SpendingByCategoryUiState {}

    /** Có dữ liệu — hiện donut + legend. */
    public static final class Success extends SpendingByCategoryUiState {
        public final List<CategorySlice> slices;
        public final long total;

        public Success(List<CategorySlice> slices, long total) {
            this.slices = slices;
            this.total  = total;
        }
    }

    /** Lỗi — hiện nút "Thử lại". */
    public static final class Error extends SpendingByCategoryUiState {
        public final String message;

        public Error(String message) {
            this.message = message;
        }
    }
}
