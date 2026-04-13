package vn.edu.usth.tip.backend.dto.debt;

import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.DebtStatus;
import vn.edu.usth.tip.backend.models.enums.DebtType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class DebtResponse {
    private UUID id;
    private UUID userId;
    private String contactName;
    private String contactPhone;
    private DebtType type;
    private BigDecimal amount;
    private BigDecimal remainingAmount;
    private LocalDate dueDate;
    private DebtStatus status;
    private String note;
    private OffsetDateTime createdAt;
}
