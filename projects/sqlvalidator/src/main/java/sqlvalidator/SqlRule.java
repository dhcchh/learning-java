package sqlvalidator;

public interface SqlRule {
    String name();
    boolean check(String sql);   // true = passes
}
