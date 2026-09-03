package sqlvalidator;

import java.util.List;

public final class ValidationResult {

    private final List<String> violations;

    private ValidationResult(List<String> violations) {
        this.violations = List.copyOf(violations);
    }

    public static ValidationResult of(List<String> violations) {
        return new ValidationResult(violations);
    }

    public List<String> violations() {
        return violations;
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
