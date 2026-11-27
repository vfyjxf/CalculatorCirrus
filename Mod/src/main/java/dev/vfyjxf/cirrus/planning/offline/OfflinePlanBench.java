package dev.vfyjxf.cirrus.planning.offline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.adapter.CanonicalJson;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultRecipeGraphCompiler;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

public final class OfflinePlanBench {

    // the bench is a command-line tool: its report is stdout and its errors are stderr
    private static final PrintStream console = System.out;
    private static final PrintStream errorConsole = System.err;

    private static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final List<TargetDemand> defaultTargets = List.of(
        new TargetDemand(ResourceKey.item("minecraft:hopper"), 1L),
        new TargetDemand(ResourceKey.item("minecraft:stone_bricks"), 5L),
        new TargetDemand(ResourceKey.item("minecraft:piston"), 1L),
        new TargetDemand(ResourceKey.item("minecraft:observer"), 1L),
        new TargetDemand(ResourceKey.item("ae2:controller"), 1L)
    );

    public record BenchConfig(
        @Nullable Path exportDir,
        @Nullable Path zipFile,
        List<String> packs,
        List<TargetDemand> explicitTargets,
        @Nullable Path outPath,
        long budgetMs,
        int maxSubgraphNodes,
        int maxSubgraphRecipes,
        @Nullable String perPackMode,
        int repeat,
        boolean compileOnly,
        boolean runClassifier,
        @Nullable Path summaryPath,
        boolean help
    ) {}

