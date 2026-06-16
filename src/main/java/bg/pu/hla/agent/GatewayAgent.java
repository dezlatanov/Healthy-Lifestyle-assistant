package bg.pu.hla.agent;

import bg.pu.hla.config.AppProperties;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class GatewayAgent extends Agent {

    @Override
    protected void setup() {
        log.info("GatewayAgent {} ready", getLocalName());

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                AgentRequestQueue queue = SpringContextHolder.getBean(AgentRequestQueue.class);
                ACLMessage request = queue.poll();
                if (request != null) {
                    addBehaviour(new CoordinatorInitiator(GatewayAgent.this, request));
                } else {
                    block(200);
                }
            }
        });
    }

    private static class CoordinatorInitiator extends AchieveREInitiator {

        private final ACLMessage originalRequest;

        CoordinatorInitiator(Agent agent, ACLMessage request) {
            super(agent, buildForward(request));
            this.originalRequest = request;
        }

        private static ACLMessage buildForward(ACLMessage request) {
            AppProperties props = SpringContextHolder.getBean(AppProperties.class);
            ACLMessage forward = new ACLMessage(ACLMessage.REQUEST);
            forward.addReceiver(new jade.core.AID(
                    props.getJade().getAgents().getCoordinator(), jade.core.AID.ISLOCALNAME));
            forward.setContent(request.getContent());
            forward.setConversationId(request.getConversationId());
            forward.setReplyWith(request.getReplyWith());
            forward.setLanguage(AclMessageFactory.CODEC.getName());
            forward.setOntology("healthy-lifestyle-requests");
            return forward;
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            deliverToSpring(originalRequest.getConversationId(), inform.getContent());
        }

        @Override
        protected void handleFailure(ACLMessage failure) {
            deliverFailure(originalRequest.getConversationId(), failure.getContent());
        }

        protected void handleNotAchieved() {
            deliverFailure(originalRequest.getConversationId(), "Coordinator agent timeout");
        }

        private void deliverToSpring(String conversationId, String content) {
            AgentRequestQueue queue = SpringContextHolder.getBean(AgentRequestQueue.class);
            try {
                Map<String, Object> payload = AclMessageFactory.fromJson(content);
                queue.complete(conversationId, payload);
                log.info("Delivered agent response for {}", conversationId);
            } catch (Exception e) {
                queue.fail(conversationId, e.getMessage());
            }
        }

        private void deliverFailure(String conversationId, String reason) {
            SpringContextHolder.getBean(AgentRequestQueue.class).fail(conversationId, reason);
            log.warn("Agent consultation failed for {}: {}", conversationId, reason);
        }
    }
}
