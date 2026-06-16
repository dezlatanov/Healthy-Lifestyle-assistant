package bg.pu.hla.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import bg.pu.hla.config.AppProperties;
import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyQueryResult;
import bg.pu.hla.ontology.OntologyRecommendation;
import bg.pu.hla.ontology.OntologyService;
import bg.pu.hla.repository.DailyLogRepository;
import bg.pu.hla.repository.UserProfileRepository;
import bg.pu.hla.service.chat.ChatIntentDetector;

import java.util.*;

@Service
@RequiredArgsConstructor
public class HybridChatService {

    private final UserProfileRepository userRepo;
    private final DailyLogRepository dailyLogRepo;
    private final OntologyContextService ontologyContextService;
    private final OntologyService ontologyService;
    private final PersonalizedAdviceService adviceService;
    private final LlmService llmService;
    private final AppProperties appProperties;
    private final UserPersonalizationService personalization;

    public Map<String, Object> buildResponse(String username, String userMessage, ChatMode mode) {
        UserProfile user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        DailyLog latestLog = dailyLogRepo.findFirstByUserOrderByLogDateDesc(user).orElse(null);

        OntologyContextService.OntologyContext ctx =
                ontologyContextService.buildContext(user, latestLog, userMessage);

        return switch (mode) {
            case SYMBOLIC -> buildSymbolic(user, latestLog, userMessage, ctx);
            case LLM_ONLY -> buildLlmOnly(user, userMessage);
            case HYBRID -> buildHybrid(user, userMessage, ctx);
        };
    }

    private Map<String, Object> buildSymbolic(UserProfile user, DailyLog latestLog,
                                              String query, OntologyContextService.OntologyContext ctx) {
        Map<String, Object> payload = switch (ctx.intent()) {
            case NUTRITION -> {
                OntologyQueryResult result = ontologyService.recommendMealsForGoal(
                        user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE);
                yield adviceService.buildNutritionAdvice(user, latestLog, query, result);
            }
            case FITNESS -> {
                OntologyQueryResult result = ontologyService.recommendExercisesForGoal(
                        user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE);
                yield adviceService.buildFitnessAdvice(user, latestLog, query, result);
            }
            case HABITS -> adviceService.buildHabitsAdvice(user, latestLog, query, ctx.items());
            default -> adviceService.buildGeneralAdvice(user, latestLog, query);
        };
        payload.put("mode", ChatMode.SYMBOLIC.name());
        payload.put("agent", "symbolic-" + payload.getOrDefault("agent", "coordinator"));
        payload.put("intent", ctx.intent().name());
        payload.put("ontologySources", ontologyContextService.formatSources(ctx.items()));
        payload.put("sparqlUsed", ctx.sparqlUsed());
        payload.put("groundingScore", computeGroundingScore(String.valueOf(payload.get("response")), ctx.items()));
        return payload;
    }

    private Map<String, Object> buildLlmOnly(UserProfile user, String userMessage) {
        String response = llmService.chat(
                LlmService.llmOnlySystemPrompt(),
                "User goal: " + (user.getGoal() != null ? user.getGoal() : "MAINTENANCE")
                        + "\nQuestion: " + userMessage,
                false
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", "llm-only");
        payload.put("mode", ChatMode.LLM_ONLY.name());
        payload.put("intent", ChatIntentDetector.detect(userMessage).name());
        payload.put("response", response);
        payload.put("contextSummary", "LLM-only mode — no ontology grounding.");
        payload.put("tips", List.of("Responses may not match verified ontology facts."));
        payload.put("recommendations", List.of());
        payload.put("ontologySources", "");
        payload.put("sparqlUsed", "none");
        payload.put("groundingScore", 0.0);
        return payload;
    }

    private Map<String, Object> buildHybrid(UserProfile user, String userMessage,
                                            OntologyContextService.OntologyContext ctx) {
        String response = hasLlmKey()
                ? llmService.chat(LlmService.hybridSystemPrompt(), ctx.contextText() + "\n\nUser question: " + userMessage, true)
                : buildOfflineHybridResponse(userMessage, ctx);

        List<Map<String, String>> recs = ctx.items().stream()
                .map(i -> Map.of(
                        "label", i.getLabel(),
                        "details", i.getDetails() != null ? i.getDetails() : "",
                        "type", i.getType() != null ? i.getType() : ""))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", "llm-coach-agent");
        payload.put("mode", ChatMode.HYBRID.name());
        payload.put("intent", ctx.intent().name());
        payload.put("response", response);
        payload.put("contextSummary", ctx.profileSummary());
        payload.put("tips", List.of(
                "Hybrid mode: LLM response grounded in OWL ontology via SPARQL.",
                "Intent detected: " + ctx.intent()));
        payload.put("recommendations", recs);
        payload.put("ontologySources", ontologyContextService.formatSources(ctx.items()));
        payload.put("sparqlUsed", ctx.sparqlUsed());
        payload.put("groundingScore", computeGroundingScore(response, ctx.items()));
        return payload;
    }

    public double computeGroundingScore(String response, List<OntologyRecommendation> items) {
        if (response == null || items == null || items.isEmpty()) {
            return 0.0;
        }
        String lower = response.toLowerCase(Locale.ROOT);
        long matched = items.stream()
                .filter(i -> i.getLabel() != null && lower.contains(i.getLabel().toLowerCase(Locale.ROOT)))
                .count();
        return Math.round((matched * 100.0 / items.size()) * 10.0) / 10.0;
    }

    private boolean hasLlmKey() {
        String key = appProperties.getLlm().getApiKey();
        return appProperties.getLlm().isEnabled() && key != null && !key.isBlank();
    }

    private String buildOfflineHybridResponse(String userMessage, OntologyContextService.OntologyContext ctx) {
        String sources = ontologyContextService.formatSources(ctx.items());
        return """
                Здравей! (Hybrid demo — без OpenAI API key, отговорът е grounded в OWL онтологията)
                
                Въпрос: %s
                
                Открит intent: %s
                
                На база SPARQL заявки към онтологията, ето проверените препоръки:
                %s
                
                Обяснение: тези елементи са извлечени от OWL knowledge base и са подходящи за твоята цел.
                За пълен разговорен LLM отговор задай OPENAI_API_KEY.
                """.formatted(
                userMessage,
                ctx.intent(),
                sources.isBlank() ? "няма намерени елементи" : sources
        );
    }
}
