package vn.edu.usth.tip;

/**
 * Model giao dịch tài chính.
 */
public class Transaction {

    public enum Type { EXPENSE, INCOME, TRANSFER }

    private String id;
    private String title;
    private String category;
    private String icon;        // emoji
    private String walletName;
    private long   amountVnd;   // luôn dương; type quyết định dấu hiển thị
    private Type   type;
    private long   timestampMs; // epoch milliseconds

    public Transaction(String id, String title, String category,
                       String icon, String walletName,
                       long amountVnd, Type type, long timestampMs) {
        this.id          = id;
        this.title       = title;
        this.category    = category;
        this.icon        = icon;
        this.walletName  = walletName;
        this.amountVnd   = amountVnd;
        this.type        = type;
        this.timestampMs = timestampMs;
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
    public String getId()           { return id; }
    public String getTitle()        { return title; }
    public String getCategory()     { return category; }
    public String getIcon()         { return icon; }
    public String getWalletName()   { return walletName; }
    public long   getAmountVnd()    { return amountVnd; }
    public Type   getType()         { return type; }
    public long   getTimestampMs()  { return timestampMs; }
}
