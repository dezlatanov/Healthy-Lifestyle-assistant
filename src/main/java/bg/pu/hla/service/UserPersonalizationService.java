package bg.pu.hla.service;

import org.springframework.stereotype.Service;

import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyRecommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class UserPersonalizationService {

    private final UserMetricsService metricsService;

    public UserPersonalizationService(UserMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public UserMetrics metricsFor(UserProfile user, DailyLog log) {
        return metricsService.compute(user, log);
    }

    public List<OntologyRecommendation> personalizeMeals(UserProfile user, DailyLog log,
                                                         List<OntologyRecommendation> meals) {
        if (meals == null || meals.isEmpty()) {
            return List.of();
        }
        UserMetrics m = metricsService.compute(user, log);
        HealthGoal goal = user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE;

        List<Scored> scored = new ArrayList<>();
        for (OntologyRecommendation meal : meals) {
            int s = metricsService.scoreMeal(meal, m, goal);
            scored.add(new Scored(meal, s));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());

        List<OntologyRecommendation> ranked = scored.stream().map(Scored::item).toList();
        return topN(rotateForUser(user, m, ranked), 6);
    }

    public List<OntologyRecommendation> personalizeExercises(UserProfile user, DailyLog log,
                                                             List<OntologyRecommendation> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return List.of();
        }
        UserMetrics m = metricsService.compute(user, log);

        List<Scored> scored = new ArrayList<>();
        for (OntologyRecommendation ex : exercises) {
            int s = metricsService.scoreExercise(ex, m, log);
            if (s > 0) {
                scored.add(new Scored(ex, s));
            }
        }
        if (scored.isEmpty()) {
            scored = exercises.stream().map(e -> new Scored(e, 1)).toList();
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());

        List<OntologyRecommendation> ranked = scored.stream().map(Scored::item).toList();
        return topN(rotateForUser(user, m, ranked), 6);
    }

    public List<OntologyRecommendation> personalizeHabits(UserProfile user, DailyLog log,
                                                          List<OntologyRecommendation> habits) {
        if (habits == null || habits.isEmpty()) {
            return List.of();
        }
        UserMetrics m = metricsService.compute(user, log);
        List<OntologyRecommendation> sorted = new ArrayList<>(habits);

        sorted.sort((a, b) -> Integer.compare(habitScore(b, m, log), habitScore(a, m, log)));
        return topN(rotateForUser(user, m, sorted), 6);
    }

    public String buildUserHeader(UserProfile user, DailyLog log) {
        UserMetrics m = metricsService.compute(user, log);
        StringBuilder sb = new StringBuilder();
        sb.append("User: ").append(user.getUsername());
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            sb.append(" (").append(user.getDisplayName()).append(")");
        }
        sb.append(" | Goal: ").append(user.getGoal() != null ? user.getGoal().getDisplayLabel() : "Поддържане");
        sb.append(" | Activity: ").append(user.getActivityLevel() != null
                ? user.getActivityLevel().getDisplayLabel() : "Умерена");
        if (user.getAge() != null) {
            sb.append(" | Age: ").append(user.getAge());
        }
        sb.append(String.format(" | BMI: %.1f (%s)", m.bmi(), m.bmiCategoryBg()));
        sb.append(String.format(" | Target: %d kcal/day", m.targetCalories()));
        return sb.toString();
    }

    private int habitScore(OntologyRecommendation habit, UserMetrics m, DailyLog log) {
        String label = habit.getLabel() != null ? habit.getLabel().toLowerCase() : "";
        int score = 10;
        if (label.contains("вода") || label.contains("water")) {
            if (log == null || log.getWaterMl() == null || log.getWaterMl() < m.waterTargetMl()) {
                score += 30;
            }
        }
        if (label.contains("сън") || label.contains("sleep")) {
            if (log == null || log.getSleepHours() == null || log.getSleepHours() < m.sleepTargetHours()) {
                score += 25;
            }
        }
        if (label.contains("стъп") || label.contains("walk")) {
            if (log == null || log.getSteps() == null || log.getSteps() < m.stepsTarget()) {
                score += 20;
            }
        }
        if (label.contains("протеин") && userGoalIsMuscle(habit)) {
            score += 15;
        }
        return score;
    }

    private boolean userGoalIsMuscle(OntologyRecommendation habit) {
        return habit.getDetails() != null;
    }

    private List<OntologyRecommendation> rotateForUser(UserProfile user, UserMetrics m,
                                                    List<OntologyRecommendation> items) {
        if (items.size() <= 1) {
            return items;
        }
        int seed = Objects.hash(
                user.getUsername(),
                user.getGoal(),
                user.getActivityLevel(),
                user.getAge(),
                user.getWeightKg() != null ? (int) (user.getWeightKg() * 10) : 0,
                (int) m.targetCalories()
        );
        int offset = Math.floorMod(seed, items.size());
        List<OntologyRecommendation> rotated = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            rotated.add(items.get((i + offset) % items.size()));
        }
        return rotated;
    }

    public List<OntologyRecommendation> topN(List<OntologyRecommendation> items, int max) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.subList(0, Math.min(max, items.size()));
    }

    private record Scored(OntologyRecommendation item, int score) {}
}
