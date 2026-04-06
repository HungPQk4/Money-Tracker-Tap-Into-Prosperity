package vn.edu.usth.tip;

/**
 * Model cho Danh mục giao dịch.
 */
public class Category {
    private String id;
    private String name;
    private String icon;
    private boolean isAddButton; // Để phân biệt nút "Thêm" đặc biệt

    public Category(String id, String name, String icon) {
        this(id, name, icon, false);
    }

    public Category(String id, String name, String icon, boolean isAddButton) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.isAddButton = isAddButton;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public boolean isAddButton() { return isAddButton; }

    public void setName(String name) { this.name = name; }
    public void setIcon(String icon) { this.icon = icon; }
}
