package dev.vfyjxf.cirrus.testutil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audits every {@code @AllowWeakTest} use in the test source set: the reason must be at least twenty
 * whitespace-separated English words of printable ASCII, the rules list must name rules that exist in
 * the test ruleset, and the number of exemptions must stay inside a budget.
 */
class AllowWeakTestAuditTest {

    /** A reason must carry at least this many whitespace-separated words. */
    private static final int minWords = 20;

    /** Exemptions are meant to be rare; a fifth use means the code should be fixed instead. */
    private static final int maxAllowances = 10;

    private static final Pattern reasonPattern = Pattern.compile("(?s)\\breason\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Pattern rulesPattern = Pattern.compile("(?s)\\brules\\s*=\\s*\\{([^}]*)\\}");

    private static final Pattern ruleIdPattern = Pattern.compile("\"([^\"]+)\"");

    private static final Pattern ruleNamePattern = Pattern.compile("<rule name=\"([^\"]+)\"");

    @Test
    void everyExemptionIsSpecificEnglishAndWithinBudget() throws IOException {
        Path sourceRoot = testSourceRoot();
        Set<String> knownRules = rulesetRuleIds();
        List<String> problems = new ArrayList<>();
        List<String> usageLines = new ArrayList<>();
        int total = 0;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            for (Path file : javaFiles) {
                String text = Files.readString(file);
                for (int[] occurrence : annotationOccurrences(text)) {
                    total++;
                    String args = argumentsOf(text, occurrence[0]);
                    int line = lineOf(text, occurrence[0]);
                    String reason = stringMember(args, reasonPattern);
                    List<String> rules = ruleMembers(args);
                    int words = wordCount(reason);
                    char offending = firstNonAscii(reason);
                    usageLines.add(
                        "AllowWeakTest " + shortPath(sourceRoot, file) + ":" + line + " words=" + words + " ascii="
                                + (offending == 0 ? "ok" : "U+%04X".formatted((int) offending)) + " rules=" + rules
                    );
                    String where = shortPath(sourceRoot, file) + ":" + line;
                    if (reason == null) {
                        problems.add(where + " declares no reason");
                    } else if (words < minWords) {
                        problems.add(where + " reason has " + words + " words, at least " + minWords + " are required");
                    } else if (offending != 0) {
                        problems.add(
                            where
                                    + " reason contains U+%04X at index %d; only printable ASCII is allowed"
                                            .formatted((int) offending, reason.indexOf(offending))
                        );
                    }
                    if (rules.isEmpty()) {
                        problems.add(where + " names no rules; list the rules it is exempting");
                    }
                    for (String rule : rules) {
                        if (!knownRules.contains(rule)) {
                            problems.add(where + " names unknown rule " + rule);
                        }
                    }
                }
            }
        }
        usageLines.forEach(System.out::println);
        System.out.println("AllowWeakTest uses: " + total + " of at most " + maxAllowances);
        int usageCount = total;
        assertTrue(
            usageCount <= maxAllowances,
            "there are " + usageCount + " @AllowWeakTest uses, the budget is " + maxAllowances
                    + "; fix the tests instead of adding exemptions"
        );
        assertTrue(problems.isEmpty(), () -> String.join(System.lineSeparator(), problems));
    }

    private static Path testSourceRoot() {
        List<Path> candidates = new ArrayList<>();
        String root = System.getProperty("moddevmcp.project.root");
        if (root != null && !root.isEmpty()) {
            candidates.add(Path.of(root, "Mod", "src", "test", "java"));
        }
        candidates.add(Path.of("src", "test", "java"));
        candidates.add(Path.of("Mod", "src", "test", "java"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("cannot locate the test source root, tried " + candidates);
    }

    private static Set<String> rulesetRuleIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>(pmdRuleIds());
        ids.addAll(errorProneCheckIds());
        return ids;
    }

    /**
     * The semantic checks publish their ids in {@code META-INF/cloudtestplugin/checks.txt}. The audit
     * reads the same file the plugin jar packages: from the classpath when the jar is there, and from
     * the sibling build the includeBuild in settings.gradle points at otherwise.
     */
    private static List<String> errorProneCheckIds() throws IOException {
        try (InputStream stream = AllowWeakTestAuditTest.class
                .getResourceAsStream("/META-INF/cloudtestplugin/checks.txt")) {
            if (stream != null) {
                return publishedIds(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        List<Path> candidates = new ArrayList<>();
        String root = System.getProperty("moddevmcp.project.root");
        if (root != null && !root.isEmpty()) {
            candidates.add(
                Path.of(root).getParent().resolve(
                    "CloudLib-Standalone/cloudlang-plugin/cloudtestplugin/src/main/resources"
                            + "/META-INF/cloudtestplugin/checks.txt"
                )
            );
        }
        candidates.add(
            Path.of(
                "../CloudLib-Standalone/cloudlang-plugin/cloudtestplugin/src/main/resources"
                        + "/META-INF/cloudtestplugin/checks.txt"
            )
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return publishedIds(Files.readString(candidate));
            }
        }
        throw new IllegalStateException("cannot locate the cloudtestplugin check list, tried " + candidates);
    }

    private static List<String> publishedIds(String text) {
        return text.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    private static Set<String> pmdRuleIds() throws IOException {
        List<Path> candidates = new ArrayList<>();
        String root = System.getProperty("moddevmcp.project.root");
        if (root != null && !root.isEmpty()) {
            candidates.add(Path.of(root, "Mod", "config", "pmd", "cloud-test-ruleset.xml"));
        }
        candidates.add(Path.of("config", "pmd", "cloud-test-ruleset.xml"));
        candidates.add(Path.of("Mod", "config", "pmd", "cloud-test-ruleset.xml"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                String xml = Files.readString(candidate);
                Set<String> ids = new LinkedHashSet<>();
                Matcher matcher = ruleNamePattern.matcher(xml);
                while (matcher.find()) {
                    ids.add(matcher.group(1));
                }
                if (ids.isEmpty()) {
                    throw new IllegalStateException("no rule names found in " + candidate);
                }
                return ids;
            }
        }
        throw new IllegalStateException("cannot locate the test ruleset, tried " + candidates);
    }

    private static List<int[]> annotationOccurrences(String text) {
        List<int[]> found = new ArrayList<>();
        int index = 0;
        while ((index = text.indexOf("@AllowWeakTest", index)) >= 0) {
            int after = index + "@AllowWeakTest".length();
            if (after < text.length() && Character.isJavaIdentifierPart(text.charAt(after))) {
                index = after;
                continue;
            }
            int cursor = after;
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= text.length() || text.charAt(cursor) != '(') {
                index = after;
                continue;
            }
            found.add(new int[]{index, cursor});
            index = cursor;
        }
        return found;
    }

    private static String argumentsOf(String text, int open) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = open; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return text.substring(open + 1, index);
                }
            }
        }
        return text.substring(open + 1);
    }

    private static String stringMember(String args, Pattern pattern) {
        Matcher matcher = pattern.matcher(args);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> ruleMembers(String args) {
        Matcher matcher = rulesPattern.matcher(args);
        if (!matcher.find()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        Matcher id = ruleIdPattern.matcher(matcher.group(1));
        while (id.find()) {
            ids.add(id.group(1));
        }
        return ids;
    }

    private static int wordCount(String reason) {
        if (reason == null || reason.isBlank()) {
            return 0;
        }
        return reason.trim().split("\\s+").length;
    }

    private static char firstNonAscii(String reason) {
        if (reason == null) {
            return 0;
        }
        for (int index = 0; index < reason.length(); index++) {
            char ch = reason.charAt(index);
            if (ch < 0x20 || ch > 0x7E) {
                return ch;
            }
        }
        return 0;
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String shortPath(Path root, Path file) {
        return root.relativize(file).toString();
    }
}
