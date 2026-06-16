package bg.pu.hla.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bg.pu.hla.domain.*;
import bg.pu.hla.repository.AgentMessageLogRepository;
import bg.pu.hla.repository.ConsultationRequestRepository;
import bg.pu.hla.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
public class AgentAuditService {

    private final UserProfileRepository userRepo;
    private final AgentMessageLogRepository messageLogRepo;
    private final ConsultationRequestRepository consultationRepo;

    @Transactional
    public void recordConsultation(String username,
                                   String senderAgent,
                                   String receiverAgent,
                                   String performative,
                                   String content,
                                   String ontologyContext,
                                   ConsultationType type,
                                   String query,
                                   String response,
                                   String recommendedItems) {
        userRepo.findByUsername(username).ifPresent(user -> {
            messageLogRepo.save(AgentMessageLog.builder()
                    .user(user)
                    .senderAgent(senderAgent)
                    .receiverAgent(receiverAgent)
                    .performative(performative)
                    .content(content)
                    .ontologyContext(ontologyContext)
                    .build());

            if (response != null) {
                consultationRepo.save(ConsultationRequest.builder()
                        .user(user)
                        .type(type)
                        .userQuery(query)
                        .agentResponse(response)
                        .recommendedItems(recommendedItems)
                        .build());
            }
        });
    }
}
