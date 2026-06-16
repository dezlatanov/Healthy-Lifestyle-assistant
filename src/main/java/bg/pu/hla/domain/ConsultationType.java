package bg.pu.hla.domain;

public enum ConsultationType {
    NUTRITION,
    FITNESS,
    HABITS,
    GENERAL,
    CHAT;

    public String getDisplayLabel() {
        return switch (this) {
            case NUTRITION -> "Хранене";
            case FITNESS -> "Тренировки";
            case HABITS -> "Навици";
            case GENERAL -> "Общ план";
            case CHAT -> "Чат";
        };
    }
}
