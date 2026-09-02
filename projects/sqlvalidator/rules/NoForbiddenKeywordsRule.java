package projects.sqlvalidator.rules;

import java.util.List;
import java.util.Set;

import projects.sqlvalidator.SqlRule;
import projects.sqlvalidator.SqlTokenizer;

public class NoForbiddenKeywordsRule implements SqlRule {
    public String name() {
        return "NO_FORBIDDEN_KEYWORDS";
    }

    private static final Set<String> FORBIDDEN = Set.of("DROP", "DELETE", "UPDATE");

    public boolean check(String sql) {
        List<String> tokens = SqlTokenizer.tokenize(sql);
        for (String token : tokens) {
            if (FORBIDDEN.contains(token)) {
                return false;
            }
        }
        return true;
    }
}