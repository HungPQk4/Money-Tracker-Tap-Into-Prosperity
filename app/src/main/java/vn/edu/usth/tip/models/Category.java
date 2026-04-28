package vn.edu.usth.tip.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Model cho Danh mục giao dịch.
 *
 * Chiến lược: đặt tất cả field dạng camelCase, dùng @ColumnInfo để khai báo
 * tên cột chính xác trong SQLite. Room sẽ dùng setter để gán giá trị khi đọc DB.
 */
@Entity(tableName = "categories")
public class Category {

    @PrimaryKey
    @NonNull
    private String id;

    private String name;
    private String icon;

    @ColumnInfo(name = "color_hex")
    private String colorHex;

    private String type;

    @ColumnInfo(name = "is_system")
    private boolean isSystem;

    // Tên field = tên cột trong SQLite → Room map trực tiếp, không cần @ColumnInfo
    private boolean isAddButton;

    // -------------------------------------------------------------------------
    // Constructor để Room tạo object khi đọc từ DB (không @Ignore → Room dùng cái này)
    // Room yêu cầu constructor duy nhất không @Ignore, HOẶC dùng setter.
    // Vì có nhiều constructor, Room sẽ dùng setter → cần constructor no-arg.
    // -------------------------------------------------------------------------

    /** Constructor mặc định — Room dùng setter để populate fields */
    public Category() {}

    @Ignore
    public Category(@NonNull String id, String name, String icon) {
        this.id = id;
        this.name = name;
        this.icon = icon;
    }

    @Ignore
    public Category(@NonNull String id, String name, String icon, boolean isAddButton) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.isAddButton = isAddButton;
    }

    @Ignore
    public Category(@NonNull String id, String name, String icon,
                    String colorHex, String type, boolean isSystem, boolean isAddButton) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.colorHex = colorHex;
        this.type = type;
        this.isSystem = isSystem;
        this.isAddButton = isAddButton;
    }

    // -------------------------------------------------------------------------
    // Getters & Setters (Room dùng setter khi đọc từ DB với no-arg constructor)
    // -------------------------------------------------------------------------
    @NonNull public String getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public String getColorHex() { return colorHex; }
    public String getType() { return type; }
    public boolean isSystem() { return isSystem; }
    public boolean isAddButton() { return isAddButton; }

    public void setId(@NonNull String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public void setType(String type) { this.type = type; }
    public void setSystem(boolean system) { isSystem = system; }
    public void setAddButton(boolean addButton) { isAddButton = addButton; }
}
