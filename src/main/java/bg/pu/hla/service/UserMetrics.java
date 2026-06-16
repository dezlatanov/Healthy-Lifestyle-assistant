package bg.pu.hla.service;

import bg.pu.hla.domain.ActivityLevel;
import bg.pu.hla.domain.HealthGoal;

import java.util.List;

public record UserMetrics(
        double bmi,
        String bmiCategoryBg,
        String ageGroupBg,
        int age,
        double bmr,
        double tdee,
        int targetCalories,
        int proteinGrams,
        int carbsGrams,
        int fatGrams,
        int targetMealCalories,
        int waterTargetMl,
        int stepsTarget,
        double sleepTargetHours,
        String maxExerciseIntensity,
        int trainingDaysPerWeek,
        List<String> reasoningNotes
) {
    public boolean allowsHighIntensity() {
        return "high".equalsIgnoreCase(maxExerciseIntensity);
    }

    public boolean allowsModerateIntensity() {
        return allowsHighIntensity() || "moderate".equalsIgnoreCase(maxExerciseIntensity);
    }

    public ActivityLevel effectiveActivity() {
        return switch (maxExerciseIntensity.toLowerCase()) {
            case "high" -> ActivityLevel.ACTIVE;
            case "moderate" -> ActivityLevel.MODERATE;
            default -> ActivityLevel.SEDENTARY;
        };
    }
}
