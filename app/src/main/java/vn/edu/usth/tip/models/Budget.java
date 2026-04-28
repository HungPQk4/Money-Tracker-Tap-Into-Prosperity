package vn.edu.usth.tip.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Model ngân sách – được lưu vào Room Database.
 */
@Entity(tableName = "budgets")
public class Budget {

    @PrimaryKey
    @NonNull
    private String id;

    private String name;         // Tên ngân sách
    private String emoji;        // Emoji đại diện (vd: 🍜)
    private String color;        // Màu hex (vd: #F2C94C)
    private String categoryName; // Tên danh mục giao dịch cần theo dõi (match vs Transaction.category)
    private long   limitAmount;  // Giới hạn ngân sách (VNĐ)
    private long   spentAmount;  // Số tiền đã chi (nhập tay)
    private long   periodStartMs;// Thời điểm bắt đầu (epoch ms)
    private long   periodEndMs;  // Thời điểm kết thúc (epoch ms)
    private long   createdMs;    // Thời điểm tạo

    public Budget(@NonNull String id, String name, String emoji, String color,
                  String categoryName, long limitAmount, long spentAmount,
                  long periodStartMs, long periodEndMs, long createdMs) {
        this.id            = id;
        this.name          = name;
        this.emoji         = emoji;
        this.color         = color;
        this.categoryName  = categoryName;
        this.limitAmount   = limitAmount;
        this.spentAmount   = spentAmount;
        this.periodStartMs = periodStartMs;
        this.periodEndMs   = periodEndMs;
        this.createdMs     = createdMs;
    }

    // ── Getters ─────────────────────────────────────────────────────
    @NonNull public String getId()           { return id; }
    public String getName()                  { return name; }
    public String getEmoji()                 { return emoji; }
    public String getColor()                 { return color; }
    public String getCategoryName()          { return categoryName; }
    public long   getLimitAmount()           { return limitAmount; }
    public long   getSpentAmount()           { return spentAmount; }
    public long   getPeriodStartMs()         { return periodStartMs; }
    public long   getPeriodEndMs()           { return periodEndMs; }
    public long   getCreatedMs()             { return createdMs; }

    // ── Setters ─────────────────────────────────────────────────────
    public void setId(@NonNull String id)    { this.id = id; }
    public void setName(String name)         { this.name = name; }
    public void setEmoji(String emoji)       { this.emoji = emoji; }
    public void setColor(String color)       { this.color = color; }
    public void setCategoryName(String n)    { this.categoryName = n; }
    public void setLimitAmount(long a)       { this.limitAmount = a; }
    public void setSpentAmount(long a)       { this.spentAmount = a; }
    public void setPeriodStartMs(long t)     { this.periodStartMs = t; }
    public void setPeriodEndMs(long t)       { this.periodEndMs = t; }
    public void setCreatedMs(long t)         { this.createdMs = t; }
}