    public static void main(String[] args) {
        try {
            BenchConfig cfg = parseArgs(args);
            if (cfg.help()) {
                printUsage();
                return;
            }
            int code = run(cfg);
            if (code != 0) {
                System.exit(code);
            }
        } catch (Exception e) {
            errorConsole.println("OfflinePlanBench error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static BenchConfig parseArgs(String[] args) {
        Path exportDir = null;
        Path zipFile = null;
        List<String> packs = new ArrayList<>();
        List<TargetDemand> explicitTargets = new ArrayList<>();
        Path outPath = null;
        long budgetMs = 250L;
        int maxSubgraphNodes = 2500;
        int maxSubgraphRecipes = 6000;
        String perPackMode = null;
        int repeat = 1;
        boolean compileOnly = false;
        boolean runClassifier = true;
        Path summaryPath = null;
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> help = true;
                case "--export-dir" -> exportDir = Path.of(requireArg(args, ++i, "--export-dir"));
                case "--zip" -> zipFile = Path.of(requireArg(args, ++i, "--zip"));
                case "--pack" -> packs.add(requireArg(args, ++i, "--pack"));
                case "--packs" -> {
                    for (String p : requireArg(args, ++i, "--packs").split(",")) {
                        String trimmed = p.trim();
                        if (!trimmed.isEmpty()) {
                            packs.add(trimmed);
                        }
                    }
                }
                case "--targets" -> {
                    for (String spec : requireArg(args, ++i, "--targets").split(",")) {
                        String trimmed = spec.trim();
                        if (!trimmed.isEmpty()) {
                            explicitTargets.add(parseTarget(trimmed));
                        }
                    }
                }
                case "--out" -> outPath = Path.of(requireArg(args, ++i, "--out"));
                case "--budget-ms" -> budgetMs = Long.parseLong(requireArg(args, ++i, "--budget-ms"));
                case "--max-subgraph-nodes" ->
                    maxSubgraphNodes = Integer.parseInt(requireArg(args, ++i, "--max-subgraph-nodes"));
                case "--max-subgraph-recipes" ->
                    maxSubgraphRecipes = Integer.parseInt(requireArg(args, ++i, "--max-subgraph-recipes"));
                case "--per-pack" -> perPackMode = requireArg(args, ++i, "--per-pack");
                case "--repeat" -> repeat = Math.max(1, Integer.parseInt(requireArg(args, ++i, "--repeat")));
                case "--compile-only" -> compileOnly = true;
                case "--no-classifier" -> runClassifier = false;
                case "--summary" -> summaryPath = Path.of(requireArg(args, ++i, "--summary"));
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (!help && exportDir == null && zipFile == null) {
            throw new IllegalArgumentException("Must provide --export-dir or --zip");
        }
        return new BenchConfig(
            exportDir,
            zipFile,
            List.copyOf(packs),
            List.copyOf(explicitTargets),
            outPath,
            budgetMs,
            maxSubgraphNodes,
            maxSubgraphRecipes,
            perPackMode,
            repeat,
            compileOnly,
            runClassifier,
            summaryPath,
            help
        );
    }

    public static int run(BenchConfig cfg) throws IOException {
        ExportJsonCatalogLoader loader = new ExportJsonCatalogLoader();
        List<String> targetPacks = cfg.packs();
        if (targetPacks.isEmpty()) {
            targetPacks = cfg.zipFile() != null
                    ? loader.discoverPacksInZip(cfg.zipFile())
                    : loader.discoverPacksInDirectory(Objects.requireNonNull(cfg.exportDir()));
        }
        if (targetPacks.isEmpty()) {
            throw new IOException("No packs discovered");
        }

        JsonObject summary = new JsonObject();
        JsonArray packsSummary = new JsonArray();
        boolean allValid = true;
        boolean multiPack = targetPacks.size() > 1;

        for (String pack : targetPacks) {
            ExportJsonCatalogLoader.validatePackName(pack);
            long t0 = System.nanoTime();
            LoadedCatalog loaded = cfg.zipFile() != null
                    ? loader.loadFromZip(cfg.zipFile(), pack, cfg.runClassifier())
                    : loader.loadFromDirectory(Objects.requireNonNull(cfg.exportDir()), pack, cfg.runClassifier());
            long t1 = System.nanoTime();

            DefaultRecipeGraphCompiler compiler = new DefaultRecipeGraphCompiler();
            CompiledRecipeGraph graph = compiler.compile(loaded.snapshot(), loaded.policy());
            long t2 = System.nanoTime();

            long loadMs = (t1 - t0) / 1_000_000L;
            long compileMs = (t2 - t1) / 1_000_000L;

            JsonObject packReport = new JsonObject();
            packReport.addProperty("pack", pack);
            packReport.addProperty("loadMs", loadMs);
            packReport.addProperty("compileMs", compileMs);
            packReport.add("loadStats", loadStatsToJson(loaded.stats()));
            packReport.add("compilationStats", compilationStatsToJson(graph.stats()));

            int targetsRun = 0;
            int validTargets = 0;
            int refinedTargets = 0;
            int budgetHitTargets = 0;
            int deterministicFailures = 0;
            long totalPlanMs = 0L;

            if (!cfg.compileOnly()) {
                List<TargetDemand> targets = selectTargets(cfg, graph);
                DefaultPlanningService service = new DefaultPlanningService(
                    graph,
                    DefaultCostMetadataProvider.instance
                );
                DefaultAcquisitionOracle oracle = new DefaultAcquisitionOracle(graph);
                FacilitySnapshot defaultFac = defaultBenchFacilities();
                PlanningBudget budget = buildBudget(cfg);
                JsonArray targetReports = new JsonArray();

                for (TargetDemand td : targets) {
                    targetsRun++;
                    PlanningRequest req = new PlanningRequest(
                        List.of(td),
                        new PlanningContext(
                            InventorySnapshot.empty,
                            defaultFac,
                            oracle,
                            DefaultHumanGuidanceView.empty(),
                            ProgressionState.overworldDefault
                        ),
                        PlanningProfile.defaultBalanced(),
                        budget
                    );

                    long pt0 = System.nanoTime();
                    PlanningResult first = service.planOnce(req);
                    long pt1 = System.nanoTime();
                    long elapsedMs = (pt1 - pt0) / 1_000_000L;
                    totalPlanMs += elapsedMs;

                    String firstCanonical = CanonicalJson.canonicalize(resultPlansToJson(first, graph, req));
                    boolean deterministicOk = true;
                    for (int r = 1; r < cfg.repeat(); r++) {
                        PlanningResult again = service.planOnce(req);
                        String againCanonical = CanonicalJson.canonicalize(resultPlansToJson(again, graph, req));
                        if (!firstCanonical.equals(againCanonical)) {
                            deterministicOk = false;
                            deterministicFailures++;
                            allValid = false;
                        }
                    }

                    boolean targetPlansValid = true;
                    for (ProductionPlan plan : first.paretoPlans()) {
                        if (!plan.certificate().valid()) {
                            targetPlansValid = false;
                            allValid = false;
                        }
                    }
                    if (targetPlansValid && !first.paretoPlans().isEmpty()) {
                        validTargets++;
                    }
                    if (first.status() == PlanningStatus.feasible) {
                        refinedTargets++;
                    } else if (first.status() == PlanningStatus.noPlanWithinBudget
                            || first.status() == PlanningStatus.conditional) {
                        budgetHitTargets++;
                    }

                    JsonObject tr = new JsonObject();
                    tr.addProperty("target", td.resource().shortString());
                    tr.addProperty("amount", td.amount());
                    tr.addProperty("status", first.status().name());
                    tr.addProperty("planTimeMs", elapsedMs);
                    tr.addProperty("deterministicRepeatOk", deterministicOk);
                    tr.add("plans", resultPlansToJson(first, graph, req));
                    tr.add("decisionPrompts", promptsToJson(first.decisionPrompts()));
                    tr.add("diagnostics", diagnosticsToJson(first.diagnostics()));
                    targetReports.add(tr);
                }

                packReport.addProperty("totalPlanMs", totalPlanMs);
                packReport.addProperty("targetsRun", targetsRun);
                packReport.addProperty("validTargets", validTargets);
                packReport.addProperty("refinedTargets", refinedTargets);
                packReport.addProperty("budgetHitTargets", budgetHitTargets);
                packReport.addProperty("deterministicFailures", deterministicFailures);
                packReport.add("targets", targetReports);
            }

            if (cfg.outPath() != null) {
                Path reportFile = resolvePackOutputPath(cfg.outPath(), pack, multiPack);
                writeJson(reportFile, packReport);
            }

            JsonObject brief = new JsonObject();
            brief.addProperty("pack", pack);
            brief.addProperty("loadMs", loadMs);
            brief.addProperty("compileMs", compileMs);
            brief.addProperty("rawRecipes", loaded.stats().rawRecipes());
            brief.addProperty("keptRecipes", loaded.stats().keptRecipes());
            brief.addProperty("compiledRecipes", graph.stats().compiledRecipes());
            brief.addProperty("cycleMacrosMined", graph.stats().cycleMacrosMined());
            brief.addProperty("largestSccSize", graph.stats().largestSccSize());
            brief.addProperty("targetsRun", targetsRun);
            brief.addProperty("validTargets", validTargets);
            brief.addProperty("refinedTargets", refinedTargets);
            brief.addProperty("budgetHitTargets", budgetHitTargets);
            brief.addProperty("totalPlanMs", totalPlanMs);
            packsSummary.add(brief);

            console.printf(
                "pack=%s loadMs=%d compileMs=%d compiled=%d targets=%d valid=%d refined=%d planMs=%d%n",
                pack,
                loadMs,
                compileMs,
                graph.stats().compiledRecipes(),
                targetsRun,
                validTargets,
                refinedTargets,
                totalPlanMs
            );
        }

        summary.add("packs", packsSummary);
        if (cfg.summaryPath() != null) {
            writeJson(cfg.summaryPath(), summary);
        } else if (cfg.outPath() != null && multiPack) {
            writeJson(cfg.outPath().resolve("summary.json"), summary);
        }
        return allValid ? 0 : 2;
    }

    public static FacilitySnapshot defaultBenchFacilities() {
        return new FacilitySnapshot(
            List.of(
                new OwnedMachineGroup(
                    "minecraft:crafting_table",
                    new MachineCapabilityId("cirrus:capability/crafting"),
                    1,
                    1.0,
                    false
                ),
                new OwnedMachineGroup(
                    "minecraft:furnace",
                    new MachineCapabilityId("cirrus:capability/smelting"),
                    1,
                    1.0,
                    false
                )
            ),
            List.of(),
            List.of(),
            Set.of()
        );
    }

    private static PlanningBudget buildBudget(BenchConfig cfg) {
        long hard = Math.max(1L, cfg.budgetMs());
        long soft = Math.min(hard, Math.max(80L, hard / 3L));
        return new PlanningBudget(
            Duration.ofMillis(soft),
            Duration.ofMillis(hard),
            cfg.maxSubgraphNodes(),
            cfg.maxSubgraphRecipes(),
            3,
            3,
            true,
            true
        );
    }

    private static List<TargetDemand> selectTargets(BenchConfig cfg, CompiledRecipeGraph graph) {
        if (!cfg.explicitTargets().isEmpty()) {
            return cfg.explicitTargets();
        }
        if (cfg.perPackMode() != null && !cfg.perPackMode().isBlank()) {
            return sampleTargets(cfg.perPackMode(), graph);
        }
        Set<ResourceKey> producible = graph.allProducibleResources();
        List<TargetDemand> chosen = new ArrayList<>();
        for (TargetDemand td : defaultTargets) {
            if (td.resource().resourceId().startsWith("minecraft:") || producible.contains(td.resource())) {
                chosen.add(td);
            }
        }
        return chosen.isEmpty() ? defaultTargets : chosen;
    }

    private static List<TargetDemand> sampleTargets(String mode, CompiledRecipeGraph graph) {
        List<ResourceKey> all = new ArrayList<>(graph.allProducibleResources());
        Collections.sort(all);
        if (all.isEmpty()) {
            return List.of();
        }
        int limit;
        long seed = 42L;
        if ("auto".equalsIgnoreCase(mode)) {
            limit = Math.min(20, all.size());
        } else if ("max".equalsIgnoreCase(mode)) {
            limit = all.size();
        } else if (mode.startsWith("seed:")) {
            seed = Long.parseLong(mode.substring("seed:".length()));
            limit = Math.min(50, all.size());
        } else {
            limit = Math.min(Integer.parseInt(mode), all.size());
        }
        if (limit >= all.size()) {
            return all.stream().map(k -> new TargetDemand(k, 1L)).toList();
        }
        SplittableRandom rng = new SplittableRandom(seed);
        List<ResourceKey> shuffled = new ArrayList<>(all);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            ResourceKey tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        List<ResourceKey> picked = new ArrayList<>(shuffled.subList(0, limit));
        Collections.sort(picked);
        return picked.stream().map(k -> new TargetDemand(k, 1L)).toList();
    }

    private static TargetDemand parseTarget(String spec) {
        int eq = spec.indexOf('=');
        String id = eq >= 0 ? spec.substring(0, eq).trim() : spec.trim();
        long qty = eq >= 0 ? Long.parseLong(spec.substring(eq + 1).trim()) : 1L;
        if (id.startsWith("[")) {
            int close = id.indexOf(']');
            if (close > 1) {
                String contentType = id.substring(1, close);
                String resId = id.substring(close + 1);
                return new TargetDemand(new ResourceKey(contentType, resId, Optional.empty()), qty);
            }
        }
        return new TargetDemand(ResourceKey.item(id), qty);
    }

    private static Path resolvePackOutputPath(Path outPath, String pack, boolean multiPack) {
        if (!multiPack && outPath.toString().endsWith(".json")) {
            return outPath;
        }
        return outPath.resolve(pack + ".json");
    }

    private static void writeJson(Path file, JsonObject obj) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String canonical = CanonicalJson.canonicalize(obj);
        JsonObject sorted = JsonParser.parseString(canonical).getAsJsonObject();
        Files.writeString(file, prettyGson.toJson(sorted) + "\n", StandardCharsets.UTF_8);
    }

    private static JsonObject loadStatsToJson(ExportLoadStats stats) {
        JsonObject obj = new JsonObject();
        obj.addProperty("rawRecipes", stats.rawRecipes());
        obj.addProperty("keptRecipes", stats.keptRecipes());
        obj.addProperty("totalDropped", stats.totalDropped());
        JsonObject dropped = new JsonObject();
        for (DropReason r : DropReason.values()) {
            dropped.addProperty(r.name(), stats.countOf(r));
        }
        obj.add("droppedByReason", dropped);
        return obj;
    }

    private static JsonObject compilationStatsToJson(GraphCompilationStats s) {
        JsonObject obj = new JsonObject();
        obj.addProperty("totalRawRecipes", s.totalRawRecipes());
        obj.addProperty("compiledRecipes", s.compiledRecipes());
        obj.addProperty("excludedInvalidOrComplex", s.excludedInvalidOrComplex());
        obj.addProperty("excludedDisplaySystems", s.excludedDisplaySystems());
        obj.addProperty("deduplicatedRecipes", s.deduplicatedRecipes());
        obj.addProperty("reversibleEdgesDetected", s.reversibleEdgesDetected());
        obj.addProperty("cycleMacrosMined", s.cycleMacrosMined());
        obj.addProperty("largestSccSize", s.largestSccSize());
        obj.addProperty("excludedByPolicy", s.excludedByPolicy());
        obj.addProperty("excludedInsaneAmount", s.excludedInsaneAmount());
        obj.addProperty("excludedNoInputs", s.excludedNoInputs());
        obj.addProperty("normalizedResourceIds", s.normalizedResourceIds());
        obj.addProperty("discouragedRecipes", s.discouragedRecipes());
        obj.addProperty("lastResortRecipes", s.lastResortRecipes());
        return obj;
    }

    private static JsonArray resultPlansToJson(
        PlanningResult result,
        CompiledRecipeGraph graph,
        PlanningRequest request
    ) {
        JsonArray arr = new JsonArray();
        for (ProductionPlan plan : result.paretoPlans()) {
            JsonObject p = new JsonObject();
            p.addProperty("planId", plan.planId());
            p.addProperty("strategyLabelKey", plan.strategyLabelKey());
            p.add("totalCost", costToJson(plan.totalCost()));
            p.add("certificate", certToJson(plan.certificate()));
            p.add("chainMetrics", ChainMetrics.compute(plan, graph, request).toJsonObject());
            JsonArray stepsArr = new JsonArray();
            for (PlanStep step : plan.steps()) {
                stepsArr.add(stepToJson(step));
            }
            p.add("steps", stepsArr);
            arr.add(p);
        }
        return arr;
    }

    private static JsonObject costToJson(CostVector c) {
        JsonObject obj = new JsonObject();
        obj.addProperty("humanActiveSeconds", round6(c.humanActiveSeconds()));
        obj.addProperty("passiveWaitSeconds", round6(c.passiveWaitSeconds()));
        obj.addProperty("machineSetupCapital", round6(c.machineSetupCapital()));
        obj.addProperty("materialScarcity", round6(c.materialScarcity()));
        obj.addProperty("cognitiveFriction", round6(c.cognitiveFriction()));
        return obj;
    }

    private static JsonObject certToJson(VerificationCertificate cert) {
        JsonObject obj = new JsonObject();
        obj.addProperty("valid", cert.valid());
        obj.addProperty("dagAcyclic", cert.dagAcyclic());
        obj.addProperty("massBalanced", cert.massBalanced());
        obj.addProperty("machinesAndCatalystsSatisfied", cert.machinesAndCatalystsSatisfied());
        obj.addProperty("integerBatchesValid", cert.integerBatchesValid());
        obj.addProperty("surplusConsistent", cert.surplusConsistent());
        JsonArray violations = new JsonArray();
        for (String v : cert.violations()) {
            violations.add(v);
        }
        obj.add("violations", violations);
        return obj;
    }

    private static JsonObject stepToJson(PlanStep step) {
        JsonObject obj = new JsonObject();
        obj.addProperty("stepId", step.stepId());
        obj.addProperty("kind", step.getClass().getSimpleName());
        switch (step) {
            case AcquireStep s -> {
                obj.addProperty("resource", s.resource().shortString());
                obj.addProperty("amount", s.amount());
                obj.addProperty("acquisitionKind", s.acquisitionKind().name());
            }
            case ProvisionMachineStep s -> {
                obj.addProperty("machineId", s.machineId());
                obj.addProperty("countToBuild", s.countToBuild());
            }
            case SetupLineStep s -> {
                obj.addProperty("systemId", s.systemId());
                obj.addProperty("primaryMachineId", s.primaryMachineId());
                obj.addProperty("targetOutput", s.targetOutput().shortString());
            }
            case CraftStep s -> {
                obj.addProperty("recipeKey", s.recipeKey().shortString());
                obj.addProperty("batches", s.batches());
                s.chosenMachineId().ifPresent(m -> obj.addProperty("chosenMachineId", m));
            }
            case LinePullStep s -> {
                obj.addProperty("lineId", s.lineId());
                obj.addProperty("resource", s.resource().shortString());
                obj.addProperty("amount", s.amount());
            }
            case ConvertStep s -> {
                obj.addProperty("recipeKey", s.conversionRecipeKey().shortString());
                obj.addProperty("batches", s.batches());
            }
            case CycleLoopStep s -> {
                obj.addProperty("macroKey", s.macroKey().macroId());
                obj.addProperty("iterations", s.iterations());
            }
        }
        return obj;
    }

    private static JsonArray promptsToJson(List<DecisionPrompt> prompts) {
        JsonArray arr = new JsonArray();
        for (DecisionPrompt prompt : prompts) {
            JsonObject o = new JsonObject();
            o.addProperty("promptId", prompt.promptId());
            o.addProperty("kind", prompt.kind().name());
            o.addProperty("titleKey", prompt.titleKey());
            o.addProperty("leverageScore", round6(prompt.leverageScore()));
            o.addProperty("optionCount", prompt.options().size());
            arr.add(o);
        }
        return arr;
    }

    private static JsonObject diagnosticsToJson(PlanningDiagnostics d) {
        JsonObject obj = new JsonObject();
        obj.addProperty("elapsedMillis", d.elapsedMillis());
        obj.addProperty("subgraphNodes", d.subgraphNodes());
        obj.addProperty("subgraphRecipes", d.subgraphRecipes());
        obj.addProperty("collapsedOrCandidates", d.collapsedOrCandidates());
        obj.addProperty("coupledComponentsEvaluated", d.coupledComponentsEvaluated());
        obj.addProperty("truncatedByNodeBudget", d.truncatedByNodeBudget());
        if (!d.unmetResources().isEmpty()) {
            JsonObject unmet = new JsonObject();
            d.unmetResources().forEach((k, v) -> unmet.addProperty(k.shortString(), v));
            obj.add("unmetResources", unmet);
        }
        return obj;
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    private static String requireArg(String[] args, int idx, String flag) {
        if (idx >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[idx];
    }

    private static void printUsage() {
        console.println(
            "Usage: OfflinePlanBench (--export-dir <dir> | --zip <file>) "
                    + "[--pack <name> | --packs a,b] [--targets id=qty,...] [--out <path>] "
                    + "[--budget-ms <n>] [--max-subgraph-nodes <n>] [--max-subgraph-recipes <n>] "
                    + "[--per-pack auto|<n>|max|seed:<n>] [--repeat <n>] [--compile-only] "
                    + "[--no-classifier] [--summary <path>]"
        );
    }
}
