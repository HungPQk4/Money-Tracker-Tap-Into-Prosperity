package vn.edu.usth.tip.backend.dto.transaction;

import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.RecurrenceInterval;
import vn.edu.usth.tip.backend.models.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class TransactionResponse {
    private UUID id;
    private UUID userId;
    private UUID accountId;
    private String accountName;
    private UUID categoryId;
    private String categoryName;
    private UUID goalId;
    private BigDecimal amount;
    private TransactionType type;
    private String note;
    private LocalDate transactionDate;
    private String receiptUrl;
    private Boolean isRecurring;
    private RecurrenceInterval recurInterval;
    private OffsetDateTime createdAt;
}