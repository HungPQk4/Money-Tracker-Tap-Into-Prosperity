package vn.edu.usth.tip.network.requests;

public class RegisterRequest {
    private String email;
    private String password;
    private String fullName;
    private String currencyCode = "VND";
    private String timezone = "Asia/Ho_Chi_Minh";
    private String deviceName; // tên thiết bị — hiển thị trong danh sách phiên đăng nhập

    public RegisterRequest(String email, String password, String fullName) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
}
