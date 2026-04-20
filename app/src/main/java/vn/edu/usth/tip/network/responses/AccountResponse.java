package vn.edu.usth.tip.network.responses;

/**
 * DTO nhận từ GET /api/accounts
 * Backend Account.balance là BigDecimal → dùng double để Gson parse đúng
 */
public class AccountResponse {
    private String id;
    private String name;
    private String type;
    private double balance; // BigDecimal JSON → double
    private String currencyCode;
    private String colorHex;
    private String icon;
    private Boolean includeInTotal;
    private Boolean isDefault;
    private Boolean isActive;

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public long getBalance() { return (long) balance; }  // helper trả long để hiển thị
    public double getBalanceDouble() { return balance; }
    public String getCurrencyCode() { return currencyCode; }
    public String getColorHex() { return colorHex; }
    public String getIcon() { return icon; }
    public Boolean getIncludeInTotal() { return includeInTotal; }
    public Boolean getIsDefault() { return isDefault; }
    public Boolean getIsActive() { return isActive; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setBalance(double balance) { this.balance = balance; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setIncludeInTotal(Boolean includeInTotal) { this.includeInTotal = includeInTotal; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
