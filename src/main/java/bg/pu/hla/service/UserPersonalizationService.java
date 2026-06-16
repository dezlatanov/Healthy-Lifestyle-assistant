package bg.pu.hla.service;

import org.springframework.stereotype.Service;

import bg.pu.hla.domain.ActivityLevel;
import bg.pu.hla.domain.HealthGoal;
import bg.pu.hla.domain.UserProfile;
import bg.pu.hla.ontology.OntologyRecommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class UserPersonalizationService {

    public List<OntologyRecommendation> personalizeMeals(UserProfile user, List<OntologyRecommendation> meals) {
        if (meals == null || meals.isEmpty()) {
            return List.of();
        }
        List<OntologyRecommendation> sorted = new ArrayList<>(meals);
        HealthGoal goal = user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE;

        sorted.sort(switch (goal) {
            case WEIGHT_LOSS -> Comparator.comparingInt(this::caloriesFromDetails);
            case MUSCLE_GAIN -> Comparator.comparingInt(this::caloriesFromDetails).reversed();
            default -> Comparator.comparing(OntologyRecommendation::getLabel);
        });

        return topN(rotateForUser(user, sorted), 6);
    }

    public List<OntologyRecommendation> personalizeExercises(UserProfile user, List<OntologyRecommendation> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return List.of();
        }
        ActivityLevel activity = user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.MODERATE;
        List<OntologyRecommendation> sorted = new ArrayList<>(exercises);

        sorted.sort((a, b) -> {
            int scoreA = exerciseScore(a, activity);
            int scoreB = exerciseScore(b, activity);
            return Integer.compare(scoreB, scoreA);
        });

        return topN(rotateForUser(user, sorted), 6);
    }

    public List<OntologyRecommendation> personalizeHabits(UserProfile user, List<OntologyRecommendation> habits) {
        if (habits == null || habits.isEmpty()) {
            return List.of();
        }
        return topN(rotateForUser(user, new ArrayList<>(habits)), 6);
    }

    public String buildUserHeader(UserProfile user) {
        StringBuilder sb = new StringBuilder();
        sb.append("User: ").append(user.getUsername());
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            sb.append(" (").append(user.getDisplayName()).append(")");
        }
        sb.append(" | Goal: ").append(user.getGoal() != null ? user.getGoal() : "MAINTENANCE");
        sb.append(" | Activity: ").append(user.getActivityLevel() != null ? user.getActivityLevel() : "MODERATE");
        if (user.getAge() != null) {
            sb.append(" | Age: ").append(user.getAge());
        }
        if (user.getWeightKg() != null && user.getHeightCm() != null && user.getHeightCm() > 0) {
            double bmi = user.getWeightKg() / Math.pow(user.getHeightCm() / 100.0, 2);
            sb.append(String.format(" | BMI: %.1f", bmi));
        }
        return sb.toString();
    }

    private List<OntologyRecommendation> rotateForUser(UserProfile user, List<OntologyRecommendation> items) {
        if (items.size() <= 1) {
            return items;
        }
        int seed = Objects.hash(
                user.getUsername(),
                user.getGoal(),
                user.getActivityLevel(),
                user.getAge(),
                user.getWeightKg() != null ? (int) (user.getWeightKg() * 10) : 0
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

    private int caloriesFromDetails(OntologyRecommendation item) {
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

    private int exerciseScore(OntologyRecommendation item, ActivityLevel activity) {
        String details = item.getDetails() != null ? item.getDetails().toLowerCase() : "";
        boolean high = details.contains("high");
        boolean low = details.contains("low");
        return switch (activity) {
            case SEDENTARY -> low ? 3 : (high ? 0 : 2);
            case ACTIVE -> high ? 3 : (low ? 1 : 2);
            case MODERATE -> 2;
        };
    }
}
