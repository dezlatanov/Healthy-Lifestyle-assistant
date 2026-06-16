package bg.pu.hla.agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import bg.pu.hla.config.AppProperties;
import bg.pu.hla.domain.ChatMode;
import bg.pu.hla.domain.ConsultationType;
import bg.pu.hla.service.AgentAuditService;
import bg.pu.hla.service.HybridChatService;

@Slf4j
public class LlmCoachAgent extends Agent {

    @Override
    protected void setup() {
        log.info("LlmCoachAgent {} ready", getLocalName());
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (msg != null) {
                    handleRequest(msg);
                } else {
                    block();
                }
            }
        });
    }

    private void handleRequest(ACLMessage msg) {
        try {
            handleRequestInternal(msg);
        } catch (Exception e) {
            log.error("LLM coach agent failed", e);
            AppProperties props = SpringContextHolder.getBean(AppProperties.class);
            ACLMessage failure = AclMessageFactory.failure(
                    props.getJade().getAgents().getCoordinator(),
                    e.getMessage()
            );
            failure.setInReplyTo(msg.getReplyWith());
            failure.setConversationId(msg.getConversationId());
            send(failure);
        }
    }

    private void handleRequestInternal(ACLMessage msg) {
        HybridChatService hybridChatService = SpringContextHolder.getBean(HybridChatService.class);
        AgentAuditService auditService = SpringContextHolder.getBean(AgentAuditService.class);
        AppProperties props = SpringContextHolder.getBean(AppProperties.class);

        Map<String, Object> payload = AclMessageFactory.fromJson(msg.getContent());
        String username = (String) payload.get("username");
        String query = (String) payload.getOrDefault("query", "");

        Map<String, Object> responsePayload = hybridChatService.buildResponse(username, query, ChatMode.HYBRID);

        auditService.recordConsultation(
                username,
                getLocalName(),
                msg.getSender().getLocalName(),
                ACLMessage.getPerformative(ACLMessage.INFORM),
                String.valueOf(responsePayload.get("response")),
                String.valueOf(responsePayload.get("sparqlUsed")),
                ConsultationType.CHAT,
                query,
                String.valueOf(responsePayload.get("response")),
                String.valueOf(responsePayload.get("ontologySources"))
        );

        ACLMessage reply = AclMessageFactory.inform(
                props.getJade().getAgents().getCoordinator(),
                AclMessageFactory.toJson(responsePayload)
        );
        reply.setInReplyTo(msg.getReplyWith());
        reply.setConversationId(msg.getConversationId());
        send(reply);
    }
}
