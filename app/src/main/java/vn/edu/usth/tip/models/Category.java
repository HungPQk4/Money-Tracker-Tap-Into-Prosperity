package vn.edu.usth.tip.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Model cho Danh mục giao dịch.
 */
@Entity(tableName = "categories")
public class Category {
    @PrimaryKey
    @NonNull
    private String id;
    private String name;
    private String icon;
    private boolean isAddButton; // Để phân biệt nút "Thêm" đặc biệt

    @Ignore
    public Category(@NonNull String id, String name, String icon) {
        this(id, name, icon, false);
    }

    public Category(@NonNull String id, String name, String icon, boolean isAddButton) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.isAddButton = isAddButton;
    }

    @NonNull
    public String getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public boolean isAddButton() { return isAddButton; }

    public void setId(@NonNull String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setAddButton(boolean isAddButton) { this.isAddButton = isAddButton; }
}
