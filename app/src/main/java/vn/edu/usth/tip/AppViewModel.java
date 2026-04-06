package vn.edu.usth.tip;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * ViewModel chia sẻ giữa DashboardFragment và NewTransactionFragment.
 * Lưu danh sách giao dịch và phát sự kiện khi có giao dịch mới.
 */
public class AppViewModel extends ViewModel {

    private final MutableLiveData<List<Transaction>> transactionsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Category>>    categoriesLiveData = new MutableLiveData<>();

    public AppViewModel() {
        // Khởi tạo với dữ liệu mẫu
        transactionsLiveData.setValue(buildSampleTransactions());
        categoriesLiveData.setValue(buildInitialCategories());
    }

    public LiveData<List<Transaction>> getTransactions() {
        return transactionsLiveData;
    }

    public LiveData<List<Category>> getCategories() {
        return categoriesLiveData;
    }

    public void addCategory(Category category) {
        List<Category> current = categoriesLiveData.getValue();
        if (current == null) current = new ArrayList<>();
        List<Category> updated = new ArrayList<>(current);
        // Chèn vào trước nút "Thêm" (nút Thêm luôn cuối cùng)
        if (!updated.isEmpty() && updated.get(updated.size() - 1).isAddButton()) {
            updated.add(updated.size() - 1, category);
        } else {
            updated.add(category);
        }
        categoriesLiveData.setValue(updated);
    }

    /** Thêm giao dịch mới vào đầu danh sách */
    public void addTransaction(Transaction tx) {
        List<Transaction> current = transactionsLiveData.getValue();
        if (current == null) current = new ArrayList<>();
        List<Transaction> updated = new ArrayList<>(current);
        updated.add(0, tx);           // mới nhất lên đầu
        transactionsLiveData.setValue(updated);
    }

    /** Xóa giao dịch khỏi danh sách */
    public void deleteTransaction(Transaction tx) {
        List<Transaction> current = transactionsLiveData.getValue();
        if (current != null) {
            List<Transaction> updated = new ArrayList<>(current);
            updated.remove(tx);
            transactionsLiveData.setValue(updated);
        }
    }

    public List<Transaction> getCurrentList() {
        return transactionsLiveData.getValue() != null
                ? transactionsLiveData.getValue()
                : new ArrayList<>();
    }

    // ── Sample seed data ──────────────────────────────────────────────

    private static List<Transaction> buildSampleTransactions() {
        List<Transaction> list = new ArrayList<>();
        long now  = System.currentTimeMillis();
        long hour = 3_600_000L;
        long day  = 86_400_000L;

        list.add(new Transaction(uuid(), "Phở Thìn",        "Ăn uống",    "🍜",
                "Tiền mặt",    75_000L,      Transaction.Type.EXPENSE,  now - 2 * hour));
        list.add(new Transaction(uuid(), "Cà phê Highlands", "Ăn uống",   "☕",
                "MoMo",         55_000L,      Transaction.Type.EXPENSE,  now - 4 * hour));
        list.add(new Transaction(uuid(), "GrabBike",          "Di chuyển", "🛵",
                "MoMo",         48_000L,      Transaction.Type.EXPENSE,  now - 6 * hour));
        list.add(new Transaction(uuid(), "Lương tháng 4",     "Thu nhập",  "💼",
                "Vietcombank",  18_000_000L,  Transaction.Type.INCOME,   now - 8 * hour));
        list.add(new Transaction(uuid(), "Siêu thị VinMart",  "Mua sắm",  "🛒",
                "Tiền mặt",    320_000L,     Transaction.Type.EXPENSE,  now - 1 * day - 2 * hour));
        list.add(new Transaction(uuid(), "Tiền điện nước",    "Hóa đơn",  "⚡",
                "Vietcombank",  450_000L,     Transaction.Type.EXPENSE,  now - 1 * day - 4 * hour));
        list.add(new Transaction(uuid(), "Chuyển tiền mẹ",    "Gia đình", "❤️",
                "Vietcombank",  1_000_000L,   Transaction.Type.EXPENSE,  now - 2 * day));
        list.add(new Transaction(uuid(), "Học phí Anh ngữ",   "Giáo dục", "🎓",
                "Vietcombank",  3_200_000L,   Transaction.Type.EXPENSE,  now - 8 * day));
        list.add(new Transaction(uuid(), "Thu nhập freelance", "Thu nhập", "💻",
                "MoMo",         2_500_000L,   Transaction.Type.INCOME,   now - 9 * day));
        return list;
    }

    private static List<Category> buildInitialCategories() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("cat_food",      "Ăn uống",    "🍜"));
        list.add(new Category("cat_transport", "Di chuyển",  "🛵"));
        list.add(new Category("cat_shopping",  "Mua sắm",    "🛒"));
        list.add(new Category("cat_fun",       "Giải trí",   "🎬"));
        list.add(new Category("cat_health",    "Sức khỏe",   "💊"));
        list.add(new Category("cat_study",     "Giáo dục",   "📚"));
        list.add(new Category("cat_housing",   "Nhà ở",      "🏠"));
        list.add(new Category("cat_bill",      "Hóa đơn",    "⚡"));
        list.add(new Category("cat_beauty",    "Làm đẹp",    "💄"));
        list.add(new Category("cat_gift",      "Quà tặng",   "🎁"));
        list.add(new Category("cat_travel",    "Du lịch",    "✈️"));
        // Nút thêm đặc biệt
        list.add(new Category("cat_add",       "Thêm",       "+", true));
        return list;
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
}
