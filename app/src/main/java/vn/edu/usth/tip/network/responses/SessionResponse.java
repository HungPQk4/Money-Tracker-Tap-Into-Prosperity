package vn.edu.usth.tip.network.responses;

import java.util.UUID;

/** Một phiên/thiết bị đang đăng nhập (trả về từ GET /api/auth/sessions). */
public class SessionResponse {
    private UUID id;
    private String deviceName;
    private String deviceInfo;
    private String createdAt;   // ISO-8601
    private String lastSeenAt;  // ISO-8601
    private boolean current;    // có phải thiết bị đang dùng không

    public UUID getId() { return id; }
    public String getDeviceName() { return deviceName; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getCreatedAt() { return createdAt; }
    public String getLastSeenAt() { return lastSeenAt; }
    public boolean isCurrent() { return current; }
}
