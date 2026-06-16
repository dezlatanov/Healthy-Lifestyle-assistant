package bg.pu.hla.service;

import org.springframework.stereotype.Service;

import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyQueryResult;
import bg.pu.hla.ontology.OntologyRecommendation;
import bg.pu.hla.ontology.OntologyService;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PersonalizedAdviceService {

    private final OntologyService ontologyService;
    private final UserPersonalizationService personalization;

    public PersonalizedAdviceService(OntologyService ontologyService,
                                       UserPersonalizationService personalization) {
        this.ontologyService = ontologyService;
        this.personalization = personalization;
    }

    public Map<String, Object> buildNutritionAdvice(UserProfile user, DailyLog latestLog,
                                                    String query, OntologyQueryResult result) {
        List<OntologyRecommendation> items = personalization.personalizeMeals(user,
                applyQueryFilters(result.getItems(), query, true));
        int totalCalories = sumMealCalories(items);

        List<String> tips = new ArrayList<>(buildLogTips(latestLog));
        tips.addAll(buildGoalTips(user.getGoal(), items, true));
        if (!query.isBlank() && items.size() < result.getItems().size()) {
            tips.add(0, "Filtered " + items.size() + " meal(s) matching your query.");
        }

        String response = formatNutritionText(user, query, items, totalCalories, tips);
        return buildPayload("nutrition", response, items, tips, user, latestLog, totalCalories);
    }

    public Map<String, Object> buildFitnessAdvice(UserProfile user, DailyLog latestLog,
                                                  String query, OntologyQueryResult result) {
        List<OntologyRecommendation> items = personalization.personalizeExercises(user,
                applyQueryFilters(result.getItems(), query, false));
        ActivityLevel activity = user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.MODERATE;

        List<String> tips = new ArrayList<>(buildLogTips(latestLog));
        tips.add("Suggested frequency: " + sessionsPerWeek(activity) + " sessions/week.");
        if (latestLog != null && latestLog.getSteps() != null && latestLog.getSteps() >= 8000) {
            tips.add("Good step count — add strength work to complement daily movement.");
        }

        String response = formatFitnessText(user, activity, query, items, tips);
        return buildPayload("fitness", response, items, tips, user, latestLog, 0);
    }

    public Map<String, Object> buildHabitsAdvice(UserProfile user, DailyLog latestLog,
                                                 String query, List<OntologyRecommendation> habits) {
        List<OntologyRecommendation> items = personalization.personalizeHabits(user,
                applyQueryFilters(habits, query, false));
        List<String> tips = new ArrayList<>(buildLogTips(latestLog));
        if (latestLog != null && latestLog.getSleepHours() != null && latestLog.getSleepHours() < 7) {
            tips.add("Prioritize the sleep habit — recovery supports all other goals.");
        }

        String response = formatHabitsText(user, query, items, tips);
        return buildPayload("coordinator", response, items, tips, user, latestLog, 0);
    }

    public Map<String, Object> buildGeneralAdvice(UserProfile user, DailyLog latestLog, String query) {
        HealthGoal goal = user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE;
        var meals = personalization.personalizeMeals(user,
                applyQueryFilters(ontologyService.recommendMealsForGoal(goal).getItems(), query, true));
        var exercises = personalization.personalizeExercises(user,
                applyQueryFilters(ontologyService.recommendExercisesForGoal(goal).getItems(), query, false));
        var habits = personalization.personalizeHabits(user,
                applyQueryFilters(ontologyService.listHabitsForGoal(goal), query, false));
        var plans = ontologyService.listWorkoutPlansForGoal(goal);

        List<OntologyRecommendation> items = new ArrayList<>();
        if (!meals.isEmpty()) items.add(meals.get(0));
        if (!exercises.isEmpty()) items.add(exercises.get(0));
        if (!habits.isEmpty()) items.add(habits.get(0));

        if (!plans.isEmpty()) items.add(plans.get(0));

        List<String> tips = new ArrayList<>(buildLogTips(latestLog));
        tips.add("GENERAL plan combines meal, exercise, habit and workout plan from ontology constraints.");

        String response = """
                Lifestyle Summary for %s
                Goal: %s | Activity: %s
                Top meal: %s
                Top exercise: %s
                Top habit: %s
                Workout plan: %s
                """.formatted(
                user.getUsername(),
                goal,
                user.getActivityLevel() != null ? user.getActivityLevel() : "MODERATE",
                meals.isEmpty() ? "none" : meals.get(0).getLabel(),
                exercises.isEmpty() ? "none" : exercises.get(0).getLabel(),
                habits.isEmpty() ? "none" : habits.get(0).getLabel(),
                plans.isEmpty() ? "none" : plans.get(0).getLabel()
        );

        return buildPayload("coordinator", response, items, tips, user, latestLog, sumMealCalories(meals));
    }

    private Map<String, Object> buildPayload(String agent, String response, List<OntologyRecommendation> items,
                                             List<String> tips, UserProfile user, DailyLog log, int calories) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", agent);
        payload.put("response", response);
        payload.put("contextSummary", buildContextSummary(user, log));
        payload.put("tips", tips);
        payload.put("recommendations", items.stream()
                .map(i -> Map.of(
                        "label", i.getLabel(),
                        "details", i.getDetails() != null ? i.getDetails() : "",
                        "type", i.getType() != null ? i.getType() : ""))
                .toList());
        if (calories > 0) {
            payload.put("estimatedCalories", calories);
        }
        return payload;
    }

    public String buildContextSummary(UserProfile user, DailyLog log) {
        StringBuilder sb = new StringBuilder(personalization.buildUserHeader(user)).append(" ");
        if (user.getWeightKg() != null && user.getHeightCm() != null && user.getHeightCm() > 0) {
            double bmi = user.getWeightKg() / Math.pow(user.getHeightCm() / 100.0, 2);
            sb.append(String.format("BMI %.1f. ", bmi));
        }
        if (user.getGoal() != null) {
            sb.append("Goal ").append(user.getGoal()).append(". ");
        }
        if (log != null) {
            sb.append(String.format("Latest log (%s): %d ml water, %d steps, %.1f h sleep.",
                    log.getLogDate(),
                    log.getWaterMl() != null ? log.getWaterMl() : 0,
                    log.getSteps() != null ? log.getSteps() : 0,
                    log.getSleepHours() != null ? log.getSleepHours() : 0.0));
        } else {
            sb.append("No daily log yet — agents use profile + ontology only.");
        }
        return sb.toString();
    }

    private List<String> buildLogTips(DailyLog log) {
        if (log == null) {
            return List.of("Log water, steps, and sleep to get more personalized tips.");
        }
        List<String> tips = new ArrayList<>();
        if (log.getWaterMl() != null && log.getWaterMl() < 1500) {
            tips.add("Water intake is low (" + log.getWaterMl() + " ml) — aim for at least 2000 ml.");
        }
        if (log.getSteps() != null && log.getSteps() < 6000) {
            tips.add("Steps are below 6000 — a 20-minute walk would help today.");
        }
        if (log.getSleepHours() != null && log.getSleepHours() < 7) {
            tips.add("Sleep is under 7 hours — recovery may affect energy and cravings.");
        }
        if (tips.isEmpty()) {
            tips.add("Daily log looks solid — keep consistent habits.");
        }
        return tips;
    }

    private List<String> buildGoalTips(HealthGoal goal, List<OntologyRecommendation> items, boolean nutrition) {
        if (!nutrition || goal == null) {
            return List.of();
        }
        return switch (goal) {
            case WEIGHT_LOSS -> List.of("Focus on lean meals with vegetables and controlled portions.");
            case MUSCLE_GAIN -> List.of("Prioritize protein across meals and post-workout nutrition.");
            case ENDURANCE -> List.of("Balance carbs and hydration around training days.");
            case MAINTENANCE -> List.of("Maintain variety — rotate meals to avoid diet fatigue.");
        };
    }

    private List<OntologyRecommendation> applyQueryFilters(List<OntologyRecommendation> items,
                                                             String query, boolean nutrition) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return new ArrayList<>(items);
        }

        String q = query.toLowerCase(Locale.ROOT);
        List<OntologyRecommendation> filtered = new ArrayList<>(items);

        if (nutrition && (q.contains("low calorie") || q.contains("light") || q.contains("лек"))) {
            filtered = items.stream()
                    .sorted(Comparator.comparingInt(this::parseCaloriesFromDetails))
                    .limit(Math.max(1, items.size() / 2 + 1))
                    .toList();
        }
        if (!nutrition && (q.contains("cardio") || q.contains("run") || q.contains("бяган"))) {
            filtered = items.stream()
                    .filter(i -> i.getDetails() != null && i.getDetails().toLowerCase(Locale.ROOT).contains("min"))
                    .toList();
        }

        String[] tokens = Arrays.stream(q.split("\\s+"))
                .filter(t -> t.length() > 2)
                .toArray(String[]::new);

        if (tokens.length > 0) {
            List<OntologyRecommendation> keywordMatches = filtered.stream()
                    .filter(i -> matchesKeywords(i, tokens))
                    .toList();
            if (!keywordMatches.isEmpty()) {
                filtered = keywordMatches;
            }
        }

        return filtered.isEmpty() ? new ArrayList<>(items) : filtered;
    }

    private boolean matchesKeywords(OntologyRecommendation item, String[] tokens) {
        String haystack = (item.getLabel() + " " + item.getDetails()).toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private int parseCaloriesFromDetails(OntologyRecommendation item) {
        if (item.getDetails() == null) {
            return Integer.MAX_VALUE;
        }
        String digits = item.getDetails().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private int sumMealCalories(List<OntologyRecommendation> meals) {
        return meals.stream().mapToInt(this::parseCaloriesFromDetails).filter(c -> c != Integer.MAX_VALUE).sum();
    }

    private int sessionsPerWeek(ActivityLevel activity) {
        return switch (activity) {
            case SEDENTARY -> 3;
            case MODERATE -> 4;
            case ACTIVE -> 5;
        };
    }

    private String formatNutritionText(UserProfile user, String query,
                                       List<OntologyRecommendation> items, int calories, List<String> tips) {
        String mealList = items.stream()
                .map(i -> i.getLabel() + (i.getDetails().isBlank() ? "" : " (" + i.getDetails() + ")"))
                .collect(Collectors.joining("; "));
        return """
                Nutrition Agent Analysis
                User: %s | Goal: %s
                Query focus: %s
                Recommended meals: %s
                Estimated calories (shown items): %d kcal
                Tips: %s
                """.formatted(
                user.getUsername(),
                user.getGoal(),
                query.isBlank() ? "general recommendation" : query,
                mealList.isBlank() ? "none matched" : mealList,
                calories == 0 ? sumMealCalories(items) : calories,
                String.join(" | ", tips));
    }

    private String formatFitnessText(UserProfile user, ActivityLevel activity, String query,
                                     List<OntologyRecommendation> items, List<String> tips) {
        String exerciseList = items.stream()
                .map(i -> i.getLabel() + " - " + i.getDetails())
                .collect(Collectors.joining("; "));
        return """
                Fitness Agent Plan
                User: %s | Goal: %s | Activity: %s
                Query focus: %s
                Recommended exercises: %s
                Tips: %s
                """.formatted(
                user.getUsername(),
                user.getGoal(),
                activity,
                query.isBlank() ? "general plan" : query,
                exerciseList.isBlank() ? "none matched" : exerciseList,
                String.join(" | ", tips));
    }

    private String formatHabitsText(UserProfile user, String query,
                                    List<OntologyRecommendation> items, List<String> tips) {
        String habitList = items.stream()
                .map(h -> "- " + h.getLabel() + " (" + h.getDetails() + ")")
                .collect(Collectors.joining("\n"));
        return """
                Habit Coach (Coordinator)
                User: %s | Goal: %s
                Query focus: %s
                Recommended habits:
                %s
                Tips: %s
                """.formatted(
                user.getUsername(),
                user.getGoal(),
                query.isBlank() ? "all habits" : query,
                habitList.isBlank() ? "No habits matched." : habitList,
                String.join(" | ", tips));
    }
}
