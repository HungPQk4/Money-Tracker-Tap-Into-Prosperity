package vn.edu.usth.tip.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class SessionResponse {
    private UUID id;
    private String deviceName;
    private String deviceInfo;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastSeenAt;
    private boolean current; // có phải phiên đang gọi request này không
}
