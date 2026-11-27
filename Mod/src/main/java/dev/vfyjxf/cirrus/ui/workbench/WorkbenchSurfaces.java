package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeSink;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotDnd;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotHost;
import dev.vfyjxf.cirrus.ui.workbench.copilot.LeftPlanDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.PromptCardWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.RightCoPilotDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowStageWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellCoPilotHost;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellSpatialDagHost;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabState;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Shell wiring point: dock islands, Co-Pilot hover preview, canvas drag routing and the chrome channel slot.
 */
public final class WorkbenchSurfaces {

    private final WorkbenchShellWidget shell;
    private final WorkbenchIntegrationProvider provider;
    private final ShellCoPilotHost copilot;
    private final PromptCardWidget.PreviewListener previewListener;
    private final LeftPlanDockWidget leftDock;
    private final RightCoPilotDockWidget rightDock;

    private volatile ChromeSink chromeSink = ChromeSink.empty;

    private WorkbenchSurfaces(WorkbenchShellWidget shell, WorkbenchIntegrationProvider provider) {
        this.shell = Objects.requireNonNull(shell, "shell");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.copilot = provider.copilotHost(shell);
        this.previewListener = previewListener(provider::plannerStage);
        this.leftDock = new LeftPlanDockWidget(copilot).onCollapse(() -> shell.setLeftDockCollapsed(true));
        this.rightDock = new RightCoPilotDockWidget(copilot, previewListener)
                .onCollapse(() -> shell.setRightDockCollapsed(true));
        this.rightDock.inspector().onOpenRecipeAssistant(shell::openRecipeAssistant);
        this.shell.recipeAssistant().previewListener(previewListener);
        installDocks();
    }

    public static WorkbenchSurfaces install(WorkbenchShellWidget shell, WorkbenchIntegrationProvider provider) {
        return new WorkbenchSurfaces(shell, provider);
    }

    public LeftPlanDockWidget leftDock() {
        return leftDock;
    }

    public RightCoPilotDockWidget rightDock() {
        return rightDock;
    }

    public ShellCoPilotHost copilotHost() {
        return copilot;
    }

    public PromptCardWidget.PreviewListener previewListener() {
        return previewListener;
    }

    /**
     * Switches to the right Co-Pilot tab and pins one decision prompt to the top.
     */
    public void focusPrompt(@Nullable String promptId) {
        rightDock.focusPrompt(promptId);
    }

    /**
     * W7C injects the global chrome channel once the shell is wired; {@code null} restores the empty implementation.
     */
    public void acceptChromeSink(@Nullable ChromeSink sink) {
        this.chromeSink = sink == null ? ChromeSink.empty : sink;
    }

    public ChromeSink chromeSink() {
        return chromeSink;
    }

    public WorkbenchIntegrationProvider provider() {
        return provider;
    }

    public WorkbenchShellWidget shell() {
        return shell;
    }

    /**
     * Translates a Co-Pilot hover preview into a canvas outline preview; {@code null} clears the preview.
     */
    public static void applyPreview(
        @Nullable FlowStageWidget stage,
        PromptCardWidget.@Nullable PreviewSubject subject
    ) {
        if (stage == null) {
            return;
        }
        if (subject == null || subject.stepIds().isEmpty()) {
            stage.clearPreview();
            return;
        }
        SpatialDagCanvasWidget.PreviewKind kind = previewKind(subject.kind());
        if (kind == null) {
            stage.clearPreview();
            return;
        }
        stage.preview(subject.stepIds(), kind);
    }

