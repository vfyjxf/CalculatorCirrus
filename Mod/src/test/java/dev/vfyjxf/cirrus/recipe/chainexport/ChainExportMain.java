package dev.vfyjxf.cirrus.recipe.chainexport;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.recipe.routing.topology.ConeStats;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import org.eclipse.collections.api.factory.Lists;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Offline chain-export driver: loads each pack's exported recipe dump, solves a
 * large stratified target set with the real routing planner, and writes pure
 * text chains classified by depth/shape — one directory per pack.
 *
 * <p>Run via the {@code chainExport} gradle task or directly on the test
 * runtime classpath; see {@code chain-export.sh} for parallel sharding.
 */
public final class ChainExportMain {

    private static final int CHAINS_PER_VOLUME = 200;

    //region entry

    private static volatile boolean bootstrapped = false;

    /** Idempotent vanilla registry bootstrap so headless JVMs can run the planner. */
    static void ensureVanillaBootstrap() throws ReflectiveOperationException {
        if (bootstrapped) {
            return;
        }
        synchronized (ChainExportMain.class) {
            if (bootstrapped) {
                return;
            }
            installEmptyLoadingModList();
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            bootstrapped = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void installEmptyLoadingModList() throws ReflectiveOperationException {
        Class<?> clazz = Class.forName("net.neoforged.fml.loading.LoadingModList");
        Object empty = clazz.getMethod("of", java.util.List.class, java.util.List.class, java.util.List.class,
                java.util.List.class, java.util.Map.class)
            .invoke(null, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        java.lang.reflect.Field instance = clazz.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, empty);
    }

    public static void main(String[] args) throws Exception {
        ensureVanillaBootstrap();

        Config config = Config.parse(args);
        Path exportDir = config.exportDir();
        Path outDir = config.outDir();

        List<Path> packs = discoverPacks(exportDir, config.packs());
        if (packs.isEmpty()) {
            System.err.println("no packs found under " + exportDir + " (filter: " + config.packs() + ")");
            System.exit(2);
        }
        Files.createDirectories(outDir);
        writeReadmeIfNeeded(outDir);

        System.out.printf("chain-export: %d pack(s), out=%s%n", packs.size(), outDir);
        int failures = 0;
        for (Path pack : packs) {
            String name = pack.getFileName().toString();
            if (config.batch() <= 0 && !config.overwrite() && Files.isRegularFile(outDir.resolve(name).resolve("manifest.txt"))) {
                System.out.printf("[skip] %s (manifest exists)%n", name);
                continue;
            }
            long start = System.nanoTime();
            try {
                int chains = runPack(pack, outDir, config);
                System.out.printf("[done] %-40s chains=%d elapsedMs=%d%n",
                    name, chains, (System.nanoTime() - start) / 1_000_000);
            } catch (Throwable t) {
                failures++;
                System.err.printf("[fail] %s: %s%n", name, t);
                t.printStackTrace(System.err);
            }
        }
        System.out.printf("chain-export finished: %d ok, %d failed%n", packs.size() - failures, failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    //endregion

    //region config

    record Config(
        Path exportDir,
        Path outDir,
        String packs,
        int perPack,
        int modCap,
        long seed,
        String rule,
        int budgetMs,
        int packTimeoutS,
        boolean overwrite,
        String targetBlacklist,
        int batch
    ) {

        static Config parse(String[] args) {
            Map<String, String> flags = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("--")) {
                    String key = arg.substring(2);
                    String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
                    flags.put(key, value);
                }
            }
            String exportDir = pick(flags, "export-dir", "../exports", "cirrus.chains.export.dir", "cirrus.export.dir");
            String outDir = pick(flags, "out", "../chain-exports", "cirrus.chains.out");
            String packs = pick(flags, "packs", "", "cirrus.chains.packs");
            String perPack = pick(flags, "per-pack", "auto", "cirrus.chains.perPack");
            String modCap = pick(flags, "mod-cap", "80", "cirrus.chains.modCap");
            String seed = pick(flags, "seed", "20260829", "cirrus.chains.seed");
            String rule = pick(flags, "rule", "preview", "cirrus.chains.rule");
            String budgetMs = pick(flags, "budget-ms", "10000", "cirrus.chains.budgetMs");
            String packTimeout = pick(flags, "pack-timeout-s", "0", "cirrus.chains.packTimeoutS");
            String overwrite = pick(flags, "overwrite", "false", "cirrus.chains.overwrite");
            String blacklist = pick(flags, "target-blacklist", "decompress", "cirrus.chains.targetBlacklist");
            String batch = pick(flags, "batch", "0", "cirrus.chains.batch");
            return new Config(
                Path.of(exportDir),
                Path.of(outDir),
                packs,
                "max".equals(perPack) ? 2000 : "auto".equals(perPack) ? -1 : Integer.parseInt(perPack),
                Integer.parseInt(modCap),
                Long.parseLong(seed),
                rule,
                Integer.parseInt(budgetMs),
                Integer.parseInt(packTimeout),
                Boolean.parseBoolean(overwrite),
                blacklist,
                Integer.parseInt(batch)
            );
        }

        /** flag value -> first non-blank sysprop -> first non-blank env var -> default. */
        private static String pick(Map<String, String> flags, String flag, String defaultValue, String... properties) {
            String value = flags.get(flag);
            if (value != null) return value;
            for (String property : properties) {
                String pv = System.getProperty(property);
                if (pv != null && !pv.isBlank()) return pv;
            }
            for (String property : properties) {
                if (property.startsWith("cirrus.")) {
                    String ev = System.getenv(property.replace('.', '_').toUpperCase());
                    if (ev != null && !ev.isBlank()) return ev;
                }
            }
            return defaultValue;
        }
    }

    private static List<Path> discoverPacks(Path exportDir, String filter) throws IOException {
        List<String> wanted = filter == null || filter.isBlank()
            ? null
            : Stream.of(filter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        try (Stream<Path> stream = Files.list(exportDir)) {
            List<Path> packs = new ArrayList<>();
            stream.filter(Files::isDirectory)
                .sorted()
                .forEach(path -> {
                    String name = path.getFileName().toString();
                    if (wanted == null || wanted.contains(name)) {
                        packs.add(path);
                    }
                });
            return packs;
        }
    }

    //endregion

    //region per-pack pipeline

    static int runPack(Path packDir, Path outRoot, Config config) throws Exception {
        return config.batch() > 0 ? runPackBatch(packDir, outRoot, config) : runPackFull(packDir, outRoot, config);
    }

    //region iterative batch mode (generate -> review -> filter -> repeat)

    static final com.google.gson.Gson STATE_GSON = new com.google.gson.Gson();

    /** Recommended recipe-coverage target by pack size (see overview analysis). */
    static double coverageTarget(int keptRecipes) {
        if (keptRecipes < 2_000) return 0.70;
        if (keptRecipes < 6_000) return 0.60;
        if (keptRecipes < 25_000) return 0.50;
        if (keptRecipes < 100_000) return 0.32;
        return 0.20;
    }

    static int runPackBatch(Path packDir, Path outRoot, Config config) throws Exception {
        ensureVanillaBootstrap();
        String packName = packDir.getFileName().toString();
        OfflineContent.Session session = OfflineContent.newSession();
        ExportPackLoader.LoadedPack pack = ExportPackLoader.load(packDir, session);

        // ---- persistent per-pack state -------------------------------------------------
        Path packRoot = outRoot.resolve(packName);
        Path stateDir = packRoot.resolve("state");
        Files.createDirectories(stateDir);
        Set<String> covered = loadStringSet(stateDir.resolve("covered-recipes.json"));
        Set<String> seenStructures = loadStringSet(stateDir.resolve("structure-seen.json"));
        List<ChainTargetSelector.ReviewFilter> filters = loadFilters(stateDir.resolve("filters.json"));
        int batchId = nextBatchId(packRoot);

        List<RecipeEntry<?>> entries = new ArrayList<>(pack.recipes().size());
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        for (ExportPackLoader.ExportRecipeSystem system : pack.systems()) {
            builder.registerSystem(system);
            for (ExportPackLoader.ExportRecipe recipe : system.allRecipes()) {
                entries.add(RecipeEntry.of(system, recipe));
            }
        }
        session.attachRegistry();
        RecipeApiImpl previousApi = attachRecipeApi(builder);
        try {
            long graphStart = System.nanoTime();
            RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.ofAll(entries));
            long graphMs = (System.nanoTime() - graphStart) / 1_000_000;

            RoutingService service = new RoutingService(graph);
            for (RuleDefinition rule : DefaultRuleDefinitions.defaultCollection().rules()) {
                service.registerRuleDefinition(rule);
            }
            RuleDefinition activeRule = ruleFor(config.rule());

            ChainTargetSelector selector = new ChainTargetSelector(pack);
            List<ChainTargetSelector.Target> targets = selector.selectBatch(config.batch(), config.modCap(), filters, covered);

            Path batchDir = packRoot.resolve(String.format("batch-%03d", batchId));
            Files.createDirectories(batchDir);
            List<java.util.regex.Pattern> recipeFilters = new ArrayList<>();
            for (ChainTargetSelector.ReviewFilter filter : filters) {
                if (ChainTargetSelector.ReviewFilter.RECIPE.equals(filter.type())) {
                    recipeFilters.add(filter.compiled());
                }
            }
            try (PackWriter writer = new PackWriter(batchDir)) {
                int done = 0;
                int written = 0;
                int structuralDupes = 0;
                int noiseDropped = 0;
                int trivialKept = 0;
                int filterHitChains = 0;
                Set<String> coveredThisBatch = new java.util.LinkedHashSet<>();
                for (ChainTargetSelector.Target target : targets) {
                    RecipeEntry<ExportPackLoader.ExportRecipe> entry = RecipeEntry.of(target.system(), target.recipe());
                    int targetIndex = service.catalog().indexOf(entry);
                    ConeStats cone = null;
                    if (targetIndex >= 0) {
                        java.util.BitSet allAllowed = new java.util.BitSet(service.catalog().size());
                        allAllowed.set(0, service.catalog().size());
                        cone = service.coneStats(targetIndex, allAllowed);
                    }
                    RoutingBudget budget = cone != null
                        ? RoutingBudget.fromConeStats(cone, PlannerMode.standard, Math.max(1, target.recipe().inputs.size()))
                        : RoutingBudget.standardDefault();
                    RoutingRequest request = RoutingRequest.builder()
                        .plannerMode(PlannerMode.standard)
                        .targetRecipe(entry, 1)
                        .ruleProfileId(activeRule.profile().profileId())
                        .sourcePolicyId(activeRule.sourcePolicy().policyId())
                        .stock(Stock.empty())
                        .loopPolicy(LoopPolicy.allowBoundedAmplification)
                        .maxProducerCandidates(16)
                        .budget(budget)
                        .build();
                    RoutingOutcome outcome = service.route(request);
                    ChainClassifier.Analyzed analyzed = ChainClassifier.analyze(outcome.plan(), target);
                    done++;

                    if (outcome.status() == dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus.failed
                        && analyzed.features().actionCount() == 0) {
                        noiseDropped++;
                        continue;
                    }
                    String fingerprint = structureFingerprint(outcome);
                    if (fingerprint.equals("no-plan") || fingerprint.equals("no-execute")
                        || !seenStructures.add(fingerprint)) {
                        structuralDupes++;
                        continue;
                    }
                    if (analyzed.features().depth() <= 1 && trivialKept >= 50) {
                        noiseDropped++;
                        continue;
                    }
                    if (analyzed.features().depth() <= 1) {
                        trivialKept++;
                    }

                    String text = ChainTextRenderer.render(written, target, analyzed, outcome, ruleLabel(activeRule));
                    writer.writeChain(written, target, analyzed.features(), text);
                    written++;

                    boolean hitRecipeFilter = false;
                    for (ChainClassifier.ActionNode node : analyzed.nodes()) {
                        if (node.execute().recipe().recipe() instanceof ExportPackLoader.ExportRecipe export) {
                            coveredThisBatch.add(export.recipeId);
                            if (!hitRecipeFilter) {
                                for (java.util.regex.Pattern pattern : recipeFilters) {
                                    if (pattern.matcher(export.recipeId).find()) {
                                        hitRecipeFilter = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (hitRecipeFilter) {
                        filterHitChains++;
                    }
                    if (done % 50 == 0) {
                        System.out.printf("[%s#%03d] %d/%d solved (%d kept)%n", packName, batchId, done, targets.size(), written);
                    }
                }
                writer.finish(pack, targets, config.batch(), config, activeRule, graphMs, written, noiseDropped, structuralDupes, 0);

                // ---- update state + coverage ledger --------------------------------------
                Set<String> previouslyCovered = new java.util.LinkedHashSet<>(covered);
                covered.addAll(coveredThisBatch);
                int trulyNew = (int) coveredThisBatch.stream().filter(r -> !previouslyCovered.contains(r)).count();
                saveStringSet(stateDir.resolve("covered-recipes.json"), covered);
                saveStringSet(stateDir.resolve("structure-seen.json"), seenStructures);
                writeBatchReport(batchDir, pack, batchId, written, done, structuralDupes, noiseDropped,
                    trulyNew, covered.size(), seenStructures.size(), filterHitChains, filters.size());
                appendCoverage(packRoot, pack, batchId, written, trulyNew, covered.size(), filterHitChains);
                System.out.printf("[%s#%03d] written=%d covered +%d -> %d/%d (%.1f%% of kept)%n",
                    packName, batchId, written, trulyNew, covered.size(),
                    pack.keptRecipes(), 100.0 * covered.size() / Math.max(1, pack.keptRecipes()));
                return written;
            }
        } finally {
            restoreRecipeApi(previousApi);
            session.close();
        }
    }

    private static int nextBatchId(Path packRoot) throws IOException {
        int next = 1;
        if (Files.isDirectory(packRoot)) {
            try (var stream = Files.list(packRoot)) {
                for (Path child : stream.toList()) {
                    String name = child.getFileName().toString();
                    if (name.startsWith("batch-")) {
                        try {
                            next = Math.max(next, Integer.parseInt(name.substring(6)) + 1);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return next;
    }

    private static Set<String> loadStringSet(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return new java.util.LinkedHashSet<>();
        try (var reader = Files.newBufferedReader(file)) {
            String[] array = STATE_GSON.fromJson(reader, String[].class);
            return new java.util.LinkedHashSet<>(java.util.Arrays.asList(array == null ? new String[0] : array));
        }
    }

    private static void saveStringSet(Path file, Set<String> set) throws IOException {
        try (var writer = Files.newBufferedWriter(file)) {
            STATE_GSON.toJson(set.toArray(new String[0]), writer);
        }
    }

    private static List<ChainTargetSelector.ReviewFilter> loadFilters(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            // seed with the known compression-ladder pattern so batch 1 starts clean
            List<ChainTargetSelector.ReviewFilter> defaults = List.of(
                new ChainTargetSelector.ReviewFilter("compression-ladders", ChainTargetSelector.ReviewFilter.TARGET, "decompress"));
            saveFilters(file, defaults);
            return defaults;
        }
        try (var reader = Files.newBufferedReader(file)) {
            var tree = STATE_GSON.fromJson(reader, com.google.gson.JsonObject.class);
            List<ChainTargetSelector.ReviewFilter> filters = new ArrayList<>();
            if (tree != null && tree.has("filters")) {
                for (var element : tree.getAsJsonArray("filters")) {
                    var obj = element.getAsJsonObject();
                    filters.add(new ChainTargetSelector.ReviewFilter(
                        obj.has("name") ? obj.get("name").getAsString() : "unnamed",
                        obj.has("type") ? obj.get("type").getAsString() : ChainTargetSelector.ReviewFilter.TARGET,
                        obj.has("pattern") ? obj.get("pattern").getAsString() : ""));
                }
            }
            return filters;
        }
    }

    static void saveFilters(Path file, List<ChainTargetSelector.ReviewFilter> filters) throws IOException {
        var array = new com.google.gson.JsonArray();
        for (ChainTargetSelector.ReviewFilter filter : filters) {
            var obj = new com.google.gson.JsonObject();
            obj.addProperty("name", filter.name());
            obj.addProperty("type", filter.type());
            obj.addProperty("pattern", filter.pattern());
            array.add(obj);
        }
        var root = new com.google.gson.JsonObject();
        root.add("filters", array);
        try (var writer = Files.newBufferedWriter(file)) {
            STATE_GSON.toJson(root, writer);
        }
    }

    private static void writeBatchReport(Path batchDir, ExportPackLoader.LoadedPack pack, int batchId,
                                         int written, int solved, int structuralDupes, int noiseDropped,
                                         int newlyCovered, int totalCovered, int totalStructures,
                                         int filterHitChains, int filterCount) throws IOException {
        double coverage = 100.0 * totalCovered / Math.max(1, pack.keptRecipes());
        double targetPct = 100.0 * coverageTarget(pack.keptRecipes());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(batchDir.resolve("report.txt"), StandardCharsets.UTF_8))) {
            out.printf("batch %03d | pack %s%n", batchId, pack.name());
            out.printf("solved=%d written=%d structuralDupes=%d noiseDropped=%d%n", solved, written, structuralDupes, noiseDropped);
            out.printf("coverage: +%d recipes this batch -> %d/%d kept (%.1f%%, target %.0f%%)%n",
                newlyCovered, totalCovered, pack.keptRecipes(), coverage, targetPct);
            out.printf("structures: total distinct=%d%n", totalStructures);
            out.printf("convergence: %.1f%% of written chains matched a review filter (%d filters active)%n",
                written == 0 ? 0 : 100.0 * filterHitChains / written, filterCount);
            out.printf("guidance: review chains in this batch, distill new patterns into state/filters.json,%n");
            out.printf("         then run the next batch. Stop when convergence >= 85%% or coverage target reached.%n");
        }
    }

    private static void appendCoverage(Path packRoot, ExportPackLoader.LoadedPack pack, int batchId,
                                       int written, int newlyCovered, int totalCovered, int filterHitChains) throws IOException {
        Path file = packRoot.resolve("coverage.tsv");
        boolean fresh = !Files.isRegularFile(file);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            fresh ? java.nio.file.StandardOpenOption.CREATE : java.nio.file.StandardOpenOption.APPEND))) {
            if (fresh) {
                out.println("batch\twritten\tnewlyCovered\ttotalCovered\tkept\tcoveragePct\ttargetPct\tfilterHitPct");
            }
            out.printf("%d\t%d\t%d\t%d\t%d\t%.1f\t%.0f\t%.1f%n", batchId, written, newlyCovered, totalCovered,
                pack.keptRecipes(), 100.0 * totalCovered / Math.max(1, pack.keptRecipes()),
                100.0 * coverageTarget(pack.keptRecipes()),
                written == 0 ? 0 : 100.0 * filterHitChains / written);
        }
    }

    //endregion

    static int runPackFull(Path packDir, Path outRoot, Config config) throws Exception {
        ensureVanillaBootstrap();
        String packName = packDir.getFileName().toString();
        OfflineContent.Session session = OfflineContent.newSession();

        ExportPackLoader.LoadedPack pack = ExportPackLoader.load(packDir, session);
        // big / heavily-modded packs get proportionally more chains: 600 floor,
        // +1 per 25 recipes, 2000 ceiling (atm10 & friends saturate the cap)
        int perPack = config.perPack() >= 0
            ? config.perPack()
            : Math.max(600, Math.min(2000, pack.totalRecipes() / 25));

        List<RecipeEntry<?>> entries = new ArrayList<>(pack.recipes().size());
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        for (ExportPackLoader.ExportRecipeSystem system : pack.systems()) {
            builder.registerSystem(system);
            for (ExportPackLoader.ExportRecipe recipe : system.allRecipes()) {
                entries.add(RecipeEntry.of(system, recipe));
            }
        }

        session.attachRegistry();
        RecipeApiImpl previousApi = attachRecipeApi(builder);
        try {
            long graphStart = System.nanoTime();
            RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.ofAll(entries));
            long graphMs = (System.nanoTime() - graphStart) / 1_000_000;

            RoutingService service = new RoutingService(graph);
            for (RuleDefinition rule : DefaultRuleDefinitions.defaultCollection().rules()) {
                service.registerRuleDefinition(rule);
            }
            RuleDefinition activeRule = ruleFor(config.rule());

            ChainTargetSelector selector = new ChainTargetSelector(pack);
            List<ChainTargetSelector.Target> targets = selector.select(perPack, config.modCap(), config.targetBlacklist());

            Path packOut = outRoot.resolve(packName);
            Files.createDirectories(packOut);
            try (PackWriter writer = new PackWriter(packOut)) {
                long deadlineNanos = config.packTimeoutS() > 0
                    ? System.nanoTime() + config.packTimeoutS() * 1_000_000_000L
                    : 0;
                int done = 0;
                int written = 0;
                int noiseDropped = 0;
                int structuralDupes = 0;
                int trivialCapped = 0;
                int trivialKept = 0;
                Map<String, Integer> structureCounts = new HashMap<>();
                for (ChainTargetSelector.Target target : targets) {
                    if (deadlineNanos > 0 && System.nanoTime() > deadlineNanos) {
                        System.out.printf("[%s] pack timeout hit after %d/%d targets%n", packName, done, targets.size());
                        break;
                    }
                    RecipeEntry<ExportPackLoader.ExportRecipe> entry = RecipeEntry.of(target.system(), target.recipe());
                    // cone-shaped deterministic budget, same sizing the production calculator uses
                    int targetIndex = service.catalog().indexOf(entry);
                    ConeStats cone = null;
                    if (targetIndex >= 0) {
                        java.util.BitSet allAllowed = new java.util.BitSet(service.catalog().size());
                        allAllowed.set(0, service.catalog().size());
                        cone = service.coneStats(targetIndex, allAllowed);
                    }
                    RoutingBudget budget = cone != null
                        ? RoutingBudget.fromConeStats(cone, PlannerMode.standard, Math.max(1, target.recipe().inputs.size()))
                        : RoutingBudget.standardDefault();
                    RoutingRequest request = RoutingRequest.builder()
                        .plannerMode(PlannerMode.standard)
                        .targetRecipe(entry, 1)
                        .ruleProfileId(activeRule.profile().profileId())
                        .sourcePolicyId(activeRule.sourcePolicy().policyId())
                        .stock(Stock.empty())
                        .loopPolicy(LoopPolicy.allowBoundedAmplification)
                        .maxProducerCandidates(16)
                        .budget(budget)
                        .build();
                    RoutingOutcome outcome = service.route(request);
                    ChainClassifier.Analyzed analyzed = ChainClassifier.analyze(outcome.plan(), target);
                    done++;

                    // primary noise screening — keep the audit corpus informative:
                    // 1. failed plans with zero actions carry no reviewable content
                    if (outcome.status() == dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus.failed
                        && analyzed.features().actionCount() == 0) {
                        noiseDropped++;
                        continue;
                    }
                    // 2. identical recipe sequences (stairs/slabs/compression families) add nothing new
                    String fingerprint = structureFingerprint(outcome);
                    if (structureCounts.merge(fingerprint, 1, Integer::sum) > 2) {
                        structuralDupes++;
                        continue;
                    }
                    // 3. cap single-step chains, they are rarely worth reviewing at scale
                    if (analyzed.features().depth() <= 1 && trivialKept >= 50) {
                        trivialCapped++;
                        continue;
                    }
                    if (analyzed.features().depth() <= 1) {
                        trivialKept++;
                    }

                    String text = ChainTextRenderer.render(written, target, analyzed, outcome, ruleLabel(activeRule));
                    writer.writeChain(written, target, analyzed.features(), text);
                    written++;
                    if (done % 100 == 0) {
                        System.out.printf("[%s] %d/%d targets solved (%d kept)%n", packName, done, targets.size(), written);
                    }
                }
                writer.finish(pack, targets, perPack, config, activeRule, graphMs, written, noiseDropped, structuralDupes, trivialCapped);
                return written;
            }
        } finally {
            restoreRecipeApi(previousApi);
            session.close();
        }
    }

    //endregion

    //region rules

    /** Structural signature: ordered sequence of executed recipe ids. */
    private static String structureFingerprint(RoutingOutcome outcome) {
        if (outcome.plan() == null) {
            return "no-plan";
        }
        StringBuilder sb = new StringBuilder(64);
        for (PlanAction action : outcome.plan().actions()) {
            if (action instanceof PlanAction.ExecuteRecipe execute) {
                if (!sb.isEmpty()) {
                    sb.append('|');
                }
                sb.append(execute.recipe().id());
            }
        }
        return sb.isEmpty() ? "no-execute" : sb.toString();
    }

    static RuleDefinition ruleFor(String key) {
        return switch (key == null ? "preview" : key) {
            case "execution", "execute" -> DefaultRuleDefinitions.standardExecution();
            case "default", "standard" -> DefaultRuleDefinitions.standardDefault();
            case "strict", "assured" -> DefaultRuleDefinitions.assuredBounded();
            case "proven" -> DefaultRuleDefinitions.assuredProven();
            default -> DefaultRuleDefinitions.calculatorPreview();
        };
    }

    static String ruleLabel(RuleDefinition rule) {
        return rule.ruleId() + " (" + rule.name() + ") | planner=standard | loop=allowBoundedAmplification";
    }

    //endregion

    //region api attach

    private static RecipeApiImpl attachRecipeApi(RecipeApiRegistryBuilder builder) {
        RecipeApi current = RecipeApiImpl.instance();
        RecipeApiImpl previous = current instanceof RecipeApiImpl impl ? impl : null;
        RecipeApiImpl.attach(new RecipeApiImpl(builder));
        return previous;
    }

    private static void restoreRecipeApi(RecipeApiImpl previous) {
        if (previous != null) {
            RecipeApiImpl.attach(previous);
        }
    }

    //endregion

    //region output writer

    private static final class PackWriter implements AutoCloseable {

        private final Path packOut;
        private final Map<String, PrintWriter> categoryWriters = new LinkedHashMap<>();
        private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        private final List<String> indexLines = new ArrayList<>();

        PackWriter(Path packOut) throws IOException {
            this.packOut = packOut;
        }

        void writeChain(int chainId, ChainTargetSelector.Target target, ChainClassifier.ChainFeatures features, String text) throws IOException {
            String category = features.category();
            PrintWriter writer = categoryWriters.computeIfAbsent(category, this::openCategory);
            writer.println(text);
            categoryCounts.merge(category, 1, Integer::sum);
            if (categoryCounts.get(category) % CHAINS_PER_VOLUME == 0) {
                rotate(category);
            }
            indexLines.add(String.join("\t",
                String.format("%04d", chainId),
                category,
                features.flags(),
                target.outputId(),
                String.valueOf(features.depth()),
                String.valueOf(features.actionCount()),
                String.valueOf(features.distinctRecipes()),
                String.valueOf(features.totalCrafts()),
                String.valueOf(features.rawKinds()),
                String.valueOf(features.pendingCount()),
                String.valueOf(features.machineKinds())
            ));
        }

        private PrintWriter openCategory(String category) {
            try {
                return new PrintWriter(Files.newBufferedWriter(
                    packOut.resolve("chains-" + category + "-001.txt"), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        private void rotate(String category) throws IOException {
            PrintWriter old = categoryWriters.get(category);
            if (old != null) {
                old.flush();
                old.close();
            }
            int volume = categoryCounts.get(category) / CHAINS_PER_VOLUME + 1;
            categoryWriters.put(category, new PrintWriter(Files.newBufferedWriter(
                packOut.resolve(String.format("chains-%s-%03d.txt", category, volume)), StandardCharsets.UTF_8)));
        }

        void finish(ExportPackLoader.LoadedPack pack, List<ChainTargetSelector.Target> targets, int perPack,
                    Config config, RuleDefinition rule, long graphMs,
                    int written, int noiseDropped, int structuralDupes, int trivialCapped) throws IOException {
            writeTargets(pack, targets);
            writeIndex();
            writeManifest(pack, perPack, config, rule, graphMs, written, noiseDropped, structuralDupes, trivialCapped);
            categoryWriters.values().forEach(PrintWriter::flush);
            categoryWriters.values().forEach(PrintWriter::close);
        }

        private void writeTargets(ExportPackLoader.LoadedPack pack, List<ChainTargetSelector.Target> targets) throws IOException {
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                packOut.resolve("targets.tsv"), StandardCharsets.UTF_8))) {
                writer.println("idx\ttarget\tsystem\trecipe\tconeScore\ttier");
                int i = 0;
                for (ChainTargetSelector.Target target : targets) {
                    writer.println(String.join("\t",
                        String.valueOf(i++),
                        target.outputId(),
                        target.recipe().systemId,
                        target.recipe().recipeId,
                        String.valueOf(target.coneScore()),
                        target.tier()
                    ));
                }
            }
        }

        private void writeIndex() throws IOException {
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                packOut.resolve("index.tsv"), StandardCharsets.UTF_8))) {
                writer.println("id\tcategory\tflags\ttarget\tdepth\tactions\trecipes\tcrafts\traw_kinds\tpending\tmachines");
                for (String line : indexLines) {
                    writer.println(line);
                }
            }
        }

        private void writeManifest(ExportPackLoader.LoadedPack pack, int perPack, Config config,
                                   RuleDefinition rule, long graphMs,
                                   int written, int noiseDropped, int structuralDupes, int trivialCapped) throws IOException {
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                packOut.resolve("manifest.txt"), StandardCharsets.UTF_8))) {
                writer.println("pack: " + pack.name());
                writer.printf("recipesTotal: %d (exported) | keptAfterClassification: %d | junkExcluded: %d | duplicateExcluded: %d | droppedNoContent: %d%n",
                    pack.totalRecipes(), pack.keptRecipes(), pack.excludedJunk(), pack.excludedDuplicate(), pack.droppedNoContent());
                writer.printf("systems: %d | graphBuildMs: %d | parseMs: %d | classifyMs: %d%n",
                    pack.systems().size(), graphMs, pack.parseMs(), pack.classifyMs());
                writer.printf("targetsRequested: %d (per-pack=%s) | chainsWritten: %d%n",
                    perPack, config.perPack() < 0 ? "auto" : String.valueOf(config.perPack()), written);
                writer.printf("noiseScreening: emptyFailedDropped=%d | structuralDupesDropped=%d | trivialCapped=%d%n",
                    noiseDropped, structuralDupes, trivialCapped);
                writer.printf("rule: %s | budget: cone-shaped (fromConeStats) | modCap: %d | seed: %d | targetBlacklist: %s%n",
                    ruleLabel(rule), config.modCap(), config.seed(),
                    config.targetBlacklist() == null || config.targetBlacklist().isBlank() ? "(none)" : config.targetBlacklist());
                writer.println();
                writer.println("classification tag counts (builtin rules v2):");
                pack.tagCounts().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> writer.printf("  %-32s %d%n", e.getKey(), e.getValue()));
                writer.println();
                writer.println("chain categories:");
                categoryCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> writer.printf("  %-16s %d%n", e.getKey(), e.getValue()));
            }
        }

        @Override
        public void close() {
            categoryWriters.values().forEach(PrintWriter::close);
        }
    }

    //endregion

    //region readme

    private static void writeReadmeIfNeeded(Path outDir) throws IOException {
        Path readme = outDir.resolve("README.txt");
        if (Files.isRegularFile(readme)) return;
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(readme, StandardCharsets.UTF_8))) {
            writer.println("""
                Recipe chain export — plain-text synthesis chains for LLM audit
                ================================================================

                Generated by CalculatorCirrus's real routing planner (StandardPlanner) running
                offline over exported recipe dumps (exports/<pack>/recipes-*.json). One directory
                per pack; chains are solved with empty stock, so every chain starts from raw
                materials and each recipe is executed the minimum number of times.

                Modeling notes (offline fidelity):
                - Content identity is the exported id string; data components are not compared.
                - Tag inputs are expanded to their exported member lists (any-of semantics).
                - 'complex' slots are not modeled into the solver; they are annotated in the
                  source dump only and do not appear in chains.
                - Multi-alternative outputs are modeled as their first exported member.
                - Junk/duplicate recipes are excluded before graph build (classification builtin
                  rules v2, same as the production calculator).

                BUILT-IN RULES ALREADY IN EFFECT - do not re-suggest these:
                - Excluded before planning (never appear in chains): cirrus:quality/junk,
                  cirrus:quality/duplicate (classification builtin rules v2).
                - Advisory tags, shown inline as "[rule-tag: pattern/exchange]" on the EXECUTE
                  line and summarized in the chain header as "builtin-rule-tags": the recipe
                  matches a known pattern the rule system already flags (pattern/exchange,
                  pattern/recolor, intent/recycling, ...). These recipes are currently advisory
                  (visible but not auto-excluded). Useful feedback here is limited to "should
                  this tag be upgraded to exclusion?" - not re-discovering the pattern.
                - Solve-time rules baked into every chain: the calculator_preview rule profile
                  (5 scoring axes: subtree cost, craft count, inventory shortage, depth,
                  amplification risk), the tag source policy, bounded-amplification loop policy
                  and cone-shaped budgets. The producer chosen at every step already reflects
                  these rules; the chains are their OUTPUT.
                - Where to focus review instead: chains/steps WITHOUT any rule-tag that still
                  look wrong (fake production, impure recipes, pathological loops the planner
                  actually chose), UNRESOLVED entries with reason=BUDGET_TRUNCATED or
                  RANK_TRUNCATED (planner gave up - why?), and wide/colossal chains where the
                  chosen path is absurd despite the scoring rules.

                Chain text format:
                ### CHAIN <id> | category=<bucket> | depthBucket=<bucket> | flags=...
                target: <output id> x1 | recipe/system/tier/coneScore meta
                status/search/rule/machines header lines
                [A<n>] EXECUTE <recipe id> x<times> @ <machine>   (indented by dependency depth)
                       in : <input id> x<amount>, ...
                       out: <output id> x<amount>, ...
                       <= from A<x>,A<y>          (which earlier actions feed this one)
                [RAW] <raw material id> x<amount>, ...
                [UNRESOLVED] <input> x<amount> (candidates=N, reason=...)
                [CATALYST] <input> x<amount> for <recipe>

                Categories (by longest dependency path, depth):
                  trivial <=1 | short 2-3 | medium 4-6 | long 7-11 | giant 12-19 | colossal >=20
                  mega-wide: >=300 actions | incomplete: any unresolved demand (audit these!)
                Flags: +wide (actions/depth >= 8), +amplified hints in search header.

                Per-pack files:
                  manifest.txt  pack stats, classification tag counts, category totals
                  index.tsv     one line per chain
                  targets.tsv   selected targets with cone ranking and tier
                  chains-<category>-<volume>.txt   the chains (200 per volume)

                ITERATIVE MODE (--batch N, default workflow):
                Batch N chains per pack into <pack>/batch-NNN/, then review, distill
                findings into filters, and run the next batch aimed at UNCOVERED recipes.
                Driver: bash chain-review.sh {init|next|status} [packs]
                        bash chain-review.sh add-filter <pack> <name> <targetRegex|recipeRegex> <pattern>
                State (per pack, the machine-readable interface for review agents):
                  state/covered-recipes.json   recipe ids any chain has executed (grows per batch)
                  state/structure-seen.json    structure fingerprints already written (global dedup)
                  state/filters.json           review filters: [{name, type, pattern}]
                                              - targetRegex: target/producer id match -> target skipped
                                              - recipeRegex: chain recipes match -> counted as convergence
                  coverage.tsv                 per-batch ledger:
                    batch written newlyCovered totalCovered kept coveragePct targetPct filterHitPct
                  batch-NNN/report.txt         human summary incl. convergence + guidance
                Coverage targets by pack size: <2k:70% <6k:60% <25k:50% <100k:32% else:20%.
                STOP when filterHitPct (convergence) >= 85% or coveragePct >= targetPct.
                Chain selection per batch: demand cones ranked by UNCOVERED recipe count
                (10 pts per uncovered recipe), so successive batches steer into new regions.
                """);
        }
    }

    //endregion
}
