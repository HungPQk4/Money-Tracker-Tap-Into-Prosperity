package vn.edu.usth.tip.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {
    @NotBlank
    private String refreshToken;
}
