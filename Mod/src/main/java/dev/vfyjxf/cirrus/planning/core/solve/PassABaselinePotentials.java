package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * M2.1 Pass A: Inventory-Free Layered Monotonic Baseline Potential Solver (Pure JDK 21).
 *
 * <p>Implements:
 * - Layered relaxation p^{d+1}_v = min(p^d_v, min_{r -> v} (h_r + sum_s a_{rs} min_{u in A_{rs}} p^d_u) / b_{rv}),
 *   strictly superior to Knuth permanent labeling when multi-output recipes yield unit price < input price
 *   (e.g. input 10 -> 4 outputs = 2.5 < 10).
 * - Bootable hypergraph reachability closure (unseeded cycles remain +infinity).
 * - Isolated reversible storage ratio conservation u (u . b = u . a) with closed-cycle reserve pricing
 *   enabled ONLY when provably no cheaper external entry exists.
 */
public final class PassABaselinePotentials {

    public static final int defaultMaxLayers = 24;
    public static final double defaultStepActionSeconds = 1.0;

    public record Witness(
        int layerDepth,
        int producerCompactId,
        double unitPriceSeconds,
        boolean fromExternalSource,
        boolean depthLimited
    ) {}

    public record BaselineResult(
        Map<ResourceKey, Double> unitPrices,
        Map<ResourceKey, Witness> witnesses,
        Set<ResourceKey> bootableResources,
        Map<ResourceKey, Double> closedCycleReservePrices,
        boolean hitDepthLimit
    ) {
        public double priceOf(ResourceKey key) {
            return unitPrices.getOrDefault(key, Double.POSITIVE_INFINITY);
        }

        public double reserveOrPriceOf(ResourceKey key) {
            double p = priceOf(key);
            if (Double.isFinite(p)) {
                return p;
            }
            return closedCycleReservePrices.getOrDefault(key, Double.POSITIVE_INFINITY);
        }
    }

    private PassABaselinePotentials() {}

