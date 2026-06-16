package bg.pu.hla.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import bg.pu.hla.domain.ChatMessage;
import bg.pu.hla.domain.ChatSession;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);

    List<ChatMessage> findTop30BySessionOrderByCreatedAtDesc(ChatSession session);
}
