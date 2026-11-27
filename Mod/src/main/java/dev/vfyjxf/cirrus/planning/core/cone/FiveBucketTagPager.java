package dev.vfyjxf.cirrus.planning.core.cone;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * M3 Five-Bucket Bounded Tag Pager with Lazy Doubling (Pure JDK 21, ZERO wordlists).
 *
 * <p>Page size decays smoothly with depth: K(d) = max(K_min=5, ceil(K_0=12 / (1 + d / d_0=3))).
 * Guarantees at least 1 representative from every non-empty bucket (B1 Anchor, B2 Effective Supply,
 * B3 Inward 1-2 Hop Producible, B4 Certified Gatherable, B5 Cross-Family Diversity), then distributes
 * remaining quota proportionally by weights (2, 3, 3, 2, 2) with deduplication releasing unused slots.
 */
public final class FiveBucketTagPager {

    public static final int defaultK0 = 12;
    public static final int defaultKMin = 5;
    public static final double defaultD0 = 3.0;
    private static final int[] bucketWeights = {2, 3, 3, 2, 2};

    public record MemberSignals(
        ResourceKey resource,
        String familyId,
        String techClusterId,
        boolean anchorMatch,
        double anchorPriority,
        boolean hasEffectiveSupply,
        double supplyNetSavingsSeconds,
        boolean inwardProducible,
        double inwardBaselinePrice,
        boolean certifiedGatherable,
        double gatherPrice
    ) {
        public MemberSignals {
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(familyId, "familyId");
            Objects.requireNonNull(techClusterId, "techClusterId");
        }
    }

    public record PageResult(
        List<ResourceKey> selectedMembers,
        int pageNumber,
        int pageCapacity,
        int totalCandidateCount,
        boolean truncated,
        boolean hasMorePages
    ) {}

    private FiveBucketTagPager() {}

    public static int baseQuotaForDepth(int depth) {
        int d = Math.max(0, depth);
        int computed = (int) Math.ceil(defaultK0 / (1.0 + d / defaultD0));
        return Math.max(defaultKMin, computed);
    }

    /**
     * Computes page {@code pageIndex} (0-based; each lazy deepening doubles capacity: K, 2K, 4K, ...).
     */
    public static PageResult selectPage(List<MemberSignals> candidates, int depth, int pageIndex) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty()) {
            return new PageResult(List.of(), pageIndex, 0, 0, false, false);
        }
        int baseCap = baseQuotaForDepth(depth);
        int shift = Math.min(10, Math.max(0, pageIndex));
        int cap = Math.min(candidates.size(), baseCap << shift);
        if (candidates.size() <= cap) {
            List<ResourceKey> all = candidates.stream()
                    .map(MemberSignals::resource)
                    .distinct()
                    .sorted()
                    .toList();
            return new PageResult(all, pageIndex, cap, candidates.size(), false, false);
        }

        // Partition into 5 deterministic sorted buckets.
        List<MemberSignals> b1Anchor = new ArrayList<>();
        List<MemberSignals> b2Supply = new ArrayList<>();
        List<MemberSignals> b3Inward = new ArrayList<>();
        List<MemberSignals> b4Gather = new ArrayList<>();
        List<MemberSignals> b5Diverse = new ArrayList<>(candidates);

        for (MemberSignals m : candidates) {
            if (m.anchorMatch()) b1Anchor.add(m);
            if (m.hasEffectiveSupply()) b2Supply.add(m);
            if (m.inwardProducible()) b3Inward.add(m);
            if (m.certifiedGatherable()) b4Gather.add(m);
        }

        b1Anchor.sort(Comparator.comparingDouble(MemberSignals::anchorPriority).reversed()
                .thenComparing(MemberSignals::resource));
        b2Supply.sort(Comparator.comparingDouble(MemberSignals::supplyNetSavingsSeconds).reversed()
                .thenComparing(MemberSignals::resource));
        b3Inward.sort(Comparator.comparingDouble(MemberSignals::inwardBaselinePrice)
                .thenComparing(MemberSignals::resource));
        b4Gather.sort(Comparator.comparingDouble(MemberSignals::gatherPrice)
                .thenComparing(MemberSignals::resource));
        b5Diverse.sort(Comparator.comparingDouble(MemberSignals::inwardBaselinePrice)
                .thenComparing(MemberSignals::familyId)
                .thenComparing(MemberSignals::techClusterId)
                .thenComparing(MemberSignals::resource));

        List<List<MemberSignals>> buckets = List.of(b1Anchor, b2Supply, b3Inward, b4Gather, b5Diverse);
        int[] cursors = new int[5];

        LinkedHashSet<ResourceKey> picked = new LinkedHashSet<>(cap * 2);
        Set<String> coveredFamilies = new LinkedHashSet<>();
        Set<String> coveredClusters = new LinkedHashSet<>();

        // Pass 1: Guarantee 1 unique representative from every non-empty bucket (B1..B5).
        for (int b = 0; b < 5 && picked.size() < cap; b++) {
            pickNext(buckets.get(b), cursors, b, b == 4, picked, coveredFamilies, coveredClusters);
        }

        // Pass 2: Weighted round-robin by (2, 3, 3, 2, 2) until reaching cap or exhausting all buckets.
        boolean progress = true;
        while (picked.size() < cap && progress) {
            progress = false;
            for (int b = 0; b < 5 && picked.size() < cap; b++) {
                int w = bucketWeights[b];
                for (int step = 0; step < w && picked.size() < cap; step++) {
                    if (pickNext(buckets.get(b), cursors, b, b == 4, picked, coveredFamilies, coveredClusters)) {
                        progress = true;
                    } else {
                        break;
                    }
                }
            }
        }

        boolean truncated = picked.size() < candidates.size();
        return new PageResult(List.copyOf(picked), pageIndex, cap, candidates.size(), truncated, truncated);
    }

    private static boolean pickNext(
        List<MemberSignals> bucket,
        int[] cursors,
        int bucketIdx,
        boolean preferNovelFamilyOrCluster,
        Set<ResourceKey> picked,
        Set<String> coveredFamilies,
        Set<String> coveredClusters
    ) {
        if (preferNovelFamilyOrCluster) {
            for (int i = cursors[bucketIdx]; i < bucket.size(); i++) {
                MemberSignals m = bucket.get(i);
                if (picked.contains(m.resource())) continue;
                boolean novel = !coveredFamilies.contains(m.familyId()) || !coveredClusters.contains(m.techClusterId());
                if (novel) {
                    picked.add(m.resource());
                    coveredFamilies.add(m.familyId());
                    coveredClusters.add(m.techClusterId());
                    return true;
                }
            }
        }
        while (cursors[bucketIdx] < bucket.size()) {
            MemberSignals m = bucket.get(cursors[bucketIdx]++);
            if (picked.add(m.resource())) {
                coveredFamilies.add(m.familyId());
                coveredClusters.add(m.techClusterId());
                return true;
            }
        }
        return false;
    }
}
