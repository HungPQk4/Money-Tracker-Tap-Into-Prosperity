package vn.edu.usth.tip.backend.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateAccountRequest {
    @NotNull
    private UUID userId;

    @NotBlank
    private String name;

    @NotNull
    private AccountType type;

    private BigDecimal balance = BigDecimal.ZERO;
    private String currencyCode = "VND";
    private Boolean isDefault = false;
}
