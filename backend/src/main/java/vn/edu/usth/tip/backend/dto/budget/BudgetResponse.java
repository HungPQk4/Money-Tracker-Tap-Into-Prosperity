package vn.edu.usth.tip.backend.dto.budget;

import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.BudgetPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class BudgetResponse {
    private UUID id;
    private UUID userId;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal amount;
    private BudgetPeriod periodType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Short alertThreshold;
    private OffsetDateTime createdAt;
}