    public static BaselineResult solve(
        CompiledRecipeGraph graph,
        AcquisitionOracle oracle,
        ProgressionState progression,
        Map<ResourceKey, Double> closedFamilyEvidenceAnchors,
        int maxLayers
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(oracle, "oracle");
        ProgressionState prog = progression == null ? ProgressionState.overworldDefault : progression;
        Map<ResourceKey, Double> reserveEvidence = closedFamilyEvidenceAnchors == null ? Map.of() : closedFamilyEvidenceAnchors;
        int depthCap = Math.max(4, maxLayers);

        // Collect all known resources & trusted recipes (exclude suspicious JEI / junk from trusted baseline).
        Set<ResourceKey> allResources = new HashSet<>(graph.allProducibleResources());
        int totalRecipes = graph.stats().compiledRecipes();
        List<CompiledRecipe> trustedRecipes = new ArrayList<>(totalRecipes);
        for (int cid = 0; cid < totalRecipes; cid++) {
            CompiledRecipe r = graph.recipeByCompactId(cid).orElse(null);
            if (r == null) continue;
            if (r.isSuspiciousJei() || r.canonicalTags().contains(CanonicalRecipeTag.junkOrDuplicate)) {
                continue;
            }
            trustedRecipes.add(r);
            for (InputSlot in : r.inputs()) {
                allResources.addAll(in.candidates());
            }
            for (OutputSlot out : r.outputs()) {
                allResources.add(out.resource());
            }
        }

        // Layer 0: approved external unit prices (deterministic bootable seeds).
        Map<ResourceKey, Double> curPrice = new HashMap<>(allResources.size() * 2);
        Map<ResourceKey, Witness> bestWitness = new HashMap<>(allResources.size() * 2);
        Set<ResourceKey> bootable = new HashSet<>();

        List<ResourceKey> sortedRes = new ArrayList<>(allResources);
        Collections.sort(sortedRes);
        for (ResourceKey rk : sortedRes) {
            Optional<AcquisitionOption> opt = oracle.assess(rk, 1L, prog);
            if (opt.isPresent() && opt.get().guaranteedQuantity() > 0L) {
                AcquisitionOption o = opt.get();
                double unitSec = o.fixedTripHumanSeconds() + o.humanSecondsPerUnit()
                        + 0.25 * o.passiveSecondsPerUnit() + 0.80 * o.scarcityPerUnit();
                curPrice.put(rk, unitSec);
                bestWitness.put(rk, new Witness(0, -1, unitSec, true, false));
                bootable.add(rk);
            }
        }

        // Bootable reachability closure on the directed hypergraph (AND over slots, OR over slot candidates).
        boolean bootChanged = true;
        while (bootChanged) {
            bootChanged = false;
            for (CompiledRecipe r : trustedRecipes) {
                if (r.canonicalTags().contains(CanonicalRecipeTag.packingUnpacking)) {
                    // Storage packing still needs its input bootable; check normally.
                }
                boolean inputsBootable = true;
                for (InputSlot in : r.inputs()) {
                    boolean anyCand = false;
                    for (ResourceKey c : in.candidates()) {
                        if (bootable.contains(c)) {
                            anyCand = true;
                            break;
                        }
                    }
                    if (!anyCand) {
                        inputsBootable = false;
                        break;
                    }
                }
                if (!inputsBootable) {
                    continue;
                }
                for (OutputSlot out : r.outputs()) {
                    if (bootable.add(out.resource())) {
                        bootChanged = true;
                    }
                }
            }
        }

        // Layered double-buffered monotonic relaxation: read prev layer, write next layer.
        boolean hitDepthLimit = false;
        for (int layer = 0; layer < depthCap; layer++) {
            Map<ResourceKey, Double> nextPrice = new HashMap<>(curPrice);
            boolean anyImproved = false;

            for (CompiledRecipe r : trustedRecipes) {
                double inSum = 0.0;
                boolean allFinite = true;
                for (InputSlot in : r.inputs()) {
                    double minSlot = Double.POSITIVE_INFINITY;
                    for (ResourceKey c : in.candidates()) {
                        double pc = curPrice.getOrDefault(c, Double.POSITIVE_INFINITY);
                        if (pc < minSlot) {
                            minSlot = pc;
                        }
                    }
                    if (!Double.isFinite(minSlot)) {
                        allFinite = false;
                        break;
                    }
                    inSum += in.amountPerBatch() * minSlot;
                }
                if (!allFinite) {
                    continue;
                }
                double stepOverhead = r.canonicalTags().contains(CanonicalRecipeTag.packingUnpacking)
                        ? 0.5 * defaultStepActionSeconds
                        : defaultStepActionSeconds;
                double batchCost = stepOverhead + inSum;
                for (OutputSlot out : r.outputs()) {
                    long b = out.amountPerBatch();
                    if (b <= 0L) continue;
                    ResourceKey v = out.resource();
                    if (!bootable.contains(v)) continue;
                    double quote = batchCost / b;
                    double prev = nextPrice.getOrDefault(v, Double.POSITIVE_INFINITY);
                    Witness curW = bestWitness.get(v);
                    boolean strictlyCheaper = quote + 1e-12 < prev;
                    boolean tieBetter = Math.abs(quote - prev) <= 1e-12
                            && curW != null
                            && (layer + 1 < curW.layerDepth()
                                    || (layer + 1 == curW.layerDepth() && r.compactId() < curW.producerCompactId()));
                    if (strictlyCheaper || tieBetter) {
                        nextPrice.put(v, quote);
                        bestWitness.put(v, new Witness(layer + 1, r.compactId(), quote, false, false));
                        anyImproved = true;
                    }
                }
            }

            curPrice = nextPrice;
            if (!anyImproved) {
                break;
            }
            if (layer == depthCap - 1) {
                hitDepthLimit = true;
            }
        }

        // Closed-cycle reserve pricing (M2.1): ONLY for isolated reversible storage clusters
        // where NO member is bootable (no external entry exists), using conserved ratio vector u.
        Map<ResourceKey, Double> closedReserves = computeClosedCycleReserves(trustedRecipes, bootable, reserveEvidence);

        return new BaselineResult(
            Collections.unmodifiableMap(new TreeMap<>(curPrice)),
            Collections.unmodifiableMap(new TreeMap<>(bestWitness)),
            Collections.unmodifiableSet(bootable),
            Collections.unmodifiableMap(new TreeMap<>(closedReserves)),
            hitDepthLimit
        );
    }

