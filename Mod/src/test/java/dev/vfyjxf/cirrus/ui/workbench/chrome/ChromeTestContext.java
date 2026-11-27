package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.SccTopology;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanningBackend;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanningRequestSource;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/**
 * chrome unit-test fixture: a recording shell facade, the real controller with a synchronous executor, and minimal graph and session doubles.
 */
final class ChromeTestContext implements WorkbenchShellContext {

    static final ResourceKey ironIngot = ResourceKey.item("minecraft:iron_ingot");
    static final ResourceKey coal = ResourceKey.item("minecraft:coal");
    static final ResourceKey ironOre = ResourceKey.item("minecraft:iron_ore");
    static final ResourceKey goldIngot = ResourceKey.item("minecraft:gold_ingot");

    record TargetCall(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {}

    final List<TargetCall> targetCalls = new ArrayList<>();
    final List<WorkbenchMode> modeCalls = new ArrayList<>();
    final List<WorkbenchThemeMode> themeCalls = new ArrayList<>();
    final List<Boolean> zenCalls = new ArrayList<>();
    final List<Float> leftWidthCalls = new ArrayList<>();
    final List<Float> rightWidthCalls = new ArrayList<>();
    final List<String> toasts = new ArrayList<>();
    final List<List<TargetDemand>> openTabCalls = new ArrayList<>();
    final List<Runnable> listeners = new ArrayList<>();
    final RecordingBackend backend;
    final FakeGraph graph = new FakeGraph();
    final SessionTabManager tabManager;

    int closeActiveTabCalls;
    int sendExecutionCalls;
    int requestReplanCalls;
    int openPaletteCalls;
    int closePaletteCalls;
    int shutdownCalls;
    int listenerAddCalls;
    int listenerRemoveCalls;

    private final float shellWidth;
    private final float shellHeight;
    private final WorkbenchBreakpoint breakpoint;
    private float leftDockWidth;
    private float rightDockWidth;
    private WorkbenchThemeMode theme = WorkbenchThemeMode.obsidianBlueprint;
    private WorkbenchMode mode = WorkbenchMode.planner;
    private boolean zen;
    private boolean paletteOpen;

    ChromeTestContext() {
        this(1280f, 720f, List.of());
    }

    ChromeTestContext(float shellWidth, float shellHeight, List<TargetDemand> initialTargets) {
        this.shellWidth = shellWidth;
        this.shellHeight = shellHeight;
        this.breakpoint = WorkbenchBreakpoint.classify(shellWidth);
        this.leftDockWidth = WorkbenchShellLayout.defaultLeftDockWidth(shellWidth);
        this.rightDockWidth = WorkbenchShellLayout.defaultRightDockWidth(shellWidth);
        this.backend = new RecordingBackend(ChromeTestContext::defaultResult);
        PlanningRequestSource source = requestSource();
        this.tabManager = new SessionTabManager(
            state -> new PlannerWorkbenchController(state, backend, source, Runnable::run)
        );
        this.tabManager.openOrFocusTab(initialTargets, null);
        this.tabManager.addListener(this::notifyListeners);
    }

    //region fixtures

    static TargetDemand demand(ResourceKey resource, long amount) {
        return new TargetDemand(resource, amount);
    }

    static PlanningResult defaultResult(PlanningRequest request) {
        TargetDemand first = request.targets().get(0);
        return result(PlanningStatus.feasible, List.of(planFor("plan-" + first.amount(), first)));
    }

    static ProductionPlan planFor(String planId, TargetDemand demand) {
        List<PlanStep> steps = List.of(
            new AcquireStep(
                "s001",
                List.of(),
                demand.resource(),
                demand.amount(),
                AcquisitionKind.manualGather,
                CostVector.zero
            )
        );
        return new ProductionPlan(
            planId,
            "cirrus.strategy.balanced",
            steps,
            PreparationSummary.empty,
            List.of(),
            new CostVector(10.0, 20.0, 1.0, 2.0, 3.0),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    static PlanningResult result(PlanningStatus status, List<ProductionPlan> plans) {
        return result(status, plans, List.of());
    }

    static PlanningResult result(PlanningStatus status, List<ProductionPlan> plans, List<DecisionPrompt> prompts) {
        return new PlanningResult(
            status,
            plans,
            prompts,
            new PlanningDiagnostics(7L, 3, 2, 0, 0, false, Optional.empty())
        );
    }

    static DecisionPrompt prompt(String promptId, DecisionPromptKind kind, double leverage) {
        return new DecisionPrompt(
            promptId,
            kind,
            "prompt.title",
            "prompt.explain",
            leverage,
            List.of(
                new DecisionOption("accept", "option.accept", CostVector.zero, List.of()),
                new DecisionOption("reject", "option.reject", CostVector.zero, List.of())
            )
        );
    }

    static PlanningRequestSource requestSource() {
        AcquisitionOracle oracle = (key, missing, progression) -> Optional.empty();
        PlanningContext context = new PlanningContext(
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            oracle,
            DefaultHumanGuidanceView.empty(),
            ProgressionState.overworldDefault
        );
        return spec -> new PlanningRequest(spec.targets(), context, spec.profile(), spec.budget());
    }

    //endregion

    //region drive

    PlannerWorkbenchController controller() {
        PlannerWorkbenchController active = activeController();
        if (active == null) {
            throw new IllegalStateException("test context has no active controller");
        }
        return active;
    }

    void publishGraph() {
        publishGraph(null);
    }

    void publishGraph(@Nullable Function<PlanningRequest, PlanningResult> factory) {
        backend.factory = factory == null ? ChromeTestContext::defaultResult : factory;
        PlannerWorkbenchController active = controller();
        active.onGraphVersionChanged(1L);
        if (!active.state().targets().isEmpty()) {
            active.replan();
        }
    }

    void openTabWith(long amount) {
        openTab(List.of(demand(ironIngot, amount)), null);
    }

    void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }

    //endregion

    //region facade

    @Override
    public SessionTabManager tabManager() {
        return tabManager;
    }

    @Override
    public @Nullable PlannerWorkbenchController activeController() {
        return tabManager.activeTab();
    }

    @Override
    public WorkbenchUiSnapshot activeSnapshot() {
        PlannerWorkbenchController active = activeController();
        return active == null ? WorkbenchUiSnapshot.empty() : active.snapshot();
    }

    @Override
    public @Nullable CompiledRecipeGraph activeGraph() {
        return graph;
    }

    @Override
    public WorkbenchBreakpoint breakpoint() {
        return breakpoint;
    }

    @Override
    public WorkbenchThemeMode theme() {
        return theme;
    }

    @Override
    public void setTheme(WorkbenchThemeMode theme) {
        WorkbenchThemeMode next = Objects.requireNonNull(theme);
        themeCalls.add(next);
        this.theme = next;
        notifyListeners();
    }

    @Override
    public CirrusPalette palette() {
        return CirrusPalette.of(theme);
    }

    @Override
    public WorkbenchMode mode() {
        return mode;
    }

    @Override
    public void setMode(WorkbenchMode mode) {
        WorkbenchMode next = Objects.requireNonNull(mode);
        if (this.mode == next) {
            return;
        }
        this.mode = next;
        modeCalls.add(next);
        notifyListeners();
    }

    @Override
    public boolean zenMode() {
        return zen;
    }

    @Override
    public void setZenMode(boolean zen) {
        if (this.zen == zen) {
            return;
        }
        this.zen = zen;
        zenCalls.add(zen);
        notifyListeners();
    }

    @Override
    public float leftDockWidth() {
        return leftDockWidth;
    }

    @Override
    public void setLeftDockWidth(float width) {
        leftWidthCalls.add(width);
        leftDockWidth = width;
    }

    @Override
    public float rightDockWidth() {
        return rightDockWidth;
    }

    @Override
    public void setRightDockWidth(float width) {
        rightWidthCalls.add(width);
        rightDockWidth = width;
    }

    @Override
    public float shellWidth() {
        return shellWidth;
    }

    @Override
    public float shellHeight() {
        return shellHeight;
    }

    @Override
    public float leftRailWidth() {
        return breakpoint.leftRailWidth();
    }

    @Override
    public float topChromeHeight() {
        return breakpoint.sessionTabBarHeight() + breakpoint.topDockHeight();
    }

    @Override
    public void toast(LangEntry message) {
        toasts.add(Objects.requireNonNull(message).key());
    }

    @Override
    public void toast(Component message) {
        toasts.add(toastToken(Objects.requireNonNull(message)));
    }

    // Translatable toasts are recorded by key so assertions hold whether or not a language table is loaded.
    private static String toastToken(Component message) {
        return message.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey()
                : message.getString();
    }

    @Override
    public void openCommandPalette() {
        openPaletteCalls++;
        paletteOpen = true;
    }

    @Override
    public void closeCommandPalette() {
        closePaletteCalls++;
        paletteOpen = false;
    }

    @Override
    public boolean commandPaletteOpen() {
        return paletteOpen;
    }

    @Override
    public void requestReplan() {
        requestReplanCalls++;
        PlannerWorkbenchController active = activeController();
        if (active != null) {
            active.replan();
        }
    }

    @Override
    public void setTargets(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {
        List<TargetDemand> copy = targets == null ? List.of() : List.copyOf(targets);
        targetCalls.add(new TargetCall(copy, primaryRecipe));
        PlannerWorkbenchController active = activeController();
        if (active != null) {
            active.setTargets(copy, primaryRecipe);
        }
        notifyListeners();
    }

    @Override
    public void openTab(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {
        List<TargetDemand> copy = targets == null ? List.of() : List.copyOf(targets);
        openTabCalls.add(copy);
        try {
            tabManager.openOrFocusTab(copy, primaryRecipe);
        } catch (IllegalStateException full) {
            toast(CirrusLangs.Workbench.Tab.maxTabsHint);
        }
        notifyListeners();
    }

    @Override
    public void closeActiveTab() {
        closeActiveTabCalls++;
        String activeTabId = tabManager.activeTabId();
        if (activeTabId != null) {
            tabManager.closeTab(activeTabId);
        }
        notifyListeners();
    }

    @Override
    public void sendActivePlanToExecution() {
        sendExecutionCalls++;
    }

    @Override
    public void addListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listenerAddCalls++;
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Runnable listener) {
        listenerRemoveCalls++;
        listeners.remove(listener);
    }

    @Override
    public void shutdown() {
        shutdownCalls++;
        tabManager.close();
    }

    //endregion

    //region doubles

    static final class RecordingBackend implements PlanningBackend {

        final List<PlanningRequest> requests = new ArrayList<>();
        final List<RecordingSession> sessions = new ArrayList<>();
        Function<PlanningRequest, PlanningResult> factory;

        RecordingBackend(Function<PlanningRequest, PlanningResult> factory) {
            this.factory = factory;
        }

        @Override
        public PlanningSession openSession(PlanningRequest request) {
            requests.add(request);
            RecordingSession session = new RecordingSession(factory.apply(request));
            sessions.add(session);
            return session;
        }

        RecordingSession lastSession() {
            return sessions.get(sessions.size() - 1);
        }

        int refineCalls() {
            int total = 0;
            for (RecordingSession session : sessions) {
                total += session.refineCount;
            }
            return total;
        }

        int undoCallCount() {
            int total = 0;
            for (RecordingSession session : sessions) {
                total += session.undoCount;
            }
            return total;
        }

        int redoCallCount() {
            int total = 0;
            for (RecordingSession session : sessions) {
                total += session.redoCount;
            }
            return total;
        }
    }

    static final class RecordingSession implements PlanningSession {

        final PlanningResult initial;
        int refineCount;
        int undoCount;
        int redoCount;
        int closeCount;
        private PlanningResult current;

        RecordingSession(PlanningResult initial) {
            this.initial = initial;
            this.current = initial;
        }

        @Override
        public PlanningResult currentResult() {
            return current;
        }

        @Override
        public PlanningResult refine(HumanFeedback feedback) {
            refineCount++;
            return current;
        }

        @Override
        public PlanningResult undo() {
            undoCount++;
            return current;
        }

        @Override
        public PlanningResult redo() {
            redoCount++;
            return current;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    static final class RecordingSink implements ChromeSink {

        final List<String> focusedSteps = new ArrayList<>();
        final List<String> focusedPrompts = new ArrayList<>();
        final List<ChromeShortcut.Command> canvasCommands = new ArrayList<>();
        int exportJsonCalls;

        @Override
        public void focusPlanStep(String stepId) {
            focusedSteps.add(stepId);
        }

        @Override
        public void focusCopilotPrompt(String promptId) {
            focusedPrompts.add(promptId);
        }

        @Override
        public void canvasCommand(ChromeShortcut.Command command) {
            canvasCommands.add(command);
        }

        @Override
        public void exportJson() {
            exportJsonCalls++;
        }
    }

    static final class FakeGraph implements CompiledRecipeGraph {

        private final Set<ResourceKey> producible = new TreeSet<>();
        private final List<CompiledRecipe> recipes = new ArrayList<>();

        FakeGraph add(ResourceKey... resources) {
            producible.addAll(List.of(resources));
            return this;
        }

        FakeGraph addRecipe(CompiledRecipe recipe) {
            recipes.add(recipe);
            for (OutputSlot out : recipe.outputs()) {
                producible.add(out.resource());
            }
            return this;
        }

        @Override
        public long catalogVersion() {
            return 1L;
        }

        @Override
        public SystemAdmission admissionOf(String systemId) {
            return SystemAdmission.enabled;
        }

        @Override
        public List<CompiledRecipe> producersOf(ResourceKey output) {
            List<CompiledRecipe> out = new ArrayList<>();
            for (CompiledRecipe recipe : recipes) {
                for (OutputSlot slot : recipe.outputs()) {
                    if (output.equals(slot.resource())) {
                        out.add(recipe);
                        break;
                    }
                }
            }
            return List.copyOf(out);
        }

        @Override
        public List<CompiledRecipe> consumersOf(ResourceKey input) {
            return List.of();
        }

        @Override
        public List<CompiledCycleMacro> cycleMacrosProducing(ResourceKey output) {
            return List.of();
        }

        @Override
        public Optional<CompiledRecipe> recipeByCompactId(int compactId) {
            return Optional.empty();
        }

        @Override
        public Optional<CompiledRecipe> recipeByKey(CompiledRecipeKey key) {
            return Optional.empty();
        }

        @Override
        public Set<ResourceKey> allProducibleResources() {
            return Set.copyOf(producible);
        }

        @Override
        public SccTopology sccTopology() {
            return new SccTopology() {
                @Override
                public int sccIdOf(ResourceKey resource) {
                    return 0;
                }

                @Override
                public int sccSize(int sccId) {
                    return 0;
                }

                @Override
                public boolean isInNonTrivialScc(ResourceKey resource) {
                    return false;
                }

                @Override
                public Set<ResourceKey> membersOfScc(int sccId) {
                    return Set.of();
                }

                @Override
                public int largestSccSize() {
                    return 0;
                }
            };
        }

        @Override
        public GraphCompilationStats stats() {
            return new GraphCompilationStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    //endregion

    //region mount

    static Mounted mount(Widget... widgets) {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeFull());
        for (Widget widget : widgets) {
            root.addWidget(widget);
        }
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(host(1280, 720)));
        scene.setLayoutArea(1280f, 720f);
        return new Mounted(scene, root);
    }

    static SceneHost host(int width, int height) {
        return new SceneHost() {
            @Override
            // Headless host: no Minecraft Font exists in unit tests, and the paths under test never read it.
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }
        };
    }

    static final class Mounted implements AutoCloseable {

        final Scene scene;
        final WidgetGroup<Widget> root;

        Mounted(Scene scene, WidgetGroup<Widget> root) {
            this.scene = scene;
            this.root = root;
        }

        Mounted layout() {
            scene.layout();
            scene.stabilize();
            return this;
        }

        @Override
        public void close() {
            scene.destroy();
        }
    }

    //endregion
}
