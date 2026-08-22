package dev.vfyjxf.cirrus.testmod;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseCatalogCache;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseFailureRegistry;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssue;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import dev.vfyjxf.cirrus.ui.process.ProcessEdge;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-only benchmark hooks for {@link TestMod}. Kept in a separate class so
 * the server dist (game tests) never resolves client-only types — TestMod
 * references this class only behind a {@code Dist.CLIENT} guard.
 */
@OnlyIn(Dist.CLIENT)
final class TestModClientUi {

    private static final Logger LOG = LoggerFactory.getLogger("CirrusTestMod");
    private static final String AUTOUI_PROPERTY = "cirrus.test.autoui";

    private TestModClientUi() {
    }

    static void register() {
        if (!TestMod.testEnabled()) {
            return; // clean client: no autoui/autocalc/dump hooks at all
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> autoUiTick());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> dumpPlanTick());
    }

    //region plan dump hook

    /**
     * Plan-analysis hook: with {@code -Dcirrus.test.dumpplan=<raw item id>}
     * (e.g. {@code allthetweaks:atm_star}) the client, once in-world, runs the
     * exact calculator build for the mimic target on a background thread and
     * writes a full path dump (summary metrics, budget shaping, plan action
     * breakdown, recipe tree, per-input branch landscape, aggregated sources)
     * to {@code runs/client/atms_plan_dump.txt}. No screen is opened.
     */
    private static final String DUMPPLAN_PROPERTY = "cirrus.test.dumpplan";
    private static int dumpPlanPhase = -1; // -1 probing, 0 waiting, 9 started/done
    private static int dumpPlanTicks;

    private static void dumpPlanTick() {
        if (dumpPlanPhase == -1) {
            String target = System.getProperty(DUMPPLAN_PROPERTY);
            dumpPlanPhase = target == null || target.isBlank() ? 9 : 0;
            return;
        }
        if (dumpPlanPhase != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ++dumpPlanTicks < 40) {
            return;
        }
        RecipeApi api;
        try {
            api = RecipeApi.instance();
        } catch (RuntimeException notReady) {
            return; // retried on the next tick
        }
        String target = System.getProperty(DUMPPLAN_PROPERTY);
        RecipeEntry<?> entry = findMimicEntry(api, target);
        if (entry == null) {
            LOG.error("dumpplan: no mimic recipe outputs {}", target);
            dumpPlanPhase = 9;
            return;
        }
        dumpPlanPhase = 9;
        LOG.info("dumpplan target={}; running calculator build off-thread", entry.id());
        Thread worker = new Thread(() -> {
            awaitDatabaseIndex(api);
            runPlanDump(entry);
        }, "cirrus-plan-dump");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Blocks until the client-lifecycle database index prewarm has finished.
     * {@code getOrBuild} holds the catalog cache monitor for the whole build, so
     * asking for the index here simply parks until the in-flight build publishes
     * its entry and then hits the cache. Without this the plan dump routes while
     * the index build saturates the CPU, and the wall-clock-bounded waves produce
     * a far thinner plan — timings swing by an order of magnitude between runs.
     */
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
            RecipeDatabaseCatalogCache.getOrBuild(
                systems,
                systemTags,
                RecipeDatabaseIndex.BuildProgress.none(),
                RecipeDatabaseFailureRegistry.shared()
            );
            LOG.info("dumpplan: database index ready after {}ms", (System.nanoTime() - startNanos) / 1_000_000L);
        } catch (RuntimeException exception) {
            LOG.warn("dumpplan: database index await failed, routing anyway", exception);
        }
    }

    private static void runPlanDump(RecipeEntry<?> entry) {
        try {
            StringBuilder out = new StringBuilder(1 << 20);
            long buildStart = System.nanoTime();
            CalculatorProcessGraphBuilder.BuildResult result = CalculatorProcessGraphBuilder.buildResult(
                entry,
                1,
                Stock.empty(),
                Map.of(),
                CalculatorProcessGraphBuilder.RouteMode.calculator
            );
            long buildMs = (System.nanoTime() - buildStart) / 1_000_000L;

            // Second build of the same target: the routing graph, topology, cone
            // stats and result cache are all warm now, which is the state a player
            // is in when they open the calculator during normal play. The cold
            // number above is dominated by one-time graph/topology work, so only
            // this one is comparable against the responsiveness target.
            long warmStart = System.nanoTime();
            CalculatorProcessGraphBuilder.BuildResult warmResult = CalculatorProcessGraphBuilder.buildResult(
                entry,
                1,
                Stock.empty(),
                Map.of(),
                CalculatorProcessGraphBuilder.RouteMode.calculator
            );
            long warmMs = (System.nanoTime() - warmStart) / 1_000_000L;
            out.append("== build ==\n")
                .append("target=").append(entry.id()).append('\n')
                .append("wallMs=").append(buildMs).append('\n')
                .append("warmWallMs=").append(warmMs).append('\n')
                .append("warmActions=").append(warmResult.routingPlan() == null
                    ? 0
                    : warmResult.routingPlan().actions().size()).append('\n')
                .append(result.debugInfo().toDebugText()).append("\n\n== buildTrace ==\n");
            for (String line : result.debugTrace()) {
                out.append(line).append('\n');
            }

            // Direct re-route with the identical request shaping, purely to
            // surface the routing metrics that BuildResult drops (exploration
            // counters). Deterministic — same seed, same budget formula.
            RecipePathSettings.RoutingContext context = RecipePathSettings.calculatorRoutingContext(entry);
            RoutingService service = context.service();
            service.registerRuleDefinition(context.rule());
            RoutingBudget budget = CalculatorProcessGraphBuilder.routingBudget(
                entry,
                1,
                context.catalog(),
                service,
                context.plannerMode(),
                CalculatorProcessGraphBuilder.RouteMode.calculator
            );
            RoutingOutcome outcome = service.route(
                RoutingRequest.builder()
                    .plannerMode(context.plannerMode())
                    .targetRecipe(entry, 1)
                    .ruleProfileId(context.rule().profile().profileId())
                    .sourcePolicyId(context.rule().sourcePolicy().policyId())
                    .budget(budget)
                    .maxProducerCandidates(CalculatorProcessGraphBuilder.maxProducerCandidates(budget, context.plannerMode()))
                    .stock(Stock.empty())
                    .inventorySensitivityProfile(context.inventorySensitivity())
                    .loopPolicy(context.loopPolicy())
                    .preferredVariants(Map.of())
                    .build()
            );
            appendMetrics(out, budget, outcome.metrics());
            appendIssues(out, outcome);
            RoutingPlan plan = outcome.plan() != null ? outcome.plan() : result.routingPlan();
            appendPlanActions(out, plan);
            appendBranchLandscape(out, plan, service);
            appendRecipeTree(out, result.graph());
            appendSources(out, result.graph());
            out.append("\n== graphStats ==\n").append(service.graph().stats().summaryLine()).append('\n')
                .append(service.graph().stats().detailLine()).append('\n');

            Path dumpPath = Path.of(System.getProperty("user.dir"), "atms_plan_dump.txt");
            Files.writeString(dumpPath, out.toString());
            LOG.info("dumpplan written to {} ({} chars)", dumpPath.toAbsolutePath(), out.length());
        } catch (Exception exception) {
            LOG.error("dumpplan failed", exception);
        }
    }

    private static void appendMetrics(StringBuilder out, RoutingBudget budget, RoutingMetrics metrics) {
        out.append("\n== budget ==\n")
            .append("maxExpandedNodes=").append(budget.maxExpandedNodes()).append('\n')
            .append("maxGeneratedCandidates=").append(budget.maxGeneratedCandidates()).append('\n')
            .append("maxWallTimeMs=").append(budget.maxWallTimeMs()).append('\n')
            .append("maxMemoryStates=").append(budget.maxMemoryStates()).append('\n')
            .append("maxRootInputExpandedNodes=").append(budget.maxRootInputExpandedNodes()).append('\n')
            .append("maxRootInputGeneratedCandidates=").append(budget.maxRootInputGeneratedCandidates()).append('\n')
            .append("explorationBudgetShare=").append(budget.explorationBudgetShare()).append('\n')
            .append("loopBudget=").append(budget.loopBudget()).append('\n');
        out.append("\n== metrics ==\n")
            .append("elapsedMs=").append(metrics.elapsedMs()).append('\n')
            .append("expandedNodes=").append(metrics.expandedNodes()).append('\n')
            .append("generatedCandidates=").append(metrics.generatedCandidates()).append('\n')
            .append("scannedRefs=").append(metrics.scannedRefs()).append('\n')
            .append("acceptedCandidates=").append(metrics.acceptedCandidates()).append('\n')
            .append("budgetUsed=").append(metrics.budgetUsed()).append('\n')
            .append("budgetExhausted=").append(metrics.budgetExhausted()).append('\n')
            .append("budgetStopReason=").append(metrics.budgetStopReason()).append('\n')
            .append("budgetCulpritRecipe=").append(metrics.budgetCulpritRecipe()).append('\n')
            .append("budgetCulpritInput=").append(metrics.budgetCulpritInput()).append('\n')
            .append("budgetCulpritDepth=").append(metrics.budgetCulpritDepth()).append('\n')
            .append("budgetCulpritRemaining=").append(metrics.budgetCulpritRemaining()).append('\n')
            .append("explorationBranchesExpanded=").append(metrics.explorationBranchesExpanded()).append('\n')
            .append("explorationBudgetUsed=").append(metrics.explorationBudgetUsed()).append('\n');
    }

    private static void appendIssues(StringBuilder out, RoutingOutcome outcome) {
        out.append("\n== issues ==\n");
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (RoutingIssue issue : outcome.issues()) {
            counts.merge(issue.messageKey(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            out.append(entry.getKey()).append(" x").append(entry.getValue()).append('\n');
        }
        int shown = 0;
        for (RoutingIssue issue : outcome.issues()) {
            if (shown++ >= 40) {
                out.append("... (truncated)\n");
                break;
            }
            out.append("- ").append(issue.messageKey())
                .append(" blocking=").append(issue.blocking())
                .append(" args=").append(issue.messageArgs())
                .append('\n');
        }
    }

    private static void appendPlanActions(StringBuilder out, @Nullable RoutingPlan plan) {
        out.append("\n== planActions ==\n");
        if (plan == null) {
            out.append("<no plan>\n");
            return;
        }
        int execute = 0;
        int consumeStock = 0;
        int pending = 0;
        int catalyst = 0;
        for (PlanAction action : plan.actions()) {
            switch (action) {
                case PlanAction.ExecuteRecipe ignored -> execute++;
                case PlanAction.ConsumeStock ignored -> consumeStock++;
                case PlanAction.PendingInput ignored -> pending++;
                case PlanAction.ReserveCatalyst ignored -> catalyst++;
            }
        }
        out.append("executeRecipe=").append(execute)
            .append(" consumeStock=").append(consumeStock)
            .append(" pendingInput=").append(pending)
            .append(" reserveCatalyst=").append(catalyst)
            .append('\n');
        out.append("\n== pendingInputs (unresolved demands) ==\n");
        for (PlanAction action : plan.actions()) {
            if (action instanceof PlanAction.PendingInput pendingInput) {
                out.append("- ").append(describeInputContent(pendingInput.input()))
                    .append(" amount=").append(pendingInput.amount())
                    .append('\n');
            }
        }
    }

    /**
     * For every bound input of every executed recipe, counts how many producer
     * recipes the graph offers for that input (the branch count the planner
     * chose from) and lists the top choices plus the producer the plan
     * actually picked.
     */
    private static void appendBranchLandscape(StringBuilder out, @Nullable RoutingPlan plan, RoutingService service) {
        out.append("\n== branchLandscape ==\n");
        if (plan == null) {
            out.append("<no plan>\n");
            return;
        }
        // resource id -> producing recipe id, from the plan's expected outputs
        HashMap<String, String> chosenProducerByResource = new HashMap<>();
        for (PlanAction action : plan.actions()) {
            if (action instanceof PlanAction.ExecuteRecipe execute) {
                String recipeId = String.valueOf(execute.recipe().id());
                for (PlanAction.OutputBinding output : execute.expectedOutputs()) {
                    chosenProducerByResource.putIfAbsent(resourceKeyOf(output.resource()), recipeId);
                }
            }
        }
        int[] buckets = new int[5]; // 1, 2-3, 4-8, 9-32, 33+
        ArrayList<String> hotSpots = new ArrayList<>();
        for (PlanAction action : plan.actions()) {
            if (!(action instanceof PlanAction.ExecuteRecipe execute)) {
                continue;
            }
            String parentId = String.valueOf(execute.recipe().id());
            for (PlanAction.InputBinding binding : execute.boundInputs()) {
                int producerCount = service.graph().producersFor(binding.input()).size();
                buckets[producerCount <= 1 ? 0 : producerCount <= 3 ? 1 : producerCount <= 8 ? 2 : producerCount <= 32 ? 3 : 4]++;
                if (producerCount > 32) {
                    String inputDescription = describeInputContent(binding.input());
                    hotSpots.add(
                        "input=" + inputDescription
                            + " x" + binding.amount()
                            + " of parent=" + parentId
                            + " producers=" + producerCount
                            + " chosen=" + chosenProducerByResource.getOrDefault(resourceKeyOf(binding.input()), "<unmatched>")
                    );
                }
            }
        }
        out.append("producerCount distribution over bound inputs: 1=")
            .append(buckets[0]).append(" 2-3=").append(buckets[1])
            .append(" 4-8=").append(buckets[2]).append(" 9-32=").append(buckets[3])
            .append(" 33+=").append(buckets[4]).append('\n');
        out.append("\n== branchHotspots (inputs with 33+ producers) ==\n");
        for (String hotSpot : hotSpots) {
            out.append("- ").append(hotSpot).append('\n');
        }
    }

    private static void appendRecipeTree(StringBuilder out, ProcessGraph graph) {
        out.append("\n== recipeTree ==\n");
        Map<String, List<ProcessEdge>> incoming = new HashMap<>();
        for (ProcessEdge edge : graph.edges()) {
            incoming.computeIfAbsent(edge.toNodeId(), ignored -> new ArrayList<>()).add(edge);
        }
        Set<String> visited = new LinkedHashSet<>();
        List<ProcessNode> sinks = graph.sinks();
        for (ProcessNode sink : sinks) {
            appendTreeNode(out, graph, incoming, sink, 0, visited);
        }
    }

    private static void appendTreeNode(
        StringBuilder out,
        ProcessGraph graph,
        Map<String, List<ProcessEdge>> incoming,
        ProcessNode node,
        int depth,
        Set<String> visited
    ) {
        if (depth > 64) {
            out.append("  ".repeat(64)).append("... (depth cap)\n");
            return;
        }
        String indent = "  ".repeat(depth);
        RecipeEntry<?> recipe = node.recipe();
        String label = recipe != null && recipe.id() != null ? recipe.id().toString() : "SOURCE[" + node.name() + "]";
        if (!visited.add(node.id())) {
            out.append(indent).append(label).append(" (shared ref)\n");
            return;
        }
        out.append(indent).append(label).append('\n');
        List<ProcessEdge> inputs = incoming.get(node.id());
        if (inputs == null) {
            return;
        }
        for (ProcessEdge edge : inputs) {
            ProcessNode child = graph.node(edge.fromNodeId());
            out.append(indent).append("  <- ").append(edge.materialName())
                .append(" x").append(edge.amount());
            if (child == null) {
                out.append(" <missing node>\n");
                continue;
            }
            out.append('\n');
            appendTreeNode(out, graph, incoming, child, depth + 1, visited);
        }
    }

    private static void appendSources(StringBuilder out, ProcessGraph graph) {
        out.append("\n== sources (aggregated raw inputs) ==\n");
        LinkedHashMap<String, Long> totals = new LinkedHashMap<>();
        for (ProcessNode source : graph.sources()) {
            for (ProcessNode.MaterialSlot slot : source.outputs()) {
                totals.merge(slot.materialName(), (long) slot.amount(), Long::sum);
            }
        }
        totals.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> out.append(entry.getKey()).append(" x").append(entry.getValue()).append('\n'));
    }

    private static String describeInputContent(InputContent input) {
        if (input instanceof StackContent<?, ?> stackContent && stackContent.content().notEmpty()) {
            ResourceLocation location = stackLocation(stackContent.content());
            if (location != null) {
                return location + " x" + stackContent.content().amount();
            }
        }
        return input.toString();
    }

    private static String resourceKeyOf(InputContent input) {
        if (input instanceof StackContent<?, ?> stackContent && stackContent.content().notEmpty()) {
            ResourceLocation location = stackLocation(stackContent.content());
            if (location != null) {
                return location.toString();
            }
        }
        return input.toString();
    }

    private static String resourceKeyOf(dev.vfyjxf.cirrus.api.content.value.ContentResource resource) {
        if (resource instanceof ContentStack<?, ?> stack && stack.notEmpty()) {
            ResourceLocation location = stackLocation(stack);
            if (location != null) {
                return location.toString();
            }
        }
        return resource.toString();
    }

    //endregion

    /**
     * UI-path benchmark hook: with {@code -Dcirrus.test.autoui=<raw item id>}
     * (e.g. {@code allthetweaks:atm_star}) the client, once in-world, sets the
     * calculator target and opens the real {@link CalculatorScreen}, then
     * switches to the Database tab, back to the calculator tab, and finally
     * closes and reopens the screen — the exact code path a player drives with
     * the keybind and tab clicks. Every step logs its render-thread elapsed
     * time.
     */
    private static String autoUiTarget;
    private static int autoUiPhase = -1; // -1 probing, 0..3 active phases, 9 disabled/done
    private static int autoUiTicks;
    private static @Nullable CalculatorScreen autoUiScreen;

    private static void autoUiTick() {
        if (autoUiPhase == -1) {
            String target = System.getProperty(AUTOUI_PROPERTY);
            if (target == null || target.isBlank()) {
                autoUiPhase = 9;
                return;
            }
            autoUiTarget = target;
            autoUiPhase = 0;
        }
        if (autoUiPhase == 9) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        autoUiTicks++;
        try {
            switch (autoUiPhase) {
                case 0 -> {
                    if (mc.screen != null || autoUiTicks < 40) {
                        return;
                    }
                    RecipeApi api;
                    try {
                        api = RecipeApi.instance();
                    } catch (RuntimeException notReady) {
                        return; // retried on the next tick
                    }
                    RecipeEntry<?> entry = findMimicEntry(api, autoUiTarget);
                    if (entry == null) {
                        LOG.error("autoui: no mimic recipe outputs {}", autoUiTarget);
                        autoUiPhase = 9;
                        return;
                    }
                    LOG.info("autoui target={} times=1; opening calculator screen", entry.id());
                    CalculatorTargetRecipeState.set(entry, 1);
                    long start = System.nanoTime();
                    autoUiScreen = new CalculatorScreen();
                    mc.setScreen(autoUiScreen);
                    LOG.info("autoui phase=open elapsed={} ms", (System.nanoTime() - start) / 1_000_000L);
                    autoUiTicks = 0;
                    autoUiPhase = 5;
                }
                case 5 -> {
                    Object view = calculatorViewReflect(autoUiScreen);
                    if (view == null) {
                        if (autoUiTicks > 200) {
                            LOG.error("autoui: calculator view unavailable");
                            autoUiPhase = 9;
                        }
                        return;
                    }
                    try {
                        boolean pending = (boolean) declaredMethod(view, "debugCalculationPending").invoke(view);
                        boolean calculated = (boolean) declaredMethod(view, "isCalculated").invoke(view);
                        if (pending || !calculated) {
                            if (autoUiTicks > 2400) {
                                LOG.error("autoui: calculator never finished; pending={} calculated={}", pending, calculated);
                                autoUiPhase = 9;
                            }
                            return;
                        }
                    } catch (Exception exception) {
                        LOG.error("autoui: calculator state check failed", exception);
                        autoUiPhase = 9;
                        return;
                    }
                    LOG.info("autoui calculator ready waitedTicks={}; dumping graph", autoUiTicks);
                    dumpCalculatorGraph(view);
                    autoUiTicks = 0;
                    autoUiPhase = 1;
                }
                case 1 -> {
                    if (autoUiTicks < 60) {
                        return;
                    }
                    long start = System.nanoTime();
                    selectTabReflect(autoUiScreen, "databaseTab");
                    LOG.info("autoui phase=databaseTab elapsed={} ms", (System.nanoTime() - start) / 1_000_000L);
                    autoUiTicks = 0;
                    autoUiPhase = 2;
                }
                case 2 -> {
                    Object page = databasePageReflect(autoUiScreen);
                    if (page == null) {
                        if (autoUiTicks > 200) {
                            LOG.error("autoui: database page unavailable");
                            autoUiPhase = 9;
                        }
                        return;
                    }
                    int records = debugIntReflect(page, "debugSystemCatalogRecordCount");
                    if (records <= 0) {
                        if (autoUiTicks > 2400) {
                            LOG.error("autoui: system catalog index never became ready");
                            autoUiPhase = 9;
                        }
                        return; // wait for the background index build
                    }
                    LOG.info("autoui dbindex ready records={} waitedTicks={}", records, autoUiTicks);
                    runDatabaseBenchmarks(page);
                    autoUiTicks = 0;
                    autoUiPhase = 3;
                }
                case 3 -> {
                    if (autoUiTicks < 60) {
                        return;
                    }
                    long start = System.nanoTime();
                    selectTabReflect(autoUiScreen, "calculatorTab");
                    LOG.info("autoui phase=calculatorTab elapsed={} ms", (System.nanoTime() - start) / 1_000_000L);
                    autoUiTicks = 0;
                    autoUiPhase = 4;
                }
                case 4 -> {
                    if (autoUiTicks < 100) {
                        return;
                    }
                    long start = System.nanoTime();
                    mc.setScreen(null);
                    autoUiScreen = new CalculatorScreen();
                    mc.setScreen(autoUiScreen);
                    LOG.info("autoui phase=reopen elapsed={} ms", (System.nanoTime() - start) / 1_000_000L);
                    autoUiPhase = 9;
                }
                default -> autoUiPhase = 9;
            }
        } catch (Exception exception) {
            LOG.error("autoui failed at phase " + autoUiPhase, exception);
            autoUiPhase = 9;
        }
    }

    /**
     * Drives the exact search/toggle code paths a player hits: set query text,
     * wait for the async build, apply on the render thread, then collapse and
     * re-expand the first result category. Each step is timed separately so
     * JProfiler recordings can be matched against concrete numbers.
     */
    private static void runDatabaseBenchmarks(Object page) throws Exception {
        Class<?> pageClass = page.getClass();
        var pendingField = pageClass.getDeclaredField("pendingQueryBuild");
        pendingField.setAccessible(true);
        var setQuery = pageClass.getMethod("debugSetQuery", String.class);
        var complete = pageClass.getMethod("debugCompletePendingQuery");
        var toggle = pageClass.getMethod("debugToggleResultCategory", int.class);
        var resultCount = pageClass.getMethod("debugResultCardCount");

        for (String query : new String[] { "ingot", "iron", "iron ingot", "iron ", "@mod:minecraft", "", "ingot", "iron", "iron ingot", "" }) {
            long t0 = System.nanoTime();
            setQuery.invoke(page, query);
            long t1 = System.nanoTime();
            Object future = pendingField.get(page);
            if (future instanceof java.util.concurrent.CompletableFuture<?> completable) {
                completable.join();
            }
            long t2 = System.nanoTime();
            complete.invoke(page);
            long t3 = System.nanoTime();
            LOG.info(
                "autoui dbsearch query='{}' schedule={} ms compute={} ms apply={} ms cards={}",
                query,
                (t1 - t0) / 1_000_000L,
                (t2 - t1) / 1_000_000L,
                (t3 - t2) / 1_000_000L,
                resultCount.invoke(page)
            );
        }

        for (int iteration = 0; iteration < 3; iteration++) {
            long start = System.nanoTime();
            toggle.invoke(page, 0);
            LOG.info("autoui dbtoggle iteration={} elapsed={} ms", iteration, (System.nanoTime() - start) / 1_000_000L);
        }

        long t0 = System.nanoTime();
        setQuery.invoke(page, "");
        Object future = pendingField.get(page);
        if (future instanceof java.util.concurrent.CompletableFuture<?> completable) {
            completable.join();
        }
        complete.invoke(page);
        LOG.info("autoui dbrestore elapsed={} ms", (System.nanoTime() - t0) / 1_000_000L);
    }

    private static @Nullable Object databasePageReflect(@Nullable CalculatorScreen screen) {
        if (screen == null) {
            return null;
        }
        try {
            var tabsField = CalculatorScreen.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);
            Object tabs = tabsField.get(screen);
            var tabField = tabs.getClass().getDeclaredField("databaseTab");
            tabField.setAccessible(true);
            Object tab = tabField.get(tabs);
            var contentField = tab.getClass().getDeclaredField("cachedContent");
            contentField.setAccessible(true);
            return contentField.get(tab);
        } catch (Exception exception) {
            return null;
        }
    }

    private static int debugIntReflect(Object page, String methodName) {
        try {
            Object value = page.getClass().getMethod(methodName).invoke(page);
            return value instanceof Integer integer ? integer : -1;
        } catch (Exception exception) {
            return -1;
        }
    }

    private static @Nullable RecipeEntry<?> findMimicEntry(RecipeApi api, String targetRawId) {
        String mimicPath = TestMod.mimicName(targetRawId);
        RecipeSystem<?> targetSystem = null;
        Object targetRecipe = null;
        int targetInputs = -1;
        for (RecipeSystem<?> system : api.allSystems()) {
            if (!TestMod.MOD_ID.equals(system.systemId().root())) {
                continue;
            }
            for (Object recipe : system.allRecipes()) {
                if (recipe instanceof TestDataPlugin.DummyRecipe dummy
                    && TestMod.outputsMimic(dummy, mimicPath)
                    && dummy.inputs().size() > targetInputs) {
                    targetInputs = dummy.inputs().size();
                    targetSystem = system;
                    targetRecipe = recipe;
                }
            }
        }
        if (targetSystem == null) {
            return null;
        }
        return entryOf(targetSystem, targetRecipe);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static RecipeEntry<?> entryOf(RecipeSystem system, Object recipe) {
        return RecipeEntry.of(system, recipe);
    }

    private static java.lang.reflect.Method declaredMethod(Object target, String name) throws NoSuchMethodException {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static @Nullable Object calculatorViewReflect(@Nullable CalculatorScreen screen) {
        if (screen == null) {
            return null;
        }
        try {
            var tabsField = CalculatorScreen.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);
            Object tabs = tabsField.get(screen);
            var tabField = tabs.getClass().getDeclaredField("calculatorTab");
            tabField.setAccessible(true);
            Object tab = tabField.get(tabs);
            var contentField = tab.getClass().getDeclaredField("cachedContent");
            contentField.setAccessible(true);
            return contentField.get(tab);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Serializes the calculator's {@link ProcessGraph} (nodes, edges, and, for
     * every intermediate material, all alternative producer recipes found in
     * the recipe api) to {@code runs/client/atms_graph_dump.json} for offline
     * path analysis. Runs the api scan off the render thread.
     */
    private static void dumpCalculatorGraph(Object view) {
        ProcessGraph graph;
        try {
            var graphField = view.getClass().getDeclaredField("graph");
            graphField.setAccessible(true);
            Object value = graphField.get(view);
            if (!(value instanceof ProcessGraph cast)) {
                LOG.error("autoui: graph field is {}", value == null ? "null" : value.getClass());
                return;
            }
            graph = cast;
        } catch (Exception exception) {
            LOG.error("autoui: graph reflection failed", exception);
            return;
        }
        LOG.info("autoui dump graph nodes={} edges={}", graph.nodeCount(), graph.edgeCount());
        new Thread(() -> {
            try {
                Path dumpPath = Path.of(System.getProperty("user.dir"), "atms_graph_dump.json");
                Files.writeString(dumpPath, buildGraphJson(graph));
                LOG.info("autoui dump written to {}", dumpPath.toAbsolutePath());
            } catch (Exception exception) {
                LOG.error("autoui dump failed", exception);
            }
        }, "cirrus-autoui-dump").start();
    }

    private static String buildGraphJson(ProcessGraph graph) {
        Set<String> interested = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder(1 << 20);
        sb.append("{\"nodes\":[");
        boolean first = true;
        for (ProcessNode node : graph.nodes()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"id\":").append(json(node.id()))
                .append(",\"name\":").append(json(node.name()))
                .append(",\"machine\":").append(json(node.machine()));
            RecipeEntry<?> recipe = node.recipe();
            sb.append(",\"recipe\":").append(recipe == null || recipe.id() == null ? "null" : json(recipe.id().toString()));
            sb.append(",\"inputs\":[");
            boolean slotFirst = true;
            for (ProcessNode.MaterialSlot slot : node.inputs()) {
                if (!slotFirst) {
                    sb.append(',');
                }
                slotFirst = false;
                appendSlot(sb, slot, interested);
            }
            sb.append("],\"outputs\":[");
            slotFirst = true;
            for (ProcessNode.MaterialSlot slot : node.outputs()) {
                if (!slotFirst) {
                    sb.append(',');
                }
                slotFirst = false;
                appendSlot(sb, slot, interested);
            }
            sb.append("]}");
        }
        sb.append("],\"edges\":[");
        first = true;
        for (ProcessEdge edge : graph.edges()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"from\":").append(json(edge.fromNodeId()))
                .append(",\"to\":").append(json(edge.toNodeId()))
                .append(",\"material\":").append(json(edge.materialName()))
                .append(",\"amount\":").append(edge.amount())
                .append('}');
        }
        sb.append("],\"producers\":{");
        LinkedHashMap<String, List<String>> producers = collectProducers(interested);
        first = true;
        for (Map.Entry<String, List<String>> entry : producers.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(json(entry.getKey())).append(":[");
            List<String> alternatives = entry.getValue();
            for (int index = 0; index < alternatives.size(); index++) {
                if (index > 0) {
                    sb.append(',');
                }
                sb.append(json(alternatives.get(index)));
            }
            sb.append(']');
        }
        sb.append("}}\n");
        return sb.toString();
    }

    private static void appendSlot(StringBuilder sb, ProcessNode.MaterialSlot slot, Set<String> interested) {
        sb.append("{\"material\":").append(json(slot.materialName()))
            .append(",\"amount\":").append(slot.amount());
        RecipeContent content = slot.content();
        String itemId = null;
        if (content instanceof StackContent<?, ?> stackContent && stackContent.content().notEmpty()) {
            ResourceLocation location = stackLocation(stackContent.content());
            if (location != null) {
                itemId = location.toString();
                interested.add(itemId);
            }
        }
        sb.append(",\"item\":").append(itemId == null ? "null" : json(itemId)).append('}');
    }

    private static <T, S> @Nullable ResourceLocation stackLocation(ContentStack<T, S> stack) {
        return stack.contentType().getLocation(stack.content());
    }

    private static LinkedHashMap<String, List<String>> collectProducers(Set<String> interested) {
        LinkedHashMap<String, List<String>> producers = new LinkedHashMap<>();
        if (interested.isEmpty()) {
            return producers;
        }
        RecipeApi api;
        try {
            api = RecipeApi.instance();
        } catch (RuntimeException notReady) {
            return producers;
        }
        for (RecipeSystem<?> system : api.allSystems()) {
            for (Object recipe : (Iterable<?>) castIterable(system.allRecipes())) {
                if (!(recipe instanceof TestDataPlugin.DummyRecipe dummy)) {
                    continue;
                }
                RecipeEntry<?> entry = entryOf(system, recipe);
                for (OutputContent output : dummy.outputs()) {
                    if (!(output instanceof StackContent<?, ?> stackContent) || stackContent.content().isEmpty()) {
                        continue;
                    }
                    ResourceLocation location = stackLocation(stackContent.content());
                    if (location == null || !interested.contains(location.toString())) {
                        continue;
                    }
                    producers.computeIfAbsent(location.toString(), ignored -> new ArrayList<>())
                        .add(system.systemId() + ":" + (entry.id() == null ? entry.toString() : entry.id().getPath()));
                }
            }
        }
        return producers;
    }

    @SuppressWarnings("unchecked")
    private static Iterable<?> castIterable(Object iterable) {
        return (Iterable<?>) iterable;
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        sb.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static void selectTabReflect(CalculatorScreen screen, String tabFieldName) throws Exception {
        var tabsField = CalculatorScreen.class.getDeclaredField("tabs");
        tabsField.setAccessible(true);
        Object tabs = tabsField.get(screen);
        var tabField = tabs.getClass().getDeclaredField(tabFieldName);
        tabField.setAccessible(true);
        Object tab = tabField.get(tabs);
        for (var method : tabs.getClass().getDeclaredMethods()) {
            if (method.getName().equals("selectTab") && method.getParameterCount() == 1) {
                method.setAccessible(true);
                method.invoke(tabs, tab);
                return;
            }
        }
        throw new NoSuchMethodException("selectTab");
    }

}
