package dev.vfyjxf.cirrus.recipe.chainexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeCostHeuristic;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.DiagnosticLevel;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingTrace;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.routing.topology.ConeStats;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.factory.Lists;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * V2 benchmark: legacy greedy planner vs the cost-aware branch-and-bound
 * planner, each with and without the Bayesian soft cost.
 *
 * <pre>
 *   A  StandardPlanner, no junk knowledge (V1 mode a)
 *   B  StandardPlanner + NB advisory multiplier (V1 mode c, cap 12 / x50)
 *   C  CostAwarePlanner, no heuristic
 *   D  CostAwarePlanner + NB heuristic (quantile-calibrated soft cost)
 * </pre>
 *
 * All modes run on the full (unfiltered) graph — hard filtering is banned by
 * the V1 conclusions. The heuristic penalty is calibrated per pack from the
 * data (no hand-tuned constants): {@code penalty = k * (softplus(lo) -
 * softplus(loBase))} with {@code k = medianStepCost / max(p90Evidence, 1)}.
 */
public final class CostAwareBenchMain {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !(args[0].equals("bench") || args[0].equals("calibrate"))) {
            System.err.println("usage: CostAwareBenchMain calibrate --export-dir ../exports --out ../experiments/naive-bayes-v2 --packs ...");
            System.err.println("       CostAwareBenchMain bench --export-dir ../exports --out ../experiments/naive-bayes-v2 --model model.json --packs ... --targets 100");
            System.exit(2);
        }
        Map<String, String> flags = parseFlags(args);
        switch (args[0]) {
            case "calibrate" -> runCalibrate(flags);
            case "bench" -> runBench(flags);
            default -> System.exit(2);
        }
    }

    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
                flags.put(key, value);
            }
        }
        return flags;
    }

    private static String flag(Map<String, String> flags, String key, String fallback) {
        String value = flags.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<Path> discoverPacks(Path exportDir, String filter) throws IOException {
        List<String> wanted = filter == null || filter.isBlank()
            ? null
            : Stream.of(filter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        try (Stream<Path> stream = Files.list(exportDir)) {
            List<Path> packs = new ArrayList<>();
            stream.filter(Files::isDirectory).sorted().forEach(path -> {
                String name = path.getFileName().toString();
                if (wanted == null || wanted.contains(name)) {
                    packs.add(path);
                }
            });
            return packs;
        }
    }

    // region heuristic + calibration

    private record Model(double priorLogOdds, Map<String, double[]> featureWeights) {
    }

    private record CalibratedHeuristic(double[] penaltyByIndex, Map<Integer, List<RecipeCostHeuristic.FeatureContribution>> contributions,
                                       double scale, double loBase, double p90Evidence) implements RecipeCostHeuristic {
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

    private static Model loadModel(Path path) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, double[]> weights = new LinkedHashMap<>();
        for (var element : json.getAsJsonArray("features")) {
            JsonObject f = element.getAsJsonObject();
            weights.put(f.get("id").getAsString(), new double[]{
                f.get("wPresent").getAsDouble(), f.get("wAbsent").getAsDouble()});
        }
        return new Model(json.get("priorLogOdds").getAsDouble(), weights);
    }

    private static double softplus(double value) {
        return value > 30 ? value : Math.log1p(Math.exp(value));
    }

    /**
     * Data-driven per-pack calibration. Writes analysis/calibration.json with
     * the distributions and produces the in-memory penalty table.
     *
     * <p>raw evidence ev(r) = logOdds(r) − loBase (log-odds above the no-feature
     * baseline); penalty(r) = scale × (softplus(loBase + ev) − softplus(loBase)) —
     * a smooth, non-negative, monotone map that is exactly 0 without evidence.
     * scale = medianStepCost / max(p90 of positive evidence, 1.0), where
     * medianStepCost follows the legacy per-node estimate
     * {@code inputCount + 0.1 × outputCount + 1}.
     */
    private static CalibratedHeuristic calibrate(RoutingService service, BayesExperimentMain.PackFeatures features,
                                                 Model model, int[] featureIndexByExportIndex) {
        // log-odds and evidence per catalog node
        int size = service.catalog().size();
        List<BayesExperimentMain.FeatureInfo> catalog = features.catalog();
        Map<String, double[]> weights = model.featureWeights();
        double[] wPresent = new double[catalog.size()];
        double[] wAbsent = new double[catalog.size()];
        for (int i = 0; i < catalog.size(); i++) {
            double[] w = weights.get(catalog.get(i).id());
            if (w != null) {
                wPresent[i] = w[0];
                wAbsent[i] = w[1];
            }
        }
        double loBase = model.priorLogOdds();
        for (int i = 0; i < catalog.size(); i++) {
            loBase += wAbsent[i];
        }
        double[] evidenceByNode = new double[size];
        java.util.Arrays.fill(evidenceByNode, 0.0);
        Map<Integer, List<RecipeCostHeuristic.FeatureContribution>> contributions = new HashMap<>();
        for (int node = 0; node < size; node++) {
            if (!(service.catalog().nodeAt(node).recipe().recipe() instanceof ExportPackLoader.ExportRecipe recipe)) {
                continue;
            }
            int exportIndex = recipe.index;
            if (exportIndex >= featureIndexByExportIndex.length) {
                continue;
            }
            Long bits = features.firedBits().get(exportIndex);
            if (bits == null) {
                continue;
            }
            double evidence = 0.0;
            List<RecipeCostHeuristic.FeatureContribution> per = new ArrayList<>();
            for (int i = 0; i < catalog.size(); i++) {
                double w = ((bits >> i) & 1L) != 0 ? wPresent[i] : 0.0;
                if (w != 0.0) {
                    evidence += w - wAbsent[i];
                    per.add(new RecipeCostHeuristic.FeatureContribution(catalog.get(i).id(), w, w > 0));
                }
            }
            evidenceByNode[node] = Math.max(0.0, evidence);
            if (!per.isEmpty()) {
                contributions.put(node, per);
            }
        }
        // distributions
        double[] positiveEvidence = Arrays.stream(evidenceByNode).filter(v -> v > 0.01).toArray();
        Arrays.sort(positiveEvidence);
        double p90 = positiveEvidence.length == 0 ? 1.0
            : positiveEvidence[(int) ((positiveEvidence.length - 1) * 0.90)];
        double[] stepCosts = new double[size];
        for (int node = 0; node < size; node++) {
            var n = service.catalog().nodeAt(node);
            stepCosts[node] = n.inputCount() + 0.1 * n.outputCount() + 1.0;
        }
        Arrays.sort(stepCosts);
        double medianStepCost = stepCosts.length == 0 ? 1.0 : stepCosts[stepCosts.length / 2];
        double scale = medianStepCost / Math.max(p90, 1.0);
        double[] penalty = new double[size];
        for (int node = 0; node < size; node++) {
            penalty[node] = scale * (softplus(loBase + evidenceByNode[node]) - softplus(loBase));
        }
        return new CalibratedHeuristic(penalty, contributions, scale, loBase, p90);
    }

    // endregion

    // region calibrate subcommand (prints the calibration numbers per pack)

    private static void runCalibrate(Map<String, String> flags) throws Exception {
        ChainExportMain.ensureVanillaBootstrap();
        Path exportDir = Path.of(flag(flags, "export-dir", "../exports"));
        Path outRoot = Path.of(flag(flags, "out", "../experiments/naive-bayes-v2"));
        Model model = loadModel(Path.of(flag(flags, "model", outRoot.resolve("model/bayes-model.json").toString())));
        JsonObject report = new JsonObject();
        for (Path packDir : discoverPacks(exportDir, flag(flags, "packs", ""))) {
            String name = packDir.getFileName().toString();
            OfflineContent.Session session = OfflineContent.newSession();
            ExportPackLoader.LoadedPack pack = ExportPackLoader.load(packDir, session, new ExportPackLoader.LoadOptions(true));
            BayesExperimentMain.PackFeatures features = BayesExperimentMain.extractFeaturesPublic(pack);
            List<RecipeEntry<?>> entries = new ArrayList<>();
            RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
            for (ExportPackLoader.ExportRecipeSystem system : pack.systems()) {
                builder.registerSystem(system);
                for (ExportPackLoader.ExportRecipe recipe : system.allRecipes()) {
                    entries.add(RecipeEntry.of(system, recipe));
                }
            }
            session.attachRegistry();
            RecipeApi current = RecipeApiImpl.instance();
            RecipeApiImpl previous = current instanceof RecipeApiImpl impl ? impl : null;
            RecipeApiImpl.attach(new RecipeApiImpl(builder));
            try {
                RoutingService service = new RoutingService(RecipeGraph.buildGraph(Lists.immutable.ofAll(entries)));
                int size = service.catalog().size();
                int[] featureIndexByExportIndex = new int[pack.totalRecipes()];
                CalibratedHeuristic heuristic = calibrate(service, features, model, featureIndexByExportIndex);
                JsonObject entry = new JsonObject();
                entry.addProperty("nodes", size);
                entry.addProperty("scale", heuristic.scale);
                entry.addProperty("loBase", heuristic.loBase);
                entry.addProperty("p90Evidence", heuristic.p90Evidence);
                double max = Arrays.stream(heuristic.penaltyByIndex).max().orElse(0);
                entry.addProperty("maxPenalty", max);
                long penalized = Arrays.stream(heuristic.penaltyByIndex).filter(v -> v > 0.001).count();
                entry.addProperty("penalizedNodes", penalized);
                report.add(name, entry);
                System.out.printf("[calibrate] %-22s nodes=%d scale=%.3f loBase=%.3f p90ev=%.2f maxPenalty=%.2f penalized=%d%n",
                    name, size, heuristic.scale, heuristic.loBase, heuristic.p90Evidence, max, penalized);
            } finally {
                if (previous != null) {
                    RecipeApiImpl.attach(previous);
                }
            }
            session.close();
        }
        Files.createDirectories(outRoot.resolve("analysis"));
        Files.writeString(outRoot.resolve("analysis/calibration.json"), GSON.toJson(report));
    }

    // endregion

    // region bench

    private static void runBench(Map<String, String> flags) throws Exception {
        ChainExportMain.ensureVanillaBootstrap();
        Path exportDir = Path.of(flag(flags, "export-dir", "../exports"));
        Path outRoot = Path.of(flag(flags, "out", "../experiments/naive-bayes-v2"));
        Path outDir = outRoot.resolve("results");
        Model model = loadModel(Path.of(flag(flags, "model", outRoot.resolve("model/bayes-model.json").toString())));
        int targetCount = Integer.parseInt(flag(flags, "targets", "100"));
        String modesFlag = flag(flags, "modes", "a,b,c,d");
        String blacklist = flag(flags, "target-blacklist", "decompress");
        Files.createDirectories(outDir);

        for (Path packDir : discoverPacks(exportDir, flag(flags, "packs", ""))) {
            String name = packDir.getFileName().toString();
            long start = System.nanoTime();
            OfflineContent.Session session = OfflineContent.newSession();
            ExportPackLoader.LoadedPack pack = ExportPackLoader.load(packDir, session, new ExportPackLoader.LoadOptions(true));
            BayesExperimentMain.PackFeatures features = BayesExperimentMain.extractFeaturesPublic(pack);

            // targets exist on the full graph; the selector runs on the full pack here
            // (no hard filter anywhere in V2)
            ChainTargetSelector selector = new ChainTargetSelector(pack);
            List<ChainTargetSelector.Target> targets = selector.select(targetCount, 80, blacklist);
            System.out.printf("[bench2] %-22s targets=%d recipes=%d%n", name, targets.size(), pack.recipes().size());

            List<RecipeEntry<?>> entries = new ArrayList<>();
            RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
            Map<String, ExportPackLoader.ExportRecipeSystem> systemsById = new HashMap<>();
            for (ExportPackLoader.ExportRecipeSystem system : pack.systems()) {
                builder.registerSystem(system);
                systemsById.put(system.systemId().toString(), system);
                for (ExportPackLoader.ExportRecipe recipe : system.allRecipes()) {
                    entries.add(RecipeEntry.of(system, recipe));
                }
            }
            session.attachRegistry();
            RecipeApi current = RecipeApiImpl.instance();
            RecipeApiImpl previous = current instanceof RecipeApiImpl impl ? impl : null;
            RecipeApiImpl.attach(new RecipeApiImpl(builder));
            try {
                RuleDefinition activeRule = ChainExportMain.ruleFor("preview");
                RoutingService service = new RoutingService(RecipeGraph.buildGraph(Lists.immutable.ofAll(entries)));
                for (RuleDefinition rule : DefaultRuleDefinitions.defaultCollection().rules()) {
                    service.registerRuleDefinition(rule);
                }
                CalibratedHeuristic heuristic = calibrate(service, features, model, new int[pack.totalRecipes()]);
                System.out.printf("[bench2] %-22s calibration: scale=%.3f maxPenalty=%.2f p90ev=%.2f%n",
                    name, heuristic.scale, Arrays.stream(heuristic.penaltyByIndex).max().orElse(0), heuristic.p90Evidence);

                RuleProfile nbMultiplierProfile = nbMultiplierProfile(service, features, model);
                service.registerRuleProfile(nbMultiplierProfile);
                RuleProfile nbHeuristicProfile = activeRule.toProfile().withCostHeuristic(heuristic);
                RuleProfile nbHeuristicProfileId = new RuleProfile(
                    Namespace.of("test_v2", "cost_aware_nb"),
                    nbHeuristicProfile.model(), nbHeuristicProfile.hardRules(), nbHeuristicProfile.scoreRules(),
                    nbHeuristicProfile.rankPolicy(), nbHeuristicProfile.tieBreakPolicy(), nbHeuristicProfile.explorePolicy(),
                    nbHeuristicProfile.unresolvedInputRule(), nbHeuristicProfile.capabilities(),
                    nbHeuristicProfile.shortlistK(), nbHeuristicProfile.rollbackWidth(), nbHeuristicProfile.nearTieEpsilon(),
                    nbHeuristicProfile.amplificationSoftThreshold(), nbHeuristicProfile.amplificationHardThreshold(),
                    nbHeuristicProfile.liftMultiSemanticChildShortages(), nbHeuristicProfile.fastPathEligible(),
                    null, heuristic);
                service.registerRuleProfile(nbHeuristicProfileId);

                Map<String, List<JsonObject>> results = new LinkedHashMap<>();
                if (modesFlag.contains("a")) {
                    results.put("a", runMode(service, "a", PlannerMode.standard, activeRule.profile().profileId(), targets, features, heuristic));
                }
                if (modesFlag.contains("b")) {
                    results.put("b", runMode(service, "b", PlannerMode.standard, nbMultiplierProfile.profileId(), targets, features, heuristic));
                }
                if (modesFlag.contains("c")) {
                    results.put("c", runMode(service, "c", PlannerMode.costAware, activeRule.profile().profileId(), targets, features, heuristic));
                }
                if (modesFlag.contains("d")) {
                    results.put("d", runMode(service, "d", PlannerMode.costAware, nbHeuristicProfileId.profileId(), targets, features, heuristic));
                }
                writeOutputs(outDir, name, targets, results, heuristic);
            } finally {
                if (previous != null) {
                    RecipeApiImpl.attach(previous);
                }
            }
            session.close();
            System.out.printf("[bench2] %-22s done elapsedMs=%d%n", name, (System.nanoTime() - start) / 1_000_000);
        }
    }

    /** V1-style advisory multiplier profile (mode B baseline). */
    private static RuleProfile nbMultiplierProfile(RoutingService service, BayesExperimentMain.PackFeatures features, Model model) {
        RuleProfile base = ChainExportMain.ruleFor("preview").toProfile();
        int size = service.catalog().size();
        double[] multiplier = new double[size];
        java.util.Arrays.fill(multiplier, 1.0);
        double cap = 12.0;
        double maxMult = 50.0;
        List<BayesExperimentMain.FeatureInfo> catalog = features.catalog();
        double[] wPresent = new double[catalog.size()];
        double[] wAbsent = new double[catalog.size()];
        for (int i = 0; i < catalog.size(); i++) {
            double[] w = model.featureWeights().get(catalog.get(i).id());
            if (w != null) {
                wPresent[i] = w[0];
                wAbsent[i] = w[1];
            }
        }
        for (int node = 0; node < size; node++) {
            if (!(service.catalog().nodeAt(node).recipe().recipe() instanceof ExportPackLoader.ExportRecipe recipe)) {
                continue;
            }
            Long bits = features.firedBits().get(recipe.index);
            if (bits == null) {
                continue;
            }
            double logOdds = model.priorLogOdds();
            for (int i = 0; i < catalog.size(); i++) {
                logOdds += ((bits >> i) & 1L) != 0 ? wPresent[i] : wAbsent[i];
            }
            double clamped = Math.max(0.0, Math.min(cap, logOdds));
            if (clamped > 0) {
                multiplier[node] = Math.pow(maxMult, clamped / cap);
            }
        }
        return new RuleProfile(
            Namespace.of("test_v2", "preview_nb_multiplier"),
            base.model(), base.hardRules(), base.scoreRules(), base.rankPolicy(), base.tieBreakPolicy(),
            base.explorePolicy(), base.unresolvedInputRule(), base.capabilities(),
            base.shortlistK(), base.rollbackWidth(), base.nearTieEpsilon(),
            base.amplificationSoftThreshold(), base.amplificationHardThreshold(),
            base.liftMultiSemanticChildShortages(), base.fastPathEligible(),
            index -> index >= 0 && index < multiplier.length ? multiplier[index] : 1.0,
            null);
    }

    private static List<JsonObject> runMode(RoutingService service, String mode, PlannerMode plannerMode,
                                            Namespace profileId, List<ChainTargetSelector.Target> targets,
                                            BayesExperimentMain.PackFeatures features, CalibratedHeuristic heuristic) {
        RuleDefinition activeRule = ChainExportMain.ruleFor("preview");
        List<JsonObject> results = new ArrayList<>(targets.size());
        long modeStart = System.nanoTime();
        for (ChainTargetSelector.Target target : targets) {
            ExportPackLoader.ExportRecipeSystem system = target.system();
            RecipeEntry<ExportPackLoader.ExportRecipe> entry = RecipeEntry.of(system, target.recipe());
            int targetIndex = service.catalog().indexOf(entry);
            RoutingBudget budget;
            if (targetIndex >= 0) {
                BitSet allAllowed = new BitSet(service.catalog().size());
                allAllowed.set(0, service.catalog().size());
                ConeStats cone = service.coneStats(targetIndex, allAllowed);
                budget = RoutingBudget.fromConeStats(cone, plannerMode, Math.max(1, target.recipe().inputs.size()));
            } else {
                budget = RoutingBudget.assuredDefault();
            }
            RoutingRequest request = RoutingRequest.builder()
                .plannerMode(plannerMode)
                .targetRecipe(entry, 1)
                .ruleProfileId(profileId)
                .sourcePolicyId(activeRule.sourcePolicy().policyId())
                .stock(Stock.empty())
                .loopPolicy(LoopPolicy.allowBoundedAmplification)
                .maxProducerCandidates(16)
                .budget(budget)
                .diagnosticLevel(DiagnosticLevel.basic)
                .build();
            RoutingOutcome outcome = service.route(request);
            results.add(describe(outcome, mode, target, features, heuristic));
        }
        System.out.printf("[bench2] mode=%s targets=%d elapsedMs=%d%n", mode, targets.size(),
            (System.nanoTime() - modeStart) / 1_000_000);
        return results;
    }

    private static JsonObject describe(RoutingOutcome outcome, String mode, ChainTargetSelector.Target target,
                                       BayesExperimentMain.PackFeatures features, CalibratedHeuristic heuristic) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", mode);
        json.addProperty("target", target.outputId());
        json.addProperty("status", outcome.status().toString());
        RoutingMetrics metrics = outcome.metrics();
        json.addProperty("elapsedMs", metrics.elapsedMs());
        json.addProperty("expandedNodes", metrics.expandedNodes());
        json.addProperty("generatedCandidates", metrics.generatedCandidates());
        json.addProperty("candidateRollbacks", metrics.candidateRollbacks());
        json.addProperty("budgetLimit", metrics.budgetLimit());
        json.addProperty("budgetStopReason", metrics.budgetStopReason());
        int steps = 0;
        int junk = 0;
        int middle = 0;
        double penaltyTotal = 0;
        JsonArray path = new JsonArray();
        if (outcome.plan() != null) {
            for (PlanAction action : outcome.plan().actions().castToList()) {
                if (action instanceof PlanAction.ExecuteRecipe exec
                    && exec.recipe().recipe() instanceof ExportPackLoader.ExportRecipe recipe) {
                    steps++;
                    String label = features.weakLabel().get(recipe.index);
                    if ("junk".equals(label)) {
                        junk++;
                    } else if ("middle".equals(label)) {
                        middle++;
                    }
                    // production + penalty split for the chosen step (node index unknown
                    // here; approximate by catalog lookup is skipped — trace carries totals)
                    if (path.size() < 12) {
                        path.add(recipe.recipeId);
                    }
                }
            }
        }
        json.addProperty("planSteps", steps);
        json.addProperty("junkInPath", junk);
        json.addProperty("middleInPath", middle);
        json.add("path", path);
        // cost-aware stats from the diagnostic trace (absent for legacy planner modes)
        if (outcome.trace() != null) {
            for (RoutingTrace.TraceStage stage : outcome.trace().stages()) {
                if (stage.values().containsKey("expandedStates")) {
                    stage.values().keyValuesView().forEach(pair -> json.addProperty(pair.getOne(), pair.getTwo()));
                }
            }
        }
        return json;
    }

    private static void writeOutputs(Path outDir, String name, List<ChainTargetSelector.Target> targets,
                                     Map<String, List<JsonObject>> resultsByMode, CalibratedHeuristic heuristic) throws IOException {
        Path packOut = outDir.resolve(name);
        Files.createDirectories(packOut);
        for (Map.Entry<String, List<JsonObject>> entry : resultsByMode.entrySet()) {
            try (BufferedWriter writer = Files.newBufferedWriter(packOut.resolve("mode-" + entry.getKey() + ".jsonl"),
                StandardCharsets.UTF_8)) {
                for (JsonObject json : entry.getValue()) {
                    writer.write(GSON.toJson(json));
                    writer.write('\n');
                }
            }
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("pack", name);
        summary.addProperty("targets", targets.size());
        summary.addProperty("calibrationScale", heuristic.scale);
        summary.addProperty("calibrationMaxPenalty", Arrays.stream(heuristic.penaltyByIndex).max().orElse(0));
        JsonObject modesJson = new JsonObject();
        for (Map.Entry<String, List<JsonObject>> entry : resultsByMode.entrySet()) {
            JsonObject m = new JsonObject();
            long success = entry.getValue().stream().filter(j -> "success".equals(j.get("status").getAsString())).count();
            long partial = entry.getValue().stream().filter(j -> "partial".equals(j.get("status").getAsString())).count();
            m.addProperty("success", success);
            m.addProperty("partial", partial);
            m.addProperty("failed", targets.size() - success - partial);
            m.addProperty("sumElapsedMs", entry.getValue().stream().mapToLong(j -> j.get("elapsedMs").getAsLong()).sum());
            m.addProperty("sumExpandedNodes", entry.getValue().stream().mapToLong(j -> j.get("expandedNodes").getAsLong()).sum());
            m.addProperty("sumPlanSteps", entry.getValue().stream().mapToLong(j -> j.get("planSteps").getAsLong()).sum());
            m.addProperty("sumJunkInPath", entry.getValue().stream().mapToLong(j -> j.get("junkInPath").getAsLong()).sum());
            m.addProperty("sumMiddleInPath", entry.getValue().stream().mapToLong(j -> j.get("middleInPath").getAsLong()).sum());
            modesJson.add(entry.getKey(), m);
        }
        summary.add("modes", modesJson);
        Files.writeString(packOut.resolve("summary.json"), GSON.toJson(summary));
    }

    // endregion
}
