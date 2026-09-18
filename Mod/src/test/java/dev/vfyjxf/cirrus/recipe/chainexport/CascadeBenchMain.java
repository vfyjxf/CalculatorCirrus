package dev.vfyjxf.cirrus.recipe.chainexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeCostHeuristic;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.service.DiagnosticLevel;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.routing.CascadeScoreRuntime;
import dev.vfyjxf.cirrus.recipe.routing.topology.ConeStats;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Cascade benchmark: the cost-aware planner with and without the offline
 * cascade junk-score heuristic.
 *
 * <pre>
 *   c  CostAwarePlanner, no heuristic (baseline)
 *   e  CostAwarePlanner + CascadeScoreRuntime heuristic
 *      (loaded from {@code --scores-dir/<pack>.cascade.tsv})
 * </pre>
 *
 * Both modes run on the full unfiltered graph; the heuristic penalty is
 * preference-only and calibrated per pack from the cascade table
 * ({@code scale = medianStepCost / max(p90, 1e-9)}, {@code penalty =
 * scale × final}).
 */
public final class CascadeBenchMain {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private CascadeBenchMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !"bench".equals(args[0])) {
            System.err.println("usage: CascadeBenchMain bench --export-dir ../exports --scores-dir ../experiments/generalization/export"
                + " --out ../experiments/generalization/results/bench --packs atm10,tfc-enhanced --targets 100");
            System.exit(2);
        }
        Map<String, String> flags = parseFlags(args);
        ChainExportMain.ensureVanillaBootstrap();
        runBench(flags);
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

    private static void runBench(Map<String, String> flags) throws Exception {
        Path exportDir = Path.of(flag(flags, "export-dir", "../exports"));
        Path scoresDir = Path.of(flag(flags, "scores-dir", "../experiments/generalization/export"));
        Path outDir = Path.of(flag(flags, "out", "../experiments/generalization/results/bench"));
        Path labelsDir = Path.of(flag(flags, "labels", "../experiments/generalization/labels"));
        int targetCount = Integer.parseInt(flag(flags, "targets", "100"));
        String blacklist = flag(flags, "target-blacklist", "decompress");
        Files.createDirectories(outDir);

        for (Path packDir : discoverPacks(exportDir, flag(flags, "packs", ""))) {
            String name = packDir.getFileName().toString();
            long start = System.nanoTime();
            OfflineContent.Session session = OfflineContent.newSession();
            ExportPackLoader.LoadedPack pack = ExportPackLoader.load(packDir, session, new ExportPackLoader.LoadOptions(true));
            BayesExperimentMain.PackFeatures features = BayesExperimentMain.extractFeaturesPublic(pack);

            // targets exist on the full graph (same selection as the V2 bench)
            ChainTargetSelector selector = new ChainTargetSelector(pack);
            List<ChainTargetSelector.Target> targets = selector.select(targetCount, 80, blacklist);
            System.out.printf("[cascade-bench] %-22s targets=%d recipes=%d%n",
                name, targets.size(), pack.recipes().size());

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

                Map<String, CascadeScoreRuntime.ScoreEntry> scores =
                    CascadeScoreRuntime.load(scoresDir.resolve(name + ".cascade.tsv"));
                RecipeCostHeuristic cascadeHeuristic = CascadeScoreRuntime.attach(service, scores);
                if (cascadeHeuristic == null) {
                    System.out.printf("[cascade-bench] %-22s no cascade scores, skipping%n", name);
                    continue;
                }
                Map<String, String> humanLabels = loadHumanLabels(labelsDir, name);

                Map<String, List<JsonObject>> results = new LinkedHashMap<>();
                results.put("c", runMode(service, "c", PlannerMode.costAware,
                    activeRule.profile().profileId(), targets, systemsById, features, null, humanLabels));
                results.put("e", runMode(service, "e", PlannerMode.costAware,
                    CascadeScoreRuntime.RUNTIME_PROFILE_ID, targets, systemsById, features, cascadeHeuristic, humanLabels));
                writeOutputs(outDir, name, targets, results, cascadeHeuristic);
            } finally {
                if (previous != null) {
                    RecipeApiImpl.attach(previous);
                }
            }
            session.close();
            System.out.printf("[cascade-bench] %-22s done elapsedMs=%d%n", name, (System.nanoTime() - start) / 1_000_000);
        }
    }

    /**
     * Loads the human label store (all-labels.tsv: {@code pack sys id verdict
     * ...}, tab-separated) for one pack: rows whose pack column equals the
     * pack name plus rows with an empty pack (audit offenders attached to
     * every pack, matching the offline matrix builder). Keys are
     * {@code sys + "\t" + id}; empty ids are legal. Returns verdict by key
     * (duplicate keys share the same verdict in the store).
     */
    private static Map<String, String> loadHumanLabels(Path labelsDir, String packName) {
        Map<String, String> labels = new HashMap<>();
        Path file = labelsDir.resolve("all-labels.tsv");
        if (!Files.isRegularFile(file)) {
            System.out.printf("[cascade-bench] %-22s label store missing: %s%n", packName, file);
            return labels;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                String[] cols = line.split("\t", -1);
                if (cols.length < 4) {
                    continue;
                }
                String rowPack = cols[0];
                if (!rowPack.equals(packName) && !rowPack.isEmpty()) {
                    continue;
                }
                labels.put(cols[1] + "\t" + cols[2], cols[3]);
            }
        } catch (IOException e) {
            System.out.printf("[cascade-bench] %-22s failed to read label store: %s%n", packName, e);
        }
        return labels;
    }

    private static List<JsonObject> runMode(RoutingService service, String mode, PlannerMode plannerMode,
                                            Namespace profileId, List<ChainTargetSelector.Target> targets,
                                            Map<String, ExportPackLoader.ExportRecipeSystem> systemsById,
                                            BayesExperimentMain.PackFeatures features,
                                            @Nullable RecipeCostHeuristic heuristic,
                                            Map<String, String> humanLabels) {
        RuleDefinition activeRule = ChainExportMain.ruleFor("preview");
        List<JsonObject> results = new ArrayList<>(targets.size());
        long modeStart = System.nanoTime();
        for (ChainTargetSelector.Target target : targets) {
            ExportPackLoader.ExportRecipeSystem system = systemsById.get(target.system().systemId().toString());
            if (system == null) {
                system = target.system();
            }
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
            results.add(describe(outcome, mode, target, features, heuristic, humanLabels));
        }
        System.out.printf("[cascade-bench] mode=%s targets=%d elapsedMs=%d%n", mode, targets.size(),
            (System.nanoTime() - modeStart) / 1_000_000);
        return results;
    }

    private static JsonObject describe(RoutingOutcome outcome, String mode, ChainTargetSelector.Target target,
                                       BayesExperimentMain.PackFeatures features,
                                       @Nullable RecipeCostHeuristic heuristic,
                                       Map<String, String> humanLabels) {
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
        int mirror = 0;
        int humanJunk = 0;
        int humanUseful = 0;
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
                    if (recipe.systemId.startsWith("jei:category/")) {
                        mirror++;
                    }
                    String sys = recipe.systemId;
                    String rid = recipe.recipeId;
                    String human = humanLabels.get(sys + "\t" + rid);
                    if (human == null && rid.startsWith("jei:/synthetic/")) {
                        // system-level labels written against the raw (empty)
                        // recipe id; loader synthesized recipeId for such rows
                        human = humanLabels.get(sys + "\t");
                    }
                    if ("junk".equals(human)) {
                        humanJunk++;
                    } else if ("useful".equals(human)) {
                        humanUseful++;
                    }
                    path.add(sys + "|" + rid);
                }
            }
        }
        json.addProperty("planSteps", steps);
        json.addProperty("junkInPath", junk);
        json.addProperty("middleInPath", middle);
        json.addProperty("mirrorInPath", mirror);
        json.addProperty("humanJunkInPath", humanJunk);
        json.addProperty("humanUsefulInPath", humanUseful);
        json.add("path", path);
        // cost-aware stats from the diagnostic trace
        if (outcome.trace() != null) {
            for (dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingTrace.TraceStage stage : outcome.trace().stages()) {
                if (stage.values().containsKey("expandedStates")) {
                    stage.values().keyValuesView().forEach(pair -> json.addProperty(pair.getOne(), pair.getTwo()));
                }
            }
        }
        return json;
    }

    private static void writeOutputs(Path outDir, String name, List<ChainTargetSelector.Target> targets,
                                     Map<String, List<JsonObject>> resultsByMode,
                                     RecipeCostHeuristic heuristic) throws IOException {
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
            m.addProperty("sumMirrorInPath", entry.getValue().stream().mapToLong(j -> j.get("mirrorInPath").getAsLong()).sum());
            m.addProperty("sumHumanJunkInPath", entry.getValue().stream().mapToLong(j -> j.get("humanJunkInPath").getAsLong()).sum());
            m.addProperty("sumHumanUsefulInPath", entry.getValue().stream().mapToLong(j -> j.get("humanUsefulInPath").getAsLong()).sum());
            modesJson.add(entry.getKey(), m);
        }
        summary.add("modes", modesJson);
        Files.writeString(packOut.resolve("summary.json"), GSON.toJson(summary));
    }
}
