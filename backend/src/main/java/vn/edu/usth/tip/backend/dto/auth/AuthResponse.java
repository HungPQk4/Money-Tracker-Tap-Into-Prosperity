package vn.edu.usth.tip.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;          // access token (JWT, ngắn hạn)
    private String refreshToken;   // refresh token opaque (để xoay access token)
    private String tokenType = "Bearer";
    private UUID userId;
    private String email;
    private String fullName;

    public AuthResponse(String token, String refreshToken, UUID userId, String email, String fullName) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
    }
}
