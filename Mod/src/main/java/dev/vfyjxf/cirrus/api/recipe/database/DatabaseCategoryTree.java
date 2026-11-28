package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Pure, UI-agnostic hierarchical category tree for the Recipe Database sidebar.
 *
 * <p>Supports three complementary views over any {@link RecipeDatabaseRecord} collection:
 * <ul>
 *     <li><b>Systems tree</b>: nested folders driven by explicit {@code *:database/folder/...}
 *         system tags with deterministic automatic fallback grouping ({@code Minecraft},
 *         {@code Mods/<mod>}, {@code JEI/<mod>}, {@code Debug}).</li>
 *     <li><b>Technical Tags tree</b>: grouped into {@code Provenance}, {@code Process},
 *         {@code Trait}, {@code Screening}, and {@code Custom} folders.</li>
 *     <li><b>Mods list</b>: grouped by recipe owner namespace.</li>
 * </ul>
 *
 * <p>Filter keys follow one uniform scheme matched by {@link #matchesFilter}:
 * {@code sys:<systemId>}, {@code dir:<seg1>/<seg2>...}, {@code tag:<tagId>}, {@code mod:<namespace>}.
 */
public final class DatabaseCategoryTree {

    public enum NodeKind {
        folder,
        system,
        tag,
        mod
    }

    public record CategoryNode(
        String key,
        String label,
        int count,
        NodeKind kind,
        boolean defaultExpanded,
        String namespace,
        List<CategoryNode> children
    ) {
        public CategoryNode {
            children = List.copyOf(children);
        }

        public boolean isFolder() {
            return kind == NodeKind.folder && !children.isEmpty();
        }
    }

    public record FlatRow(
        CategoryNode node,
        int depth,
        boolean expandable,
        boolean expanded
    ) {}

    private DatabaseCategoryTree() {}

    /**
     * Resolves the hierarchical folder path for a recipe system:
     * <ol>
     *     <li>Explicit {@code *:database/folder/<seg1>/<seg2>/...} tag if registered.</li>
     *     <li>Automatic fallback from technical provenance tags and {@code systemId} structure.</li>
     * </ol>
     */
    public static List<String> resolveSystemFolderPath(Namespace systemId, Collection<Namespace> systemTags) {
        for (Namespace tag : systemTags) {
            List<String> explicit = RecipeSystemTags.folderSegments(tag);
            if (explicit != null && !explicit.isEmpty()) {
                return explicit;
            }
        }
        String root = systemId.root().toLowerCase(Locale.ROOT);
        String path = systemId.path().toLowerCase(Locale.ROOT);

        if (systemTags.contains(RecipeSystemTags.debug) || path.contains("debug")) {
            return List.of("debug");
        }
        if (systemTags.contains(RecipeSystemTags.jeiFallback) || "jei".equals(root)) {
            String jeiMod = extractJeiSourceMod(path);
            return jeiMod != null ? List.of("jei", jeiMod) : List.of("jei");
        }
        if (systemTags.contains(RecipeSystemTags.vanilla) || "minecraft".equals(root)) {
            return List.of("minecraft");
        }
        return List.of("native", root);
    }

    /**
     * Computes a clean leaf label for a recipe system inside its resolved folder path,
     * avoiding redundant mod/source prefixes already shown on parent folders.
     */
    public static String systemLeafLabel(Namespace systemId, List<String> folderPath) {
        String path = systemId.path();
        if ("jei".equalsIgnoreCase(systemId.root()) && path.startsWith("category/")) {
            String rest = path.substring("category/".length());
            int slash = rest.indexOf('/');
            if (slash >= 0 && slash + 1 < rest.length()) {
                String categoryTail = rest.substring(slash + 1);
                return humanizeSegment(categoryTail);
            }
            return humanizeSegment(rest);
        }
        int lastSlash = path.lastIndexOf('/');
        String tail = lastSlash >= 0 && lastSlash + 1 < path.length() ? path.substring(lastSlash + 1) : path;
        return humanizeSegment(tail.isEmpty() ? systemId.root() : tail);
    }

    /**
     * Builds the multi-level Systems folder tree (e.g. {@code Minecraft}, {@code Mods/...},
     * {@code JEI/<mod>/...}, {@code Debug}, plus any custom plugin folders).
     */
    public static List<CategoryNode> buildSystemTree(Collection<RecipeDatabaseRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Namespace, Integer> systemCounts = new LinkedHashMap<>();
        Map<Namespace, List<Namespace>> systemTagsMap = new LinkedHashMap<>();
        for (RecipeDatabaseRecord record : records) {
            Namespace sysId = record.recipe().systemId();
            systemCounts.merge(sysId, 1, Integer::sum);
            systemTagsMap.putIfAbsent(sysId, record.systemTags());
        }

        MutableFolder root = new MutableFolder("", "", true);
        for (var entry : systemCounts.entrySet()) {
            Namespace sysId = entry.getKey();
            int count = entry.getValue();
            List<Namespace> tags = systemTagsMap.getOrDefault(sysId, List.of());
            List<String> folderPath = resolveSystemFolderPath(sysId, tags);

            MutableFolder current = root;
            StringBuilder dirPath = new StringBuilder();
            for (int i = 0; i < folderPath.size(); i++) {
                String seg = folderPath.get(i);
                if (!dirPath.isEmpty()) {
                    dirPath.append('/');
                }
                dirPath.append(seg);
                String fullDir = dirPath.toString();
                boolean defaultExpanded = defaultFolderExpanded(folderPath, i);
                current = current.childFolder(seg, "dir:" + fullDir, folderDisplayLabel(seg, i), defaultExpanded);
            }
            String leafLabel = systemLeafLabel(sysId, folderPath);
            current.leaves.add(
                new CategoryNode(
                    "sys:" + sysId,
                    leafLabel,
                    count,
                    NodeKind.system,
                    false,
                    sysId.root(),
                    List.of()
                )
            );
        }

        return root.toSortedChildren(true);
    }

    /**
     * Builds the technical Tags folder tree grouped by axis
     * ({@code Provenance}, {@code Process}, {@code Trait}, {@code Screening}, {@code Custom}).
     */
    public static List<CategoryNode> buildTagTree(Collection<RecipeDatabaseRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> systemTagCounts = new LinkedHashMap<>();
        Map<String, Namespace> systemTagObjects = new LinkedHashMap<>();
        Map<String, Integer> screeningCounts = new LinkedHashMap<>();

        for (RecipeDatabaseRecord record : records) {
            Set<String> seenSysTags = new LinkedHashSet<>();
            for (Namespace tag : record.systemTags()) {
                if (RecipeSystemTags.isFolderTag(tag)) {
                    continue;
                }
                String key = tag.toString();
                if (seenSysTags.add(key)) {
                    systemTagCounts.merge(key, 1, Integer::sum);
                    systemTagObjects.putIfAbsent(key, tag);
                }
            }
            Set<String> seenScreen = new LinkedHashSet<>();
            for (String rawTag : record.searchDocument().rawText(CirrusSearchFields.tag)) {
                if (rawTag == null || rawTag.isBlank()) {
                    continue;
                }
                if (seenSysTags.contains(rawTag)) {
                    continue;
                }
                // Skip internal folder tags mirrored onto search docs
                if (rawTag.contains(RecipeSystemTags.databaseFolderPrefix)) {
                    continue;
                }
                if (seenScreen.add(rawTag)) {
                    screeningCounts.merge(rawTag, 1, Integer::sum);
                }
            }
        }

        MutableFolder root = new MutableFolder("", "", true);
        for (var entry : systemTagCounts.entrySet()) {
            String rawKey = entry.getKey();
            int count = entry.getValue();
            Namespace tag = systemTagObjects.get(rawKey);
            if (tag == null) {
                continue;
            }
            RecipeSystemTags.TagAxis axis = RecipeSystemTags.tagAxis(tag);
            String axisSeg = axis.name();
            MutableFolder axisFolder = root.childFolder(
                axisSeg,
                "tagaxis:" + axisSeg,
                tagAxisLabel(axis),
                axis == RecipeSystemTags.TagAxis.provenance || axis == RecipeSystemTags.TagAxis.process
            );
            axisFolder.leaves.add(
                new CategoryNode(
                    "tag:" + rawKey,
                    tagDisplayLabel(tag),
                    count,
                    NodeKind.tag,
                    false,
                    tag.root(),
                    List.of()
                )
            );
        }

        if (!screeningCounts.isEmpty()) {
            MutableFolder screenFolder = root.childFolder("screening", "tagaxis:screening", "Screening", false);
            for (var entry : screeningCounts.entrySet()) {
                String raw = entry.getKey();
                screenFolder.leaves.add(
                    new CategoryNode(
                        "tag:" + raw,
                        screeningTagLabel(raw),
                        entry.getValue(),
                        NodeKind.tag,
                        false,
                        "cirrus",
                        List.of()
                    )
                );
            }
        }

        return root.toSortedChildren(false);
    }

    /**
     * Builds the flat Mods list grouped by recipe owner namespace.
     */
    public static List<CategoryNode> buildModTree(Collection<RecipeDatabaseRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RecipeDatabaseRecord record : records) {
            counts.merge(ownerNamespace(record), 1, Integer::sum);
        }
        List<CategoryNode> out = new ArrayList<>(counts.size());
        for (var entry : counts.entrySet()) {
            String ns = entry.getKey();
            out.add(
                new CategoryNode(
                    "mod:" + ns,
                    humanizeSegment(ns),
                    entry.getValue(),
                    NodeKind.mod,
                    false,
                    ns,
                    List.of()
                )
            );
        }
        out.sort(Comparator.comparingInt(CategoryNode::count).reversed().thenComparing(CategoryNode::label));
        return List.copyOf(out);
    }

    /**
     * Flattens a category tree into visible sidebar rows, applying folder collapse/expand state
     * and search-query pruning (matching children automatically reveal their ancestor folders).
     */
    public static List<FlatRow> flatten(
        List<CategoryNode> roots,
        Set<String> expandedOverrides,
        Set<String> collapsedOverrides,
        @Nullable String searchQuery
    ) {
        return flatten(roots, expandedOverrides, collapsedOverrides, searchQuery, node -> List.of());
    }

    /**
     * Flattens a category tree with optional per-node extra search terms (such as registered
     * {@code MachineEntry} display names) and structured filter prefixes ({@code machine:},
     * {@code dir:}, {@code mod:}, {@code tag:}, {@code @}, {@code #}).
     */
    public static List<FlatRow> flatten(
        List<CategoryNode> roots,
        Set<String> expandedOverrides,
        Set<String> collapsedOverrides,
        @Nullable String searchQuery,
        @Nullable Function<CategoryNode, ? extends Collection<String>> extraTermsProvider
    ) {
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        Function<CategoryNode, ? extends Collection<String>> provider = extraTermsProvider == null
                ? node -> List.of()
                : extraTermsProvider;
        List<FlatRow> out = new ArrayList<>();
        for (CategoryNode root : roots) {
            appendFlattened(root, 0, expandedOverrides, collapsedOverrides, query, provider, out);
        }
        return List.copyOf(out);
    }

    private static void appendFlattened(
        CategoryNode node,
        int depth,
        Set<String> expandedOverrides,
        Set<String> collapsedOverrides,
        String query,
        Function<CategoryNode, ? extends Collection<String>> extraTermsProvider,
        List<FlatRow> out
    ) {
        boolean hasQuery = !query.isEmpty();
        boolean selfMatches = !hasQuery || nodeMatchesQuery(node, query, extraTermsProvider);
        List<CategoryNode> visibleChildren = node.children();
        if (hasQuery && !selfMatches) {
            visibleChildren = new ArrayList<>();
            for (CategoryNode child : node.children()) {
                if (subtreeMatchesQuery(child, query, extraTermsProvider)) {
                    visibleChildren.add(child);
                }
            }
            if (visibleChildren.isEmpty()) {
                return;
            }
        }
        boolean expandable = !visibleChildren.isEmpty();
        boolean expanded = expandable && (hasQuery || isFolderExpanded(node, expandedOverrides, collapsedOverrides));
        out.add(new FlatRow(node, depth, expandable, expanded));
        if (expanded) {
            for (CategoryNode child : visibleChildren) {
                appendFlattened(child, depth + 1, expandedOverrides, collapsedOverrides, query, extraTermsProvider, out);
            }
        }
    }

    public static boolean isFolderExpanded(
        CategoryNode node,
        Set<String> expandedOverrides,
        Set<String> collapsedOverrides
    ) {
        if (expandedOverrides.contains(node.key())) {
            return true;
        }
        if (collapsedOverrides.contains(node.key())) {
            return false;
        }
        return node.defaultExpanded();
    }

    private static boolean subtreeMatchesQuery(
        CategoryNode node,
        String query,
        Function<CategoryNode, ? extends Collection<String>> extraTermsProvider
    ) {
        if (nodeMatchesQuery(node, query, extraTermsProvider)) {
            return true;
        }
        for (CategoryNode child : node.children()) {
            if (subtreeMatchesQuery(child, query, extraTermsProvider)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nodeMatchesQuery(
        CategoryNode node,
        String query,
        Function<CategoryNode, ? extends Collection<String>> extraTermsProvider
    ) {
        if (query.startsWith("machine:")) {
            String needle = query.substring("machine:".length()).trim();
            if (needle.isEmpty()) {
                return node.kind() == NodeKind.system && !extraTermsProvider.apply(node).isEmpty();
            }
            return matchesExtraTerms(node, needle, extraTermsProvider);
        }
        if (query.startsWith("dir:") || query.startsWith("folder:")) {
            String needle = query.substring(query.indexOf(':') + 1).trim();
            if (node.kind() != NodeKind.folder) {
                return false;
            }
            return needle.isEmpty()
                    || node.label().toLowerCase(Locale.ROOT).contains(needle)
                    || node.key().toLowerCase(Locale.ROOT).contains(needle);
        }
        if (query.startsWith("mod:") || query.startsWith("@")) {
            String needle = (query.startsWith("@") ? query.substring(1) : query.substring(4)).trim();
            return needle.isEmpty() || node.namespace().toLowerCase(Locale.ROOT).contains(needle);
        }
        if (query.startsWith("tag:") || query.startsWith("#")) {
            String needle = (query.startsWith("#") ? query.substring(1) : query.substring(4)).trim();
            if (node.kind() != NodeKind.tag) {
                return false;
            }
            return needle.isEmpty()
                    || node.label().toLowerCase(Locale.ROOT).contains(needle)
                    || node.key().toLowerCase(Locale.ROOT).contains(needle);
        }
        return node.label().toLowerCase(Locale.ROOT).contains(query)
                || node.key().toLowerCase(Locale.ROOT).contains(query)
                || node.namespace().toLowerCase(Locale.ROOT).contains(query)
                || matchesExtraTerms(node, query, extraTermsProvider);
    }

    private static boolean matchesExtraTerms(
        CategoryNode node,
        String needle,
        Function<CategoryNode, ? extends Collection<String>> extraTermsProvider
    ) {
        Collection<String> extras = extraTermsProvider.apply(node);
        if (extras == null || extras.isEmpty()) {
            return false;
        }
        for (String term : extras) {
            if (term != null && term.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates whether {@code record} matches any category filter key produced by this tree
     * ({@code sys:...}, {@code dir:...}, {@code tag:...}, {@code tagaxis:...}, {@code mod:...}).
     */
    public static boolean matchesFilter(RecipeDatabaseRecord record, @Nullable String filterKey) {
        if (filterKey == null || filterKey.isBlank()) {
            return true;
        }
        if (filterKey.startsWith("sys:")) {
            return record.recipe().systemId().toString().equals(filterKey.substring(4));
        }
        if (filterKey.startsWith("dir:")) {
            String targetDir = filterKey.substring(4).toLowerCase(Locale.ROOT);
            List<String> recordPath = resolveSystemFolderPath(record.recipe().systemId(), record.systemTags());
            String joined = String.join("/", recordPath);
            return joined.equals(targetDir) || joined.startsWith(targetDir + "/");
        }
        if (filterKey.startsWith("mod:")) {
            return ownerNamespace(record).equals(filterKey.substring(4));
        }
        if (filterKey.startsWith("tag:")) {
            String targetTag = filterKey.substring(4);
            for (Namespace sysTag : record.systemTags()) {
                if (sysTag.toString().equals(targetTag)) {
                    return true;
                }
            }
            for (String raw : record.searchDocument().rawText(CirrusSearchFields.tag)) {
                if (targetTag.equals(raw)) {
                    return true;
                }
            }
            return false;
        }
        if (filterKey.startsWith("tagaxis:")) {
            String axisName = filterKey.substring("tagaxis:".length());
            if ("screening".equals(axisName)) {
                Set<String> sysStrings = new LinkedHashSet<>();
                for (Namespace sysTag : record.systemTags()) {
                    sysStrings.add(sysTag.toString());
                }
                for (String raw : record.searchDocument().rawText(CirrusSearchFields.tag)) {
                    if (raw != null
                            && !raw.isBlank()
                            && !sysStrings.contains(raw)
                            && !raw.contains(RecipeSystemTags.databaseFolderPrefix)) {
                        return true;
                    }
                }
                return false;
            }
            for (Namespace sysTag : record.systemTags()) {
                if (RecipeSystemTags.tagAxis(sysTag).name().equals(axisName)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Resolves a human-readable breadcrumb label (e.g. {@code JEI / Minecraft / Crafting}) for {@code filterKey}.
     */
    public static @Nullable String findBreadcrumb(List<CategoryNode> roots, String filterKey) {
        for (CategoryNode root : roots) {
            String found = findBreadcrumbRecursive(root, filterKey, "");
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @Nullable String findBreadcrumbRecursive(CategoryNode node, String key, String prefix) {
        String current = prefix.isEmpty() ? node.label() : prefix + " / " + node.label();
        if (node.key().equals(key)) {
            return current;
        }
        for (CategoryNode child : node.children()) {
            String found = findBreadcrumbRecursive(child, key, current);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static String ownerNamespace(RecipeDatabaseRecord record) {
        ResourceLocation id = record.recipe().id();
        return id != null ? id.getNamespace() : record.recipe().systemId().root();
    }

    private static @Nullable String extractJeiSourceMod(String path) {
        if (!path.startsWith("category/")) {
            return null;
        }
        String rest = path.substring("category/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return rest.substring(0, slash);
    }

    private static boolean defaultFolderExpanded(List<String> fullPath, int depth) {
        if (depth == 0) {
            String top = fullPath.getFirst();
            // Keep JEI collapsed by default so hundreds of JEI categories stay tucked away until needed
            return !"jei".equals(top);
        }
        return false;
    }

    private static String folderDisplayLabel(String segment, int depth) {
        if (depth == 0) {
            return switch (segment) {
                case "minecraft" -> "Minecraft";
                case "jei" -> "JEI";
                case "native" -> "Mod Native";
                case "debug" -> "Debug";
                default -> humanizeSegment(segment);
            };
        }
        return switch (segment) {
            case "minecraft" -> "Minecraft";
            case "jei" -> "JEI";
            default -> humanizeSegment(segment);
        };
    }

    private static String tagAxisLabel(RecipeSystemTags.TagAxis axis) {
        return switch (axis) {
            case provenance -> "Provenance";
            case process -> "Process";
            case trait -> "Traits";
            case folder -> "Folders";
            case custom -> "Custom";
        };
    }

    private static String tagDisplayLabel(Namespace tag) {
        String path = tag.path();
        int slash = path.lastIndexOf('/');
        String tail = slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : path;
        return humanizeSegment(tail);
    }

    private static String screeningTagLabel(String raw) {
        int slash = raw.lastIndexOf('/');
        String tail = slash >= 0 && slash + 1 < raw.length() ? raw.substring(slash + 1) : raw;
        int colon = tail.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < tail.length()) {
            tail = tail.substring(colon + 1);
        }
        return humanizeSegment(tail);
    }

    public static String humanizeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = raw.replace('/', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(cleaned.length());
        boolean capNext = true;
        for (int i = 0; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                capNext = true;
            } else if (capNext) {
                sb.append(Character.toUpperCase(ch));
                capNext = false;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static int topFolderPriority(String key) {
        return switch (key) {
            case "dir:minecraft" -> 0;
            case "dir:native" -> 1;
            case "dir:jei" -> 3;
            case "dir:debug" -> 4;
            case "tagaxis:provenance" -> 0;
            case "tagaxis:process" -> 1;
            case "tagaxis:trait" -> 2;
            case "tagaxis:screening" -> 3;
            case "tagaxis:custom" -> 4;
            default -> 2;
        };
    }

    private static final class MutableFolder {
        private final String key;
        private final String label;
        private final boolean defaultExpanded;
        private final Map<String, MutableFolder> folders = new LinkedHashMap<>();
        private final List<CategoryNode> leaves = new ArrayList<>();

        private MutableFolder(String key, String label, boolean defaultExpanded) {
            this.key = key;
            this.label = label;
            this.defaultExpanded = defaultExpanded;
        }

        private MutableFolder childFolder(String segment, String dirKey, String displayLabel, boolean defaultExp) {
            return folders.computeIfAbsent(segment, ignored -> new MutableFolder(dirKey, displayLabel, defaultExp));
        }

        private CategoryNode toNode(boolean isTopLevel) {
            List<CategoryNode> children = toSortedChildren(false);
            int total = 0;
            for (CategoryNode child : children) {
                total += child.count();
            }
            return new CategoryNode(key, label, total, NodeKind.folder, defaultExpanded, "", children);
        }

        private List<CategoryNode> toSortedChildren(boolean topLevel) {
            List<CategoryNode> out = new ArrayList<>(folders.size() + leaves.size());
            for (MutableFolder sub : folders.values()) {
                out.add(sub.toNode(false));
            }
            out.addAll(leaves);
            if (topLevel) {
                out.sort(
                    Comparator.<CategoryNode>comparingInt(n -> topFolderPriority(n.key()))
                            .thenComparing(Comparator.comparingInt(CategoryNode::count).reversed())
                            .thenComparing(CategoryNode::label)
                );
            } else {
                out.sort(
                    Comparator.<CategoryNode>comparingInt(n -> n.isFolder() ? 0 : 1)
                            .thenComparing(Comparator.comparingInt(CategoryNode::count).reversed())
                            .thenComparing(CategoryNode::label)
                );
            }
            return List.copyOf(out);
        }
    }
}
