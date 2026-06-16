package bg.pu.hla.domain;

public enum ActivityLevel {
    SEDENTARY,
    MODERATE,
    ACTIVE;

    public String getDisplayLabel() {
        return switch (this) {
            case SEDENTARY -> "Ниска";
            case MODERATE -> "Умерена";
            case ACTIVE -> "Висока";
        };
    }
}
