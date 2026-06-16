package bg.pu.hla.domain;

public enum Gender {
    MALE,
    FEMALE,
    UNSPECIFIED;

    public String getDisplayLabel() {
        return switch (this) {
            case MALE -> "Мъж";
            case FEMALE -> "Жена";
            case UNSPECIFIED -> "Неуточнен";
        };
    }
}
