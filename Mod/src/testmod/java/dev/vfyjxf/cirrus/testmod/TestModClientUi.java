package dev.vfyjxf.cirrus.testmod;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseCatalogCache;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseFailureRegistry;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
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
            return; // clean client: no autoui hooks at all
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> autoUiTick());
    }


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
