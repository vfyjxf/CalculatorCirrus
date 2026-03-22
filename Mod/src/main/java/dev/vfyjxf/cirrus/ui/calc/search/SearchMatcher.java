package dev.vfyjxf.cirrus.ui.calc.search;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.ui.calc.database.DatabaseDefinition;
import dev.vfyjxf.cirrus.ui.calc.database.DatabaseKind;
import dev.vfyjxf.cirrus.ui.calc.target.RecipeCandidate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchMatcher {
    private SearchMatcher() {
    }

    public static boolean matches(SearchExpression expr, RecipeCandidate candidate) {
        if (expr instanceof SearchExpression.And and) {
            return and.terms().stream().allMatch(term -> matches(term, candidate));
        }
        if (expr instanceof SearchExpression.Or or) {
            return or.terms().stream().anyMatch(term -> matches(term, candidate));
        }
        if (expr instanceof SearchExpression.Not not) {
            return !matches(not.term(), candidate);
        }
        SearchExpression.Term term = (SearchExpression.Term) expr;
        return matchTerm(term.term(), candidate);
    }

    private static boolean matchTerm(SearchTerm term, RecipeCandidate candidate) {
        if (term.operator() == SearchOperator.exists) {
            return hasFieldValue(term.field(), candidate);
        }
        if (term.operator() == SearchOperator.notExists) {
            return !hasFieldValue(term.field(), candidate);
        }

        if (isNumericField(term.field())) {
            return matchNumeric(term, candidate);
        }

        String raw = term.raw();
        List<String> haystack = switch (term.field()) {
            case output -> candidate.outputNames();
            case input -> candidate.inputNames();
            case name -> List.of(candidate.displayName());
            case id -> List.of(candidate.recipeId());
            case system -> List.of(candidate.systemId());
            case db -> databaseNames(candidate.database());
            case mod -> extractNamespaces(candidate);
            case tag -> candidate.outputNames();
            case plan -> databaseKinds(candidate, DatabaseKind.plan);
            case profile -> databaseKinds(candidate, DatabaseKind.profile);
            case catalyst -> List.of(Boolean.toString(hasCatalyst(candidate)));
            case byproduct -> List.of(Boolean.toString(hasByproduct(candidate)));
            default -> candidate.outputNames();
        };

        boolean hasWildcard = raw.contains("*") || raw.contains("?");
        FuzzyPattern fuzzyPattern = FuzzyPattern.parse(raw);
        return haystack.stream().anyMatch(value -> matchValue(raw, value, hasWildcard, fuzzyPattern));
    }

    private static boolean matchValue(String raw, String value, boolean wildcard, @Nullable FuzzyPattern fuzzyPattern) {
        String candidate = value == null ? "" : value;
        if (fuzzyPattern != null) {
            String lowered = candidate.toLowerCase(Locale.ROOT);
            String[] tokens = lowered.split("[^a-z0-9]+");
            for (String token : tokens) {
                if (token.isBlank()) {
                    continue;
                }
                if (levenshtein(token, fuzzyPattern.term()) <= fuzzyPattern.distance()) {
                    return true;
                }
            }
            return false;
        }
        if (wildcard) {
            return wildcardMatch(raw, candidate);
        }
        return candidate.toLowerCase(Locale.ROOT).contains(raw.toLowerCase(Locale.ROOT));
    }

    private static boolean wildcardMatch(String pattern, String value) {
        String regex = pattern.replace("*", ".*").replace("?", ".");
        return value.matches("(?i)" + regex);
    }

    private static boolean hasFieldValue(SearchField field, RecipeCandidate candidate) {
        return switch (field) {
            case output -> !candidate.outputNames().isEmpty();
            case input -> !candidate.inputNames().isEmpty();
            case name -> candidate.displayName() != null && !candidate.displayName().isBlank();
            case id -> candidate.recipeId() != null && !candidate.recipeId().isBlank();
            case system -> candidate.systemId() != null && !candidate.systemId().isBlank();
            case db -> candidate.database() != null;
            case mod -> !extractNamespaces(candidate).isEmpty();
            case tag -> !candidate.outputNames().isEmpty();
            case machine -> hasMachine(candidate);
            case time -> !timeValues(candidate).isEmpty();
            case energy -> !energyValues(candidate).isEmpty();
            case chance -> !chanceValues(candidate).isEmpty();
            case catalyst -> hasCatalyst(candidate);
            case byproduct -> hasByproduct(candidate);
            case plan -> candidate.database() != null && candidate.database().kind() == DatabaseKind.plan;
            case profile -> candidate.database() != null && candidate.database().kind() == DatabaseKind.profile;
            default -> false;
        };
    }

    private static boolean matchNumeric(SearchTerm term, RecipeCandidate candidate) {
        Range range = Range.parse(term.raw(), term.field());
        if (range == null) {
            return false;
        }
        if (term.field() == SearchField.time) {
            return timeValues(candidate).stream().anyMatch(value -> range.matches(value));
        }
        if (term.field() == SearchField.energy) {
            return energyValues(candidate).stream().anyMatch(value -> range.matches(value));
        }
        if (term.field() == SearchField.chance) {
            return chanceValues(candidate).stream().anyMatch(range::matches);
        }
        return false;
    }

    private static List<String> databaseNames(DatabaseDefinition database) {
        if (database == null) {
            return List.of();
        }
        return List.of(database.name(), database.id());
    }

    private static List<String> databaseKinds(RecipeCandidate candidate, DatabaseKind kind) {
        if (candidate.database() == null) {
            return List.of();
        }
        return candidate.database().kind() == kind ? List.of(candidate.database().name()) : List.of();
    }

    private static List<String> extractNamespaces(RecipeCandidate candidate) {
        List<String> namespaces = new ArrayList<>();
        for (String value : candidate.outputNames()) {
            int idx = value.indexOf(':');
            if (idx > 0) {
                namespaces.add(value.substring(0, idx));
            }
        }
        for (String value : candidate.inputNames()) {
            int idx = value.indexOf(':');
            if (idx > 0) {
                namespaces.add(value.substring(0, idx));
            }
        }
        return namespaces;
    }

    private static boolean hasMachine(RecipeCandidate candidate) {
        if (candidate.node() != null) {
            return !candidate.node().machineEntry().isEmpty();
        }
        if (candidate.recipeEntry() != null) {
            return !candidate.recipeEntry().system().conventionMachine().isEmpty();
        }
        return false;
    }

    private static boolean hasCatalyst(RecipeCandidate candidate) {
        for (InputContent input : inputs(candidate)) {
            if (input.notConsumed()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasByproduct(RecipeCandidate candidate) {
        for (OutputContent output : outputs(candidate)) {
            if (output.chance() < RecipeContent.MAX_CHANCE) {
                return true;
            }
        }
        return false;
    }

    private static List<Long> timeValues(RecipeCandidate candidate) {
        List<Long> values = new ArrayList<>();
        for (OutputContent output : outputs(candidate)) {
            if (output instanceof ValueContent<?> value && value.type() == CirrusTypes.Contents.TIME) {
                values.add(value.content().value());
            }
        }
        return values;
    }

    private static List<Long> energyValues(RecipeCandidate candidate) {
        List<Long> values = new ArrayList<>();
        for (OutputContent output : outputs(candidate)) {
            if (output instanceof ValueContent<?> value && value.type() == CirrusTypes.Contents.ENERGY) {
                values.add(value.content().value());
            }
        }
        return values;
    }

    private static List<Double> chanceValues(RecipeCandidate candidate) {
        List<Double> values = new ArrayList<>();
        for (InputContent input : inputs(candidate)) {
            values.add((double) input.chance());
        }
        for (OutputContent output : outputs(candidate)) {
            values.add((double) output.chance());
        }
        return values;
    }

    private static List<InputContent> inputs(RecipeCandidate candidate) {
        List<InputContent> inputs = new ArrayList<>();
        if (candidate.node() != null) {
            for (InputContent input : candidate.node().mergedInputs()) {
                inputs.add(input);
            }
            return inputs;
        }
        RecipeProjection projection = resolve(candidate);
        if (projection != null) {
            for (ClausedContent<InputContent> input : projection.inputs()) {
                inputs.add(input.content());
            }
        }
        return inputs;
    }

    private static List<OutputContent> outputs(RecipeCandidate candidate) {
        List<OutputContent> outputs = new ArrayList<>();
        if (candidate.node() != null) {
            for (OutputContent output : candidate.node().mergedOutputs()) {
                outputs.add(output);
            }
            return outputs;
        }
        RecipeProjection projection = resolve(candidate);
        if (projection != null) {
            for (ClausedContent<OutputContent> output : projection.outputs()) {
                outputs.add(output.content());
            }
        }
        return outputs;
    }

    private static @Nullable RecipeProjection resolve(RecipeCandidate candidate) {
        if (candidate.recipeEntry() == null) {
            return null;
        }
        return candidate.recipeEntry().resolve();
    }

    private static boolean isNumericField(SearchField field) {
        return field == SearchField.time
               || field == SearchField.energy
               || field == SearchField.chance
               || field == SearchField.tier
               || field == SearchField.level;
    }

    private record Range(Double min, Double max, boolean includeMin, boolean includeMax) {
        boolean matches(double value) {
            if (min != null) {
                if (includeMin) {
                    if (value < min) {
                        return false;
                    }
                } else if (value <= min) {
                    return false;
                }
            }
            if (max != null) {
                if (includeMax) {
                    if (value > max) {
                        return false;
                    }
                } else if (value >= max) {
                    return false;
                }
            }
            return true;
        }

        static @Nullable Range parse(String raw, SearchField field) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String trimmed = raw.trim();
            if (trimmed.contains("..")) {
                String[] parts = trimmed.split("\\.\\.", -1);
                Double min = parts[0].isBlank() ? null : parseNumber(field, parts[0].trim());
                Double max = parts.length > 1 && !parts[1].isBlank() ? parseNumber(field, parts[1].trim()) : null;
                return new Range(min, max, true, true);
            }
            Range comparator = parseComparator(trimmed, field);
            if (comparator != null) {
                return comparator;
            }
            Double value = parseNumber(field, trimmed);
            return value == null ? null : new Range(value, value, true, true);
        }

        private static @Nullable Range parseComparator(String raw, SearchField field) {
            if (raw.startsWith(">=")) {
                Double value = parseNumber(field, raw.substring(2));
                return value == null ? null : new Range(value, null, true, true);
            }
            if (raw.startsWith(">")) {
                Double value = parseNumber(field, raw.substring(1));
                return value == null ? null : new Range(value, null, false, true);
            }
            if (raw.startsWith("<=")) {
                Double value = parseNumber(field, raw.substring(2));
                return value == null ? null : new Range(null, value, true, true);
            }
            if (raw.startsWith("<")) {
                Double value = parseNumber(field, raw.substring(1));
                return value == null ? null : new Range(null, value, true, false);
            }
            return null;
        }
    }

    private static @Nullable Double parseNumber(SearchField field, String raw) {
        try {
            return switch (field) {
                case time -> (double) UnitParser.parseTime(raw);
                case energy -> (double) UnitParser.parseEnergy(raw);
                case chance -> UnitParser.parseChance(raw);
                case tier, level -> Double.parseDouble(raw.trim());
                default -> Double.parseDouble(raw.trim());
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record FuzzyPattern(String term, int distance) {
        static @Nullable FuzzyPattern parse(String raw) {
            Matcher matcher = fuzzy.matcher(raw);
            if (!matcher.matches()) {
                return null;
            }
            String base = matcher.group(1);
            String dist = matcher.group(2);
            int distance = dist == null || dist.isBlank() ? 1 : Integer.parseInt(dist);
            return new FuzzyPattern(base.toLowerCase(Locale.ROOT), distance);
        }
    }

    private static final Pattern fuzzy = Pattern.compile("^(.+?)~(\\d+)?$");

    private static int levenshtein(String left, String right) {
        int[] prev = new int[right.length() + 1];
        int[] next = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            next[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                next[j] = Math.min(
                    Math.min(next[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = next;
            next = tmp;
        }
        return prev[right.length()];
    }
}
