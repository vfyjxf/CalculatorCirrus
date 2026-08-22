package dev.vfyjxf.cirrus.testmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseCatalogCache;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseFailureRegistry;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless plan-sampling hook: with {@code -Dcirrus.test.autosample=<count>}
 * the server, once RecipeApi is up, ranks every mimic-craftable item by an
 * approximate demand-cone size, picks the most complex {@code <count>} targets
 * (namespace-diversified, plus {@code -Dcirrus.test.autosample.force=<ids>}
 * forced ones), runs the exact calculator routing on each and writes one JSON
 * dump per target — raw pack ids, per-action chosen recipes with
 * classification tags / purity / free-input flags, pending inputs with their
 * unresolved candidate counts and the producer alternatives the planner had
 * available — to {@code <out>/<pack>/plans/}, then halts the server
 * ({@code -Dcirrus.test.autosample.exit=0} to keep it running).
 *
 * <p>Complexity tiers ({@code -Dcirrus.test.autosample.tier=}):
 * {@code complex} (default) exact re-ranks the top slice and takes the deepest
 * cones; {@code medium} / {@code plain} sample evenly across a percentile band
 * of the cheap tree-sum ranking (35-65% / 75-95%) so ordinary shallow targets
 * are covered too. Non-complex tiers write to {@code <out>/<pack>-<tier>/}.
 *
 * <p>The dumps are consumed by the offline plan audit: every id is mapped back
 * to the export pack so plans can be cross-checked against the pack's real
 * recipe JSON.
 */
public final class TestModAutoSample {
    private static final Logger LOG = LoggerFactory.getLogger("CirrusTestAutoSample");
    private static final String PROPERTY = "cirrus.test.autosample";

    private static volatile boolean started;
    private static volatile MinecraftServer server;

    private TestModAutoSample() {
    }

