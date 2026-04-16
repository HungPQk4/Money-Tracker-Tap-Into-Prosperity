package vn.edu.usth.tip.backend.dto.account;

import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.AccountType;

import java.math.BigDecimal;

@Data
public class AccountRequest {
    private String name;
    private AccountType type;
    private BigDecimal balance;
    private String colorHex;
    private String icon;
    private Boolean includeInTotal;
}
