package bg.pu.hla.service;

import org.springframework.stereotype.Service;

import bg.pu.hla.domain.*;
import bg.pu.hla.ontology.OntologyRecommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserMetricsService {

    public UserMetrics compute(UserProfile user, DailyLog log) {
        int age = user.getAge() != null ? user.getAge() : 30;
        double weight = user.getWeightKg() != null ? user.getWeightKg() : 70.0;
        double heightCm = user.getHeightCm() != null ? user.getHeightCm() : 170.0;
        HealthGoal goal = user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE;
        ActivityLevel activity = user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.MODERATE;
        Gender gender = user.getGender() != null ? user.getGender() : Gender.UNSPECIFIED;

        double heightM = heightCm / 100.0;
        double bmi = heightM > 0 ? weight / (heightM * heightM) : 0;
        String bmiCategory = categorizeBmi(bmi);
        String ageGroup = categorizeAge(age);

        double bmr = mifflinStJeor(weight, heightCm, age, gender);
        double activityFactor = activityFactor(activity);
        double tdee = bmr * activityFactor;
        int targetCalories = targetCalories(tdee, goal, bmi);
        int protein = proteinTarget(weight, goal);
        int fat = (int) Math.round(targetCalories * 0.25 / 9.0);
        int carbs = Math.max(0, targetCalories - protein * 4 - fat * 9) / 4;
        int mealCalories = targetCalories / (goal == HealthGoal.MUSCLE_GAIN ? 4 : 3);
        int water = waterTarget(weight, log);
        int steps = stepsTarget(goal, activity, log);
        double sleep = sleepTarget(age, log);
        String maxIntensity = maxIntensity(age, bmi, log);
        int trainingDays = trainingDays(goal, activity, age);

        List<String> notes = buildReasoningNotes(user, log, bmi, bmiCategory, age, targetCalories, protein, maxIntensity);

        return new UserMetrics(
                round1(bmi), bmiCategory, ageGroup, age,
                round0(bmr), round0(tdee), targetCalories,
                protein, carbs, fat, mealCalories,
                water, steps, sleep, maxIntensity, trainingDays, notes
        );
    }

    public int scoreMeal(OntologyRecommendation meal, UserMetrics m, HealthGoal goal) {
        int mealCal = parseCalories(meal.getDetails());
        int score = 50;

        if (mealCal <= 0) {
            return score;
        }

        int diff = Math.abs(mealCal - m.targetMealCalories());
        score += Math.max(0, 30 - diff / 15);

        score += switch (goal) {
            case WEIGHT_LOSS -> mealCal <= m.targetMealCalories() ? 25 : Math.max(0, 15 - (mealCal - m.targetMealCalories()) / 20);
            case MUSCLE_GAIN -> mealCal >= m.targetMealCalories() * 0.85 ? 20 : 5;
            case ENDURANCE -> mealCal >= 350 && mealCal <= 650 ? 20 : 10;
            case MAINTENANCE -> 15;
        };

        if (m.bmi() >= 30 && goal == HealthGoal.WEIGHT_LOSS && mealCal > m.targetMealCalories() * 1.2) {
            score -= 25;
        }
        if (m.bmi() < 18.5 && goal == HealthGoal.MUSCLE_GAIN && mealCal < 400) {
            score -= 10;
        }
        if (meal.getDetails() != null && meal.getDetails().toLowerCase(Locale.ROOT).contains("protein") && goal == HealthGoal.MUSCLE_GAIN) {
            score += 10;
        }
        return score;
    }

    public int scoreExercise(OntologyRecommendation exercise, UserMetrics m, DailyLog log) {
        String details = exercise.getDetails() != null ? exercise.getDetails().toLowerCase(Locale.ROOT) : "";
        String intensity = extractIntensity(details);
        int score = 40;

        score += switch (intensity) {
            case "high" -> m.allowsHighIntensity() ? 25 : -30;
            case "moderate" -> m.allowsModerateIntensity() ? 20 : -10;
            case "low" -> 15;
            default -> 10;
        };

        if (m.age() >= 55 && "high".equals(intensity)) {
            score -= 20;
        }
        if (log != null) {
            if (log.getSleepHours() != null && log.getSleepHours() < 6.5 && "high".equals(intensity)) {
                score -= 25;
            }
            if (log.getSteps() != null && log.getSteps() < 5000 && details.contains("walk")) {
                score += 20;
            }
            if (log.getSteps() != null && log.getSteps() >= 9000 && details.contains("strength")) {
                score += 15;
            }
        }

        ActivityLevel activity = m.effectiveActivity();
        score += switch (activity) {
            case SEDENTARY -> "low".equals(intensity) ? 15 : 0;
            case ACTIVE -> "high".equals(intensity) ? 15 : 5;
            case MODERATE -> "moderate".equals(intensity) ? 12 : 5;
        };
        return score;
    }

    public String formatMetricsBlock(UserMetrics m, UserProfile user, DailyLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Персонален метаболитен профил (диетолог + тренер) ===\n");
        sb.append(String.format("BMI: %.1f (%s) | Възраст: %d (%s)\n", m.bmi(), m.bmiCategoryBg(), m.age(), m.ageGroupBg()));
        sb.append(String.format("BMR: %.0f kcal | TDEE: %.0f kcal | Дневна цел: %d kcal\n",
                m.bmr(), m.tdee(), m.targetCalories()));
        sb.append(String.format("Макроси: протеин %dg | въглехидрати %dg | мазнини %dg\n",
                m.proteinGrams(), m.carbsGrams(), m.fatGrams()));
        sb.append(String.format("Цел на хранене: ~%d kcal/основно ястие | Вода: %d ml | Стъпки: %d | Сън: %.1fh\n",
                m.targetMealCalories(), m.waterTargetMl(), m.stepsTarget(), m.sleepTargetHours()));
        sb.append(String.format("Тренировки: %d дни/седм. | Макс. интензивност: %s\n",
                m.trainingDaysPerWeek(), m.maxExerciseIntensity()));
        if (log != null) {
            sb.append(String.format("Последен дневник: вода %d ml, стъпки %d, сън %.1f h\n",
                    nz(log.getWaterMl()), nz(log.getSteps()), log.getSleepHours() != null ? log.getSleepHours() : 0));
        }
        if (!m.reasoningNotes().isEmpty()) {
            sb.append("Reasoning:\n");
            m.reasoningNotes().forEach(n -> sb.append("- ").append(n).append("\n"));
        }
        return sb.toString();
    }

    private List<String> buildReasoningNotes(UserProfile user, DailyLog log,
                                              double bmi, String bmiCat, int age,
                                              int targetCal, int protein, String intensity) {
        List<String> notes = new ArrayList<>();
        HealthGoal goal = user.getGoal() != null ? user.getGoal() : HealthGoal.MAINTENANCE;

        notes.add(String.format("BMI %.1f → категория „%s“ — калориите са настроени към цел %s.",
                bmi, bmiCat, goal.getDisplayLabel()));

        if (age >= 50) {
            notes.add("Възраст 50+ → предпочитание към умерена/ниска интензивност и акцент върху възстановяване.");
        } else if (age <= 25) {
            notes.add("Млада възраст → по-висок TDEE; допустими по-интензивни тренировки при добър сън.");
        }

        notes.add(String.format("Дневен протеин ~%dg (≈ %.1fg/kg) за поддръжка на мускулна маса.", protein,
                user.getWeightKg() != null ? protein / user.getWeightKg() : 0));

        if (log != null) {
            if (log.getWaterMl() != null && log.getWaterMl() < waterTarget(user.getWeightKg() != null ? user.getWeightKg() : 70, log) * 0.75) {
                notes.add("Хидратация под целта → приоритет на вода пред интензивно кардио.");
            }
            if (log.getSleepHours() != null && log.getSleepHours() < 7) {
                notes.add("Недостатъчен сън → намалена препоръка за високо интензивни натоварвания.");
            }
            if (log.getSteps() != null && log.getSteps() < 6000) {
                notes.add("Ниски стъпки → акцент на ежедневно движение и NEAT.");
            }
        }
        return notes;
    }

    private double mifflinStJeor(double weightKg, double heightCm, int age, Gender gender) {
        double base = 10 * weightKg + 6.25 * heightCm - 5 * age;
        return switch (gender) {
            case MALE -> base + 5;
            case FEMALE -> base - 161;
            case UNSPECIFIED -> base - 78;
        };
    }

    private double activityFactor(ActivityLevel level) {
        return switch (level) {
            case SEDENTARY -> 1.2;
            case MODERATE -> 1.55;
            case ACTIVE -> 1.725;
        };
    }

    private int targetCalories(double tdee, HealthGoal goal, double bmi) {
        int base = (int) Math.round(tdee);
        return switch (goal) {
            case WEIGHT_LOSS -> (int) Math.round(tdee * (bmi >= 30 ? 0.75 : 0.82));
            case MUSCLE_GAIN -> (int) Math.round(tdee * 1.12);
            case ENDURANCE -> (int) Math.round(tdee * 1.08);
            case MAINTENANCE -> base;
        };
    }

    private int proteinTarget(double weightKg, HealthGoal goal) {
        double factor = switch (goal) {
            case MUSCLE_GAIN -> 2.0;
            case WEIGHT_LOSS -> 1.8;
            case ENDURANCE -> 1.6;
            case MAINTENANCE -> 1.4;
        };
        return (int) Math.round(weightKg * factor);
    }

    private int waterTarget(double weightKg, DailyLog log) {
        int base = (int) Math.round(weightKg * 35);
        if (log != null && log.getWaterMl() != null && log.getWaterMl() > base) {
            return log.getWaterMl();
        }
        return Math.max(2000, base);
    }

    private int stepsTarget(HealthGoal goal, ActivityLevel activity, DailyLog log) {
        int base = switch (goal) {
            case WEIGHT_LOSS -> 9000;
            case ENDURANCE -> 10000;
            case MUSCLE_GAIN -> 7500;
            case MAINTENANCE -> 8000;
        };
        if (activity == ActivityLevel.SEDENTARY) {
            base = Math.min(base, 8000);
        }
        return base;
    }

    private double sleepTarget(int age, DailyLog log) {
        double base = age >= 50 ? 7.5 : 8.0;
        if (log != null && log.getSleepHours() != null && log.getSleepHours() < 7) {
            return 8.0;
        }
        return base;
    }

    private String maxIntensity(int age, double bmi, DailyLog log) {
        if (age >= 60 || bmi >= 35) {
            return "low";
        }
        if (age >= 50 || bmi >= 30) {
            return "moderate";
        }
        if (log != null && log.getSleepHours() != null && log.getSleepHours() < 6) {
            return "moderate";
        }
        return "high";
    }

    private int trainingDays(HealthGoal goal, ActivityLevel activity, int age) {
        int base = switch (goal) {
            case MUSCLE_GAIN -> 4;
            case WEIGHT_LOSS -> 4;
            case ENDURANCE -> 5;
            case MAINTENANCE -> 3;
        };
        if (activity == ActivityLevel.ACTIVE) {
            base = Math.min(6, base + 1);
        }
        if (age >= 55) {
            base = Math.max(3, base - 1);
        }
        return base;
    }

    private String categorizeBmi(double bmi) {
        if (bmi <= 0) return "неуточнен";
        if (bmi < 18.5) return "поднормено тегло";
        if (bmi < 25) return "нормално тегло";
        if (bmi < 30) return "наднормено тегло";
        return "затлъстяване";
    }

    private String categorizeAge(int age) {
        if (age < 25) return "млад възрастен (18–24)";
        if (age < 40) return "възрастен (25–39)";
        if (age < 55) return "зрял (40–54)";
        return "55+ (акцент върху възстановяване)";
    }

    private int parseCalories(String details) {
        if (details == null) return 0;
        String digits = details.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractIntensity(String details) {
        if (details.contains("high")) return "high";
        if (details.contains("low")) return "low";
        if (details.contains("moderate")) return "moderate";
        return "moderate";
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }

    private double round0(double v) {
        return Math.round(v);
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
