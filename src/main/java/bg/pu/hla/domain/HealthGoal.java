package bg.pu.hla.domain;

public enum HealthGoal {
    WEIGHT_LOSS,
    MUSCLE_GAIN,
    MAINTENANCE,
    ENDURANCE;

    public String getDisplayLabel() {
        return switch (this) {
            case WEIGHT_LOSS -> "Отслабване";
            case MUSCLE_GAIN -> "Мускулна маса";
            case MAINTENANCE -> "Поддържане";
            case ENDURANCE -> "Издръжливост";
        };
    }
}
