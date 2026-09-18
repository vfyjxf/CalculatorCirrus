package dev.vfyjxf.cirrus.recipe.routing;

import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeCostHeuristic;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.rule.CatalogRuleInput;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime bridge from the offline cascade junk scores to the cost-aware
 * planner: loads {@code experiments/generalization/export/<pack>.cascade.tsv}
 * (one {@link ScoreEntry} per {@code (system, recipeId)} row), looks each
 * catalog node's key up in the table, quantile-calibrates the soft cost with
 * the same data-driven formula as the V2 bench ({@code scale =
 * medianStepCost / max(p90, 1e-9)}; {@code penalty = scale × finalScore}),
 * and registers a {@code cirrus_cascade:runtime} rule profile carrying the
 * {@link RecipeCostHeuristic}. Preference only — feasibility is never
 * affected.
 *
 * <p>The cascade scores come from the offline three-layer pipeline
 * (similarity kNN + structural predicates + relational mirror), which was
 * validated against 2,584 human labels; this class only does table lookup and
 * calibration. Attach once per {@link RoutingService} right after the routing
 * graph is built, mirroring {@link BayesRuntime}.
 */
public final class CascadeScoreRuntime {

    public static final Namespace RUNTIME_PROFILE_ID = Namespace.of("cirrus_cascade", "runtime");

    private static final Logger logger = LoggerFactory.getLogger("CirrusCascadeRuntime");

    private CascadeScoreRuntime() {
    }

    /**
     * One exported cascade row (final score in [0,1] plus per-layer evidence).
     * For a (system, id) key seen multiple times in the TSV, callers must
     * retain the row with the highest {@link #finalScore()}.
     */
    public record ScoreEntry(double finalScore, String disposition,
                             double knn, double mirror, double freegen,
                             double storageRt, double identity) {
    }

