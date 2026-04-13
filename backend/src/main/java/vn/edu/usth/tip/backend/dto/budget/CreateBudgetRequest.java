package vn.edu.usth.tip.backend.dto.budget;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.BudgetPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateBudgetRequest {
    @NotNull private UUID userId;
    @NotNull private UUID categoryId;

    @NotNull @Positive
    private BigDecimal amount;

    @NotNull private BudgetPeriod periodType;
    @NotNull private LocalDate periodStart;
    @NotNull private LocalDate periodEnd;
    private Short alertThreshold = 80;
}
