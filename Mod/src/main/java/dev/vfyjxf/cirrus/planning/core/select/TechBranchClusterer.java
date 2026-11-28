package dev.vfyjxf.cirrus.planning.core.select;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic IDF-weighted sparse tech-branch clusterer (zero mod hardcoding).
 *
 * <p>Clusters recipes into parallel technology paradigms in O(k_p * L_c) by comparing against at most
 * k_p = 8 prototypes per primary inverted bucket using weighted Jaccard similarity >= 0.60.
 */
public final class TechBranchClusterer {

    public static final double defaultSimilarityThreshold = 0.60;
    public static final int defaultMaxPrototypesPerBucket = 8;
    public static final String sharedCommonCluster = "cluster:shared_common";

    public record ClusterAssignment(
        Map<Integer, String> clusterByRecipeCompactId,
        Map<String, Set<String>> prototypeTokensByCluster
    ) {
        public static final ClusterAssignment empty = new ClusterAssignment(Map.of(), Map.of());

        public String clusterOf(int compactId) {
            return clusterByRecipeCompactId.getOrDefault(compactId, sharedCommonCluster);
        }
    }

    private record Prototype(String clusterId, Map<String, Double> weightedTokens) {}

    private TechBranchClusterer() {}

    public static ClusterAssignment cluster(CompiledRecipeGraph graph) {
        Objects.requireNonNull(graph, "graph");
        int n = graph.stats().compiledRecipes();
        if (n == 0) {
            return ClusterAssignment.empty;
        }

        // 1. Extract sparse feature tokens per recipe & compute document frequency for frozen IDF.
        List<List<String>> rawTokens = new ArrayList<>(n);
        Map<String, Integer> docFreq = new HashMap<>();
        int activeDocs = 0;

        for (int cid = 0; cid < n; cid++) {
            CompiledRecipe r = graph.recipeByCompactId(cid).orElse(null);
            if (r == null) {
                rawTokens.add(List.of());
                continue;
            }
            activeDocs++;
            List<String> toks = extractTokens(r);
            rawTokens.add(toks);
            for (String tok : toks) {
                docFreq.merge(tok, 1, Integer::sum);
            }
        }
        int totalDocs = Math.max(1, activeDocs);

        // 2. Build IDF-weighted sparse vectors & sort recipes canonically for order-independence.
        Map<String, List<Prototype>> buckets = new HashMap<>();
        Map<Integer, String> assignments = new HashMap<>(n * 2);
        Map<String, Set<String>> protoTokenView = new LinkedHashMap<>();
        int nextClusterNum = 0;

        for (int cid = 0; cid < n; cid++) {
            List<String> toks = rawTokens.get(cid);
            if (toks.isEmpty()) {
                assignments.put(cid, sharedCommonCluster);
                continue;
            }
            Map<String, Double> vec = new TreeMap<>();
            String rarestCoreKey = toks.get(0);
            double maxIdf = -1.0;
            for (String tok : toks) {
                int df = docFreq.getOrDefault(tok, 1);
                double idf = 1.0 + StrictMath.log((totalDocs + 1.0) / (df + 1.0));
                vec.put(tok, idf);
                if (idf > maxIdf || (Math.abs(idf - maxIdf) <= 1e-12 && tok.compareTo(rarestCoreKey) < 0)) {
                    maxIdf = idf;
                    rarestCoreKey = tok;
                }
            }

            List<Prototype> bucket = buckets.computeIfAbsent(rarestCoreKey, k -> new ArrayList<>());
            String bestCluster = null;
            double bestSim = defaultSimilarityThreshold;
            String fallbackCluster = null;
            double fallbackSim = -1.0;
            int limit = Math.min(defaultMaxPrototypesPerBucket, bucket.size());
            for (int i = 0; i < limit; i++) {
                Prototype p = bucket.get(i);
                double sim = weightedJaccardSimilarity(vec, p.weightedTokens());
                if (sim >= bestSim) {
                    bestSim = sim;
                    bestCluster = p.clusterId();
                }
                if (sim > fallbackSim) {
                    fallbackSim = sim;
                    fallbackCluster = p.clusterId();
                }
            }

            if (bestCluster == null) {
                if (bucket.size() < defaultMaxPrototypesPerBucket) {
                    bestCluster = "cluster:" + (nextClusterNum++) + ":" + rarestCoreKey;
                    bucket.add(new Prototype(bestCluster, vec));
                    protoTokenView.put(bestCluster, Collections.unmodifiableSet(new TreeSet<>(vec.keySet())));
                } else {
                    bestCluster = fallbackCluster != null ? fallbackCluster : sharedCommonCluster;
                }
            }
            assignments.put(cid, bestCluster);
        }

        return new ClusterAssignment(
            Collections.unmodifiableMap(assignments),
            Collections.unmodifiableMap(protoTokenView)
        );
    }

    private static List<String> extractTokens(CompiledRecipe r) {
        TreeSet<String> tokens = new TreeSet<>();
        if (r.machineRequirement().handCraftable()) {
            return List.of(); // Hand-craftable / universal recipes stay in sharedCommonCluster.
        }
        tokens.add("sys:" + r.key().systemId());
        r.machineRequirement().capabilityId().ifPresent(cap -> tokens.add("cap:" + cap.id()));
        for (Namespace tag : r.systemTags()) {
            tokens.add("stag:" + tag);
        }
        if (!r.catalysts().isEmpty()) {
            tokens.add("has_cat:" + r.catalysts().size());
        }
        return List.copyOf(tokens);
    }

    public static double weightedJaccardSimilarity(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        double num = 0.0;
        double den = 0.0;
        for (var e : a.entrySet()) {
            double va = e.getValue();
            double vb = b.getOrDefault(e.getKey(), 0.0);
            num += Math.min(va, vb);
            den += Math.max(va, vb);
        }
        for (var e : b.entrySet()) {
            if (!a.containsKey(e.getKey())) {
                den += e.getValue();
            }
        }
        return den <= 0.0 ? 1.0 : num / den;
    }
}
