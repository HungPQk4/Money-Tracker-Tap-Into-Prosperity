package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.Goal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {
    @Query("SELECT g FROM Goal g WHERE g.user.id = :userId AND g.deletedAt IS NULL")
    List<Goal> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT g FROM Goal g WHERE g.user.id = :uid " +
           "AND g.updatedAt > :since AND g.updatedAt <= :until " +
           "AND (:lastTs IS NULL OR g.updatedAt > :lastTs " +
           "     OR (g.updatedAt = :lastTs AND g.id > :lastId)) " +
           "ORDER BY g.updatedAt ASC, g.id ASC")
    Slice<Goal> findDelta(
            @Param("uid") UUID uid,
            @Param("since") OffsetDateTime since,
            @Param("until") OffsetDateTime until,
            @Param("lastTs") OffsetDateTime lastTs,
            @Param("lastId") UUID lastId,
            Pageable pageable
    );
}