    /**
     * Parses a cascade TSV (tab-separated, header row skipped, columns
     * {@code sys id wl label knn mirror freegen freegen_sole storage_rt
     * identity final t_hi disposition}).
     *
     * <p>Rows are keyed by {@code sys + "\t" + id}; duplicate keys keep the
     * maximum {@code final}. Unparseable lines are logged and skipped. Returns
     * {@code null} when the file cannot be read at all.
     */
    public static @Nullable Map<String, ScoreEntry> load(Path cascadeTsv) {
        if (!Files.isRegularFile(cascadeTsv)) {
            logger.warn("cascade.runtime: score file missing: {}", cascadeTsv);
            return null;
        }
        Map<String, ScoreEntry> best = new HashMap<>();
        long rows = 0;
        long skipped = 0;
        try (BufferedReader reader = Files.newBufferedReader(cascadeTsv, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || rows == 0 && line.startsWith("sys\t")) {
                    rows++;
                    continue;
                }
                rows++;
                String[] cols = line.split("\t", -1);
                if (cols.length < 13) {
                    skipped++;
                    continue;
                }
                try {
                    double knn = Double.parseDouble(cols[4]);
                    double finalScore = Double.parseDouble(cols[10]);
                    String key = cols[0] + "\t" + cols[1];
                    ScoreEntry entry = new ScoreEntry(
                        finalScore,
                        cols[12],
                        knn,
                        parseFlag(cols[5]),
                        parseFlag(cols[6]),
                        parseFlag(cols[8]),
                        parseFlag(cols[9])
                    );
                    ScoreEntry previous = best.get(key);
                    if (previous == null || finalScore > previous.finalScore()) {
                        best.put(key, entry);
                    }
                } catch (NumberFormatException e) {
                    skipped++;
                }
            }
        } catch (IOException e) {
            logger.error("cascade.runtime: failed to read score file {}", cascadeTsv, e);
            return null;
        }
        logger.info("cascade.runtime: loaded {} rows from {} ({} unique keys, {} skipped)",
            rows - 1, cascadeTsv.getFileName(), best.size(), skipped);
        return best;
    }

    private static double parseFlag(String value) {
        return "1".equals(value.trim()) ? 1.0 : 0.0;
    }

    /**
     * Attaches the cascade heuristic to the service: looks every catalog node
     * up by {@code (systemId, recipeId)}, builds the per-node penalty table
     * (missing entries score 0) and registers {@link #RUNTIME_PROFILE_ID} as a
     * clone of the default {@code standardDefault} profile carrying the
     * heuristic. Returns the heuristic for diagnostics, or {@code null} when
     * the score table is empty or the catalog is empty.
     */
    public static @Nullable RecipeCostHeuristic attach(RoutingService service, Map<String, ScoreEntry> scores) {
        if (scores == null || scores.isEmpty()) {
            logger.info("cascade.runtime: no score table, skipping");
            return null;
        }
        long start = System.nanoTime();
        int nodes = service.catalog().size();
        if (nodes == 0) {
            return null;
        }

        double[] evidence = new double[nodes];
        Map<Integer, List<RecipeCostHeuristic.FeatureContribution>> contributions = new HashMap<>();
        for (int node = 0; node < nodes; node++) {
            RecipeClassifyView view = ClassifyViewFactory.fromNode(service.catalog().nodeAt(node), node);
            CatalogRuleInput input = new CatalogRuleInput(view);
            ScoreEntry entry = scores.get(input.systemId() + "\t" + input.recipeId());
            if (entry == null) {
                continue;
            }
            double score = Math.max(0.0, Math.min(1.0, entry.finalScore()));
            evidence[node] = score;
            if (score > 0.0) {
                List<RecipeCostHeuristic.FeatureContribution> per = new ArrayList<>(4);
                // per-layer contributions: knn evidence itself and the layer
                // boosts that shaped final (0.35 mirror / 0.15 L0 / freegen 1.0)
                if (entry.knn() > 0.0) {
                    per.add(new RecipeCostHeuristic.FeatureContribution("cascade/knn", entry.knn(), true));
                }
                if (entry.mirror() > 0.0) {
                    per.add(new RecipeCostHeuristic.FeatureContribution("cascade/mirror", 0.35, true));
                }
                if (entry.storageRt() > 0.0) {
                    per.add(new RecipeCostHeuristic.FeatureContribution("cascade/storage_rt", 0.15, true));
                }
                if (entry.identity() > 0.0) {
                    per.add(new RecipeCostHeuristic.FeatureContribution("cascade/identity", 0.15, true));
                }
                if (entry.freegen() > 0.0) {
                    per.add(new RecipeCostHeuristic.FeatureContribution("cascade/freegen", 1.0, true));
                }
                contributions.put(node, per);
            }
        }

        // quantile calibration (identical spirit to the V2 offline bench;
        // final ∈ [0,1] is already monotone so no extra softplus is needed)
        double[] positive = Arrays.stream(evidence).filter(v -> v > 0.001).toArray();
        Arrays.sort(positive);
        double p90 = positive.length == 0 ? 1.0 : positive[(int) ((positive.length - 1) * 0.90)];
        double[] stepCosts = new double[nodes];
        for (int node = 0; node < nodes; node++) {
            var n = service.catalog().nodeAt(node);
            stepCosts[node] = n.inputCount() + 0.1 * n.outputCount() + 1.0;
        }
        Arrays.sort(stepCosts);
        double medianStepCost = stepCosts[nodes / 2];
        double scale = medianStepCost / Math.max(p90, 1e-9);
        double[] penalty = new double[nodes];
        for (int node = 0; node < nodes; node++) {
            penalty[node] = scale * evidence[node];
        }

        RuleProfile base = service.ruleProfile(dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles.standardDefaultId);
        if (base == null) {
            base = service.ruleProfile(dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles.assuredBoundedDefaultId);
        }
        if (base == null) {
            logger.warn("cascade.runtime: no base profile registered on service, skipping");
            return null;
        }
        RuntimeHeuristic heuristic = new RuntimeHeuristic(penalty, contributions);
        service.registerRuleProfile(new RuleProfile(
            RUNTIME_PROFILE_ID,
            base.model(), base.hardRules(), base.scoreRules(), base.rankPolicy(), base.tieBreakPolicy(),
            base.explorePolicy(), base.unresolvedInputRule(), base.capabilities(),
            base.shortlistK(), base.rollbackWidth(), base.nearTieEpsilon(),
            base.amplificationSoftThreshold(), base.amplificationHardThreshold(),
            base.liftMultiSemanticChildShortages(), base.fastPathEligible(),
            null, heuristic));
        long penalized = Arrays.stream(penalty).filter(v -> v > 0.001).count();
        logger.info(
            "cascade.runtime: attached profile={} nodes={} penalized={} scale={} p90={} maxPenalty={} elapsedMs={}",
            RUNTIME_PROFILE_ID, nodes, penalized, scale, p90,
            Arrays.stream(penalty).max().orElse(0),
            (System.nanoTime() - start) / 1_000_000L);
        return heuristic;
    }

    private record RuntimeHeuristic(
        double[] penaltyByIndex,
        Map<Integer, List<RecipeCostHeuristic.FeatureContribution>> contributions
    ) implements RecipeCostHeuristic {
        @Override
        public double penalty(int recipeIndex) {
            return recipeIndex >= 0 && recipeIndex < penaltyByIndex.length ? penaltyByIndex[recipeIndex] : 0.0;
        }

        @Override
        public List<FeatureContribution> explain(int recipeIndex) {
            List<FeatureContribution> list = contributions.get(recipeIndex);
            return list == null ? List.of() : list;
        }
    }
}
