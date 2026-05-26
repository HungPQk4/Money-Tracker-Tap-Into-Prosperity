package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.Budget;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    @Query("SELECT b FROM Budget b WHERE b.user.id = :userId AND b.deletedAt IS NULL")
    List<Budget> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT b FROM Budget b WHERE b.user.id = :uid " +
           "AND b.updatedAt > :since AND b.updatedAt <= :until " +
           "AND (:lastTs IS NULL OR b.updatedAt > :lastTs " +
           "     OR (b.updatedAt = :lastTs AND b.id > :lastId)) " +
           "ORDER BY b.updatedAt ASC, b.id ASC")
    Slice<Budget> findDelta(
            @Param("uid") UUID uid,
            @Param("since") OffsetDateTime since,
            @Param("until") OffsetDateTime until,
            @Param("lastTs") OffsetDateTime lastTs,
            @Param("lastId") UUID lastId,
            Pageable pageable
    );
}
