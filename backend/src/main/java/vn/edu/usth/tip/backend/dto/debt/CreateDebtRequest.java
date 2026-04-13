package vn.edu.usth.tip.backend.dto.debt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.DebtType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateDebtRequest {
    @NotNull private UUID userId;

    @NotBlank
    private String contactName;
    private String contactPhone;

    @NotNull private DebtType type;

    @NotNull
    private BigDecimal amount;

    private LocalDate dueDate;
    private String note;
}
