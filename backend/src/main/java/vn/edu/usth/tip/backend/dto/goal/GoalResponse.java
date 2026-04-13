package vn.edu.usth.tip.backend.dto.goal;

import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.GoalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class GoalResponse {
    private UUID id;
    private UUID userId;
    private String name;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private LocalDate deadline;
    private GoalStatus status;
    private String icon;
    private String colorHex;
    private OffsetDateTime createdAt;
}
