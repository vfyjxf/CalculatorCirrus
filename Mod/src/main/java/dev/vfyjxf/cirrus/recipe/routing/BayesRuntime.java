package dev.vfyjxf.cirrus.recipe.routing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationContext;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationRule;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationSink;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.ColonyViewMirrorRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.ExchangeStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.GenerationStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.MassSinkStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.RecolorStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.RecyclingStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeCostHeuristic;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.rule.CatalogRuleInput;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleEngine;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleSpec;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime bridge from the trained Bernoulli-NB junk model to the cost-aware
 * planner: loads the model shipped under mod assets, extracts per-rule feature
 * bits for the live catalog, quantile-calibrates the soft cost (same formula
 * as the offline bench — {@code k = medianStepCost / max(p90ev, 1)}), and
 * registers a {@code cirrus_bayes:runtime} rule profile carrying the
 * {@link RecipeCostHeuristic}. Preference only — feasibility is never affected.
 *
 * <p>Attached once per {@link RoutingService} right after the routing graph is
 * built ({@code RecipePathSettings}); idempotent per service instance.
 */
public final class BayesRuntime {

    public static final Namespace RUNTIME_PROFILE_ID = Namespace.of("cirrus_bayes", "runtime");
    private static final String MODEL_RESOURCE = "assets/calculator_cirrus/bayes/bayes-model.json";
    private static final Logger logger = LoggerFactory.getLogger("CirrusBayesRuntime");

    private BayesRuntime() {
    }

    /** True when a model resource ships with the mod (feature: off when absent). */
    public static boolean modelAvailable() {
        return RecipeClassifier.class.getClassLoader().getResource(MODEL_RESOURCE) != null;
    }

    /**
     * Extracts features, calibrates and registers the NB rule profile on the
     * service. Returns the heuristic (for diagnostics), or null when the model
     * is missing or the catalog is empty.
     */
    public static @Nullable RecipeCostHeuristic attach(RoutingService service, RecipeGraph graph) {
        if (!modelAvailable()) {
            logger.info("bayes.runtime: no model resource, skipping");
            return null;
        }
        long start = System.nanoTime();
        Model model = loadModel();
        if (model == null) {
            return null;
        }
        List<RuleSpec> specs = RecipeClassifier.loadBuiltinRuleSpecs();
        RuleEngine engine = RuleEngine.compileCatalogOnly(specs);
        int nodes = service.catalog().size();
        if (nodes == 0) {
            return null;
        }

        // per-node feature bits (same bit order as the offline harness)
        int jsonCount = engine.catalogRules().size();
        ClassificationRule[] structuralLocal = {
            ExchangeStructuralRule.instance,
            RecolorStructuralRule.instance,
            RecyclingStructuralRule.instance,
            GenerationStructuralRule.instance
        };
        List<RecipeClassifyView> views = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; i++) {
            views.add(dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory.fromNode(service.catalog().nodeAt(i), i));
        }
        ClassificationContext context = ClassificationContext.of(views);
        BitSet massSink = new BitSet();
        MassSinkStructuralRule.create().classifyAll(context, (index, tag) -> massSink.set(index));
        BitSet colonyMirror = new BitSet();
        for (RecipeClassifyView view : views) {
            String[] firedTag = {null};
            ColonyViewMirrorRule.instance.classify(view, context, tag -> firedTag[0] = tag);
            if (dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags.DUPLICATE.equals(firedTag[0])) {
                colonyMirror.set(view.index());
            }
        }
        Map<String, double[]> weights = model.featureWeights();
        int featureCount = jsonCount + structuralLocal.length + 2;
        double[] wPresent = new double[featureCount];
        double[] wAbsent = new double[featureCount];
        for (int i = 0; i < jsonCount; i++) {
            double[] w = weights.get(engine.catalogRules().get(i).id().toString());
            if (w != null) {
                wPresent[i] = w[0];
                wAbsent[i] = w[1];
            }
        }
        double loBase = model.priorLogOdds();
        for (int i = 0; i < featureCount; i++) {
            loBase += wAbsent[i];
        }

