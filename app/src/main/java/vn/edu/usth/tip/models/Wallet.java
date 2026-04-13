package vn.edu.usth.tip.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "wallets")
public class Wallet {

    public enum Type { CASH, BANK, EWALLET, INVESTMENT, OTHER }

    @PrimaryKey
    @NonNull
    private String id;
    private String name;
    private long balanceVnd;
    private String icon;         // emoji hoặc drawable name
    private int color;           // ARGB int
    private Type type;
    private boolean includedInTotal;

    public Wallet(@NonNull String id, String name, long balanceVnd,
                  String icon, int color, Type type, boolean includedInTotal) {
        this.id = id;
        this.name = name;
        this.balanceVnd = balanceVnd;
        this.icon = icon;
        this.color = color;
        this.type = type;
        this.includedInTotal = includedInTotal;
    }

    public String getFormattedBalance() {
        return String.format("₫%,.0f", (double) balanceVnd)
                .replace(",", ".");
    }

    public String getTypeName() {
        switch (type) {
            case CASH:       return "Tiền mặt";
            case BANK:       return "Ngân hàng";
            case EWALLET:    return "Ví điện tử";
            case INVESTMENT: return "Đầu tư";
            default:         return "Khác";
        }
    }

    // Getters
    @NonNull
    public String getId()             { return id; }
    public String getName()           { return name; }
    public long getBalanceVnd()       { return balanceVnd; }
    public String getIcon()           { return icon; }
    public int getColor()             { return color; }
    public Type getType()             { return type; }
    public boolean isIncludedInTotal(){ return includedInTotal; }

    // Setters
    public void setId(@NonNull String id)           { this.id = id; }
    public void setName(String name)              { this.name = name; }
    public void setBalanceVnd(long b)             { this.balanceVnd = b; }
    public void setIncludedInTotal(boolean v)     { this.includedInTotal = v; }
    public void setIcon(String icon)              { this.icon = icon; }
    public void setColor(int color)               { this.color = color; }
    public void setType(Type type)                { this.type = type; }
}