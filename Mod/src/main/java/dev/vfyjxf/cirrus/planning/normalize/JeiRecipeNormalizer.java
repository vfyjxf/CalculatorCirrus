package dev.vfyjxf.cirrus.planning.normalize;

import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.spi.RecipeMetadataEnricher;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeSemanticKey;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.planning.core.trust.FidelityScorer;
import dev.vfyjxf.cirrus.planning.core.trust.RecipeSemanticKeyBuilder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 4-Stage Universal JEI Recipe Normalization & Anti-Fraud Fidelity Pipeline:
 * J1 Slot cleanup & SHA-256 semantic identity ->
 * J2 Cross-system I/O fingerprint mirror deduplication ->
 * J3 Contamination-proof M8 sibling fidelity scoring ->
 * J4 Declarative JeiNormalizationPatch & RecipeMetadataEnricher SPI overlay.
 */
public final class JeiRecipeNormalizer {

    public record NormalizationStats(
        int inputRecords,
        int outputRecords,
        int mirrorDuplicatesMarked,
        int suspiciousJeiMarked,
        int uniqueProducersProtected,
        int patchesApplied
    ) {}

    public record NormalizationResult(
        List<RawRecipeRecord> normalizedRecords,
        NormalizationStats stats
    ) {}

    private JeiRecipeNormalizer() {}

