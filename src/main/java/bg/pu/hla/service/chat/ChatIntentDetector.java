package bg.pu.hla.service.chat;

import bg.pu.hla.domain.ConsultationType;

import java.util.Locale;

public final class ChatIntentDetector {

    private ChatIntentDetector() {
    }

    public static ConsultationType detect(String message) {
        if (message == null || message.isBlank()) {
            return ConsultationType.GENERAL;
        }
        String q = message.toLowerCase(Locale.ROOT);
        if (containsAny(q, "тренир", "упражн", "фитнес", "cardio", "бяган", "workout", "exercise", "gym")) {
            return ConsultationType.FITNESS;
        }
        if (containsAny(q, "навик", "сън", "sleep", "вода", "water", "habit", "стъпк")) {
            return ConsultationType.HABITS;
        }
        if (containsAny(q, "хран", "ястие", "обяд", "вечер", "закус", "meal", "food", "calorie", "калори",
                "протеин", "protein", "nutrition", "диет")) {
            return ConsultationType.NUTRITION;
        }
        if (containsAny(q, "общ", "plan", "summary", "general", "lifestyle", "програма")) {
            return ConsultationType.GENERAL;
        }
        return ConsultationType.GENERAL;
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
