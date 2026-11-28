package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.planning.bridge.StorageInventorySnapshotAdapter;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.database.DatabaseExplorerWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesAndRulesWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.flow.ExecutionChecklistWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowStageWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.PreparationLedgerWidget;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellCoPilotHost;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellFacilitiesHost;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellFlowViewHost;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellSettingsHost;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellSpatialDagHost;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanVisualResolver;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.PromptSubjectResolver;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPrompt;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/**
 * Production wiring for the four top-level workspaces: panels only see narrow host contracts, and any wiring failure falls back to a placeholder widget instead of breaking the shell.
 */
public final class WorkbenchIntegrationProvider implements WorkspaceProvider {

    private static final String toastUnavailable = "cirrus.ui.workbench.toast.world_persist_unavailable";

    private final Supplier<Widget> databaseWorkspaceFactory;

    private @Nullable FlowStageWidget plannerStage;
    private @Nullable ShellSpatialDagHost plannerDagHost;
    private @Nullable ShellCoPilotHost copilotHost;
    private @Nullable WorkbenchShellContext copilotContext;

    public WorkbenchIntegrationProvider() {
        this(DatabasePage::new);
    }

    public WorkbenchIntegrationProvider(Supplier<Widget> databaseWorkspaceFactory) {
        this.databaseWorkspaceFactory = Objects.requireNonNull(databaseWorkspaceFactory, "databaseWorkspaceFactory");
    }

    @Override
    public Widget plannerWorkspace(WorkbenchShellContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        try {
            CompiledRecipeGraph graph = runtimeGraph();
            FlowStageWidget stage = FlowStageWidget.create();
            ShellCoPilotHost copilot = copilotHost(ctx);
            ShellSpatialDagHost dagHost = new ShellSpatialDagHost(
                ctx,
                WorkbenchIntegrationProvider::reducedMotion,
                (payload, targetStepId) -> WorkbenchSurfaces.routeDrop(ctx, copilot, payload, targetStepId),
                message -> ctx.toast(message)
            );
            ShellFlowViewHost flowHost = new ShellFlowViewHost(ctx, WorkbenchIntegrationProvider::reducedMotion)
                    .focusHandler(stepId -> {
                        stage.requestViewMode(FlowViewMode.spatialDag);
                        stage.dag().focusStep(stepId);
                    });
            plannerStage = stage;
            plannerDagHost = dagHost;
            stage.host(dagHost).resolver(defaultResolver()).graph(graph).themeMode(ctx.theme())
                    .inlineBeacons(inlineBeaconsOf(ctx.activeSnapshot(), graph))
                    .feedbackSink(feedback -> refine(ctx, feedback));
            injectView(
                ctx,
                stage,
                FlowViewMode.preparationLedger,
                () -> new PreparationLedgerWidget(flowHost)
                        .setInventorySource(WorkbenchIntegrationProvider::captureLivePlayerInventory)
            );
            injectView(
                ctx,
                stage,
                FlowViewMode.executionChecklist,
                () -> new ExecutionChecklistWidget(flowHost, (plan, closeOnSend) -> ctx.sendActivePlanToExecution())
                        .setGraph(graph)
            );
            return stage;
        } catch (RuntimeException | LinkageError failure) {
            return fallback(ctx);
        }
    }