    public static NormalizationResult normalize(
        List<RawRecipeRecord> rawRecords,
        List<JeiNormalizationPatch> patches,
        List<RecipeMetadataEnricher> enrichers
    ) {
        if (rawRecords == null || rawRecords.isEmpty()) {
            return new NormalizationResult(List.of(), new NormalizationStats(0, 0, 0, 0, 0, 0));
        }
        List<JeiNormalizationPatch> activePatches = patches == null ? List.of() : patches;
        List<RecipeMetadataEnricher> activeEnrichers = enrichers == null ? List.of() : enrichers;

        // Stage J1 + J4 pre-pass: apply declarative patches & enrichers, compute SHA-256 semantic keys.
        List<RawRecipeRecord> staged = new ArrayList<>(rawRecords.size());
        Set<String> nativeIoFingerprints = new HashSet<>();
        Map<String, Integer> systemRecipeCount = new HashMap<>();
        Map<String, Integer> systemMissingIdCount = new HashMap<>();
        Map<ResourceKey, List<FidelityScorer.SiblingSample>> siblingSamplesByOut = new HashMap<>();
        Map<ResourceKey, Integer> producerCountByOut = new HashMap<>();
        int patchesApplied = 0;

        for (RawRecipeRecord rec : rawRecords) {
            RawRecipeRecord cur = rec;
            for (JeiNormalizationPatch patch : activePatches) {
                if (patch.matches(cur)) {
                    cur = applyPatch(cur, patch);
                    patchesApplied++;
                }
            }
            for (RecipeMetadataEnricher enricher : activeEnrichers) {
                RecipeMetadataEnricher.EnrichmentDelta delta = enricher.enrich(cur);
                if (delta != null && delta != RecipeMetadataEnricher.EnrichmentDelta.none) {
                    cur = applyEnrichment(cur, delta);
                }
            }
            RawRecipeRecord beforeKey = cur;
            RecipeSemanticKey semKey = beforeKey.semanticKey().orElseGet(() -> RecipeSemanticKeyBuilder.build(beforeKey));
            cur = new RawRecipeRecord(
                cur.systemId(),
                cur.sourceKind(),
                cur.explicitId(),
                cur.machineIds(),
                cur.inputs(),
                cur.outputs(),
                cur.recipeValues(),
                cur.sourceRef(),
                cur.systemTags(),
                cur.canonicalTags(),
                cur.evidenceTier(),
                cur.structuralFidelity(),
                Optional.of(semKey),
                cur.hasOpaqueConstraint()
            );
            staged.add(cur);

            systemRecipeCount.merge(cur.systemId(), 1, Integer::sum);
            if (cur.explicitId().isEmpty()) {
                systemMissingIdCount.merge(cur.systemId(), 1, Integer::sum);
            }

            boolean isJei = isJeiFallback(cur);
            if (!isJei && !cur.canonicalTags().contains(CanonicalRecipeTag.junkOrDuplicate)) {
                nativeIoFingerprints.add(RecipeSemanticKeyBuilder.ioFingerprint(cur));
            }

            double apparentUnitCost = estimateApparentUnitCost(cur);
            boolean feasibleAnchor = !cur.hasOpaqueConstraint()
                    && !cur.canonicalTags().contains(CanonicalRecipeTag.junkOrDuplicate)
                    && !cur.systemTags().contains(RecipeSystemTags.displayOnly.toString());
            for (RawOutputSlot out : cur.outputs()) {
                producerCountByOut.merge(out.resource(), 1, Integer::sum);
                siblingSamplesByOut.computeIfAbsent(out.resource(), k -> new ArrayList<>())
                        .add(new FidelityScorer.SiblingSample(
                            semKey.lineageGroup(),
                            cur.evidenceTier(),
                            apparentUnitCost / Math.max(1L, out.amount()),
                            feasibleAnchor
                        ));
            }
        }

        // Precompute contamination-proof M8 sibling anchors per output.
        Map<ResourceKey, FidelityScorer.SiblingAnchor> anchorByOut = new HashMap<>(siblingSamplesByOut.size() * 2);
        for (var entry : siblingSamplesByOut.entrySet()) {
            anchorByOut.put(entry.getKey(), FidelityScorer.computeSiblingAnchor(entry.getValue()));
        }

        // Stage J2 + J3: mirror dedup, structural fidelity scoring, dual-layer tagging, unique-producer safety net.
        List<RawRecipeRecord> outRecords = new ArrayList<>(staged.size());
        int mirrorCount = 0;
        int suspectCount = 0;
        int uniqueProtected = 0;

        for (RawRecipeRecord cur : staged) {
            boolean isJei = isJeiFallback(cur);
            String fp = RecipeSemanticKeyBuilder.ioFingerprint(cur);
            boolean mirrorDup = isJei && nativeIoFingerprints.contains(fp);

            int sysTotal = systemRecipeCount.getOrDefault(cur.systemId(), 1);
            int sysMissing = systemMissingIdCount.getOrDefault(cur.systemId(), 0);
            double missingIdRatio = (double) sysMissing / Math.max(1, sysTotal);

            double srcQuality = switch (cur.evidenceTier()) {
                case tierA -> 1.0;
                case tierB -> 0.85;
                case tierC -> missingIdRatio > 0.90 && sysTotal > 20 ? 0.45 : 0.68;
            };
            double inComplete = cur.inputs().isEmpty() ? 0.10 : 1.0;
            double outComplete = cur.outputs().isEmpty() ? 0.0 : (cur.outputs().size() >= 4 && isJei ? 0.35 : 1.0);
            double gateDesc = cur.hasOpaqueConstraint() ? 0.25
                    : (isJei && cur.machineIds().isEmpty() && !cur.systemTags().contains(RecipeSystemTags.handOperable.toString()) ? 0.55 : 0.95);
            double repro = cur.evidenceTier() == RecipeEvidenceTier.tierA ? 1.0 : 0.60;

            FidelityScorer.StructuralFeatures feats = new FidelityScorer.StructuralFeatures(
                srcQuality,
                inComplete,
                outComplete,
                gateDesc,
                repro
            );

            // Evaluate against primary output anchor (worst-case if multi-output).
            double apparentTotal = estimateApparentUnitCost(cur);
            double minPhi = 1.0;
            boolean anySuspect = false;
            boolean isUniqueProducer = false;
            for (RawOutputSlot out : cur.outputs()) {
                if (producerCountByOut.getOrDefault(out.resource(), 0) == 1) {
                    isUniqueProducer = true;
                }
                FidelityScorer.SiblingAnchor anchor = anchorByOut.getOrDefault(out.resource(), FidelityScorer.SiblingAnchor.none);
                FidelityScorer.FidelityAssessment assessment = FidelityScorer.assess(
                    feats,
                    cur.evidenceTier(),
                    apparentTotal / Math.max(1L, out.amount()),
                    cur.hasOpaqueConstraint() || cur.outputs().isEmpty(),
                    anchor
                );
                if (assessment.phiStruct() < minPhi) {
                    minPhi = assessment.phiStruct();
                }
                if (assessment.suspect()) {
                    anySuspect = true;
                }
            }

            // Respect explicit high-fidelity patch override.
            double finalPhi = cur.evidenceTier() == RecipeEvidenceTier.tierB && cur.structuralFidelity() >= 0.85
                    ? cur.structuralFidelity()
                    : minPhi;
            boolean suspectJei = isJei && (anySuspect || finalPhi < FidelityScorer.defaultSuspectThreshold);

            var assignments = DualTagClassifier.classify(cur, mirrorDup, suspectJei);
            EnumSet<CanonicalRecipeTag> finalTags = EnumSet.copyOf(DualTagClassifier.tagSet(assignments));

            // Unique producer safety net (M8): never silently drop the sole producer as junk;
            // keep it inspectable as suspiciousJei so planner produces CONDITIONAL / VERIFY_RECIPE.
            if (isUniqueProducer && finalTags.contains(CanonicalRecipeTag.junkOrDuplicate) && !mirrorDup) {
                finalTags.remove(CanonicalRecipeTag.junkOrDuplicate);
                finalTags.add(CanonicalRecipeTag.suspiciousJei);
                uniqueProtected++;
            }

            if (mirrorDup) {
                mirrorCount++;
            }
            if (finalTags.contains(CanonicalRecipeTag.suspiciousJei)) {
                suspectCount++;
            }

            outRecords.add(new RawRecipeRecord(
                cur.systemId(),
                cur.sourceKind(),
                cur.explicitId(),
                cur.machineIds(),
                cur.inputs(),
                cur.outputs(),
                cur.recipeValues(),
                cur.sourceRef(),
                cur.systemTags(),
                finalTags,
                cur.evidenceTier(),
                finalPhi,
                cur.semanticKey(),
                cur.hasOpaqueConstraint()
            ));
        }

        return new NormalizationResult(
            List.copyOf(outRecords),
            new NormalizationStats(rawRecords.size(), outRecords.size(), mirrorCount, suspectCount, uniqueProtected, patchesApplied)
        );
    }

