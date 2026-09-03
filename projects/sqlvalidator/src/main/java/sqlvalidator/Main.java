package sqlvalidator;

import sqlvalidator.rules.HasLimitRule;
import sqlvalidator.rules.NoForbiddenKeywordsRule;
import sqlvalidator.rules.NotEmptyRule;
import sqlvalidator.rules.StartsWithSelectRule;

import java.util.List;

/**
 * Tiny demo of the validator in use. Correctness is covered by the JUnit tests
 * in src/test/java (run with `mvn test`); this class is just a hand-runnable
 * example: `mvn -q compile exec:java` or run it from the IDE.
 */
public class Main {

    private static final SqlValidator VALIDATOR = new SqlValidator(List.of(
            new NotEmptyRule(),
            new StartsWithSelectRule(),
            new HasLimitRule(),
            new NoForbiddenKeywordsRule()
    ));

    public static void main(String[] args) {
        show("SELECT name FROM users LIMIT 10");
        show("SELECT name FROM users");
        show("select id\n  from orders\n  limit 50   -- capped");
        show("SELECT * FROM users; DROP TABLE users");
    }

    private static void show(String sql) {
        ValidationResult result = VALIDATOR.validate(sql);
        System.out.println(sql);
        if (result.isValid()) {
            System.out.println("  -> valid");
        } else {
            System.out.println("  -> invalid: " + result.violations());
        }
        System.out.println();
    }
}
