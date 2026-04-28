package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.Transaction;
import vn.edu.usth.tip.backend.models.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // ─── Queries hiện có ──────────────────────────────────────────────────────
    List<Transaction> findByUser_Id(UUID userId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = :type AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate")
    BigDecimal getTotalAmountByTypeAndDateRange(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<Transaction> findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDesc(
            UUID userId, LocalDate startDate, LocalDate endDate);

    // ─── Queries mới: lọc 30 ngày theo transactionDate ───────────────────────
    // Dùng Spring Data method naming thay vì JPQL tùy chỉnh → Hibernate tự bind
    // LocalDate (không có timezone issue như OffsetDateTime trên Neon TIMESTAMPTZ)

    /**
     * Lấy giao dịch từ ngày `since` trở đi, mới nhất lên đầu.
     * Dùng cho API /recent — lọc theo transactionDate (LocalDate, an toàn hơn OffsetDateTime).
     */
    List<Transaction> findByUser_IdAndTransactionDateGreaterThanEqualOrderByTransactionDateDescCreatedAtDesc(
            UUID userId, LocalDate since);

    /**
     * Kiểm tra trùng khi sync: cùng user + amount + ngày giao dịch + loại.
     * Dùng native query để tránh issue với OffsetDateTime trong JPQL.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM transactions WHERE user_id = :userId AND amount = :amount AND transaction_date = :txDate AND type = :type",
           nativeQuery = true)
    boolean existsDuplicate(
            @Param("userId") UUID userId,
            @Param("amount") BigDecimal amount,
            @Param("txDate") LocalDate txDate,
            @Param("type") String type
    );
}