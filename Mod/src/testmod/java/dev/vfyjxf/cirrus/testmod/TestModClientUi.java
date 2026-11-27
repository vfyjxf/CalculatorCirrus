package dev.vfyjxf.cirrus.testmod;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.testmod.testutil.Reflect;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Client-only benchmark hooks for {@link TestMod}. Kept in a separate class so
 * the server dist (game tests) never resolves client-only types — TestMod
 * references this class only behind a {@code Dist.CLIENT} guard.
 */
@OnlyIn(Dist.CLIENT)
final class TestModClientUi {

    private static final Logger log = LoggerFactory.getLogger("CirrusTestMod");
    private static final String autouiProperty = "cirrus.test.autoui";

    private TestModClientUi() {}

    static void register() {
        if (!TestMod.testEnabled()) {
            return; // clean client: no autoui hooks at all
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> autoUiTick());
    }

    /**
     * UI-path benchmark hook: with {@code -Dcirrus.test.autoui=<raw item id>}
     * (e.g. {@code allthetweaks:atm_star}) the client, once in-world, sets the
     * calculator target and opens the real {@link CalculatorScreen}, waits for
     * the first planning snapshot, switches to the database workspace and back,
     * and finally closes and reopens the screen — the exact code path a player
     * drives with the keybind and mode rail. Every step logs its render-thread
     * elapsed time.
     */
    private static String autoUiTarget;
    private static int autoUiPhase = -1; // -1 probing, 0..5 active phases, 9 disabled/done
    private static int autoUiTicks;
    private static @Nullable CalculatorScreen autoUiScreen;

    private static void autoUiTick() {
        if (autoUiPhase == -1) {
            String target = System.getProperty(autouiProperty);
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
                        log.error("autoui: no mimic recipe outputs {}", autoUiTarget);
                        autoUiPhase = 9;
                        return;
                    }
                    log.info("autoui target={} times=1; opening calculator screen", entry.id());
                    CalculatorTargetRecipeState.set(entry, 1);
                    long start = System.nanoTime();
                    autoUiScreen = new CalculatorScreen();
                    mc.setScreen(autoUiScreen);
                    log.info("autoui phase=open elapsed={} ms", (System.nanoTime() - start) / 1_000_000L);
                    autoUiTicks = 0;
                    autoUiPhase = 5;
                }
                case 5 -> {
                    if (autoUiScreen == null) {
                        autoUiPhase = 9;
                        return;
                    }
                    WorkbenchUiSnapshot snapshot = autoUiScreen.shell().activeSnapshot();
                    boolean planning = snapshot.planningInFlight() || snapshot.phase() == WorkbenchPhase.planning;
                    boolean settled = snapshot.phase() != WorkbenchPhase.noGraph && !planning;
                    if (!settled) {
                        if (autoUiTicks > 2400) {
                            log.warn(
                                "autoui: planning snapshot never settled phase={} pending={}",
                                snapshot.phase(),
                                snapshot.planningInFlight()
                            );
                            autoUiTicks = 0;
                            autoUiPhase = 1;
                        }
                        return;
                    }
                    log.info("autoui planner ready phase={} waitedTicks={}", snapshot.phase(), autoUiTicks);
                    autoUiTicks = 0;
                    autoUiPhase = 1;
                }
                case 1 -> {
                    if (autoUiTicks < 60) {
                        return;
                    }
                    long start = System.nanoTime();
                    autoUiScreen.shell().setMode(WorkbenchMode.database);
                    log.info("autoui phase=databaseWorkspace elapsed={} ms", (System.nanoTime() - start) / 1_000_000L);
                    autoUiTicks = 0;
                    autoUiPhase = 2;
                }
                case 2 -> {
                    Object page = activeWorkspace(autoUiScreen);
                    if (page == null) {
                        if (autoUiTicks > 200) {
                            log.error("autoui: database page unavailable");
                            autoUiPhase = 9;
                        }
                        return;
                    }
                    int records = debugIntReflect(page, "debugSystemCatalogRecordCount");
                    if (records <= 0) {
                        if (autoUiTicks > 2400) {
                            log.error("autoui: system catalog index never became ready");
                            autoUiPhase = 9;
                        }
                        return; // wait for the background index build
                    }
                    log.info("autoui dbindex ready records={} waitedTicks={}", records, autoUiTicks);
                    runDatabaseBenchmarks(page);
                    autoUiTicks = 0;
                    autoUiPhase = 3;
                }
                case 3 -> {
                    if (autoUiTicks < 60) {
                        return;
                    }
                    long start = System.nanoTime();
                    autoUiScreen.shell().setMode(WorkbenchMode.planner);
                    log.info("autoui phase=plannerWorkspace elapsed={} ms", (System.nanoTime() - start) / 1_000_000L);
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
                    log.info("autoui phase=reopen elapsed={} ms", (System.nanoTime() - start) / 1_000_000L);
                    autoUiPhase = 9;
                }
                default -> autoUiPhase = 9;
            }
        } catch (Exception exception) {
            log.error("autoui failed at phase " + autoUiPhase, exception);
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
        var setQuery = pageClass.getMethod("debugSetQuery", String.class);
        var complete = pageClass.getMethod("debugCompletePendingQuery");
        var toggle = pageClass.getMethod("debugToggleResultCategory", int.class);
        var resultCount = pageClass.getMethod("debugResultCardCount");

        for (String query : new String[]{"ingot", "iron", "iron ingot", "iron ", "@mod:minecraft", "", "ingot", "iron",
                "iron ingot", ""}) {
            long t0 = System.nanoTime();
            setQuery.invoke(page, query);
            long t1 = System.nanoTime();
            Object future = Reflect.field(page, "pendingQueryBuild", Object.class);
            if (future instanceof CompletableFuture<?> completable) {
                completable.join();
            }
            long t2 = System.nanoTime();
            complete.invoke(page);
            long t3 = System.nanoTime();
            log.info(
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
            log.info("autoui dbtoggle iteration={} elapsed={} ms", iteration, (System.nanoTime() - start) / 1_000_000L);
        }

        long t0 = System.nanoTime();
        setQuery.invoke(page, "");
        Object future = Reflect.field(page, "pendingQueryBuild", Object.class);
        if (future instanceof CompletableFuture<?> completable) {
            completable.join();
        }
        complete.invoke(page);
        log.info("autoui dbrestore elapsed={} ms", (System.nanoTime() - t0) / 1_000_000L);
    }

    private static Object activeWorkspace(@Nullable CalculatorScreen screen) {
        if (screen == null) {
            return null;
        }
        try {
            return Reflect.field(screen.shell(), "activeWorkspace", Object.class);
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
            if (!TestMod.modId.equals(system.systemId().root())) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RecipeEntry<?> entryOf(RecipeSystem system, Object recipe) {
        return RecipeEntry.of(system, recipe);
    }
}
