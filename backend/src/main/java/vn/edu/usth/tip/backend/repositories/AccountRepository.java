package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.Account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.deletedAt IS NULL")
    List<Account> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.user.id = :userId AND a.includeInTotal = true AND a.deletedAt IS NULL")
    BigDecimal getTotalBalanceByUserId(@Param("userId") UUID userId);

    @Query("SELECT a FROM Account a WHERE a.user.id = :uid " +
           "AND a.updatedAt > :since AND a.updatedAt <= :until " +
           "AND (:lastTs IS NULL OR a.updatedAt > :lastTs " +
           "     OR (a.updatedAt = :lastTs AND a.id > :lastId)) " +
           "ORDER BY a.updatedAt ASC, a.id ASC")
    Slice<Account> findDelta(
            @Param("uid") UUID uid,
            @Param("since") OffsetDateTime since,
            @Param("until") OffsetDateTime until,
            @Param("lastTs") OffsetDateTime lastTs,
            @Param("lastId") UUID lastId,
            Pageable pageable
    );
}
