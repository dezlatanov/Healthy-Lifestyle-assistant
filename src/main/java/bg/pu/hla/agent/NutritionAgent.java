package bg.pu.hla.agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import bg.pu.hla.config.AppProperties;
import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyService;
import bg.pu.hla.ontology.OntologyQueryResult;
import bg.pu.hla.repository.DailyLogRepository;
import bg.pu.hla.repository.UserProfileRepository;
import bg.pu.hla.service.AgentAuditService;
import bg.pu.hla.service.PersonalizedAdviceService;
import bg.pu.hla.service.UserPersonalizationService;

@Slf4j
public class NutritionAgent extends Agent {

    @Override
    protected void setup() {
        log.info("NutritionAgent {} ready", getLocalName());
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
            log.error("Nutrition agent failed", e);
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
        OntologyService ontologyService = SpringContextHolder.getBean(OntologyService.class);
        AgentAuditService auditService = SpringContextHolder.getBean(AgentAuditService.class);
        PersonalizedAdviceService adviceService = SpringContextHolder.getBean(PersonalizedAdviceService.class);
        UserProfileRepository userRepo = SpringContextHolder.getBean(UserProfileRepository.class);
        DailyLogRepository dailyLogRepo = SpringContextHolder.getBean(DailyLogRepository.class);
        AppProperties props = SpringContextHolder.getBean(AppProperties.class);

        Map<String, Object> payload = AclMessageFactory.fromJson(msg.getContent());
        String username = (String) payload.get("username");
        String query = (String) payload.getOrDefault("query", "");
        String goalStr = (String) payload.get("goal");

        UserProfile user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        DailyLog latestLog = dailyLogRepo.findFirstByUserOrderByLogDateDesc(user).orElse(null);

        HealthGoal goal = HealthGoal.valueOf(goalStr);
        var raw = ontologyService.recommendMealsForGoal(goal);
        UserPersonalizationService personalization = SpringContextHolder.getBean(UserPersonalizationService.class);
        var personalizedItems = personalization.personalizeMeals(user, raw.getItems());
        var result = OntologyQueryResult.builder()
                .items(personalizedItems)
                .totalCalories(raw.getTotalCalories())
                .sparqlUsed(raw.getSparqlUsed())
                .build();
        Map<String, Object> responsePayload = adviceService.buildNutritionAdvice(user, latestLog, query, result);

        auditService.recordConsultation(
                username,
                getLocalName(),
                msg.getSender().getLocalName(),
                ACLMessage.getPerformative(ACLMessage.INFORM),
                String.valueOf(responsePayload.get("response")),
                result.getSparqlUsed(),
                ConsultationType.NUTRITION,
                query,
                String.valueOf(responsePayload.get("response")),
                responsePayload.get("recommendations").toString()
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
