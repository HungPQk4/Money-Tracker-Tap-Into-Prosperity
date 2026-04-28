package vn.edu.usth.tip.models;

import androidx.annotation.NonNull;
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
    private boolean isSynced = false; // Mặc định là false cho tạo mới cục bộ

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
        this.isSynced    = false;
    }

    /** Trả về chuỗi số tiền có dấu, vd: "-₫75.000" hoặc "+₫18.000.000" */
    public String getFormattedAmount() {
        String sign = (type == Type.INCOME) ? "+" : "-";
        String abs  = String.format("₫%,.0f", (double) amountVnd).replace(",", ".");
        return sign + abs;
    }

    /** Trả về giờ:phút từ timestamp */
    public String getFormattedTime() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        return String.format("%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE));
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
    public boolean isSynced()       { return isSynced; }

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
    public void setSynced(boolean synced) { isSynced = synced; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getAccountId() { return accountId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategoryId() { return categoryId; }
}