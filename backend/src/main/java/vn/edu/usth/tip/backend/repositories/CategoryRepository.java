package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserId(UUID userId);

    /** System categories (user=NULL) + categories owned by the given user */
    @Query("SELECT c FROM Category c WHERE c.user IS NULL OR c.user.id = :userId")
    List<Category> findSystemAndUserCategories(@Param("userId") UUID userId);

    /** Find the system fallback "Khác" category */
    Optional<Category> findByNameAndUserIsNull(String name);
}