    static boolean enabled() {
        String value = System.getProperty(PROPERTY);
        return value != null && !value.isBlank() && !value.equals("0") && !value.equals("false");
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> server = event.getServer());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> maybeStart());
    }

    private static void maybeStart() {
        if (started) {
            return;
        }
        RecipeApi api;
        try {
            api = RecipeApi.instance();
        } catch (RuntimeException notReady) {
            if (notReady.getMessage() != null && notReady.getMessage().contains("not initialized")) {
                return; // retried on the next tick
            }
            started = true;
            LOG.error("autosample failed to acquire RecipeApi", notReady);
            return;
        }
        started = true;
        Thread worker = new Thread(() -> run(api), "cirrus-autosample");
        worker.setDaemon(true);
        worker.start();
    }

    //region sampling run

    private static void run(RecipeApi api) {
        try {
            int count = Math.max(1, Integer.parseInt(System.getProperty(PROPERTY, "100").trim()));
            int times = Math.max(1, Integer.getInteger(PROPERTY + ".times", 1));
            int modCap = Math.max(1, Integer.getInteger(PROPERTY + ".modcap", 20));
            Path outRoot = Path.of(System.getProperty(PROPERTY + ".out", "/tmp/cirrus_sampling"));
            List<String> forced = Arrays.stream(System.getProperty(PROPERTY + ".force", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
            String tier = System.getProperty(PROPERTY + ".tier", "complex").trim().toLowerCase(java.util.Locale.ROOT);

            awaitDatabaseIndex(api);

            SamplingModel model = buildModel(api);
            Path packDir = outRoot.resolve(model.pack + (tier.equals("complex") ? "" : "-" + tier));
            Files.createDirectories(packDir.resolve("plans"));

            List<Target> targets = selectTargets(model, count, modCap, forced, tier);
            LOG.info("autosample pack={} items={} producers={} targets={}",
                model.pack, model.reverseRaw.size(), model.producers.size(), targets.size());
            writeSelection(packDir, targets);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonArray rows = new JsonArray();
            for (int i = 0; i < targets.size(); i++) {
                Target target = targets.get(i);
                long start = System.nanoTime();
                JsonObject row = sampleTarget(model, target, times, i, packDir, gson);
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                row.addProperty("elapsedMs", elapsedMs);
                rows.add(row);
                LOG.info("autosample [{}/{}] target={} cone={} actions={} pending={} elapsed={} ms",
                    i + 1, targets.size(), target.rawId, target.coneScore,
                    row.get("planActions").getAsInt(), row.get("pendingInputs").getAsInt(), elapsedMs);
            }

            JsonObject summary = new JsonObject();
            summary.addProperty("pack", model.pack);
            summary.addProperty("tier", tier);
            summary.addProperty("targetCount", targets.size());
            summary.addProperty("times", times);
            summary.add("targets", rows);
            Files.writeString(packDir.resolve("summary.json"), gson.toJson(summary));
            LOG.info("AUTOSAMPLE COMPLETE {} ({} targets)", packDir.toAbsolutePath(), targets.size());
        } catch (Exception exception) {
            LOG.error("autosample failed", exception);
        }
        if (!"0".equals(System.getProperty(PROPERTY + ".exit", "1"))) {
            MinecraftServer halt = server;
            if (halt != null) {
                halt.halt(false);
            } else {
                LOG.warn("autosample: no server reference, cannot halt");
            }
        }
    }

    private static void awaitDatabaseIndex(RecipeApi api) {
        long startNanos = System.nanoTime();
        try {
            List<RecipeSystem<?>> systems = new ArrayList<>();
            Map<Namespace, List<Namespace>> systemTags = new HashMap<>();
            for (RecipeSystem<?> system : api.allSystems()) {
                systems.add(system);
                ArrayList<Namespace> tags = new ArrayList<>();
                api.getSystemTags(system.systemId()).forEach(tags::add);
                if (!tags.isEmpty()) {
                    systemTags.put(system.systemId(), List.copyOf(tags));
                }
            }
            RecipeDatabaseIndex index = RecipeDatabaseCatalogCache.getOrBuild(
                systems,
                systemTags,
                RecipeDatabaseIndex.BuildProgress.none(),
                RecipeDatabaseFailureRegistry.shared()
            );
            LOG.info("autosample: database index ready after {}ms ({} records)",
                (System.nanoTime() - startNanos) / 1_000_000L, index.records().size());
        } catch (RuntimeException exception) {
            LOG.warn("autosample: database index await failed, routing anyway", exception);
        }
    }

    //endregion

    //region model + target selection

    private record Producer(RecipeEntry<TestDataPlugin.DummyRecipe> entry, int inputCount) {
    }

    private record Target(String rawId, RecipeEntry<TestDataPlugin.DummyRecipe> entry, long coneScore) {
    }

    private static final class SamplingModel {
        String pack;
        Map<String, String> reverseRaw = new HashMap<>(); // mimicName(path) -> raw id
        Map<String, List<Producer>> producers = new HashMap<>(); // raw item/fluid id -> producers
        Map<String, Producer> bestProducer = new HashMap<>(); // raw id -> producer with most inputs
    }

    private static SamplingModel buildModel(RecipeApi api) {
        SamplingModel model = new SamplingModel();
        MimicManifest manifest = MimicManifest.load();
        model.pack = manifest.pack() == null ? "unknown" : manifest.pack();
        for (String rawId : manifest.itemIds()) {
            String previous = model.reverseRaw.putIfAbsent(TestMod.mimicName(rawId), rawId);
            if (previous != null && !previous.equals(rawId)) {
                LOG.warn("autosample: mimic name collision {} -> {} / {}", TestMod.mimicName(rawId), previous, rawId);
            }
        }
        for (String rawId : manifest.fluidIds()) {
            model.reverseRaw.putIfAbsent(TestMod.mimicName(rawId), rawId);
        }
        for (RecipeSystem<?> system : api.allSystems()) {
            if (!TestMod.MOD_ID.equals(system.systemId().root())) {
                continue;
            }
            for (Object recipe : system.allRecipes()) {
                if (!(recipe instanceof TestDataPlugin.DummyRecipe dummy)) {
                    continue;
                }
                RecipeEntry<TestDataPlugin.DummyRecipe> entry = castEntry(system, dummy);
                Producer producer = new Producer(entry, dummy.inputs().size());
                for (OutputContent output : dummy.outputs()) {
                    String rawId = rawOutputId(model, output);
                    if (rawId != null) {
                        model.producers.computeIfAbsent(rawId, ignored -> new ArrayList<>()).add(producer);
                    }
                }
            }
        }
        for (Map.Entry<String, List<Producer>> entry : model.producers.entrySet()) {
            Producer best = null;
            for (Producer producer : entry.getValue()) {
                if (best == null || producer.inputCount() > best.inputCount()) {
                    best = producer;
                }
            }
            model.bestProducer.put(entry.getKey(), best);
        }
        return model;
    }

    @SuppressWarnings("unchecked")
    private static RecipeEntry<TestDataPlugin.DummyRecipe> castEntry(RecipeSystem<?> system, TestDataPlugin.DummyRecipe recipe) {
        return RecipeEntry.of((RecipeSystem<TestDataPlugin.DummyRecipe>) system, recipe);
    }

    /** Maps a mimic content id back to the raw pack id; {@code null} when unknown. */
    private static @Nullable String toRaw(SamplingModel model, @Nullable String contentId) {
        if (contentId == null) {
            return null;
        }
        int colon = contentId.indexOf(':');
        String path = colon >= 0 && TestMod.MOD_ID.equals(contentId.substring(0, colon))
            ? contentId.substring(colon + 1)
            : contentId;
        String raw = model.reverseRaw.get(path);
        if (raw != null) {
            return raw;
        }
        // best-effort tag reversal: mimic tags are sanitize(ns) + "__" + sanitize(path)
        int split = path.indexOf("__");
        if (split > 0 && split < path.length() - 2) {
            return path.substring(0, split) + ":" + path.substring(split + 2);
        }
        return contentId;
    }

    private static @Nullable String rawInputId(SamplingModel model, InputContent input) {
        if (input instanceof StackContent<?, ?> stack) {
            return toRaw(model, ClassifyViewFactory.contentId(stack.content()));
        }
        return null;
    }

    private static @Nullable String rawOutputId(SamplingModel model, OutputContent output) {
        if (output instanceof StackContent<?, ?> stack) {
            return toRaw(model, ClassifyViewFactory.resourceIdOf(output));
        }
        return null;
    }

    /**
     * Approximate demand-cone size per item: memoized DFS following the
     * best-producer chain, cycles contributing 0. Tree-sum (shared subtrees
     * re-counted) — a cheap monotone ranking key only; the top candidates are
     * re-measured exactly by {@link #distinctConeSize}.
     */
    private static long coneScore(SamplingModel model, String rawId) {
        return coneScore(model, rawId, new HashMap<>(), new HashSet<>(), 0);
    }

    private static long coneScore(SamplingModel model, String rawId, Map<String, Long> memo, Set<String> onStack, int depth) {
        if (depth > 240) {
            return 0;
        }
        Long cached = memo.get(rawId);
        if (cached != null) {
            return cached;
        }
        if (!onStack.add(rawId)) {
            return 0;
        }
        Producer producer = model.bestProducer.get(rawId);
        long score = 1;
        if (producer != null) {
            Set<String> seenInputs = new HashSet<>();
            for (InputContent input : producer.entry().recipe().inputs()) {
                String inputId = rawInputId(model, input);
                if (inputId == null) {
                    score += 2; // tag / list / value slot
                    continue;
                }
                if (seenInputs.add(inputId)) {
                    score += coneScore(model, inputId, memo, onStack, depth + 1);
                }
            }
        }
        onStack.remove(rawId);
        memo.put(rawId, score);
        return score;
    }

    /**
     * Exact complexity: distinct items (and recipes) reachable through the
     * best-producer chain from the target — deduplicated, so it measures cone
     * breadth without the tree-sum's exponential fan-out bias.
     */
    private static long distinctConeSize(SamplingModel model, String rawId) {
        Set<String> visitedItems = new HashSet<>();
        Set<RecipeEntry<?>> visitedRecipes = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(rawId);
        visitedItems.add(rawId);
        while (!queue.isEmpty() && visitedItems.size() < 60_000) {
            String current = queue.remove(queue.size() - 1);
            Producer producer = model.bestProducer.get(current);
            if (producer == null || !visitedRecipes.add(producer.entry())) {
                continue;
            }
            for (InputContent input : producer.entry().recipe().inputs()) {
                String inputId = rawInputId(model, input);
                if (inputId != null && visitedItems.add(inputId)) {
                    queue.add(inputId);
                }
            }
        }
        return (long) visitedItems.size() * 1000 + visitedRecipes.size();
    }

    private static List<Target> selectTargets(SamplingModel model, int count, int modCap, List<String> forced, String tier) {
        record Scored(String rawId, Producer producer, long score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<String, Producer> entry : model.bestProducer.entrySet()) {
            String rawId = entry.getKey();
            if (!rawId.contains(":") || TestMod.MOD_ID.equals(rawId.substring(0, rawId.indexOf(':')))) {
                continue; // showcase content, not pack mimics
            }
            scored.add(new Scored(rawId, entry.getValue(), coneScore(model, rawId)));
        }
        scored.sort(Comparator.<Scored>comparingLong(Scored::score).reversed());

        List<Target> targets = new ArrayList<>(count);
        Set<String> taken = new HashSet<>();
        Map<String, Integer> perMod = new HashMap<>();
        for (String forcedId : forced) {
            Producer producer = model.bestProducer.get(forcedId);
            if (producer == null) {
                LOG.warn("autosample: forced target {} has no producer, skipped", forcedId);
                continue;
            }
            taken.add(forcedId);
            perMod.merge(modOf(forcedId), 1, Integer::sum);
            targets.add(new Target(forcedId, producer.entry(), distinctConeSize(model, forcedId)));
        }

        if ("medium".equals(tier) || "plain".equals(tier)) {
            // percentile band of the cheap tree-sum ranking: the exact re-rank
            // only covers the top slice, so mid/low tiers rank by tree-sum and
            // pick evenly spaced targets across the band (plus a sequential
            // fill pass) — precision does not matter away from the top.
            int total = scored.size();
            double lo = "medium".equals(tier) ? 0.35 : 0.75;
            double hi = "medium".equals(tier) ? 0.65 : 0.95;
            int from = (int) Math.floor(total * lo);
            int to = Math.min(total, Math.max(from + count, (int) Math.ceil(total * hi)));
            List<Scored> band = scored.subList(from, to);
            LOG.info("autosample tier={} band=[{},{}) of {} items", tier, from, to, total);
            int stride = Math.max(1, band.size() / (count * 2));
            for (int pass = 0; pass < 2 && targets.size() < count; pass++) {
                for (int i = pass == 0 ? 0 : 1; i < band.size() && targets.size() < count; i += stride) {
                    Scored candidate = band.get(i);
                    if (!taken.add(candidate.rawId())) {
                        continue;
                    }
                    String mod = modOf(candidate.rawId());
                    if (perMod.getOrDefault(mod, 0) >= modCap) {
                        continue;
                    }
                    perMod.merge(mod, 1, Integer::sum);
                    targets.add(new Target(candidate.rawId(), candidate.producer().entry(), candidate.score()));
                }
            }
            return targets;
        }

        // complex (default): re-measure the top slice exactly — the tree-sum
        // above is only monotone
        int reRank = Math.min(scored.size(), Math.max(count * 4, 200));
        List<Scored> exact = new ArrayList<>(reRank);
        for (int i = 0; i < reRank; i++) {
            Scored candidate = scored.get(i);
            exact.add(new Scored(candidate.rawId(), candidate.producer(), distinctConeSize(model, candidate.rawId())));
        }
        exact.sort(Comparator.<Scored>comparingLong(Scored::score).reversed());

        for (Scored candidate : exact) {
            if (targets.size() >= count) {
                break;
            }
            if (!taken.add(candidate.rawId())) {
                continue;
            }
            String mod = modOf(candidate.rawId());
            if (perMod.getOrDefault(mod, 0) >= modCap) {
                continue;
            }
            perMod.merge(mod, 1, Integer::sum);
            targets.add(new Target(candidate.rawId(), candidate.producer().entry(), candidate.score()));
        }
        return targets;
    }

    private static String modOf(String rawId) {
        int colon = rawId.indexOf(':');
        return colon > 0 ? rawId.substring(0, colon) : "minecraft";
    }

    //endregion

    //region per-target sampling

    private static JsonObject sampleTarget(
        SamplingModel model, Target target, int times, int index, Path packDir, Gson gson
    ) throws IOException {
        JsonObject row = new JsonObject();
        row.addProperty("target", target.rawId);
        row.add("targetRecipe", describeRecipe(model, target.entry()));
        row.addProperty("times", times);
        row.addProperty("coneScore", target.coneScore);
        JsonObject error = null;
        CalculatorProcessGraphBuilder.BuildResult result = null;
        try {
            result = CalculatorProcessGraphBuilder.buildResult(
                target.entry(), times, dev.vfyjxf.cirrus.api.recipe.routing.Stock.empty(),
                Map.of(), CalculatorProcessGraphBuilder.RouteMode.calculator
            );
        } catch (RuntimeException exception) {
            error = new JsonObject();
            error.addProperty("exception", String.valueOf(exception));
            LOG.error("autosample: build failed for " + target.rawId, exception);
        }

        RecipeClassifier classifier = RecipeClassifier.createDefault();
        int execute = 0;
        int pending = 0;
        int consumeStock = 0;
        JsonArray actions = new JsonArray();
        Map<String, InputContent> demands = new LinkedHashMap<>();
        if (result != null && result.routingPlan() != null) {
            for (PlanAction action : result.routingPlan().actions()) {
                JsonObject actionJson = new JsonObject();
                switch (action) {
                    case PlanAction.ExecuteRecipe executeRecipe -> {
                        execute++;
                        actionJson.addProperty("kind", "execute");
                        actionJson.add("recipe", describeRecipe(model, executeRecipe.recipe()));
                        actionJson.addProperty("times", executeRecipe.times());
                        actionJson.add("tags", tagsOf(classifier, executeRecipe.recipe()));
                        actionJson.add("inputs", bindingsOf(model, executeRecipe.boundInputs().castToList()));
                        actionJson.add("outputs", outputsOf(model, executeRecipe.recipe()));
                        for (PlanAction.InputBinding binding : executeRecipe.boundInputs()) {
                            String key = ClassifyViewFactory.demandKey(binding.input());
                            if (key != null) {
                                demands.putIfAbsent(key, binding.input());
                            }
                        }
                    }
                    case PlanAction.PendingInput pendingInput -> {
                        pending++;
                        actionJson.addProperty("kind", "pending");
                        actionJson.addProperty("input", describeContent(model, pendingInput.input()));
                        actionJson.addProperty("amount", pendingInput.amount());
                        actionJson.addProperty("candidateCount", pendingInput.candidateCount());
                        actionJson.addProperty("attemptedCount", pendingInput.attemptedCount());
                        actionJson.addProperty("truncated", pendingInput.truncated());
                        actionJson.addProperty("reason", pendingInput.reason().name());
                    }
                    case PlanAction.ConsumeStock consume -> {
                        consumeStock++;
                        actionJson.addProperty("kind", "consumeStock");
                        actionJson.addProperty("input", describeContent(model, consume.input()));
                        actionJson.addProperty("amount", consume.amount());
                    }
                    case PlanAction.ReserveCatalyst catalyst -> {
                        actionJson.addProperty("kind", "reserveCatalyst");
                        actionJson.addProperty("input", describeContent(model, catalyst.input()));
                        actionJson.addProperty("amount", catalyst.amount());
                    }
                }
                actions.add(actionJson);
            }
        }
        row.addProperty("planActions", result == null || result.routingPlan() == null ? 0 : result.routingPlan().actions().size());
        row.addProperty("executeRecipe", execute);
        row.addProperty("pendingInputs", pending);
        row.addProperty("consumeStock", consumeStock);
        if (result != null) {
            var debug = result.debugInfo();
            row.addProperty("routingElapsedMs", debug.routingElapsedMs());
            row.addProperty("expandedNodes", debug.expandedNodes());
            row.addProperty("generatedCandidates", debug.generatedCandidates());
            row.addProperty("budgetExhausted", debug.budgetExhausted());
            row.addProperty("budgetStopReason", String.valueOf(debug.budgetStopReason()));
            row.addProperty("fallbackUsed", debug.fallbackUsed());
            row.addProperty("graphNodes", result.graph().nodeCount());
        }
        if (error != null) {
            row.add("buildError", error);
        }

        JsonObject root = new JsonObject();
        root.add("meta", row);
        root.add("actions", actions);
        root.add("alternatives", alternativesOf(model, target, demands));
        String fileName = String.format("%03d_%s.json", index, target.rawId.replaceAll("[^a-zA-Z0-9_.-]", "_"));
        Files.writeString(packDir.resolve("plans").resolve(fileName), gson.toJson(root));
        return row;
    }

    /** Producer alternatives the planner had for every distinct bound demand. */
    private static JsonObject alternativesOf(SamplingModel model, Target target, Map<String, InputContent> demands) {
        JsonObject out = new JsonObject();
        if (demands.isEmpty()) {
            return out;
        }
        RecipePathSettings.RoutingContext context;
        try {
            context = RecipePathSettings.calculatorRoutingContext(target.entry());
        } catch (RuntimeException exception) {
            LOG.warn("autosample: routing context failed for " + target.rawId, exception);
            return out;
        }
        for (Map.Entry<String, InputContent> demand : demands.entrySet()) {
            JsonArray producers = new JsonArray();
            int total;
            try {
                var refs = context.service().graph().producersFor(demand.getValue());
                total = refs.size();
                int emitted = 0;
                for (var ref : refs) {
                    if (emitted >= 24) {
                        break;
                    }
                    RecipeEntry<?> entry = ref.node(context.service().graph()).recipe();
                    if (entry.recipe() instanceof TestDataPlugin.DummyRecipe dummy) {
                        producers.add(summarizeRecipe(model, dummy));
                        emitted++;
                    }
                }
            } catch (RuntimeException exception) {
                total = -1;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("demand", describeContent(model, demand.getValue()));
            entry.addProperty("producerCount", total);
            entry.add("producers", producers);
            out.add(demand.getKey(), entry);
        }
        return out;
    }

    private static JsonArray tagsOf(RecipeClassifier classifier, RecipeEntry<?> entry) {
        JsonArray tags = new JsonArray();
        try {
            RecipeClassifyView view = ClassifyViewFactory.fromProjection(entry, entry.resolve());
            for (String tag : classifier.classifyLocal(view)) {
                tags.add(tag);
            }
        } catch (RuntimeException ignored) {
            // unresolved projection: tag-less is fine for the audit dump
        }
        return tags;
    }

    private static JsonObject summarizeRecipe(SamplingModel model, TestDataPlugin.DummyRecipe recipe) {
        JsonObject json = new JsonObject();
        json.addProperty("system", recipe.system());
        json.addProperty("id", recipe.id());
        json.addProperty("inputCount", recipe.inputs().size());
        json.addProperty("outputCount", recipe.outputs().size());
        JsonArray inputs = new JsonArray();
        int emitted = 0;
        for (InputContent input : recipe.inputs()) {
            if (emitted++ >= 10) {
                break;
            }
            inputs.add(describeContent(model, input));
        }
        JsonArray outputs = new JsonArray();
        emitted = 0;
        for (OutputContent output : recipe.outputs()) {
            if (emitted++ >= 10) {
                break;
            }
            outputs.add(describeOutput(model, output));
        }
        json.add("inputs", inputs);
        json.add("outputs", outputs);
        return json;
    }

    private static JsonObject describeRecipe(SamplingModel model, RecipeEntry<?> entry) {
        JsonObject json = new JsonObject();
        if (entry.recipe() instanceof TestDataPlugin.DummyRecipe dummy) {
            json.addProperty("system", dummy.system());
            json.addProperty("id", dummy.id());
            JsonObject summarized = summarizeRecipe(model, dummy);
            json.add("inputs", summarized.get("inputs"));
            json.add("outputs", summarized.get("outputs"));
            // purity (guaranteed multi-output recipes are loot-table-like)
            int guaranteed = 0;
            for (OutputContent output : dummy.outputs()) {
                if (output.chance() >= RecipeContent.MAX_CHANCE) {
                    guaranteed++;
                }
            }
            json.addProperty("guaranteedOutputs", guaranteed);
        } else {
            json.addProperty("system", String.valueOf(entry.systemId()));
            json.addProperty("id", String.valueOf(entry.id()));
        }
        return json;
    }

    private static JsonArray bindingsOf(SamplingModel model, Iterable<PlanAction.InputBinding> bindings) {
        JsonArray array = new JsonArray();
        for (PlanAction.InputBinding binding : bindings) {
            JsonObject json = new JsonObject();
            json.addProperty("input", describeContent(model, binding.input()));
            json.addProperty("amount", binding.amount());
            array.add(json);
        }
        return array;
    }

    private static JsonArray outputsOf(SamplingModel model, RecipeEntry<?> entry) {
        JsonArray array = new JsonArray();
        if (entry.recipe() instanceof TestDataPlugin.DummyRecipe dummy) {
            for (OutputContent output : dummy.outputs()) {
                array.add(describeOutput(model, output));
            }
        }
        return array;
    }

    private static String describeContent(SamplingModel model, InputContent input) {
        if (input instanceof StackContent<?, ?> stack && stack.content().notEmpty()) {
            String raw = rawInputId(model, input);
            return raw + " x" + (long) input.amount();
        }
        if (input instanceof dev.vfyjxf.cirrus.api.recipe.content.TagStackContent<?> tag) {
            return "#" + toRaw(model, tag.tagKey().location().toString()) + " x" + (long) input.amount();
        }
        return String.valueOf(input);
    }

    private static String describeOutput(SamplingModel model, OutputContent output) {
        String raw = rawOutputId(model, output);
        if (raw != null) {
            return raw + " x" + (long) output.amount()
                + (output.chance() < RecipeContent.MAX_CHANCE ? " @" + output.chance() : "");
        }
        return String.valueOf(output);
    }

    private static void writeSelection(Path packDir, List<Target> targets) throws IOException {
        JsonArray array = new JsonArray();
        for (Target target : targets) {
            JsonObject json = new JsonObject();
            json.addProperty("target", target.rawId);
            json.addProperty("system", target.entry().recipe().system());
            json.addProperty("recipe", target.entry().recipe().id());
            json.addProperty("coneScore", target.coneScore);
            array.add(json);
        }
        JsonObject root = new JsonObject();
        root.add("selection", array);
        Files.writeString(packDir.resolve("selection.json"), new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    //endregion
}
