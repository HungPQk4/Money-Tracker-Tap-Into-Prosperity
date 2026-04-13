package vn.edu.usth.tip.backend.dto.goal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateGoalRequest {
    @NotNull private UUID userId;

    @NotBlank
    private String name;

    @NotNull @Positive
    private BigDecimal targetAmount;

    private BigDecimal currentAmount = BigDecimal.ZERO;
    private LocalDate deadline;
    private String icon;
    private String colorHex;
}
