package sqlvalidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import sqlvalidator.rules.HasLimitRule;
import sqlvalidator.rules.NoForbiddenKeywordsRule;
import sqlvalidator.rules.NotEmptyRule;
import sqlvalidator.rules.StartsWithSelectRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SqlValidator} and the built-in {@link SqlRule}s.
 *
 * <p>This is what "testing" looks like in a real Java project: each case is a
 * method (or one row of a parameterized method), it states its own expectation
 * with an {@code assert*} call, and {@code mvn test} fails the build if any
 * expectation is not met. Nobody reads the output by eye.
 *
 * <p>JUnit 5 features shown here: {@code @Test}, {@code @BeforeEach},
 * {@code @DisplayName}, {@code @Nested} groups, {@code @ParameterizedTest} with
 * {@code @ValueSource} / {@code @NullAndEmptySource} / {@code @MethodSource},
 * {@code assertAll}, and {@code assertThrows}.
 */
@DisplayName("SqlValidator")
class SqlValidatorTest {

    /** The standard rule set, rebuilt fresh before every test. */
    private SqlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SqlValidator(List.of(
                new NotEmptyRule(),
                new StartsWithSelectRule(),
                new HasLimitRule(),
                new NoForbiddenKeywordsRule()
        ));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void assertValid(String sql) {
        ValidationResult result = validator.validate(sql);
        assertAll(
                () -> assertTrue(result.isValid(),
                        () -> "expected [" + sql + "] to be valid, but got " + result.violations()),
                () -> assertEquals(List.of(), result.violations()));
    }

    private void assertViolations(String sql, String... expected) {
        ValidationResult result = validator.validate(sql);
        assertAll(
                () -> assertFalse(result.isValid(), () -> "expected [" + sql + "] to be invalid"),
                () -> assertEquals(Set.of(expected), Set.copyOf(result.violations()),
                        () -> "wrong violations for [" + sql + "]"));
    }

    // ------------------------------------------------------------------
    // null / empty / blank
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("null, empty and blank input")
    class NullEmptyBlank {

        @Test
        @DisplayName("null is rejected with NO_NULL and nothing else")
        void nullInput() {
            ValidationResult result = validator.validate(null);
            assertAll(
                    () -> assertFalse(result.isValid()),
                    () -> assertEquals(List.of("NO_NULL"), result.violations()));
        }

        @ParameterizedTest(name = "[{0}] fails the emptiness-dependent rules")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\t", "\n", "  \n\t "})
        @DisplayName("blank input fails NOT_EMPTY, STARTS_WITH_SELECT and HAS_LIMIT")
        void blankInput(String sql) {
            if (sql == null) {
                assertViolations(null, "NO_NULL");
            } else {
                assertViolations(sql, "NOT_EMPTY", "STARTS_WITH_SELECT", "HAS_LIMIT");
            }
        }
    }

