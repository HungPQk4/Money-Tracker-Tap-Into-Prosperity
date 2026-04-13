package vn.edu.usth.tip.backend.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6)
    private String password;

    @NotBlank @Size(max = 100)
    private String fullName;

    private String avatarUrl;
    private String currencyCode = "VND";
    private String timezone = "Asia/Ho_Chi_Minh";
}
