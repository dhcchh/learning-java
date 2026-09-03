package sqlvalidator.rules;

import sqlvalidator.SqlRule;

public class NotEmptyRule implements SqlRule {
    public String name() { return "NOT_EMPTY"; }
    public boolean check(String sql) {
        return sql != null && !sql.isBlank();
    }
}

// isEmpty - true only if length is 0, literal ""
// isBlank - true if empty or every character is whitespace