package projects.sqlvalidator.rules;

import projects.sqlvalidator.SqlRule;
import projects.sqlvalidator.SqlTokenizer;

import java.util.List;

public class HasLimitRule implements SqlRule {
    public String name() { return "HAS_LIMIT"; }

    public boolean check(String sql) {
        List<String> tokens = SqlTokenizer.tokenize(sql);
        for (String token : tokens) {
            if ( token.equalsIgnoreCase("LIMIT")) {
                return true;
            }
        }
        return false;
    }
}