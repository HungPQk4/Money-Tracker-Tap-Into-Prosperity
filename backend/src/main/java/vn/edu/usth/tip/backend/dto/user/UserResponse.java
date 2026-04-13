package vn.edu.usth.tip.backend.dto.user;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String currencyCode;
    private String timezone;
    private Boolean isActive;
    private OffsetDateTime createdAt;
}
