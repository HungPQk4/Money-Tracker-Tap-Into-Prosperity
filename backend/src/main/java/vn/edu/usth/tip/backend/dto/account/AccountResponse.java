package vn.edu.usth.tip.backend.dto.account;

import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AccountResponse {
    private UUID id;
    private String name;
    private AccountType type;
    private BigDecimal balance;
    private String currencyCode;
    private String colorHex;
    private String icon;
    private Boolean includeInTotal;
    private Boolean isDefault;
    private Boolean isActive;
}
