package bg.pu.hla.agent;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import bg.pu.hla.config.AppProperties;
import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyRecommendation;
import bg.pu.hla.ontology.OntologyService;
import bg.pu.hla.service.PersonalizedAdviceService;
import bg.pu.hla.service.UserPersonalizationService;
import bg.pu.hla.repository.AgentMessageLogRepository;
import bg.pu.hla.repository.ConsultationRequestRepository;
import bg.pu.hla.repository.DailyLogRepository;
import bg.pu.hla.repository.UserProfileRepository;

@Slf4j
public class CoordinatorAgent extends Agent {

    private final Map<String, ACLMessage> pendingRequests = new ConcurrentHashMap<>();

    @Override
    protected void setup() {
        log.info("CoordinatorAgent {} ready", getLocalName());

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive(MessageTemplate.or(
                        MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                        MessageTemplate.or(
                                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                                MessageTemplate.MatchPerformative(ACLMessage.FAILURE)
                        )
                ));

                if (msg == null) {
                    block();
                    return;
                }

                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    handleUserRequest(msg);
                } else if (msg.getPerformative() == ACLMessage.INFORM) {
                    forwardSpecialistReply(msg);
                } else if (msg.getPerformative() == ACLMessage.FAILURE) {
                    forwardSpecialistReply(msg);
                }
            }
        });
    }

    private void handleUserRequest(ACLMessage msg) {
        AppProperties props = SpringContextHolder.getBean(AppProperties.class);
        AgentMessageLogRepository messageLogRepo = SpringContextHolder.getBean(AgentMessageLogRepository.class);
        UserProfileRepository userRepo = SpringContextHolder.getBean(UserProfileRepository.class);

        Map<String, Object> payload = AclMessageFactory.fromJson(msg.getContent());
        String conversationId = msg.getConversationId() != null ? msg.getConversationId() : msg.getReplyWith();
        String username = (String) payload.get("username");
        String type = (String) payload.get("type");

        pendingRequests.put(conversationId, msg);

        userRepo.findByUsername(username).ifPresent(user ->
                messageLogRepo.save(AgentMessageLog.builder()
                        .user(user)
                        .senderAgent("web-ui")
                        .receiverAgent(getLocalName())
                        .performative(ACLMessage.getPerformative(ACLMessage.REQUEST))
                        .content(msg.getContent())
                        .build())
        );

        ConsultationType consultationType = ConsultationType.valueOf(type);

        if (consultationType == ConsultationType.HABITS) {
            handleHabitsRequest(msg, conversationId, payload);
            pendingRequests.remove(conversationId);
            return;
        }

        if (consultationType == ConsultationType.GENERAL) {
            handleGeneralRequest(msg, conversationId, payload);
            pendingRequests.remove(conversationId);
            return;
        }

        if (consultationType == ConsultationType.CHAT) {
            String target = props.getJade().getAgents().getLlmCoach();
            ACLMessage delegate = AclMessageFactory.request(target, msg.getContent());
            delegate.setConversationId(conversationId);
            delegate.setReplyWith(conversationId);
            send(delegate);
            return;
        }

        String targetAgent = consultationType == ConsultationType.FITNESS
                ? props.getJade().getAgents().getFitness()
                : props.getJade().getAgents().getNutrition();

        ACLMessage delegate = AclMessageFactory.request(targetAgent, msg.getContent());
        delegate.setConversationId(conversationId);
        delegate.setReplyWith(conversationId);
        send(delegate);
    }

    private void handleHabitsRequest(ACLMessage original, String conversationId, Map<String, Object> payload) {
        OntologyService ontologyService = SpringContextHolder.getBean(OntologyService.class);
        PersonalizedAdviceService adviceService = SpringContextHolder.getBean(PersonalizedAdviceService.class);
        ConsultationRequestRepository consultationRepo = SpringContextHolder.getBean(ConsultationRequestRepository.class);
        UserProfileRepository userRepo = SpringContextHolder.getBean(UserProfileRepository.class);
        DailyLogRepository dailyLogRepo = SpringContextHolder.getBean(DailyLogRepository.class);

        String username = (String) payload.get("username");
        String query = (String) payload.getOrDefault("query", "");
        UserProfile user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        DailyLog latestLog = dailyLogRepo.findFirstByUserOrderByLogDateDesc(user).orElse(null);

        List<OntologyRecommendation> habits = ontologyService.listHabitsForGoal(
                user.getGoal() != null ? user.getGoal() : bg.pu.hla.domain.HealthGoal.MAINTENANCE);
        UserPersonalizationService personalization = SpringContextHolder.getBean(UserPersonalizationService.class);
        habits = personalization.personalizeHabits(user, latestLog, habits);
        Map<String, Object> responsePayload = adviceService.buildHabitsAdvice(user, latestLog, query, habits);
        String response = String.valueOf(responsePayload.get("response"));

        consultationRepo.save(ConsultationRequest.builder()
                .user(user)
                .type(ConsultationType.HABITS)
                .userQuery(query)
                .agentResponse(response)
                .recommendedItems(habits.stream().map(OntologyRecommendation::getLabel)
                        .reduce((a, b) -> a + ", " + b).orElse(""))
                .build());

        AppProperties props = SpringContextHolder.getBean(AppProperties.class);
        ACLMessage reply = AclMessageFactory.inform(
                props.getJade().getAgents().getGateway(),
                AclMessageFactory.toJson(responsePayload));
        reply.setInReplyTo(original.getReplyWith());
        reply.setConversationId(conversationId);
        send(reply);
    }

    private void handleGeneralRequest(ACLMessage original, String conversationId, Map<String, Object> payload) {
        PersonalizedAdviceService adviceService = SpringContextHolder.getBean(PersonalizedAdviceService.class);
        ConsultationRequestRepository consultationRepo = SpringContextHolder.getBean(ConsultationRequestRepository.class);
        UserProfileRepository userRepo = SpringContextHolder.getBean(UserProfileRepository.class);
        DailyLogRepository dailyLogRepo = SpringContextHolder.getBean(DailyLogRepository.class);

        String username = (String) payload.get("username");
        String query = (String) payload.getOrDefault("query", "");
        UserProfile user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        DailyLog latestLog = dailyLogRepo.findFirstByUserOrderByLogDateDesc(user).orElse(null);

        Map<String, Object> responsePayload = adviceService.buildGeneralAdvice(user, latestLog, query);
        String response = String.valueOf(responsePayload.get("response"));

        consultationRepo.save(ConsultationRequest.builder()
                .user(user)
                .type(ConsultationType.GENERAL)
                .userQuery(query)
                .agentResponse(response)
                .recommendedItems(responsePayload.get("recommendations").toString())
                .build());

        AppProperties props = SpringContextHolder.getBean(AppProperties.class);
        ACLMessage reply = AclMessageFactory.inform(
                props.getJade().getAgents().getGateway(),
                AclMessageFactory.toJson(responsePayload));
        reply.setInReplyTo(original.getReplyWith());
        reply.setConversationId(conversationId);
        send(reply);
    }

    private void forwardSpecialistReply(ACLMessage specialistReply) {
        String conversationId = specialistReply.getInReplyTo();
        if (conversationId == null) {
            return;
        }

        ACLMessage original = pendingRequests.remove(conversationId);
        if (original == null) {
            log.warn("No pending request for conversation {}", conversationId);
            return;
        }

        AppProperties props = SpringContextHolder.getBean(AppProperties.class);

        ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
        reply.addReceiver(new AID(props.getJade().getAgents().getGateway(), AID.ISLOCALNAME));
        reply.setContent(specialistReply.getContent());
        reply.setInReplyTo(original.getReplyWith());
        reply.setConversationId(conversationId);
        reply.setLanguage(AclMessageFactory.CODEC.getName());
        send(reply);
        log.info("Forwarded specialist reply for conversation {}", conversationId);
    }
}
