package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanningBackend;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanningRequestSource;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabState;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Headless host adapter fixture: the real planning controller, a recording planning backend and a recording shell context.
 */
final class HostsTestFixtures {

    static final ComponentStackKey ironOre = OfflineContentTypes.item("minecraft:iron_ore");
    static final ComponentStackKey ironIngot = OfflineContentTypes.item("minecraft:iron_ingot");
    static final String tabId = "tab-hosts";
    static final String planId = "plan-alpha";

    private HostsTestFixtures() {}

    record Fixture(PlannerWorkbenchController controller, RecordingBackend backend) {

        FakeShellContext context() {
            return new FakeShellContext(controller);
        }
    }

    static Fixture fixture() {
        RecordingBackend backend = new RecordingBackend();
        SessionTabState state = new SessionTabState(tabId);
        state.setRuntimeGraphVersion(1L);
        state.setTargets(List.of(new TargetDemand(ironIngot, 8L)), null);
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state,
            backend,
            requestSource(),
            Runnable::run
        );
        controller.replan();
        return new Fixture(controller, backend);
    }

    static CompiledRecipeKey recipeKey(String systemId, String recipeId) {
        return new CompiledRecipeKey(Namespace.parse(systemId), OfflineContentTypes.tryParseLocation(recipeId), 0L);
    }

    static CostVector cost() {
        return new CostVector(1.0, 1.0, 0.0, 0.0, 0.0);
    }

    static ProductionPlan plan(String planId) {
        PlanStep step = new CraftStep(
            "s001",
            List.of(),
            recipeKey("minecraft:crafting", "minecraft:iron_ingot"),
            1L,
            Optional.of(OfflineContentTypes.machine("minecraft:crafting_table")),
            false,
            Map.of(0, new SlotBinding(ironOre, List.of())),
            Map.of(),
            cost()
        );
        return new ProductionPlan(
            planId,
            "cirrus.strategy.balanced",
            List.of(step),
            PreparationSummary.empty,
            List.of(),
            cost(),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    static PlanningResult result(ProductionPlan plan) {
        return new PlanningResult(
            PlanningStatus.feasible,
            List.of(plan),
            List.of(),
            new PlanningDiagnostics(1L, 0, 0, 0, 0, false, Optional.empty())
        );
    }

    static WorkbenchUiSnapshot snapshotWithPlan(ProductionPlan plan) {
        return new WorkbenchUiSnapshot(
            tabId,
            "Hosts",
            WorkbenchPhase.readyPareto,
            false,
            1L,
            List.of(new TargetDemand(ironIngot, 8L)),
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive(),
            true,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            result(plan),
            null,
            plan.planId(),
            null,
            Set.of(),
            null,
            null,
            FlowViewMode.spatialDag,
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            null
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

    static final class RecordingBackend implements PlanningBackend {

        private final List<RecordingSession> sessions = new ArrayList<>();

        @Override
        public PlanningSession openSession(PlanningRequest request) {
            RecordingSession session = new RecordingSession(result(plan(planId)));
            sessions.add(session);
            return session;
        }

        List<RecordingSession> sessions() {
            return List.copyOf(sessions);
        }

        RecordingSession lastSession() {
            if (sessions.isEmpty()) {
                throw new AssertionError("no planning session was opened");
            }
            return sessions.get(sessions.size() - 1);
        }
    }

    static final class RecordingSession implements PlanningSession {

        private final List<HumanFeedback> refined = new ArrayList<>();
        private final PlanningResult initial;
        private PlanningResult current;
        private int undoCount;
        private int redoCount;
        private int closeCount;

        private RecordingSession(PlanningResult initial) {
            this.initial = initial;
            this.current = initial;
        }

        List<HumanFeedback> refined() {
            return List.copyOf(refined);
        }

        int undoCount() {
            return undoCount;
        }

        int redoCount() {
            return redoCount;
        }

        int closeCount() {
            return closeCount;
        }

        @Override
        public PlanningResult currentResult() {
            return current;
        }

        @Override
        public PlanningResult refine(HumanFeedback feedback) {
            refined.add(feedback);
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

    static final class FakeShellContext implements WorkbenchShellContext {

        private static final CirrusPalette palette = CirrusPalette.obsidianBlueprint;

        private final @Nullable PlannerWorkbenchController controller;
        private final SessionTabManager tabManager;
        private final List<LangEntry> langToasts = new ArrayList<>();
        private final List<Component> componentToasts = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();

        private WorkbenchThemeMode theme = WorkbenchThemeMode.obsidianBlueprint;
        private WorkbenchMode mode = WorkbenchMode.planner;
        private boolean zen;
        private float leftDockWidth = 176f;
        private float rightDockWidth = 210f;
        private final List<Runnable> listeners = new ArrayList<>();

        FakeShellContext(@Nullable PlannerWorkbenchController controller) {
            this.controller = controller;
            this.tabManager = new SessionTabManager(state -> {
                if (controller == null) {
                    throw new IllegalStateException("no controller in this fixture");
                }
                return controller;
            });
        }

        List<LangEntry> langToasts() {
            return List.copyOf(langToasts);
        }

        List<Component> componentToasts() {
            return List.copyOf(componentToasts);
        }

        List<String> calls() {
            return List.copyOf(calls);
        }

        @Override
        public SessionTabManager tabManager() {
            return tabManager;
        }

        @Override
        public @Nullable PlannerWorkbenchController activeController() {
            return controller;
        }

        @Override
        public WorkbenchUiSnapshot activeSnapshot() {
            return controller == null ? WorkbenchUiSnapshot.empty() : controller.snapshot();
        }

        @Override
        public dev.vfyjxf.cirrus.api.planning.graph.@Nullable CompiledRecipeGraph activeGraph() {
            return null;
        }

        @Override
        public WorkbenchBreakpoint breakpoint() {
            return WorkbenchBreakpoint.classify(1280f);
        }

        @Override
        public WorkbenchThemeMode theme() {
            return theme;
        }

        @Override
        public void setTheme(WorkbenchThemeMode theme) {
            this.theme = theme;
            calls.add("setTheme:" + theme);
        }

        @Override
        public CirrusPalette palette() {
            return palette;
        }

        @Override
        public WorkbenchMode mode() {
            return mode;
        }

        @Override
        public void setMode(WorkbenchMode mode) {
            this.mode = mode;
            calls.add("setMode:" + mode);
        }

        @Override
        public boolean zenMode() {
            return zen;
        }

        @Override
        public void setZenMode(boolean zen) {
            this.zen = zen;
            calls.add("setZenMode:" + zen);
        }

        @Override
        public float leftDockWidth() {
            return leftDockWidth;
        }

        @Override
        public void setLeftDockWidth(float width) {
            this.leftDockWidth = width;
            calls.add("setLeftDockWidth:" + width);
        }

        @Override
        public float rightDockWidth() {
            return rightDockWidth;
        }

        @Override
        public void setRightDockWidth(float width) {
            this.rightDockWidth = width;
            calls.add("setRightDockWidth:" + width);
        }

        @Override
        public float shellWidth() {
            return 1280f;
        }

        @Override
        public float shellHeight() {
            return 720f;
        }

        @Override
        public float leftRailWidth() {
            return 24f;
        }

        @Override
        public float topChromeHeight() {
            return 48f;
        }

        @Override
        public void toast(LangEntry message) {
            langToasts.add(message);
        }

        @Override
        public void toast(Component message) {
            componentToasts.add(message);
        }

        @Override
        public void openCommandPalette() {
            calls.add("openCommandPalette");
        }

        @Override
        public void closeCommandPalette() {
            calls.add("closeCommandPalette");
        }

        @Override
        public boolean commandPaletteOpen() {
            return false;
        }

        @Override
        public void requestReplan() {
            calls.add("requestReplan");
        }

        @Override
        public void setTargets(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {
            calls.add("setTargets:" + targets.size());
        }

        @Override
        public void openTab(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {
            calls.add("openTab:" + targets.size());
        }

        @Override
        public void closeActiveTab() {
            calls.add("closeActiveTab");
        }

        @Override
        public void sendActivePlanToExecution() {
            calls.add("sendActivePlanToExecution");
        }

        @Override
        public void addListener(Runnable listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(Runnable listener) {
            listeners.remove(listener);
        }

        @Override
        public void shutdown() {
            calls.add("shutdown");
        }
    }

    static FloatPos offset(double x, double y) {
        return new FloatPos(x, y);
    }
}
