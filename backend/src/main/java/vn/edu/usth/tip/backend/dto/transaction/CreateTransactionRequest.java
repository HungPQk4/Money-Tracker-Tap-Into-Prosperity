package vn.edu.usth.tip.backend.dto.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.RecurrenceInterval;
import vn.edu.usth.tip.backend.models.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateTransactionRequest {
    @NotNull private UUID userId;
    @NotNull private UUID accountId;
    @NotNull private UUID categoryId;
    private UUID goalId;

    @NotNull @Positive
    private BigDecimal amount;

    @NotNull private TransactionType type;
    private String note;

    @NotNull private LocalDate transactionDate;
    private String receiptUrl;
    private Boolean isRecurring = false;
    private RecurrenceInterval recurInterval;
}
