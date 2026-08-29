package dev.vfyjxf.cirrus.api.recipe.rule;

import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiled text matcher for one id-style condition leaf. Instances are
 * immutable and thread-safe; regex/glob patterns are compiled once at
 * construction so evaluation never pays compilation cost.
 *
 * <p>An empty value list matches nothing (fail-safe: a rule with no values
 * can never be satisfied accidentally).
 */
final class TextMatcher {

    private final TextMatchOp op;
    private final String[] values;
    private final Pattern[] patterns;

    private TextMatcher(TextMatchOp op, String[] values, Pattern[] patterns) {
        this.op = op;
        this.values = values;
        this.patterns = patterns;
    }

    static TextMatcher compile(TextMatchOp op, List<String> values) {
        Checks.checkNotNull(op, "op");
        Checks.checkNotNull(values, "values");
        String[] cleaned = values.stream()
            .map(value -> value == null ? "" : value.trim())
            .filter(value -> !value.isEmpty())
            .distinct()
            .toArray(String[]::new);
        if (cleaned.length == 0) {
            return new TextMatcher(op, cleaned, new Pattern[0]);
        }
        if (op == TextMatchOp.glob || op == TextMatchOp.regex) {
            Pattern[] compiled = new Pattern[cleaned.length];
            for (int i = 0; i < cleaned.length; i++) {
                try {
                    compiled[i] = op == TextMatchOp.glob ? globToRegex(cleaned[i]) : Pattern.compile(cleaned[i]);
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("Invalid " + op + " pattern '" + cleaned[i] + "': " + e.getMessage(), e);
                }
            }
            return new TextMatcher(op, cleaned, compiled);
        }
        return new TextMatcher(op, cleaned, new Pattern[0]);
    }

    /** {@code *} matches any run, {@code ?} matches one char; the glob must cover the whole id. */
    private static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    boolean isEmpty() {
        return values.length == 0;
    }

    boolean matches(String candidate) {
        if (candidate == null || isEmpty()) {
            return false;
        }
        switch (op) {
            case in -> {
                for (String value : values) {
                    if (value.equals(candidate)) {
                        return true;
                    }
                }
                return false;
            }
            case prefix -> {
                for (String value : values) {
                    if (candidate.startsWith(value)) {
                        return true;
                    }
                }
                return false;
            }
            case suffix -> {
                for (String value : values) {
                    if (candidate.endsWith(value)) {
                        return true;
                    }
                }
                return false;
            }
            case contains -> {
                for (String value : values) {
                    if (candidate.contains(value)) {
                        return true;
                    }
                }
                return false;
            }
            case glob, regex -> {
                for (Pattern pattern : patterns) {
                    // glob compiles to an anchored pattern (full match);
                    // regex keeps substring (find) semantics like the old pattern rules
                    boolean hit = op == TextMatchOp.glob
                        ? pattern.matcher(candidate).matches()
                        : pattern.matcher(candidate).find();
                    if (hit) {
                        return true;
                    }
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    TextMatchOp op() {
        return op;
    }

    List<String> values() {
        return List.of(values);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextMatcher other
            && op == other.op
            && java.util.Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return 31 * op.hashCode() + java.util.Arrays.hashCode(values);
    }
}
