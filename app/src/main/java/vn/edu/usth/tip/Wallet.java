package vn.edu.usth.tip;

public class Wallet {

    public enum Type { CASH, BANK, EWALLET, INVESTMENT, OTHER }

    private String id;
    private String name;
    private long balanceVnd;
    private String icon;         // emoji hoặc drawable name
    private int color;           // ARGB int
    private Type type;
    private boolean includedInTotal;

    public Wallet(String id, String name, long balanceVnd,
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
    public String getId()             { return id; }
    public String getName()           { return name; }
    public long getBalanceVnd()       { return balanceVnd; }
    public String getIcon()           { return icon; }
    public int getColor()             { return color; }
    public Type getType()             { return type; }
    public boolean isIncludedInTotal(){ return includedInTotal; }

    // Setters
    public void setName(String name)              { this.name = name; }
    public void setBalanceVnd(long b)             { this.balanceVnd = b; }
    public void setIncludedInTotal(boolean v)     { this.includedInTotal = v; }
}