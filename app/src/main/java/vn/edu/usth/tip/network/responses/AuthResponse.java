package vn.edu.usth.tip.network.responses;

import java.util.UUID;

public class AuthResponse {
    private String token;
    private String tokenType;
    private UUID userId;
    private String email;
    private String fullName;

    // Getters
    public String getToken() { return token; }
    public String getTokenType() { return tokenType; }
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
}