    private static boolean isJeiFallback(RawRecipeRecord rec) {
        return "jei_fallback".equals(rec.sourceKind())
                || rec.systemTags().contains(RecipeSystemTags.jeiFallback.toString())
                || rec.systemId().startsWith("jei:");
    }

    /** Scale-invariant structural input proxy (sum of consumed input amounts + machine complexity proxy). */
    private static double estimateApparentUnitCost(RawRecipeRecord rec) {
        double cost = 1.0;
        for (RawIngredientSlot in : rec.inputs()) {
            if (!in.catalyst()) {
                cost += Math.min(1000.0, (double) in.amount());
            } else {
                cost += 0.25;
            }
        }
        return cost;
    }

    private static RawRecipeRecord applyPatch(RawRecipeRecord cur, JeiNormalizationPatch patch) {
        Set<String> sysTags = new LinkedHashSet<>(cur.systemTags());
        sysTags.addAll(patch.systemTagsToAdd());

        List<String> machines = new ArrayList<>(cur.machineIds());
        for (String m : patch.requiredMachineIdsToAdd()) {
            if (!machines.contains(m)) {
                machines.add(m);
            }
        }

        List<RawIngredientSlot> inputs = new ArrayList<>(cur.inputs().size());
        for (int i = 0; i < cur.inputs().size(); i++) {
            RawIngredientSlot s = cur.inputs().get(i);
            if (patch.catalystSlotIndices().contains(i) && !s.catalyst()) {
                inputs.add(new RawIngredientSlot(s.tagId(), s.candidates(), 0L, s.matchMode(), 0, 1));
            } else {
                inputs.add(s);
            }
        }

        EnumSet<CanonicalRecipeTag> cTags = cur.canonicalTags().isEmpty()
                ? EnumSet.noneOf(CanonicalRecipeTag.class)
                : EnumSet.copyOf(cur.canonicalTags());
        cTags.removeAll(patch.canonicalTagsToRemove());
        cTags.addAll(patch.canonicalTagsToAdd());

        RecipeEvidenceTier tier = patch.evidenceTierOverride() != null ? patch.evidenceTierOverride() : cur.evidenceTier();
        double phi = patch.fidelityOverride() != null ? patch.fidelityOverride() : cur.structuralFidelity();

        return new RawRecipeRecord(
            cur.systemId(),
            cur.sourceKind(),
            cur.explicitId(),
            machines,
            inputs,
            cur.outputs(),
            cur.recipeValues(),
            cur.sourceRef(),
            sysTags,
            cTags,
            tier,
            phi,
            cur.semanticKey(),
            cur.hasOpaqueConstraint()
        );
    }

    private static RawRecipeRecord applyEnrichment(RawRecipeRecord cur, RecipeMetadataEnricher.EnrichmentDelta delta) {
        Set<String> sysTags = new LinkedHashSet<>(cur.systemTags());
        sysTags.addAll(delta.systemTagsToAdd());
        EnumSet<CanonicalRecipeTag> cTags = cur.canonicalTags().isEmpty()
                ? EnumSet.noneOf(CanonicalRecipeTag.class)
                : EnumSet.copyOf(cur.canonicalTags());
        cTags.removeAll(delta.canonicalTagsToRemove());
        cTags.addAll(delta.canonicalTagsToAdd());
        RecipeEvidenceTier tier = delta.evidenceTierOverride() != null ? delta.evidenceTierOverride() : cur.evidenceTier();
        double phi = delta.fidelityOverride() != null ? delta.fidelityOverride() : cur.structuralFidelity();
        return new RawRecipeRecord(
            cur.systemId(),
            cur.sourceKind(),
            cur.explicitId(),
            cur.machineIds(),
            cur.inputs(),
            cur.outputs(),
            cur.recipeValues(),
            cur.sourceRef(),
            sysTags,
            cTags,
            tier,
            phi,
            cur.semanticKey(),
            cur.hasOpaqueConstraint()
        );
    }
}
