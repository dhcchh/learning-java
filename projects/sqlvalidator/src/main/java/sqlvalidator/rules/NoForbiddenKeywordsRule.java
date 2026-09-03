package sqlvalidator.rules;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import sqlvalidator.SqlRule;
import sqlvalidator.SqlTokenizer;

public class NoForbiddenKeywordsRule implements SqlRule {
    public String name() {
        return "NO_FORBIDDEN_KEYWORDS";
    }

    private static final Set<String> FORBIDDEN = Set.of("DROP", "DELETE", "UPDATE");

    public boolean check(String sql) {
        List<String> tokens = SqlTokenizer.tokenize(sql);
        for (String token : tokens) {
            // Match case-insensitively: `drop` is as dangerous as `DROP`.
            // Locale.ROOT keeps the upper-casing independent of the default locale.
            if (FORBIDDEN.contains(token.toUpperCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }
}