    @Override
    public @Nullable Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        try {
            ShellFacilitiesHost host = ShellFacilitiesHost.forShell(message -> ctx.toast(message));
            host.sessionRecipeSource(() -> ShellFacilitiesHost.sessionRecipeKeysOf(ctx.activeSnapshot()));
            return new FacilitiesAndRulesWorkspace(host, ctx);
        } catch (RuntimeException | LinkageError failure) {
            return fallback(ctx);
        }
    }

    @Override
    public @Nullable Widget settingsWorkspace(WorkbenchShellContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        try {
            return new SettingsAndDiagnosticsWorkspace(ShellSettingsHost.forShell(message -> ctx.toast(message)), ctx);
        } catch (RuntimeException | LinkageError failure) {
            return fallback(ctx);
        }
    }

    @Override
    public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        try {
            Widget created = databaseWorkspaceFactory.get();
            if (created instanceof DatabasePage databasePage) {
                return new DatabaseExplorerWorkspace(ctx, databasePage);
            }
            WidgetGroup<Widget> container = new WidgetGroup<>();
            container.useStyle(sizeFull(), flexColumn(), minWidth(0), minHeight(0));
            container.addWidget(created);
            return container;
        } catch (RuntimeException | LinkageError failure) {
            return fallback(ctx);
        }
    }

    /**
     * One Co-Pilot host per shell context; the dock island and canvas drops share the same instance.
     */
    public ShellCoPilotHost copilotHost(WorkbenchShellContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if (copilotHost == null || copilotContext != ctx) {
            copilotContext = ctx;
            copilotHost = new ShellCoPilotHost(ctx, WorkbenchIntegrationProvider::reducedMotion);
        }
        return copilotHost;
    }

    /**
     * The planning stage from the latest wiring; {@code null} when wiring failed.
     */
    public @Nullable FlowStageWidget plannerStage() {
        return plannerStage;
    }

    /**
     * The canvas host from the latest wiring; {@code null} when wiring failed.
     */
    public @Nullable ShellSpatialDagHost plannerDagHost() {
        return plannerDagHost;
    }

    /* region wiring helpers */

    /**
     * Inline decision beacon map, {@code stepId -> promptId}, taken from each decision prompt's anchored step.
     */
    public static Map<String, String> inlineBeaconsOf(
        @Nullable WorkbenchUiSnapshot snapshot,
        @Nullable CompiledRecipeGraph graph
    ) {
        if (snapshot == null || graph == null) {
            return Map.of();
        }
        PlanningResult result = snapshot.result();
        if (result == null || result.decisionPrompts().isEmpty()) {
            return Map.of();
        }
        try {
            List<ResolvedPrompt> prompts = PromptSubjectResolver
                    .resolveAll(result.decisionPrompts(), snapshot.selectedPlan().orElse(null), graph);
            Map<String, String> beacons = new LinkedHashMap<>();
            for (ResolvedPrompt prompt : prompts) {
                String promptId = prompt.raw().promptId();
                for (String stepId : prompt.anchoredStepIds()) {
                    if (stepId != null && !stepId.isBlank()) {
                        beacons.putIfAbsent(stepId, promptId);
                    }
                }
            }
            return Map.copyOf(beacons);
        } catch (RuntimeException | LinkageError failure) {
            return Map.of();
        }
    }

    /**
     * Default visual resolution: content stacks resolve best-effort through the content bridge, names fall back to the resource id.
     */
    public static PlanVisualResolver defaultResolver() {
        return DefaultPlanVisualResolver.instance;
    }

    /**
     * Injects one flow view; a throwing factory leaves that mode an empty slot (the stage paints the empty_view hint) instead of failing the wiring.
     *
     * @return whether the view was injected
     */
    public static boolean injectView(
        WorkbenchShellContext ctx,
        FlowStageWidget stage,
        FlowViewMode mode,
        @Nullable Supplier<Widget> factory
    ) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(mode, "mode");
        try {
            Widget widget = factory == null ? null : factory.get();
            if (widget == null) {
                stage.setViewWidget(mode, null);
                return false;
            }
            stage.setViewWidget(mode, widget);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            try {
                stage.setViewWidget(mode, null);
            } catch (RuntimeException | LinkageError ignored) {
                // The empty-slot fallback is best-effort; the stage paints its own empty view.
            }
            ctx.toast(Component.translatable(toastUnavailable));
            return false;
        }
    }

    private static void refine(WorkbenchShellContext ctx, HumanFeedback feedback) {
        if (feedback == null) {
            return;
        }
        PlannerWorkbenchController controller = ctx.activeController();
        if (controller != null) {
            controller.refine(feedback, null);
        }
    }

    private static Widget fallback(WorkbenchShellContext ctx) {
        ctx.toast(Component.translatable(toastUnavailable));
        return new FallbackWorkspace(ctx);
    }

    private static @Nullable CompiledRecipeGraph runtimeGraph() {
        try {
            CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
            return runtime == null ? null : runtime.currentGraph().orElse(null);
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    private static boolean reducedMotion() {
        try {
            return ExecutionOverlayClientConfig.workbenchReducedMotion();
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static InventorySnapshot captureLivePlayerInventory() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null || minecraft.player == null
                    ? InventorySnapshot.empty
                    : StorageInventorySnapshotAdapter.capture(minecraft.player);
        } catch (RuntimeException | LinkageError failure) {
            return InventorySnapshot.empty;
        }
    }

    /* endregion */

    private enum DefaultPlanVisualResolver implements PlanVisualResolver {

        instance;

        @Override
        public @Nullable ContentStack<?, ?> stackOf(ComponentStackKey resource, long amount) {
            if (resource == null || amount <= 0L) {
                return null;
            }
            try {
                return resource.toStack(amount);
            } catch (RuntimeException | LinkageError failure) {
                return null;
            }
        }

        @Override
        public Component nameOf(ComponentStackKey resource) {
            if (resource == null) {
                return Component.empty();
            }
            Component fallback = Component.literal(resource.displayId());
            ContentStack<?, ?> stack = stackOf(resource, 1L);
            if (stack == null || stack.isEmpty()) {
                return fallback;
            }
            try {
                return CoPilotPaint.stackName(stack, fallback);
            } catch (RuntimeException | LinkageError failure) {
                return fallback;
            }
        }

        @Override
        public Component machineNameOf(MachineEntry machine) {
            if (machine == null || machine.isEmpty()) {
                return Component.empty();
            }
            try {
                return CoPilotPaint.machineName(machine);
            } catch (RuntimeException | LinkageError failure) {
                return Component.literal(machine.canonicalKey());
            }
        }
    }

    private static final class FallbackWorkspace extends WidgetGroup<Widget> {

        private final WorkbenchShellContext context;

        private FallbackWorkspace(WorkbenchShellContext context) {
            this.context = context;
            useStyle(sizeFull(), flexColumn(), minWidth(0), minHeight(0));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            int width = width();
            int height = height();
            if (width <= 0 || height <= 0) {
                return;
            }
            CirrusPalette palette = context.palette();
            canvas.fill(0, 0, width, height, palette.canvasVoid());
            WorkbenchPaint.dotGrid(canvas, width, height, 1f, palette);
            canvas.drawCenteredString(
                Component.translatable(toastUnavailable),
                width / 2,
                Math.max(4, height / 2 - 4),
                palette.textMuted()
            );
        }
    }
}
