package bg.pu.hla.repository;

import bg.pu.hla.domain.ConsultationRequest;
import bg.pu.hla.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, Long> {
    List<ConsultationRequest> findTop20ByUserOrderByCreatedAtDesc(UserProfile user);
}
