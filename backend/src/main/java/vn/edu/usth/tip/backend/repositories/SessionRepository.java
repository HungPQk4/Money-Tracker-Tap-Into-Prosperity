package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.usth.tip.backend.models.Session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByRefreshTokenHash(String refreshTokenHash);

    List<Session> findByUserIdAndRevokedFalseOrderByLastSeenAtDesc(UUID userId);
}
