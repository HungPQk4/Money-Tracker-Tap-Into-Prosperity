package vn.edu.usth.tip.network.requests;

public class AccountRequest {
    private String name;
    private String type; // bank, cash, e_wallet, investment, credit_card
    private long balance;
    private String colorHex;
    private String icon;
    private Boolean includeInTotal;

    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public long getBalance() { return balance; }
    public String getColorHex() { return colorHex; }
    public String getIcon() { return icon; }
    public Boolean getIncludeInTotal() { return includeInTotal; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setBalance(long balance) { this.balance = balance; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setIncludeInTotal(Boolean includeInTotal) { this.includeInTotal = includeInTotal; }
}
