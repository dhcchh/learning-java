package sqlvalidator.rules;

/*
handle any case - select, SELECT, Select, SeLEcT etc

String.regionMatches()
Compares a substring region of the calling string against a substring region of another string,
character by character, without allocating either substring.

toffset — where to start comparing in the string you're calling it on
other — the string to compare against
ooffset — where to start comparing in other
len — how many characters to compare
ignoreCase — case-sensitive or not
 */

import sqlvalidator.SqlRule;

public class StartsWithSelectRule implements SqlRule {
    public String name() { return "STARTS_WITH_SELECT"; }

    public boolean check(String sql){
        return sql.regionMatches(true, 0, "SELECT", 0, 6)
                && (sql.length() == 6 || Character.isWhitespace(sql.charAt(6))); //
    }
}
