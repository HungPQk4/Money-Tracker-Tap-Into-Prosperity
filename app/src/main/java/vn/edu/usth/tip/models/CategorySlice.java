package vn.edu.usth.tip.models;

/** Một lát cắt trong biểu đồ donut "Chi tiêu theo danh mục". */
public class CategorySlice {
    public final String categoryName;
    public final long   amount;
    public final float  percentage; // 0–100
    public final int    color;      // ARGB int

    public CategorySlice(String categoryName, long amount, float percentage, int color) {
        this.categoryName = categoryName;
        this.amount       = amount;
        this.percentage   = percentage;
        this.color        = color;
    }
}