    // ------------------------------------------------------------------
    // queries that should pass every rule
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "valid: {0}")
    @MethodSource("validQueries")
    @DisplayName("well-formed read-only queries are accepted")
    void acceptsValidQueries(String sql) {
        assertValid(sql);
    }

    static Stream<String> validQueries() {
        return Stream.of(
                "SELECT name FROM users LIMIT 10",
                "select name from users limit 10",                       // all lower case
                "SeLeCt name FROM users LiMiT 5",                        // mixed case
                "SELECT 1 LIMIT 1",                                      // minimal
                "   SELECT name FROM users LIMIT 10   ",                 // surrounding whitespace
                "\tSELECT name FROM users LIMIT 10\n",                   // leading tab / trailing newline
                "SELECT name\n  FROM users\n  LIMIT 10",                 // multi-line
                "SELECT   name   FROM   users   LIMIT   10",             // repeated spaces
                "SELECT name FROM users LIMIT 10 -- trailing comment",   // line comment
                "SELECT name FROM users LIMIT 10 -- TODO: DROP this cap", // keyword only in a comment
                "SELECT name /* the display name */ FROM users LIMIT 10", // block comment between clauses
                "SELECT name FROM users LIMIT/* ten */10",               // block comment wedged into a token
                "SELECT name FROM users /* multi\nline\ncomment */ LIMIT 10",
                "SELECT a /* c1 */, b /* c2 */ FROM t LIMIT 1",          // several block comments
                "SELECT note FROM t WHERE note = '-- not a comment' LIMIT 1", // marker inside a string literal
                "SELECT note FROM t WHERE note = '/* also not a comment */' LIMIT 1",
                "SELECT dropped, updated, deleted FROM audit LIMIT 100"  // forbidden words as substrings, not tokens
        );
    }

    // ------------------------------------------------------------------
    // queries that should be rejected, with the exact violation set
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "invalid: {0} -> {1}")
    @MethodSource("invalidQueries")
    @DisplayName("malformed or unsafe queries are rejected with the right violations")
    void rejectsInvalidQueries(String sql, Set<String> expected) {
        ValidationResult result = validator.validate(sql);
        assertAll(
                () -> assertFalse(result.isValid()),
                () -> assertEquals(expected, Set.copyOf(result.violations())));
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of("SELECT name FROM users",
                        Set.of("HAS_LIMIT")),
                Arguments.of("SELECT nolimit FROM users",
                        Set.of("HAS_LIMIT")),                            // 'nolimit' is not the LIMIT keyword
                Arguments.of("SELECT name FROM users LIMIT 10; DROP TABLE users",
                        Set.of("NO_FORBIDDEN_KEYWORDS")),
                Arguments.of("select name from users limit 10; drop table users",
                        Set.of("NO_FORBIDDEN_KEYWORDS")),                // lower-case 'drop' still caught
                Arguments.of("SELECT name FROM users DROP TABLE users",
                        Set.of("HAS_LIMIT", "NO_FORBIDDEN_KEYWORDS")),
                Arguments.of("DELETE FROM users WHERE id = 1",
                        Set.of("STARTS_WITH_SELECT", "HAS_LIMIT", "NO_FORBIDDEN_KEYWORDS")),
                Arguments.of("UPDATE users SET name = 'x' LIMIT 1",
                        Set.of("STARTS_WITH_SELECT", "NO_FORBIDDEN_KEYWORDS")),
                Arguments.of("SELECTED name FROM t LIMIT 1",
                        Set.of("STARTS_WITH_SELECT")),                   // word boundary after SELECT
                Arguments.of("WITH cte AS (SELECT 1) SELECT * FROM cte LIMIT 1",
                        Set.of("STARTS_WITH_SELECT")),                   // CTE prefix not supported
                Arguments.of("EXPLAIN SELECT * FROM t LIMIT 1",
                        Set.of("STARTS_WITH_SELECT")),
                Arguments.of("SELECT * FROM t -- LIMIT 100",
                        Set.of("HAS_LIMIT")),                            // LIMIT only inside the comment
                Arguments.of("SELECT * FROM t /* LIMIT 100 */",
                        Set.of("HAS_LIMIT")));
    }

    // ------------------------------------------------------------------
    // individual rules
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("STARTS_WITH_SELECT")
    class StartsWithSelect {

        @Test
        @DisplayName("a keyword that merely begins with 'select' does not count")
        void requiresWordBoundary() {
            assertViolations("SELECTION FROM t LIMIT 1", "STARTS_WITH_SELECT");
        }

        @Test
        @DisplayName("leading whitespace and comments are normalised away first")
        void toleratesLeadingNoise() {
            assertValid("   SELECT id FROM t LIMIT 1");
            assertValid("/* header */ SELECT id FROM t LIMIT 1");
        }

        @Test
        @DisplayName("'SELECT' with no following token still needs the other rules")
        void bareSelect() {
            assertViolations("SELECT", "HAS_LIMIT");
        }
    }

    @Nested
    @DisplayName("HAS_LIMIT")
    class HasLimit {

        @ParameterizedTest(name = "{0} is accepted")
        @ValueSource(strings = {
                "SELECT 1 FROM t LIMIT 1",
                "SELECT 1 FROM t limit 1",
                "SELECT 1 FROM t LiMiT 1",
                "SELECT 1 FROM t\nLIMIT\n1"
        })
        void acceptsLimitInAnyCaseOrLayout(String sql) {
            assertValid(sql);
        }

        @Test
        @DisplayName("LIMIT appearing only in a comment does not satisfy the rule")
        void limitInCommentDoesNotCount() {
            assertViolations("SELECT 1 FROM t /* no LIMIT here */", "HAS_LIMIT");
        }
    }

    @Nested
    @DisplayName("NO_FORBIDDEN_KEYWORDS")
    class ForbiddenKeywords {

        @ParameterizedTest(name = "{0} is forbidden")
        @ValueSource(strings = {
                "DROP", "drop", "Drop", "DrOp",
                "DELETE", "delete",
                "UPDATE", "update", "uPdAtE"
        })
        void rejectsForbiddenKeywordInAnyCase(String keyword) {
            assertViolations("SELECT x FROM t LIMIT 1 " + keyword + " y", "NO_FORBIDDEN_KEYWORDS");
        }

        @ParameterizedTest(name = "{0} is allowed")
        @ValueSource(strings = {"dropped", "updates", "deletion", "undroppable"})
        void allowsTokensThatOnlyContainAForbiddenWord(String token) {
            assertValid("SELECT " + token + " FROM t LIMIT 1");
        }

        @Test
        @DisplayName("a forbidden keyword hidden in a comment is ignored")
        void forbiddenKeywordInCommentIsIgnored() {
            assertValid("SELECT x FROM t LIMIT 1 -- DROP TABLE t");
            assertValid("SELECT x FROM t LIMIT 1 /* DELETE everything */");
        }
    }

    // ------------------------------------------------------------------
    // comment / string-literal handling (the normalise() step)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("normalise(): comments and string literals")
    class Normalisation {

        @Test
        @DisplayName("comment markers inside a string literal are preserved")
        void stringLiteralsAreNotComments() {
            assertValid("SELECT '-- x' , \"/* y */\" FROM t LIMIT 1");
        }

        @Test
        @DisplayName("known limitation: a keyword inside a string literal still trips the rule")
        void keywordInsideStringLiteralIsAFalsePositive() {
            // The tokenizer sees the literal's contents, so this safe query is
            // rejected. Documented here so a future fix has a failing-then-passing
            // test to target.
            assertViolations("SELECT note FROM t WHERE note = 'DROP TABLE' LIMIT 1",
                    "NO_FORBIDDEN_KEYWORDS");
        }

        @Test
        @DisplayName("an unterminated block comment swallows the rest of the query")
        void unterminatedBlockComment() {
            // Everything from /* onward is dropped, so the LIMIT clause is lost.
            assertViolations("SELECT id FROM t /* LIMIT 1", "HAS_LIMIT");
        }
    }

    // ------------------------------------------------------------------
    // validator wiring
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("validator wiring")
    class Wiring {

        @Test
        @DisplayName("violations are returned in rule order")
        void violationsFollowRuleOrder() {
            ValidationResult result = validator.validate("DELETE FROM users");
            assertEquals(List.of("STARTS_WITH_SELECT", "HAS_LIMIT", "NO_FORBIDDEN_KEYWORDS"),
                    result.violations());
        }

        @Test
        @DisplayName("the rule list is copied defensively")
        void ruleListIsDefensivelyCopied() {
            List<SqlRule> rules = new ArrayList<>();
            rules.add(new HasLimitRule());
            SqlValidator v = new SqlValidator(rules);

            rules.clear(); // must not disarm the validator

            assertFalse(v.validate("SELECT 1").isValid());
        }

        @Test
        @DisplayName("the returned violation list is unmodifiable")
        void violationListIsUnmodifiable() {
            List<String> violations = validator.validate("SELECT 1").violations();
            assertThrows(UnsupportedOperationException.class, () -> violations.add("X"));
        }

        @Test
        @DisplayName("a null rule list is rejected")
        void nullRuleListRejected() {
            assertThrows(NullPointerException.class, () -> new SqlValidator(null));
        }

        @Test
        @DisplayName("with no rules everything passes - except null, which is checked first")
        void emptyRuleSet() {
            SqlValidator permissive = new SqlValidator(List.of());
            assertAll(
                    () -> assertTrue(permissive.validate("SELECT 1").isValid()),
                    () -> assertTrue(permissive.validate("literally anything").isValid()),
                    () -> assertTrue(permissive.validate("").isValid()),
                    () -> assertFalse(permissive.validate(null).isValid()));
        }

        @Test
        @DisplayName("rules compose - a validator can enforce just one rule")
        void singleRuleValidator() {
            SqlValidator selectOnly = new SqlValidator(List.of(new StartsWithSelectRule()));
            assertAll(
                    () -> assertTrue(selectOnly.validate("SELECT with no limit at all").isValid()),
                    () -> assertFalse(selectOnly.validate("INSERT INTO t VALUES (1)").isValid()));
        }
    }
}