        double[] evidence = new double[nodes];
        Map<Integer, List<RecipeCostHeuristic.FeatureContribution>> contributions = new HashMap<>();
        for (int node = 0; node < nodes; node++) {
            RecipeClassifyView view = views.get(node);
            CatalogRuleInput input = new CatalogRuleInput(view);
            double value = 0.0;
            List<RecipeCostHeuristic.FeatureContribution> per = null;
            for (int i = 0; i < jsonCount; i++) {
                RuleSpec spec = engine.catalogRules().get(i);
                if (spec.enabled() && spec.condition().matches(input)) {
                    if (wPresent[i] != 0.0) {
                        value += wPresent[i] - wAbsent[i];
                        if (per == null) {
                            per = new ArrayList<>();
                        }
                        per.add(new RecipeCostHeuristic.FeatureContribution(spec.id().toString(), wPresent[i], wPresent[i] > 0));
                    }
                }
            }
            for (int s = 0; s < structuralLocal.length; s++) {
                boolean[] hit = {false};
                structuralLocal[s].classify(view, context, tag -> hit[0] = true);
                if (hit[0] && wPresent[jsonCount + s] != 0.0) {
                    value += wPresent[jsonCount + s] - wAbsent[jsonCount + s];
                    if (per == null) {
                        per = new ArrayList<>();
                    }
                    per.add(new RecipeCostHeuristic.FeatureContribution(
                        structuralLocal[s].id(), wPresent[jsonCount + s], wPresent[jsonCount + s] > 0));
                }
            }
            if (massSink.get(node) && wPresent[jsonCount + structuralLocal.length] != 0.0) {
                int idx = jsonCount + structuralLocal.length;
                value += wPresent[idx] - wAbsent[idx];
                if (per == null) {
                    per = new ArrayList<>();
                }
                per.add(new RecipeCostHeuristic.FeatureContribution(
                    MassSinkStructuralRule.ID, wPresent[idx], wPresent[idx] > 0));
            }
            if (colonyMirror.get(node) && wPresent[jsonCount + structuralLocal.length + 1] != 0.0) {
                int idx = jsonCount + structuralLocal.length + 1;
                value += wPresent[idx] - wAbsent[idx];
                if (per == null) {
                    per = new ArrayList<>();
                }
                per.add(new RecipeCostHeuristic.FeatureContribution(
                    ColonyViewMirrorRule.ID, wPresent[idx], wPresent[idx] > 0));
            }
            evidence[node] = Math.max(0.0, value);
            if (per != null) {
                contributions.put(node, per);
            }
        }

        // quantile calibration (identical formula to the offline bench)
        double[] positiveEvidence = Arrays.stream(evidence).filter(v -> v > 0.01).toArray();
        Arrays.sort(positiveEvidence);
        double p90 = positiveEvidence.length == 0 ? 1.0
            : positiveEvidence[(int) ((positiveEvidence.length - 1) * 0.90)];
        double[] stepCosts = new double[nodes];
        for (int node = 0; node < nodes; node++) {
            var n = service.catalog().nodeAt(node);
            stepCosts[node] = n.inputCount() + 0.1 * n.outputCount() + 1.0;
        }
        Arrays.sort(stepCosts);
        double medianStepCost = stepCosts[nodes / 2];
        double scale = medianStepCost / Math.max(p90, 1.0);
        double[] penalty = new double[nodes];
        for (int node = 0; node < nodes; node++) {
            penalty[node] = scale * (softplus(loBase + evidence[node]) - softplus(loBase));
        }

        RuleProfile base = service.ruleProfile(dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles.standardDefaultId);
        if (base == null) {
            base = service.ruleProfile(dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles.assuredBoundedDefaultId);
        }
        if (base == null) {
            logger.warn("bayes.runtime: no base profile registered on service, skipping");
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
            "bayes.runtime: attached profile={} nodes={} penalized={} scale={} loBase={} p90ev={} maxPenalty={} elapsedMs={}",
            RUNTIME_PROFILE_ID, nodes, penalized, scale, loBase, p90,
            Arrays.stream(penalty).max().orElse(0),
            (System.nanoTime() - start) / 1_000_000L);
        return heuristic;
    }

    private static double softplus(double value) {
        return value > 30 ? value : Math.log1p(Math.exp(value));
    }

    private record Model(double priorLogOdds, Map<String, double[]> featureWeights) {
    }

    private static @Nullable Model loadModel() {
        try (InputStream stream = RecipeClassifier.class.getClassLoader().getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) {
                logger.warn("bayes.runtime: model resource missing: {}", MODEL_RESOURCE);
                return null;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, double[]> weights = new LinkedHashMap<>();
            for (var element : json.getAsJsonArray("features")) {
                JsonObject f = element.getAsJsonObject();
                weights.put(f.get("id").getAsString(), new double[]{
                    f.get("wPresent").getAsDouble(), f.get("wAbsent").getAsDouble()});
            }
            return new Model(json.get("priorLogOdds").getAsDouble(), weights);
        } catch (Exception e) {
            logger.error("bayes.runtime: model load failed", e);
            return null;
        }
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
