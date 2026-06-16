package bg.pu.hla.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import bg.pu.hla.domain.ChatSession;
import bg.pu.hla.domain.UserProfile;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findTop10ByUserOrderByUpdatedAtDesc(UserProfile user);

    Optional<ChatSession> findFirstByUserOrderByUpdatedAtDesc(UserProfile user);
}
