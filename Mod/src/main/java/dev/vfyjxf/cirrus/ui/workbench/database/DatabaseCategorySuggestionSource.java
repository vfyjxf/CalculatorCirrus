package dev.vfyjxf.cirrus.ui.workbench.database;

import dev.vfyjxf.cirrus.api.recipe.database.DatabaseCategoryTree;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchHighlightKind;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchHighlightSegment;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestion;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestionSource;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Autocomplete {@link SearchSuggestionSource} for the Database Explorer category sidebar.
 *
 * <p>Suggests recipe systems (label and full ID), folder paths, mods, technical tags, and
 * registered machine names (via {@code machine:...} or plain text), ranked by prefix match
 * quality and recipe volume.
 */
public final class DatabaseCategorySuggestionSource implements SearchSuggestionSource {

    private static final List<String> prefixTokens = List.of("machine:", "dir:", "mod:", "tag:");

    private final Supplier<List<DatabaseCategoryTree.CategoryNode>> rootsSupplier;
    private final Function<DatabaseCategoryTree.CategoryNode, List<String>> machineLabelsSupplier;

    public DatabaseCategorySuggestionSource(
        Supplier<List<DatabaseCategoryTree.CategoryNode>> rootsSupplier,
        Function<DatabaseCategoryTree.CategoryNode, List<String>> machineLabelsSupplier
    ) {
        this.rootsSupplier = Objects.requireNonNull(rootsSupplier, "rootsSupplier");
        this.machineLabelsSupplier = Objects.requireNonNull(machineLabelsSupplier, "machineLabelsSupplier");
    }

