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
    private final UserMetricsService metricsService;

    public PersonalizedAdviceService(OntologyService ontologyService,
                                     UserPersonalizationService personalization,
                                     UserMetricsService metricsService) {
        this.ontologyService = ontologyService;
        this.personalization = personalization;
        this.metricsService = metricsService;
    }

    public Map<String, Object> buildNutritionAdvice(UserProfile user, DailyLog latestLog,
                                                    String query, OntologyQueryResult result) {
        UserMetrics m = metricsService.compute(user, latestLog);
        List<OntologyRecommendation> items = personalization.personalizeMeals(user, latestLog,
                applyQueryFilters(result.getItems(), query, true));

        List<String> tips = new ArrayList<>(buildMetricsTips(m, latestLog));
        tips.addAll(buildGoalTips(user.getGoal()));
        if (!query.isBlank() && items.size() < result.getItems().size()) {
            tips.add(0, "Филтрирани " + items.size() + " ястия според заявката.");
        }

        String response = formatNutritionText(user, m, query, items, tips);
        return buildPayload("nutrition", response, items, tips, user, latestLog, m);
    }

    public Map<String, Object> buildFitnessAdvice(UserProfile user, DailyLog latestLog,
                                                  String query, OntologyQueryResult result) {
        UserMetrics m = metricsService.compute(user, latestLog);
        List<OntologyRecommendation> items = personalization.personalizeExercises(user, latestLog,
                applyQueryFilters(result.getItems(), query, false));

        List<String> tips = new ArrayList<>(buildMetricsTips(m, latestLog));
        tips.add(String.format("Препоръчителна честота: %d тренировки/седмица.", m.trainingDaysPerWeek()));
        tips.add(String.format("Макс. интензивност за теб: %s (BMI + възраст + сън).", m.maxExerciseIntensity()));

        String response = formatFitnessText(user, m, query, items, tips);
        return buildPayload("fitness", response, items, tips, user, latestLog, m);
    }

    public Map<String, Object> buildHabitsAdvice(UserProfile user, DailyLog latestLog,
                                                 String query, List<OntologyRecommendation> habits) {
        UserMetrics m = metricsService.compute(user, latestLog);
        List<OntologyRecommendation> items = personalization.personalizeHabits(user, latestLog,
                applyQueryFilters(habits, query, false));
        List<String> tips = new ArrayList<>(buildMetricsTips(m, latestLog));

        String response = formatHabitsText(user, m, query, items, tips);
        return buildPayload("coordinator", response, items, tips, user, latestLog, m);
    }

    public Map<String, Object> buildGeneralAdvice(UserProfile user, DailyLog latestLog, String query) {
        HealthGoal goal = user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE;
        UserMetrics m = metricsService.compute(user, latestLog);

        var meals = personalization.personalizeMeals(user, latestLog,
                applyQueryFilters(ontologyService.recommendMealsForGoal(goal).getItems(), query, true));
        var exercises = personalization.personalizeExercises(user, latestLog,
                applyQueryFilters(ontologyService.recommendExercisesForGoal(goal).getItems(), query, false));
        var habits = personalization.personalizeHabits(user, latestLog,
                applyQueryFilters(ontologyService.listHabitsForGoal(goal), query, false));
        var plans = ontologyService.listWorkoutPlansForGoal(goal);

        List<OntologyRecommendation> items = new ArrayList<>();
        if (!meals.isEmpty()) items.add(meals.get(0));
        if (!exercises.isEmpty()) items.add(exercises.get(0));
        if (!habits.isEmpty()) items.add(habits.get(0));
        if (!plans.isEmpty()) items.add(plans.get(0));

        List<String> tips = new ArrayList<>(buildMetricsTips(m, latestLog));

        String response = """
                Персонален план за %s
                Цел: %s | BMI: %.1f (%s)
                Дневни калории: %d | Протеин: %dg
                Топ ястие: %s
                Топ упражнение: %s
                Топ навик: %s
                План: %s
                """.formatted(
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                goal.getDisplayLabel(), m.bmi(), m.bmiCategoryBg(),
                m.targetCalories(), m.proteinGrams(),
                meals.isEmpty() ? "—" : meals.get(0).getLabel(),
                exercises.isEmpty() ? "—" : exercises.get(0).getLabel(),
                habits.isEmpty() ? "—" : habits.get(0).getLabel(),
                plans.isEmpty() ? "—" : plans.get(0).getLabel()
        );

        return buildPayload("coordinator", response, items, tips, user, latestLog, m);
    }

    private Map<String, Object> buildPayload(String agent, String response, List<OntologyRecommendation> items,
                                             List<String> tips, UserProfile user, DailyLog log, UserMetrics m) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", agent);
        payload.put("response", response);
        payload.put("contextSummary", buildContextSummary(user, log));
        payload.put("tips", tips);
        payload.put("metrics", metricsToMap(m));
        payload.put("recommendations", items.stream()
                .map(i -> Map.of(
                        "label", i.getLabel(),
                        "details", i.getDetails() != null ? i.getDetails() : "",
                        "type", i.getType() != null ? i.getType() : ""))
                .toList());
        payload.put("targetCalories", m.targetCalories());
        return payload;
    }

    public Map<String, Object> metricsToMap(UserMetrics m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bmi", m.bmi());
        map.put("bmiCategory", m.bmiCategoryBg());
        map.put("targetCalories", m.targetCalories());
        map.put("proteinGrams", m.proteinGrams());
        map.put("carbsGrams", m.carbsGrams());
        map.put("fatGrams", m.fatGrams());
        map.put("waterTargetMl", m.waterTargetMl());
        map.put("stepsTarget", m.stepsTarget());
        map.put("trainingDaysPerWeek", m.trainingDaysPerWeek());
        return map;
    }

    public String buildContextSummary(UserProfile user, DailyLog log) {
        UserMetrics m = metricsService.compute(user, log);
        return metricsService.formatMetricsBlock(m, user, log);
    }

    private List<String> buildMetricsTips(UserMetrics m, DailyLog log) {
        List<String> tips = new ArrayList<>(m.reasoningNotes());
        if (log == null) {
            tips.add("Запиши дневник (вода, стъпки, сън) за още по-точни тренировъчни и хранителни корекции.");
            return tips;
        }
        if (log.getWaterMl() != null && log.getWaterMl() < m.waterTargetMl() * 0.8) {
            tips.add(String.format("Вода: %d ml — цел %d ml/ден.", log.getWaterMl(), m.waterTargetMl()));
        }
        if (log.getSteps() != null && log.getSteps() < m.stepsTarget()) {
            tips.add(String.format("Стъпки: %d — цел %d/ден.", log.getSteps(), m.stepsTarget()));
        }
        if (log.getSleepHours() != null && log.getSleepHours() < m.sleepTargetHours()) {
            tips.add(String.format("Сън: %.1f h — цел %.1f h за оптимално възстановяване.", log.getSleepHours(), m.sleepTargetHours()));
        }
        return tips;
    }

    private List<String> buildGoalTips(HealthGoal goal) {
        if (goal == null) return List.of();
        return switch (goal) {
            case WEIGHT_LOSS -> List.of("Фокус: калorie deficit с висок протеин — запазва мускулите при сваляне.");
            case MUSCLE_GAIN -> List.of("Фокус: калorie surplus + 1.8–2g протеин/kg — хранене около тренировка.");
            case ENDURANCE -> List.of("Фокус: въглехидрати около тренировка + хидратация.");
            case MAINTENANCE -> List.of("Фокус: баланс и разнообразие — устойчиви навици.");
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

    private String formatNutritionText(UserProfile user, UserMetrics m, String query,
                                       List<OntologyRecommendation> items, List<String> tips) {
        String mealList = items.stream()
                .map(i -> i.getLabel() + (i.getDetails() == null || i.getDetails().isBlank() ? "" : " (" + i.getDetails() + ")"))
                .collect(Collectors.joining("; "));
        return """
                План за хранене — %s
                BMI %.1f (%s) | Дневна цел: %d kcal | Протеин: %dg
                Цел на основно ястие: ~%d kcal
                Препоръчани ястия: %s
                Съвети: %s
                """.formatted(
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                m.bmi(), m.bmiCategoryBg(), m.targetCalories(), m.proteinGrams(), m.targetMealCalories(),
                mealList.isBlank() ? "няма съвпадения" : mealList,
                String.join(" | ", tips));
    }

    private String formatFitnessText(UserProfile user, UserMetrics m, String query,
                                     List<OntologyRecommendation> items, List<String> tips) {
        String exerciseList = items.stream()
                .map(i -> i.getLabel() + " — " + i.getDetails())
                .collect(Collectors.joining("; "));
        return """
                Тренировъчен план — %s
                BMI %.1f | Възраст %d (%s) | %d тренировки/седм.
                Макс. интензивност: %s
                Упражнения: %s
                Съвети: %s
                """.formatted(
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                m.bmi(), m.age(), m.ageGroupBg(), m.trainingDaysPerWeek(), m.maxExerciseIntensity(),
                exerciseList.isBlank() ? "няма съвпадения" : exerciseList,
                String.join(" | ", tips));
    }

    private String formatHabitsText(UserProfile user, UserMetrics m, String query,
                                    List<OntologyRecommendation> items, List<String> tips) {
        String habitList = items.stream()
                .map(h -> "• " + h.getLabel() + " (" + h.getDetails() + ")")
                .collect(Collectors.joining("\n"));
        return """
                Навици — %s
                BMI %.1f | Вода цел: %d ml | Стъпки цел: %d
                %s
                Съвети: %s
                """.formatted(
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                m.bmi(), m.waterTargetMl(), m.stepsTarget(),
                habitList.isBlank() ? "Няма намерени навици." : habitList,
                String.join(" | ", tips));
    }
}