    /**
     * Identifies isolated reversible 1-to-1 conversion components with zero external bootable entries,
     * checks ratio conservation u_out * b = u_in * a, and assigns evidence-backed reserve prices p^{reserve}_v = a_C * u_v.
     */
    private static Map<ResourceKey, Double> computeClosedCycleReserves(
        List<CompiledRecipe> recipes,
        Set<ResourceKey> bootable,
        Map<ResourceKey, Double> reserveEvidence
    ) {
        if (reserveEvidence.isEmpty()) {
            return Map.of();
        }
        // Build undirected conversion adjacency among non-bootable 1:1 packing/conversion edges.
        record RatioEdge(ResourceKey to, double ratioToOverFrom) {}
        Map<ResourceKey, List<RatioEdge>> adj = new HashMap<>();
        for (CompiledRecipe r : recipes) {
            if (r.inputs().size() != 1 || r.outputs().size() != 1) continue;
            InputSlot in = r.inputs().get(0);
            if (in.candidates().size() != 1) continue;
            ResourceKey u = in.candidates().get(0);
            ResourceKey v = r.outputs().get(0).resource();
            if (u.equals(v)) continue;
            if (bootable.contains(u) || bootable.contains(v)) continue;
            double ratio = (double) in.amountPerBatch() / Math.max(1L, r.outputs().get(0).amountPerBatch());
            adj.computeIfAbsent(u, k -> new ArrayList<>()).add(new RatioEdge(v, ratio));
            adj.computeIfAbsent(v, k -> new ArrayList<>()).add(new RatioEdge(u, 1.0 / ratio));
        }

        Map<ResourceKey, Double> reserves = new HashMap<>();
        Set<ResourceKey> visited = new HashSet<>();
        for (ResourceKey start : adj.keySet()) {
            if (!visited.add(start)) continue;
            Map<ResourceKey, Double> unitScale = new HashMap<>();
            unitScale.put(start, 1.0);
            ArrayDeque<ResourceKey> queue = new ArrayDeque<>();
            queue.add(start);
            boolean consistent = true;
            while (!queue.isEmpty()) {
                ResourceKey cur = queue.poll();
                double curU = unitScale.getOrDefault(cur, 1.0);
                for (RatioEdge edge : adj.getOrDefault(cur, List.of())) {
                    double expected = curU * edge.ratioToOverFrom();
                    Double existing = unitScale.get(edge.to());
                    if (existing == null) {
                        unitScale.put(edge.to(), expected);
                        visited.add(edge.to());
                        queue.add(edge.to());
                    } else if (Math.abs(existing - expected) / Math.max(1e-9, expected) > 1e-6) {
                        consistent = false;
                    }
                }
            }
            if (!consistent) {
                continue;
            }
            // Anchor solely from explicit evidence inside THIS isolated cluster (never from global cheapest leaf!).
            double anchorPerUnit = Double.POSITIVE_INFINITY;
            for (var e : unitScale.entrySet()) {
                Double ev = reserveEvidence.get(e.getKey());
                if (ev != null && Double.isFinite(ev) && ev > 0.0) {
                    double candidateAnchor = ev / e.getValue();
                    if (candidateAnchor < anchorPerUnit) {
                        anchorPerUnit = candidateAnchor;
                    }
                }
            }
            if (Double.isFinite(anchorPerUnit)) {
                for (var e : unitScale.entrySet()) {
                    reserves.put(e.getKey(), anchorPerUnit * e.getValue());
                }
            }
        }
        return reserves;
    }
}