    @Override
    public List<SearchSuggestion> suggest(String query, int cursor, int limit) {
        int max = Math.max(0, limit);
        if (max == 0) {
            return List.of();
        }
        String raw = query == null ? "" : query;
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int replaceEnd = raw.length();

        String activePrefix = extractPrefix(lower);
        String body = activePrefix.isEmpty() ? lower : lower.substring(activePrefix.length()).trim();

        List<DatabaseCategoryTree.CategoryNode> roots = rootsSupplier.get();
        List<Candidate> candidates = new ArrayList<>();

        // Offer structured filter prefixes when typing the start of a prefix or empty query
        if (activePrefix.isEmpty()) {
            addPrefixCandidates(candidates, lower, replaceEnd);
        }

        Map<String, MachineAggregate> machineAggregates = new LinkedHashMap<>();
        collectTreeCandidates(roots, "", activePrefix, body, replaceEnd, candidates, machineAggregates);

        // Offer machine-name suggestions (both under `machine:` prefix and plain queries)
        if (activePrefix.isEmpty() || "machine:".equals(activePrefix)) {
            for (MachineAggregate agg : machineAggregates.values()) {
                int rank = matchRank(agg.displayName(), body);
                if (rank < 0) {
                    continue;
                }
                if (activePrefix.isEmpty() && body.isEmpty()) {
                    continue;
                }
                String replacement = "machine:" + agg.displayName();
                String desc = agg.systemCount() == 1
                        ? "Machine · " + agg.firstSystemLabel()
                        : "Machine · " + agg.systemCount() + " systems";
                candidates.add(
                    new Candidate(
                        new SearchSuggestion(
                            replacement,
                            replacement,
                            0,
                            replaceEnd,
                            agg.recipeCount(),
                            Component.literal(desc)
                        ),
                        rank + (activePrefix.isEmpty() ? 1 : 0)
                    )
                );
            }
        }

        candidates.sort(
            Comparator.comparingInt(Candidate::rank)
                    .thenComparing(Comparator.comparingInt((Candidate c) -> c.suggestion().count()).reversed())
                    .thenComparing(c -> c.suggestion().token().toLowerCase(Locale.ROOT))
        );

        Map<String, SearchSuggestion> dedup = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            String key = c.suggestion().token().toLowerCase(Locale.ROOT);
            dedup.putIfAbsent(key, c.suggestion());
            if (dedup.size() >= max) {
                break;
            }
        }
        return List.copyOf(dedup.values());
    }

    private static void addPrefixCandidates(List<Candidate> out, String lower, int replaceEnd) {
        if (lower.isEmpty()) {
            return;
        }
        for (String prefix : prefixTokens) {
            if (prefix.startsWith(lower) && !prefix.equals(lower)) {
                String desc = switch (prefix) {
                    case "machine:" -> "Filter systems by machine name";
                    case "dir:" -> "Filter by category folder path";
                    case "mod:" -> "Filter by mod namespace";
                    case "tag:" -> "Filter by technical tag";
                    default -> "Category filter prefix";
                };
                out.add(
                    new Candidate(
                        new SearchSuggestion(prefix, prefix, 0, replaceEnd, 0, Component.literal(desc)),
                        0
                    )
                );
            }
        }
    }

    private void collectTreeCandidates(
        List<DatabaseCategoryTree.CategoryNode> nodes,
        String parentPath,
        String activePrefix,
        String body,
        int replaceEnd,
        List<Candidate> out,
        Map<String, MachineAggregate> machineAggregates
    ) {
        for (DatabaseCategoryTree.CategoryNode node : nodes) {
            String breadcrumb = parentPath.isEmpty() ? node.label() : parentPath + " / " + node.label();
            List<String> machines = node.kind() == DatabaseCategoryTree.NodeKind.system
                    ? machineLabelsSupplier.apply(node)
                    : List.of();
            for (String machine : machines) {
                if (machine == null || machine.isBlank()) {
                    continue;
                }
                String mKey = machine.toLowerCase(Locale.ROOT);
                machineAggregates.compute(mKey, (k, prev) -> prev == null
                        ? new MachineAggregate(machine, node.label(), 1, node.count())
                        : new MachineAggregate(
                            prev.displayName(),
                            prev.firstSystemLabel(),
                            prev.systemCount() + 1,
                            prev.recipeCount() + node.count()
                        ));
            }

            if (matchesActivePrefix(node, activePrefix)) {
                String rawKey = stripKeyTypePrefix(node.key());
                int rank = bestNodeRank(node, rawKey, breadcrumb, machines, body);
                if (rank >= 0) {
                    String replacement = buildReplacement(node, rawKey, activePrefix);
                    String token = node.kind() == DatabaseCategoryTree.NodeKind.system && activePrefix.isEmpty()
                            ? node.label()
                            : replacement;
                    String desc = buildDescription(node, parentPath, rawKey, machines);
                    out.add(
                        new Candidate(
                            new SearchSuggestion(
                                token,
                                replacement,
                                0,
                                replaceEnd,
                                node.count(),
                                Component.literal(desc)
                            ),
                            rank
                        )
                    );
                }
            }

            if (!node.children().isEmpty()) {
                collectTreeCandidates(
                    node.children(),
                    breadcrumb,
                    activePrefix,
                    body,
                    replaceEnd,
                    out,
                    machineAggregates
                );
            }
        }
    }

    private static boolean matchesActivePrefix(DatabaseCategoryTree.CategoryNode node, String activePrefix) {
        if (activePrefix.isEmpty()) {
            return true;
        }
        return switch (activePrefix) {
            case "dir:", "folder:" -> node.kind() == DatabaseCategoryTree.NodeKind.folder;
            case "mod:", "@" -> node.kind() == DatabaseCategoryTree.NodeKind.mod
                    || node.kind() == DatabaseCategoryTree.NodeKind.system;
            case "tag:", "#" -> node.kind() == DatabaseCategoryTree.NodeKind.tag;
            case "machine:" -> false;
            default -> true;
        };
    }

    private static String buildReplacement(
        DatabaseCategoryTree.CategoryNode node,
        String rawKey,
        String activePrefix
    ) {
        if ("dir:".equals(activePrefix) || "folder:".equals(activePrefix)) {
            return "dir:" + rawKey;
        }
        if ("mod:".equals(activePrefix) || "@".equals(activePrefix)) {
            return activePrefix + node.namespace();
        }
        if ("tag:".equals(activePrefix) || "#".equals(activePrefix)) {
            return activePrefix + rawKey;
        }
        return node.kind() == DatabaseCategoryTree.NodeKind.system ? rawKey : node.label();
    }

    private static String buildDescription(
        DatabaseCategoryTree.CategoryNode node,
        String parentPath,
        String rawKey,
        List<String> machines
    ) {
        return switch (node.kind()) {
            case folder -> "Folder · " + node.children().size() + " items";
            case system -> {
                StringBuilder sb = new StringBuilder();
                sb.append(parentPath.isEmpty() ? rawKey : parentPath);
                if (!machines.isEmpty()) {
                    sb.append(" · ").append(machines.getFirst());
                    if (machines.size() > 1) {
                        sb.append(" +").append(machines.size() - 1);
                    }
                }
                yield sb.toString();
            }
            case mod -> "Mod · " + node.namespace();
            case tag -> parentPath.isEmpty() ? "Tag · " + rawKey : parentPath + " · " + rawKey;
        };
    }

    private static int bestNodeRank(
        DatabaseCategoryTree.CategoryNode node,
        String rawKey,
        String breadcrumb,
        List<String> machines,
        String body
    ) {
        if (body.isEmpty()) {
            return node.kind() == DatabaseCategoryTree.NodeKind.system ? 1 : 2;
        }
        int best = matchRank(node.label(), body);
        int keyRank = matchRank(rawKey, body);
        if (keyRank >= 0) {
            best = best < 0 ? keyRank : Math.min(best, keyRank);
        }
        int nsRank = matchRank(node.namespace(), body);
        if (nsRank >= 0) {
            int weighted = nsRank + 1;
            best = best < 0 ? weighted : Math.min(best, weighted);
        }
        int crumbRank = matchRank(breadcrumb, body);
        if (crumbRank >= 0) {
            int weighted = crumbRank + 2;
            best = best < 0 ? weighted : Math.min(best, weighted);
        }
        for (String machine : machines) {
            int mRank = matchRank(machine, body);
            if (mRank >= 0) {
                int weighted = mRank + 1;
                best = best < 0 ? weighted : Math.min(best, weighted);
            }
        }
        return best;
    }

    private static int matchRank(@Nullable String candidate, String query) {
        if (candidate == null || candidate.isEmpty()) {
            return -1;
        }
        if (query.isEmpty()) {
            return 1;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.equals(query)) {
            return 0;
        }
        if (lower.startsWith(query)) {
            return 1;
        }
        int idx = lower.indexOf(query);
        if (idx < 0) {
            return -1;
        }
        char prev = lower.charAt(idx - 1);
        if (prev == ' ' || prev == '/' || prev == ':' || prev == '_' || prev == '-') {
            return 2;
        }
        return 3;
    }

    private static String extractPrefix(String lower) {
        for (String prefix : List.of("machine:", "folder:", "dir:", "mod:", "tag:", "@", "#")) {
            if (lower.startsWith(prefix)) {
                return prefix;
            }
        }
        return "";
    }

    private static String stripKeyTypePrefix(String key) {
        for (String p : List.of("sys:", "dir:", "mod:", "tag:", "tagaxis:")) {
            if (key.startsWith(p)) {
                return key.substring(p.length());
            }
        }
        return key;
    }

    /**
     * Lightweight syntax highlighter for sidebar category filter input (highlights optional
     * {@code machine:}, {@code dir:}, {@code mod:}, {@code tag:}, {@code @}, {@code #} prefixes
     * without flagging normal category labels as syntax errors).
     */
    public static List<SearchHighlightSegment> highlight(@Nullable String query) {
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        String lower = query.toLowerCase(Locale.ROOT);
        String prefix = extractPrefix(lower);
        if (prefix.isEmpty()) {
            return List.of(new SearchHighlightSegment(query, SearchHighlightKind.text, 0, query.length()));
        }
        List<SearchHighlightSegment> segments = new ArrayList<>(2);
        SearchHighlightKind kind = prefix.length() == 1 ? SearchHighlightKind.prefix : SearchHighlightKind.field;
        segments.add(new SearchHighlightSegment(query.substring(0, prefix.length()), kind, 0, prefix.length()));
        if (prefix.length() < query.length()) {
            segments.add(
                new SearchHighlightSegment(
                    query.substring(prefix.length()),
                    SearchHighlightKind.text,
                    prefix.length(),
                    query.length()
                )
            );
        }
        return List.copyOf(segments);
    }

    private record Candidate(SearchSuggestion suggestion, int rank) {}

    private record MachineAggregate(String displayName, String firstSystemLabel, int systemCount, int recipeCount) {}
}
