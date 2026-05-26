package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.Debt;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DebtRepository extends JpaRepository<Debt, UUID> {
    @Query("SELECT d FROM Debt d WHERE d.user.id = :userId AND d.deletedAt IS NULL")
    List<Debt> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT d FROM Debt d WHERE d.user.id = :uid " +
           "AND d.updatedAt > :since AND d.updatedAt <= :until " +
           "AND (:lastTs IS NULL OR d.updatedAt > :lastTs " +
           "     OR (d.updatedAt = :lastTs AND d.id > :lastId)) " +
           "ORDER BY d.updatedAt ASC, d.id ASC")
    Slice<Debt> findDelta(
            @Param("uid") UUID uid,
            @Param("since") OffsetDateTime since,
            @Param("until") OffsetDateTime until,
            @Param("lastTs") OffsetDateTime lastTs,
            @Param("lastId") UUID lastId,
            Pageable pageable
    );
}
