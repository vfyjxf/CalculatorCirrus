package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * M3 Zero-Wordlist Tag Homomorphism & Material Family Lineage Analyzer (Pure JDK 21).
 *
 * <p>Implements:
 * - Single-trunk deterministic mapping R_tau extraction (excluding recycling, round-trips, ambiguous variants).
 * - Tag pair homomorphism discovery (T' -> T) with coverage >= 0.75, purity >= 0.90, uniqueness >= 0.90, support >= 3.
 * - Lineage root propagation marking converging roots as AMBIGUOUS (never falsely unioning distinct families!).
 * - Never fabricates Cartesian 1-to-1 lineage from Any-of -> fixed single output recipes.
 */
public final class TagHomomorphismAnalyzer {

    public static final double defaultMinCoverage = 0.75;
    public static final double defaultMinPurity = 0.90;
    public static final double defaultMinUniqueness = 0.90;
    public static final int defaultMinSupport = 3;

    public static final String ambiguousFamily = "AMBIGUOUS";
    public static final String unknownFamily = "UNKNOWN";

    public enum TagEquivalenceClass {
        uniformEquivalent,
        multiFamilySymmetric,
        heterogeneousBroad
    }

    public record TagHomomorphismEdge(
        String sourceTagId,
        String targetTagId,
        String templateSignature,
        int supportCount,
        double coverage,
        double purity,
        double uniqueness,
        boolean exact
    ) {}

    public record TagSemanticProfile(
        String tagId,
        int memberCount,
        int producibleCount,
        int distinctFamilies,
        TagEquivalenceClass equivalenceClass,
        List<TagHomomorphismEdge> incomingHomomorphisms
    ) {}

    public record LineageIndex(
        Map<ResourceKey, String> familyOfResource,
        Map<String, TagSemanticProfile> tagProfiles,
        List<TagHomomorphismEdge> homomorphisms
    ) {
        public static final LineageIndex empty = new LineageIndex(Map.of(), Map.of(), List.of());

        public String familyOf(ResourceKey resource) {
            return familyOfResource.getOrDefault(resource, unknownFamily);
        }

        public boolean hasKnownFamily(ResourceKey resource) {
            String f = familyOf(resource);
            return !unknownFamily.equals(f) && !ambiguousFamily.equals(f);
        }
    }

    private TagHomomorphismAnalyzer() {}

    public static LineageIndex analyze(CompiledRecipeGraph graph) {
        Objects.requireNonNull(graph, "graph");
        int recipeCount = graph.stats().compiledRecipes();

        // 1. Collect inverted index member -> tags and tag -> members from all recipe slots.
        Map<String, Set<ResourceKey>> tagToMembers = new TreeMap<>();
        Map<ResourceKey, Set<String>> memberToTags = new HashMap<>();

        // 2. Collect single-trunk deterministic edges (x -> y) grouped by canonical template tau.
        // Strictly ignore multi-candidate Any-of -> fixed output slots!
        Map<String, Map<ResourceKey, Set<ResourceKey>>> edgesByTau = new TreeMap<>();
        Map<ResourceKey, Set<ResourceKey>> uniqueParents = new HashMap<>();

        for (int cid = 0; cid < recipeCount; cid++) {
            CompiledRecipe r = graph.recipeByCompactId(cid).orElse(null);
            if (r == null) continue;

            for (InputSlot in : r.inputs()) {
                if (in.tagId().isPresent()) {
                    String tid = in.tagId().get();
                    Set<ResourceKey> members = tagToMembers.computeIfAbsent(tid, k -> new TreeSet<>());
                    for (ResourceKey c : in.candidates()) {
                        members.add(c);
                        memberToTags.computeIfAbsent(c, k -> new TreeSet<>()).add(tid);
                    }
                }
            }

            if (r.canonicalTags().contains(CanonicalRecipeTag.junkOrDuplicate)
                    || r.canonicalTags().contains(CanonicalRecipeTag.recycling)
                    || r.canonicalTags().contains(CanonicalRecipeTag.recolor)
                    || r.canonicalTags().contains(CanonicalRecipeTag.variantSwap)
                    || r.isReversibleConversion()
                    || r.outputs().size() != 1) {
                continue;
            }

            // Require a single concrete trunk input (single-candidate slot) -> single guaranteed output.
            InputSlot trunk = findSingleTrunkSlot(r);
            if (trunk == null) {
                continue;
            }
            ResourceKey x = trunk.candidates().get(0);
            ResourceKey y = r.outputs().get(0).resource();
            if (x.equals(y)) {
                continue;
            }

            String tau = templateSignature(r, trunk);
            edgesByTau.computeIfAbsent(tau, k -> new HashMap<>())
                    .computeIfAbsent(x, k -> new TreeSet<>())
                    .add(y);
            uniqueParents.computeIfAbsent(y, k -> new TreeSet<>()).add(x);
        }

        // 3. Discover Tag Homomorphism edges T' -> T via inverted member->tags index.
        List<TagHomomorphismEdge> homomorphisms = new ArrayList<>();
        Map<String, List<TagHomomorphismEdge>> incomingByTarget = new HashMap<>();

        for (var tauEntry : edgesByTau.entrySet()) {
            String tau = tauEntry.getKey();
            Map<ResourceKey, Set<ResourceKey>> mapping = tauEntry.getValue();
            if (mapping.size() < defaultMinSupport) {
                continue;
            }

            // Accumulate pair counts (T', T) using inverted member -> tags.
            Map<String, Map<String, Set<ResourceKey>>> supportedSources = new HashMap<>();
            Map<String, Map<String, Integer>> totalEdgesIntoT = new HashMap<>();
            Map<String, Integer> totalEdgesFromSourceTag = new HashMap<>();

            for (var edge : mapping.entrySet()) {
                ResourceKey x = edge.getKey();
                Set<ResourceKey> ys = edge.getValue();
                Set<String> srcTags = memberToTags.get(x);
                if (srcTags == null || srcTags.isEmpty()) continue;

                for (String srcTag : srcTags) {
                    totalEdgesFromSourceTag.merge(srcTag, ys.size(), Integer::sum);
                    for (ResourceKey y : ys) {
                        Set<String> dstTags = memberToTags.get(y);
                        if (dstTags == null) continue;
                        for (String dstTag : dstTags) {
                            if (srcTag.equals(dstTag)) continue;
                            supportedSources.computeIfAbsent(srcTag, k -> new HashMap<>())
                                    .computeIfAbsent(dstTag, k -> new HashSet<>())
                                    .add(x);
                            totalEdgesIntoT.computeIfAbsent(srcTag, k -> new HashMap<>())
                                    .merge(dstTag, 1, Integer::sum);
                        }
                    }
                }
            }

            for (var srcEntry : supportedSources.entrySet()) {
                String srcTag = srcEntry.getKey();
                int srcSize = tagToMembers.getOrDefault(srcTag, Set.of()).size();
                if (srcSize == 0) continue;
                int totalFromSrc = Math.max(1, totalEdgesFromSourceTag.getOrDefault(srcTag, 1));

                for (var dstEntry : srcEntry.getValue().entrySet()) {
                    String dstTag = dstEntry.getKey();
                    Set<ResourceKey> support = dstEntry.getValue();
                    int supCount = support.size();
                    if (supCount < defaultMinSupport) continue;

                    double coverage = (double) supCount / srcSize;
                    int intoDst = totalEdgesIntoT.getOrDefault(srcTag, Map.of()).getOrDefault(dstTag, 0);
                    double purity = (double) intoDst / totalFromSrc;

                    int uniqueCount = 0;
                    for (ResourceKey x : support) {
                        Set<ResourceKey> dstMembers = tagToMembers.getOrDefault(dstTag, Set.of());
                        int matches = 0;
                        for (ResourceKey y : mapping.getOrDefault(x, Set.of())) {
                            if (dstMembers.contains(y)) {
                                matches++;
                            }
                        }
                        if (matches == 1) {
                            uniqueCount++;
                        }
                    }
                    double uniqueness = (double) uniqueCount / supCount;

                    if (coverage >= defaultMinCoverage && purity >= defaultMinPurity && uniqueness >= defaultMinUniqueness) {
                        boolean exact = (supCount == srcSize) && (intoDst == totalFromSrc) && (uniqueCount == supCount);
                        TagHomomorphismEdge hEdge = new TagHomomorphismEdge(
                            srcTag,
                            dstTag,
                            tau,
                            supCount,
                            coverage,
                            purity,
                            uniqueness,
                            exact
                        );
                        homomorphisms.add(hEdge);
                        incomingByTarget.computeIfAbsent(dstTag, k -> new ArrayList<>()).add(hEdge);
                    }
                }
            }
        }

        // 4. Propagate deterministic lineage roots (marking converging multi-root products AMBIGUOUS!).
        Map<ResourceKey, String> familyOf = new TreeMap<>();
        Set<ResourceKey> allNodes = new TreeSet<>(uniqueParents.keySet());
        for (Set<ResourceKey> parents : uniqueParents.values()) {
            allNodes.addAll(parents);
        }
        // Roots (no unique trunk parent) seed their own structural family ID.
        for (ResourceKey node : allNodes) {
            if (!uniqueParents.containsKey(node)) {
                familyOf.put(node, "fam:" + node.canonicalString());
            }
        }
        // Bounded DAG propagation; converging distinct families mark AMBIGUOUS and never merge roots.
        for (int round = 0; round < 12; round++) {
            boolean changed = false;
            for (var entry : uniqueParents.entrySet()) {
                ResourceKey child = entry.getKey();
                Set<String> parentFams = new HashSet<>();
                for (ResourceKey p : entry.getValue()) {
                    String pf = familyOf.get(p);
                    if (pf != null && !unknownFamily.equals(pf)) {
                        parentFams.add(pf);
                    }
                }
                if (parentFams.isEmpty()) continue;
                String resolved = (parentFams.size() == 1 && !parentFams.contains(ambiguousFamily))
                        ? parentFams.iterator().next()
                        : ambiguousFamily;
                String prev = familyOf.put(child, resolved);
                if (!resolved.equals(prev)) {
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // 5. Build TagSemanticProfiles.
        Map<String, TagSemanticProfile> profiles = new TreeMap<>();
        for (var entry : tagToMembers.entrySet()) {
            String tid = entry.getKey();
            Set<ResourceKey> members = entry.getValue();
            int prodCount = 0;
            Set<String> distinctFams = new HashSet<>();
            for (ResourceKey m : members) {
                if (!graph.producersOf(m).isEmpty()) {
                    prodCount++;
                }
                String f = familyOf.get(m);
                if (f != null && !unknownFamily.equals(f) && !ambiguousFamily.equals(f)) {
                    distinctFams.add(f);
                }
            }
            List<TagHomomorphismEdge> incoming = incomingByTarget.getOrDefault(tid, List.of());
            TagEquivalenceClass eqClass;
            if (members.size() <= 4 && distinctFams.size() <= 1) {
                eqClass = TagEquivalenceClass.uniformEquivalent;
            } else if (!incoming.isEmpty() || distinctFams.size() >= Math.max(2, members.size() / 2)) {
                eqClass = TagEquivalenceClass.multiFamilySymmetric;
            } else {
                eqClass = TagEquivalenceClass.heterogeneousBroad;
            }
            profiles.put(tid, new TagSemanticProfile(
                tid,
                members.size(),
                prodCount,
                distinctFams.size(),
                eqClass,
                List.copyOf(incoming)
            ));
        }

        return new LineageIndex(
            Collections.unmodifiableMap(familyOf),
            Collections.unmodifiableMap(profiles),
            List.copyOf(homomorphisms)
        );
    }

    private static @Nullable InputSlot findSingleTrunkSlot(CompiledRecipe r) {
        InputSlot single = null;
        for (InputSlot in : r.inputs()) {
            if (in.candidates().size() == 1) {
                if (single == null || in.amountPerBatch() > single.amountPerBatch()) {
                    single = in;
                }
            }
        }
        // Only treat as deterministic single-trunk when there is at most one non-aux variable slot.
        if (single == null) return null;
        int multiCandSlots = 0;
        for (InputSlot in : r.inputs()) {
            if (in.candidates().size() > 1) {
                multiCandSlots++;
            }
        }
        return multiCandSlots == 0 ? single : null;
    }

    private static String templateSignature(CompiledRecipe r, InputSlot trunk) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.key().systemId()).append("|in=").append(r.inputs().size())
                .append("|trunkAmt=").append(trunk.amountPerBatch())
                .append("|outAmt=").append(r.outputs().get(0).amountPerBatch());
        for (InputSlot s : r.inputs()) {
            if (s != trunk && s.candidates().size() == 1) {
                sb.append("|aux=").append(s.candidates().get(0).canonicalString()).append("x").append(s.amountPerBatch());
            }
        }
        return sb.toString();
    }
}
