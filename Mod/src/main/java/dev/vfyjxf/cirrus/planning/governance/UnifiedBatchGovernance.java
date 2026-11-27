package dev.vfyjxf.cirrus.planning.governance;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.planning.normalize.JeiNormalizationPatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Unified Batch Recipe Filtering & Human-Governance Workbench Engine (Planning v3 §8.4):
 * 1. System / JEI Category Triage Board
 * 2. Composable No-Regex Faceted Recipe Filter
 * 3. "Find Similar" 4-Tier Interactive Generalization (fused with H5 human verification)
 * 4. Orphan Protection Guard (unique-producer safety net before bulk banning)
 * 5. Pack Curation Bundle (importable/exportable pack governance payload)
 */
public final class UnifiedBatchGovernance {

    /** Tool 1: Per-system triage summary for JEI Category / System Triage Board. */
    public record SystemTriageSummary(
        String systemId,
        int totalRecipes,
        int suspiciousCount,
        int junkCount,
        double averageFidelity,
        Set<String> systemTags,
        int uniqueOutputCountIfDisabled
    ) {}

    /** Tool 2: No-regex composable multi-criteria recipe filter. */
    public record FacetedFilterSpec(
        Set<String> modNamespaces,
        Set<String> systemIds,
        Set<String> requiredSystemTags,
        Set<CanonicalRecipeTag> anyCanonicalTags,
        ResourceKey containsInputOrNull,
        ResourceKey containsOutputOrNull,
        String containsInputTagOrNull,
        Integer minInputSlots,
        Integer maxInputSlots,
        Double maxFidelityExclusive,
        Boolean missingExplicitIdOnly,
        String keywordSubstring
    ) {
        public static FacetedFilterSpec matchAll() {
            return new FacetedFilterSpec(Set.of(), Set.of(), Set.of(), Set.of(), null, null, null, null, null, null, null, "");
        }

        public boolean matches(CompiledRecipe r) {
            if (modNamespaces != null && !modNamespaces.isEmpty()) {
                String ns = extractNamespace(r.key().explicitRecipeId().orElse(r.key().systemId()));
                if (!modNamespaces.contains(ns)) return false;
            }
            if (systemIds != null && !systemIds.isEmpty() && !systemIds.contains(r.key().systemId())) {
                return false;
            }
            if (requiredSystemTags != null && !requiredSystemTags.isEmpty() && !r.systemTags().containsAll(requiredSystemTags)) {
                return false;
            }
            if (anyCanonicalTags != null && !anyCanonicalTags.isEmpty()) {
                boolean any = false;
                for (CanonicalRecipeTag t : anyCanonicalTags) {
                    if (r.canonicalTags().contains(t)) {
                        any = true;
                        break;
                    }
                }
                if (!any) return false;
            }
            if (containsInputOrNull != null) {
                boolean found = false;
                for (var slot : r.inputs()) {
                    if (slot.candidates().contains(containsInputOrNull)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            if (containsOutputOrNull != null) {
                boolean found = false;
                for (var out : r.outputs()) {
                    if (out.resource().equals(containsOutputOrNull)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            if (containsInputTagOrNull != null && !containsInputTagOrNull.isBlank()) {
                boolean found = false;
                for (var slot : r.inputs()) {
                    if (slot.tagId().isPresent() && slot.tagId().get().equals(containsInputTagOrNull)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            if (minInputSlots != null && r.inputs().size() < minInputSlots) return false;
            if (maxInputSlots != null && r.inputs().size() > maxInputSlots) return false;
            if (maxFidelityExclusive != null && r.structuralFidelity() >= maxFidelityExclusive) return false;
            if (Boolean.TRUE.equals(missingExplicitIdOnly) && r.key().explicitRecipeId().isPresent()) return false;
            if (keywordSubstring != null && !keywordSubstring.isBlank()) {
                String kw = keywordSubstring.toLowerCase(Locale.ROOT);
                String hay = (r.key().systemId() + " " + r.key().explicitRecipeId().orElse("")).toLowerCase(Locale.ROOT);
                if (!hay.contains(kw)) return false;
            }
            return true;
        }
    }

    /** Tool 3: 4-Tier "Find Similar" generalization result fused with H5 prompt. */
    public enum SimilarityTier {
        singleRecipe,
        sameTemplateLineage,
        sameSystemAndPrimaryInput,
        entireSystemCategory
    }

    public record SimilarityBucket(
        SimilarityTier tier,
        int matchCount,
        List<CompiledRecipeKey> sampleKeys,
        List<CompiledRecipe> allMatches,
        OrphanImpactReport orphanImpact
    ) {}

    /** Tool 4: Orphan Protection Guard pre-commit report. */
    public record OrphanImpactReport(
        int requestedBanCount,
        Set<ResourceKey> orphanedResourcesIfUnprotected,
        List<CompiledRecipe> safeRecipesWithExemption,
        int exemptedUniqueProducerCount
    ) {}

    /** Tool 5: Shareable Pack Curation Bundle. */
    public record PackCurationBundle(
        String bundleVersion,
        String packFingerprint,
        List<GuidanceRule> rules,
        List<JeiNormalizationPatch> jeiPatches
    ) {
        public PackCurationBundle {
            Objects.requireNonNull(bundleVersion, "bundleVersion");
            Objects.requireNonNull(packFingerprint, "packFingerprint");
            rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
            jeiPatches = List.copyOf(Objects.requireNonNull(jeiPatches, "jeiPatches"));
        }
    }

    private UnifiedBatchGovernance() {}

    /** Tool 1: Builds per-system triage board sorted by suspicious/junk ratio descending. */
    public static List<SystemTriageSummary> buildSystemTriageBoard(CompiledRecipeGraph graph) {
        Objects.requireNonNull(graph, "graph");
        Map<String, List<CompiledRecipe>> bySys = new TreeMap<>();
        int count = graph.stats().compiledRecipes();
        for (int cid = 0; cid < count; cid++) {
            CompiledRecipe r = graph.recipeByCompactId(cid).orElse(null);
            if (r != null) {
                bySys.computeIfAbsent(r.key().systemId(), k -> new ArrayList<>()).add(r);
            }
        }
        List<SystemTriageSummary> summaries = new ArrayList<>(bySys.size());
        for (var e : bySys.entrySet()) {
            String sysId = e.getKey();
            List<CompiledRecipe> list = e.getValue();
            int susp = 0;
            int junk = 0;
            double fidSum = 0.0;
            TreeSet<String> tags = new TreeSet<>();
            for (CompiledRecipe r : list) {
                if (r.isSuspiciousJei()) susp++;
                if (r.canonicalTags().contains(CanonicalRecipeTag.junkOrDuplicate)) junk++;
                fidSum += r.structuralFidelity();
                tags.addAll(r.systemTags());
            }
            OrphanImpactReport orphan = previewOrphanImpact(graph, list);
            summaries.add(new SystemTriageSummary(
                sysId,
                list.size(),
                susp,
                junk,
                list.isEmpty() ? 1.0 : fidSum / list.size(),
                Set.copyOf(tags),
                orphan.orphanedResourcesIfUnprotected().size()
            ));
        }
        summaries.sort(Comparator.comparingInt(SystemTriageSummary::suspiciousCount).reversed()
                .thenComparing(Comparator.comparingInt(SystemTriageSummary::totalRecipes).reversed())
                .thenComparing(SystemTriageSummary::systemId));
        return List.copyOf(summaries);
    }

    /** Tool 2: Runs composable multi-criteria filter across compiled graph. */
    public static List<CompiledRecipe> filterRecipes(CompiledRecipeGraph graph, FacetedFilterSpec spec) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(spec, "spec");
        List<CompiledRecipe> matches = new ArrayList<>();
        int count = graph.stats().compiledRecipes();
        for (int cid = 0; cid < count; cid++) {
            CompiledRecipe r = graph.recipeByCompactId(cid).orElse(null);
            if (r != null && spec.matches(r)) {
                matches.add(r);
            }
        }
        return List.copyOf(matches);
    }

    /** Tool 3: Computes 4-tier "Find Similar" buckets for a clicked recipe (fused with H5). */
    public static List<SimilarityBucket> findSimilarBuckets(CompiledRecipeGraph graph, CompiledRecipe anchor) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(anchor, "anchor");
        String anchorSys = anchor.key().systemId();
        String anchorLineage = anchor.semanticKey().map(k -> k.lineageGroup())
                .orElse(anchorSys + "|in=" + anchor.inputs().size() + "|out=" + anchor.outputs().size());
        ResourceKey primaryIn = anchor.inputs().isEmpty() || anchor.inputs().get(0).candidates().isEmpty()
                ? null
                : anchor.inputs().get(0).candidates().get(0);

        List<CompiledRecipe> t1 = List.of(anchor);
        List<CompiledRecipe> t2 = new ArrayList<>();
        List<CompiledRecipe> t3 = new ArrayList<>();
        List<CompiledRecipe> t4 = new ArrayList<>();

        int count = graph.stats().compiledRecipes();
        for (int cid = 0; cid < count; cid++) {
            CompiledRecipe r = graph.recipeByCompactId(cid).orElse(null);
            if (r == null) continue;
            if (!r.key().systemId().equals(anchorSys)) continue;
            t4.add(r);
            String lin = r.semanticKey().map(k -> k.lineageGroup())
                    .orElse(r.key().systemId() + "|in=" + r.inputs().size() + "|out=" + r.outputs().size());
            if (lin.equals(anchorLineage)) {
                t2.add(r);
            }
            if (primaryIn != null) {
                for (var slot : r.inputs()) {
                    if (slot.candidates().contains(primaryIn)) {
                        t3.add(r);
                        break;
                    }
                }
            }
        }

        return List.of(
            buildBucket(graph, SimilarityTier.singleRecipe, t1),
            buildBucket(graph, SimilarityTier.sameTemplateLineage, t2),
            buildBucket(graph, SimilarityTier.sameSystemAndPrimaryInput, t3.isEmpty() ? t1 : t3),
            buildBucket(graph, SimilarityTier.entireSystemCategory, t4)
        );
    }

    /** Tool 4: Previews orphan impact and computes safe ban subset when unique-producer exemption is enabled. */
    public static OrphanImpactReport previewOrphanImpact(CompiledRecipeGraph graph, List<CompiledRecipe> candidatesToBan) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(candidatesToBan, "candidatesToBan");
        Set<Integer> banCompactIds = new HashSet<>(candidatesToBan.size() * 2);
        Map<ResourceKey, Integer> bannedProdCountByRes = new HashMap<>();
        for (CompiledRecipe r : candidatesToBan) {
            if (banCompactIds.add(r.compactId())) {
                for (var out : r.outputs()) {
                    bannedProdCountByRes.merge(out.resource(), 1, Integer::sum);
                }
            }
        }

        Set<ResourceKey> orphaned = new TreeSet<>();
        for (var e : bannedProdCountByRes.entrySet()) {
            int totalProds = graph.producersOf(e.getKey()).size();
            if (totalProds > 0 && e.getValue() >= totalProds) {
                orphaned.add(e.getKey());
            }
        }

        List<CompiledRecipe> safeList = new ArrayList<>(candidatesToBan.size());
        int exempted = 0;
        for (CompiledRecipe r : candidatesToBan) {
            boolean protectsOrphan = false;
            for (var out : r.outputs()) {
                if (orphaned.contains(out.resource())) {
                    protectsOrphan = true;
                    break;
                }
            }
            if (protectsOrphan) {
                exempted++;
            } else {
                safeList.add(r);
            }
        }
        return new OrphanImpactReport(
            candidatesToBan.size(),
            Set.copyOf(orphaned),
            List.copyOf(safeList),
            exempted
        );
    }

    /** Compiles bulk ban selection into clean GovernanceRules (with optional orphan exemption). */
    public static List<GuidanceRule> compileBulkBanRules(
        CompiledRecipeGraph graph,
        List<CompiledRecipe> matched,
        boolean exemptUniqueProducers,
        GuidanceScope scope,
        String rulePrefix
    ) {
        OrphanImpactReport report = previewOrphanImpact(graph, matched);
        List<CompiledRecipe> targets = exemptUniqueProducers ? report.safeRecipesWithExemption() : matched;
        List<GuidanceRule> rules = new ArrayList<>(targets.size());
        for (CompiledRecipe r : targets) {
            rules.add(new RecipeRule(
                rulePrefix + ":" + r.key().shortString(),
                scope,
                RuleStrength.hardBan,
                r.key()
            ));
        }
        return List.copyOf(rules);
    }

    public static GuidanceRule compileSystemBanRule(String systemId, GuidanceScope scope) {
        return new SystemRule("rule:triage_ban:" + systemId, scope, RuleStrength.hardBan, systemId, false);
    }

    private static SimilarityBucket buildBucket(CompiledRecipeGraph graph, SimilarityTier tier, List<CompiledRecipe> matches) {
        List<CompiledRecipeKey> samples = matches.stream().limit(3).map(CompiledRecipe::key).toList();
        return new SimilarityBucket(tier, matches.size(), samples, List.copyOf(matches), previewOrphanImpact(graph, matches));
    }

    private static String extractNamespace(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : id;
    }
}
