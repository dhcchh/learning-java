package sqlvalidator;

import java.util.ArrayList;
import java.util.List;

public class SqlValidator {

    private final List<SqlRule> rules;

    public SqlValidator(List<SqlRule> rules) {
        this.rules = List.copyOf(rules);
    }

    private String stripComments(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        StringBuilder cleaned = new StringBuilder(sql.length());
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    cleaned.append(current);
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                } else if (current == '\n' || current == '\r') {
                    cleaned.append(current);
                }
                continue;
            }

            if (!inDoubleQuote && current == '\'') {
                inSingleQuote = !inSingleQuote;
                cleaned.append(current);
                continue;
            }

            if (!inSingleQuote && current == '"') {
                inDoubleQuote = !inDoubleQuote;
                cleaned.append(current);
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && current == '-' && next == '-') {
                inLineComment = true;
                cleaned.append(' ');
                i++;
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && current == '/' && next == '*') {
                inBlockComment = true;
                cleaned.append(' ');
                i++;
                continue;
            }

            cleaned.append(current);
        }

        return cleaned.toString();
    }

    /*
     * Normalise raw SQL into a single canonical form before any rule sees it:
     *   1. strip -- line comments and block comments (see stripComments)
     *   2. collapse every run of whitespace - newlines, tabs, repeated spaces -
     *      to one space, so a multiline query is checked like a single-line one
     *   3. trim the leading/trailing space that steps 1-2 can leave behind
     */
    private String normalise(String sql) {
        return stripComments(sql)
                .replaceAll("\\s+", " ")
                .strip();
    }

    public ValidationResult validate(String sql) {
        if (sql == null) {
            return ValidationResult.of(List.of("NO_NULL"));
        }

        String normalised = normalise(sql);
        List<String> violations = new ArrayList<>();

        for (SqlRule rule : rules) {
            if (!rule.check(normalised)) {
                violations.add(rule.name());
            }
        }

        return ValidationResult.of(violations);
    }
}