    public static SpatialDagCanvasWidget.@Nullable PreviewKind previewKind(
        PromptCardWidget.@Nullable PreviewKind kind
    ) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case banRecipe -> SpatialDagCanvasWidget.PreviewKind.banRecipe;
            case ownMachine -> SpatialDagCanvasWidget.PreviewKind.ownMachine;
            case buildMachine -> SpatialDagCanvasWidget.PreviewKind.buildMachine;
            case pickVariant -> SpatialDagCanvasWidget.PreviewKind.pickVariant;
            case treatFree -> SpatialDagCanvasWidget.PreviewKind.treatFree;
        };
    }

    public static PromptCardWidget.PreviewListener previewListener(Supplier<FlowStageWidget> stage) {
        Objects.requireNonNull(stage, "stage");
        return subject -> applyPreview(stage.get(), subject);
    }

    /**
     * Canvas drop entry: the hit step type becomes a {@link CoPilotDnd.DropTarget} and goes to the single drop router.
     */
    public static CoPilotDnd.DropOutcome routeDrop(
        WorkbenchShellContext ctx,
        CoPilotHost copilot,
        @Nullable DragPayload payload,
        @Nullable String targetStepId
    ) {
        if (payload == null) {
            return CoPilotDnd.DropOutcome.rejected();
        }
        CoPilotDnd.DropTarget target = dropTargetOf(copilot, targetStepId);
        CoPilotDnd.DropOutcome outcome = CoPilotDnd.accept(
            copilot,
            target,
            payload,
            CoPilotDnd.DropContext.onStep(targetStepId, false).withSink(targetSink(ctx))
        );
        if (!outcome.handled() && outcome.message() != null) {
            ctx.toast(outcome.message());
        }
        return outcome;
    }

    public static CoPilotDnd.DropTarget dropTargetOf(CoPilotHost copilot, @Nullable String targetStepId) {
        if (targetStepId == null || targetStepId.isBlank()) {
            return CoPilotDnd.DropTarget.canvasBlank;
        }
        PlanStep step = CoPilotDnd.findStep(copilot, targetStepId);
        if (step instanceof AcquireStep) {
            return CoPilotDnd.DropTarget.acquireStep;
        }
        if (step instanceof CraftStep) {
            return CoPilotDnd.DropTarget.craftStep;
        }
        if (step instanceof ProvisionMachineStep) {
            return CoPilotDnd.DropTarget.provisionStep;
        }
        return CoPilotDnd.DropTarget.invalid;
    }

    /**
     * Project basket drop: append to the current tab's targets, or replace the primary target outright.
     */
    public static CoPilotDnd.TargetSink targetSink(WorkbenchShellContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        return new CoPilotDnd.TargetSink() {
            @Override
            public void addTarget(TargetDemand demand) {
                if (demand != null) {
                    ctx.setTargets(appendTarget(ctx.activeSnapshot().targets(), demand), null);
                }
            }

            @Override
            public void replaceTarget(TargetDemand demand) {
                if (demand != null) {
                    ctx.setTargets(List.of(demand), null);
                }
            }
        };
    }

    static List<TargetDemand> appendTarget(List<TargetDemand> current, TargetDemand added) {
        List<TargetDemand> merged = new ArrayList<>(current == null ? List.of() : current);
        for (int index = 0; index < merged.size(); index++) {
            TargetDemand existing = merged.get(index);
            if (existing.resource().equals(added.resource())) {
                merged.set(
                    index,
                    new TargetDemand(existing.resource(), saturatedAdd(existing.amount(), added.amount()))
                );
                return List.copyOf(merged);
            }
        }
        merged.add(added);
        return List.copyOf(merged);
    }

    private static long saturatedAdd(long left, long right) {
        long sum = left + right;
        return sum < 0L ? Long.MAX_VALUE : sum;
    }

    private void installDocks() {
        rightDock.graph(shell.activeGraph());
        shell.setLeftDock(leftDock);
        shell.setRightDock(rightDock);
        ShellSpatialDagHost dagHost = provider.plannerDagHost();
        if (dagHost != null) {
            dagHost.beaconSink((promptId, stepId) -> {
                if (shell.rightDockCollapsed()) {
                    shell.setRightDockCollapsed(false);
                }
                rightDock.focusPrompt(promptId);
            });
        }
        AtomicReference<@Nullable PlanningResult> lastResult = new AtomicReference<>(shell.activeSnapshot().result());
        AtomicReference<@Nullable String> lastTabId = new AtomicReference<>(shell.activeSnapshot().tabId());
        AtomicReference<@Nullable SessionTabState> lastTabState = new AtomicReference<>(
            shell.activeController() == null ? null : shell.activeController().state()
        );
        AtomicReference<@Nullable Object> lastGraph = new AtomicReference<>(shell.activeGraph());
        AtomicReference<@Nullable String> lastSelectedPlanId = new AtomicReference<>(
            shell.activeSnapshot().selectedPlanId()
        );
        AtomicReference<@Nullable String> lastComparePlanId = new AtomicReference<>(
            shell.activeSnapshot().comparePlanId()
        );
        AtomicReference<Object> lastPhase = new AtomicReference<>(shell.activeSnapshot().phase());
        AtomicReference<List<TargetDemand>> lastTargets = new AtomicReference<>(shell.activeSnapshot().targets());
        AtomicBoolean syncDirty = new AtomicBoolean(true);
        Runnable syncDocks = () -> {
            if (leftDock.lifecycle().destroyed() || rightDock.lifecycle().destroyed()) {
                return;
            }
            WorkbenchUiSnapshot snapshot = shell.activeSnapshot();
            PlanningResult nextResult = snapshot.result();
            String nextTabId = snapshot.tabId();
            var nextGraph = shell.activeGraph();
            var activeController = shell.activeController();
            var activeState = activeController == null ? null : activeController.state();
            boolean tabChanged = !Objects.equals(nextTabId, lastTabId.get());
            if (tabChanged) {
                var outgoing = lastTabState.get();
                if (outgoing != null) {
                    outgoing.setLeftDockCollapsed(shell.leftDockCollapsed());
                    outgoing.setRightDockCollapsed(shell.rightDockCollapsed());
                    outgoing.setLeftDockMode(leftDock.mode().name());
                    outgoing.setRightDockTab(rightDock.tab().name());
                }
                if (activeState != null) {
                    shell.setLeftDockCollapsed(activeState.leftDockCollapsed());
                    shell.setRightDockCollapsed(activeState.rightDockCollapsed());
                    leftDock.setMode(
                        "scatter".equals(activeState.leftDockMode())
                                ? LeftPlanDockWidget.Mode.scatter
                                : LeftPlanDockWidget.Mode.cards
                    );
                    RightCoPilotDockWidget.Tab restoredTab = switch (activeState.rightDockTab()) {
                        case "inspector" -> RightCoPilotDockWidget.Tab.inspector;
                        case "quickDrop" -> RightCoPilotDockWidget.Tab.quickDrop;
                        default -> RightCoPilotDockWidget.Tab.prompts;
                    };
                    rightDock.setTab(restoredTab);
                }
                lastTabState.set(activeState);
            } else if (activeState != null) {
                activeState.setLeftDockCollapsed(shell.leftDockCollapsed());
                activeState.setRightDockCollapsed(shell.rightDockCollapsed());
                activeState.setLeftDockMode(leftDock.mode().name());
                activeState.setRightDockTab(rightDock.tab().name());
            }
            boolean contentChanged = tabChanged
                    || nextResult != lastResult.get()
                    || nextGraph != lastGraph.get()
                    || !Objects.equals(snapshot.selectedPlanId(), lastSelectedPlanId.get())
                    || !Objects.equals(snapshot.comparePlanId(), lastComparePlanId.get())
                    || snapshot.phase() != lastPhase.get()
                    || !Objects.equals(snapshot.targets(), lastTargets.get());
            if (!contentChanged) {
                return;
            }
            lastResult.set(nextResult);
            lastTabId.set(nextTabId);
            lastGraph.set(nextGraph);
            lastSelectedPlanId.set(snapshot.selectedPlanId());
            lastComparePlanId.set(snapshot.comparePlanId());
            lastPhase.set(snapshot.phase());
            lastTargets.set(snapshot.targets());
            rightDock.graph(nextGraph);
            FlowStageWidget stage = provider.plannerStage();
            if (stage != null) {
                stage.graph(nextGraph);
                stage.inlineBeacons(WorkbenchIntegrationProvider.inlineBeaconsOf(snapshot, nextGraph));
            }
            leftDock.refresh();
            rightDock.rebuildPrompts();
        };
        leftDock.onMount((scene, ctx, handle) -> syncDirty.set(true));
        shell.onTick(() -> {
            if (syncDirty.compareAndSet(true, false)) {
                syncDocks.run();
            }
        });
        shell.addListener(() -> {
            if (leftDock.lifecycle().destroyed() || rightDock.lifecycle().destroyed()) {
                return;
            }
            syncDirty.set(true);
        });
    }
}
