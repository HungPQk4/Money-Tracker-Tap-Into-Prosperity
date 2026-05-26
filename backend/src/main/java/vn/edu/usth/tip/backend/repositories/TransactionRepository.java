package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.Transaction;
import vn.edu.usth.tip.backend.models.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // ─── Queries hiện có ──────────────────────────────────────────────────────
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.deletedAt IS NULL")
    List<Transaction> findByUser_Id(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = :type AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate AND t.deletedAt IS NULL")
    BigDecimal getTotalAmountByTypeAndDateRange(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.transactionDate BETWEEN :startDate AND :endDate AND t.deletedAt IS NULL ORDER BY t.transactionDate DESC")
    List<Transaction> findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDesc(
            @Param("userId") UUID userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** LWW PATH A: lookup transaction by ID owned by this user */
    Optional<Transaction> findByIdAndUser_Id(UUID id, UUID userId);

    // ─── Queries mới: lọc 30 ngày theo transactionDate ───────────────────────
    // Dùng Spring Data method naming thay vì JPQL tùy chỉnh → Hibernate tự bind
    // LocalDate (không có timezone issue như OffsetDateTime trên Neon TIMESTAMPTZ)

    /**
     * Lấy giao dịch từ ngày `since` trở đi, mới nhất lên đầu.
     * Dùng cho API /recent — lọc theo transactionDate (LocalDate, an toàn hơn OffsetDateTime).
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.transactionDate >= :since AND t.deletedAt IS NULL ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findByUser_IdAndTransactionDateGreaterThanEqualOrderByTransactionDateDescCreatedAtDesc(
            @Param("userId") UUID userId, @Param("since") LocalDate since);

    /** Chuyển tất cả giao dịch từ category cũ sang category mới (tránh FK violation khi xóa category) */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Transaction t SET t.category.id = :newCatId WHERE t.category.id = :oldCatId")
    void reassignCategoryId(@Param("oldCatId") UUID oldCatId, @Param("newCatId") UUID newCatId);

    /**
     * Delta sync — cursor-based, dùng Slice (không COUNT).
     * Xử lý cả trang đầu (lastTs=null) lẫn trang tiếp theo (cursor).
     * Bao gồm cả bản ghi đã soft-delete (deletedAt != null) để Device B biết mà xóa.
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :uid " +
           "AND t.updatedAt > :since AND t.updatedAt <= :until " +
           "AND (:lastTs IS NULL OR t.updatedAt > :lastTs " +
           "     OR (t.updatedAt = :lastTs AND t.id > :lastId)) " +
           "ORDER BY t.updatedAt ASC, t.id ASC")
    Slice<Transaction> findDelta(
            @Param("uid") UUID uid,
            @Param("since") OffsetDateTime since,
            @Param("until") OffsetDateTime until,
            @Param("lastTs") OffsetDateTime lastTs,
            @Param("lastId") UUID lastId,
            Pageable pageable
    );

    /**
     * Kiểm tra trùng khi sync (legacy PATH B): cùng user + amount + ngày + loại + category + note.
     * Dùng native query để tránh issue với OffsetDateTime trong JPQL.
     * Trả về Optional<Transaction> để client nhận UUID server thật (ngăn Bóng ma Duplicate).
     */
    @Query(value = "SELECT t.* FROM transactions t " +
                   "JOIN categories c ON t.category_id = c.id " +
                   "WHERE t.user_id = :userId AND t.amount = :amount AND t.transaction_date = :txDate " +
                   "AND t.type = :type AND LOWER(c.name) = LOWER(:categoryName) " +
                   "AND ((:note IS NULL AND t.note IS NULL) OR LOWER(t.note) = LOWER(:note)) " +
                   "AND t.deleted_at IS NULL " +
                   "LIMIT 1",
           nativeQuery = true)
    Optional<Transaction> findDuplicate(
            @Param("userId") UUID userId,
            @Param("amount") BigDecimal amount,
            @Param("txDate") LocalDate txDate,
            @Param("type") String type,
            @Param("categoryName") String categoryName,
            @Param("note") String note
    );
}