package bg.pu.hla.agent;

import jade.lang.acl.ACLMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import bg.pu.hla.config.AppProperties;
import bg.pu.hla.domain.*;
import bg.pu.hla.repository.AgentMessageLogRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentGateway {

    private static final int TIMEOUT_SECONDS = 15;

    private final AppProperties appProperties;
    private final AgentMessageLogRepository messageLogRepo;
    private final AgentRequestQueue requestQueue;

    public Map<String, Object> sendConsultation(UserProfile user, ConsultationType type, String query) {
        try {
            String conversationId = UUID.randomUUID().toString();
            Map<String, Object> payload = Map.of(
                    "username", user.getUsername(),
                    "goal", user.getGoal().name(),
                    "activityLevel", user.getActivityLevel().name(),
                    "type", type.name(),
                    "query", query == null ? "" : query
            );

            ACLMessage request = AclMessageFactory.request(
                    appProperties.getJade().getAgents().getCoordinator(),
                    AclMessageFactory.toJson(payload)
            );
            request.setConversationId(conversationId);
            request.setReplyWith(conversationId);

            messageLogRepo.save(AgentMessageLog.builder()
                    .user(user)
                    .senderAgent("web-ui")
                    .receiverAgent(appProperties.getJade().getAgents().getCoordinator())
                    .performative(ACLMessage.getPerformative(ACLMessage.REQUEST))
                    .content(AclMessageFactory.toJson(payload))
                    .build());

            requestQueue.register(conversationId);
            requestQueue.enqueue(request);

            Map<String, Object> result = requestQueue.await(conversationId, TIMEOUT_SECONDS);

            messageLogRepo.save(AgentMessageLog.builder()
                    .user(user)
                    .senderAgent(appProperties.getJade().getAgents().getCoordinator())
                    .receiverAgent("web-ui")
                    .performative(ACLMessage.getPerformative(ACLMessage.INFORM))
                    .content(AclMessageFactory.toJson(result))
                    .build());

            if (result.containsKey("error")) {
                throw new IllegalStateException(String.valueOf(result.get("error")));
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent communication failed", e);
        }
    }
}
