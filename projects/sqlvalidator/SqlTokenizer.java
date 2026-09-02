package projects.sqlvalidator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/*
 * Split on \W+ (one or more non-word characters: whitespace, punctuation,
 * operators). \W is the negation of \w ([a-zA-Z0-9_]). Double backslash
 * is Java's escape for a literal backslash in the string literal, so the
 * regex engine sees \W+.
 *
 * Note: may produce a leading "" if the string starts with a delimiter.
 */

public final class SqlTokenizer {
    private SqlTokenizer() {}
    public static List<String> tokenize(String sql) {
        return Arrays.stream(sql.split("\\W+"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}