package vn.edu.usth.tip.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Model giao dịch tài chính.
 */
@Entity(tableName = "transactions")
public class Transaction {

    public enum Type { EXPENSE, INCOME, TRANSFER }

    @PrimaryKey
    @NonNull
    private String id;
    private String title;
    private String category;
    private String icon;        // emoji
    private String walletName;
    private long   amountVnd;   // luôn dương; type quyết định dấu hiển thị
    private Type   type;
    private long   timestampMs; // epoch milliseconds
    private String note;        // Ghi chú giao dịch
    private String photoUri;    // Ảnh đính kèm
    private boolean isSynced = false; // Mặc định là false cho tạo mới cục bộ
    private long updatedAtMs;   // epoch ms — set khi tạo/sửa local; ghi đè khi pull từ server
    private boolean isDeleted = false; // soft delete — push lên server trước khi hard delete
    private boolean isRecurring = false; // giao dịch định kỳ — server sinh clone hàng ngày/tuần/tháng/năm
    private String recurInterval; // "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" | null
    @ColumnInfo(name = "user_id")
    private String userId;

    @Ignore // Không lưu vào Room, chỉ dùng khi gửi lên API
    private String accountId;

    @Ignore // Không lưu vào Room, chỉ dùng khi gửi lên API
    private String categoryId;

    public Transaction(@NonNull String id, String title, String category,
                       String icon, String walletName,
                       long amountVnd, Type type, long timestampMs, String note) {
        this.id          = id;
        this.title       = title;
        this.category    = category;
        this.icon        = icon;
        this.walletName  = walletName;
        this.amountVnd   = amountVnd;
        this.type        = type;
        this.timestampMs = timestampMs;
        this.note        = note;
        this.isSynced      = false;
        this.updatedAtMs   = System.currentTimeMillis();
        this.isDeleted     = false;
        this.isRecurring   = false;
        this.recurInterval = null;
    }

    /** Trả về chuỗi số tiền có dấu, vd: "+75.000 ₫" hoặc "-18.000.000 ₫" */
    public String getFormattedAmount() {
        String sign = (type == Type.INCOME) ? "+" : "-";
        String abs  = String.format(java.util.Locale.US, "%,d", amountVnd).replace(",", ".");
        return sign + abs + " VND";
    }

    /** Trả về giờ:phút từ timestamp */
    public String getFormattedTime() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        return String.format("%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE));
    }

    /** Trả về ngày + giờ dạng "Hôm nay 09:15", "Hôm qua 09:15", hoặc "15/05 09:15" */
    public String getFormattedDateTime() {
        java.util.Calendar tx  = java.util.Calendar.getInstance();
        tx.setTimeInMillis(timestampMs);

        java.util.Calendar today = java.util.Calendar.getInstance();

        java.util.Calendar yesterday = java.util.Calendar.getInstance();
        yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1);

        String time = String.format("%02d:%02d",
                tx.get(java.util.Calendar.HOUR_OF_DAY),
                tx.get(java.util.Calendar.MINUTE));

        if (isSameDay(tx, today)) {
            return "Hôm nay " + time;
        } else if (isSameDay(tx, yesterday)) {
            return "Hôm qua " + time;
        } else {
            String date = String.format("%02d/%02d",
                    tx.get(java.util.Calendar.DAY_OF_MONTH),
                    tx.get(java.util.Calendar.MONTH) + 1);
            return date + " " + time;
        }
    }

    private static boolean isSameDay(java.util.Calendar a, java.util.Calendar b) {
        return a.get(java.util.Calendar.YEAR)       == b.get(java.util.Calendar.YEAR)
            && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }

    // ── Getters ───────────────────────────────────────────────────────
    @NonNull
    public String getId()           { return id; }
    public String getTitle()        { return title; }
    public String getCategory()     { return category; }
    public String getIcon()         { return icon; }
    public String getWalletName()   { return walletName; }
    public long   getAmountVnd()    { return amountVnd; }
    public Type   getType()         { return type; }
    public long   getTimestampMs()  { return timestampMs; }
    public String getNote()         { return note; }
    public String getPhotoUri()     { return photoUri; }
    public boolean isSynced()       { return isSynced; }
    public long getUpdatedAtMs()    { return updatedAtMs; }
    public boolean isDeleted()       { return isDeleted; }
    public boolean isRecurring()     { return isRecurring; }
    public String getRecurInterval() { return recurInterval; }

    // ── Setters ───────────────────────────────────────────────────────
    public void setId(@NonNull String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setCategory(String category) { this.category = category; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setWalletName(String walletName) { this.walletName = walletName; }
    public void setAmountVnd(long amountVnd) { this.amountVnd = amountVnd; }
    public void setType(Type type) { this.type = type; }
    public void setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; }
    public void setNote(String note) { this.note = note; }
    public void setPhotoUri(String photoUri) { this.photoUri = photoUri; }
    public void setSynced(boolean synced) { isSynced = synced; }
    public void setUpdatedAtMs(long updatedAtMs) { this.updatedAtMs = updatedAtMs; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }
    public void setRecurInterval(String recurInterval) { this.recurInterval = recurInterval; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getAccountId() { return accountId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategoryId() { return categoryId; }
}