package bg.pu.hla.repository;

import bg.pu.hla.domain.AgentMessageLog;
import bg.pu.hla.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMessageLogRepository extends JpaRepository<AgentMessageLog, Long> {
    List<AgentMessageLog> findTop50ByUserOrderByTimestampDesc(UserProfile user);
    List<AgentMessageLog> findTop100ByOrderByTimestampDesc();
}
