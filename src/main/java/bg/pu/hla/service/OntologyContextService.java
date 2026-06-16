package bg.pu.hla.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyQueryResult;
import bg.pu.hla.ontology.OntologyRecommendation;
import bg.pu.hla.ontology.OntologyService;
import bg.pu.hla.service.chat.ChatIntentDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OntologyContextService {

    private final OntologyService ontologyService;
    private final PersonalizedAdviceService adviceService;
    private final UserPersonalizationService personalization;
    private final UserMetricsService metricsService;

    public record OntologyContext(
            ConsultationType intent,
            List<OntologyRecommendation> items,
            String sparqlUsed,
            String contextText,
            String profileSummary,
            UserMetrics metrics
    ) {
    }

    public OntologyContext buildContext(UserProfile user, DailyLog latestLog, String userMessage) {
        ConsultationType intent = ChatIntentDetector.detect(userMessage);
        HealthGoal goal = user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE;
        UserMetrics metrics = metricsService.compute(user, latestLog);

        List<OntologyRecommendation> items = new ArrayList<>();
        String sparql = "";

        switch (intent) {
            case NUTRITION -> {
                OntologyQueryResult meals = ontologyService.recommendMealsForGoal(goal);
                items = personalization.personalizeMeals(user, latestLog, meals.getItems());
                sparql = meals.getSparqlUsed();
            }
            case FITNESS -> {
                OntologyQueryResult exercises = ontologyService.recommendExercisesForGoal(goal);
                items = personalization.personalizeExercises(user, latestLog, exercises.getItems());
                sparql = exercises.getSparqlUsed();
            }
            case HABITS -> {
                items = personalization.personalizeHabits(user, latestLog,
                        ontologyService.listHabitsForGoal(goal));
                sparql = "listHabitsForGoal(" + goal + ")";
            }
            default -> {
                var meals = personalization.personalizeMeals(user, latestLog,
                        ontologyService.recommendMealsForGoal(goal).getItems());
                var exercises = personalization.personalizeExercises(user, latestLog,
                        ontologyService.recommendExercisesForGoal(goal).getItems());
                var habits = personalization.personalizeHabits(user, latestLog,
                        ontologyService.listHabitsForGoal(goal));
                var plans = ontologyService.listWorkoutPlansForGoal(goal);
                if (!meals.isEmpty()) items.add(meals.get(0));
                if (!exercises.isEmpty()) items.add(exercises.get(0));
                if (!habits.isEmpty()) items.add(habits.get(0));
                if (!plans.isEmpty()) items.add(plans.get(0));
                sparql = "generalCombinedQueries+UserMetrics";
            }
        }

        String profileSummary = adviceService.buildContextSummary(user, latestLog);
        String contextText = formatContextBlock(user, latestLog, intent, goal, items, profileSummary, metrics);

        return new OntologyContext(intent, items, sparql, contextText, profileSummary, metrics);
    }

    private String formatContextBlock(UserProfile user, DailyLog latestLog, ConsultationType intent,
                                      HealthGoal goal, List<OntologyRecommendation> items,
                                      String profileSummary, UserMetrics metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append(personalization.buildUserHeader(user, latestLog)).append("\n");
        sb.append(profileSummary).append("\n");
        sb.append("Detected intent: ").append(intent).append("\n");
        sb.append("Health goal: ").append(goal.getDisplayLabel()).append("\n");
        sb.append(String.format("Daily macro targets: P %dg | C %dg | F %dg | Meal target ~%d kcal\n",
                metrics.proteinGrams(), metrics.carbsGrams(), metrics.fatGrams(), metrics.targetMealCalories()));
        sb.append("Ontology-backed recommendations (ONLY use these facts — do not invent numbers):\n");
        if (items.isEmpty()) {
            sb.append("- No matching ontology items found.\n");
        } else {
            for (OntologyRecommendation item : items) {
                sb.append("- [").append(item.getType()).append("] ")
                        .append(item.getLabel());
                if (item.getDetails() != null && !item.getDetails().isBlank()) {
                    sb.append(" — ").append(item.getDetails());
                }
                sb.append(" (id: ").append(item.getId()).append(")\n");
            }
        }
        return sb.toString();
    }

    public String formatSources(List<OntologyRecommendation> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(i -> i.getLabel() + (i.getDetails() != null && !i.getDetails().isBlank()
                        ? " (" + i.getDetails() + ")" : ""))
                .collect(Collectors.joining("; "));
    }
}
