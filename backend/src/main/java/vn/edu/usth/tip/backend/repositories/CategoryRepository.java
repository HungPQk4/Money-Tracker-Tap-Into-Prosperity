package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.Category;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.user.id = :userId AND c.deletedAt IS NULL")
    List<Category> findByUserId(@Param("userId") UUID userId);

    /** System categories (user=NULL) + categories owned by the given user */
    @Query("SELECT c FROM Category c WHERE (c.user IS NULL OR c.user.id = :userId) AND c.deletedAt IS NULL")
    List<Category> findSystemAndUserCategories(@Param("userId") UUID userId);

    /** Find the system fallback "Khác" category */
    Optional<Category> findByNameAndUserIsNull(String name);

    @Query("SELECT c FROM Category c WHERE (c.user IS NULL OR c.user.id = :uid) " +
           "AND c.updatedAt > :since AND c.updatedAt <= :until " +
           "AND (:lastTs IS NULL OR c.updatedAt > :lastTs " +
           "     OR (c.updatedAt = :lastTs AND c.id > :lastId)) " +
           "ORDER BY c.updatedAt ASC, c.id ASC")
    Slice<Category> findDelta(
            @Param("uid") UUID uid,
            @Param("since") OffsetDateTime since,
            @Param("until") OffsetDateTime until,
            @Param("lastTs") OffsetDateTime lastTs,
            @Param("lastId") UUID lastId,
            Pageable pageable
    );
}
