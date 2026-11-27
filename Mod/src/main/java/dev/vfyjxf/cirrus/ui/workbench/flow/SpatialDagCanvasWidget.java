package dev.vfyjxf.cirrus.ui.workbench.flow;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.SccTopology;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.copilot.RightCoPilotDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.layout.CriticalPathCalculator;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagEdgeRoute;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayout;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.layout.SwimlaneBand;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.DagEdgeStyle;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLodLevel;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanVisualResolver;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabState;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.event.context.BubbleContext;
import dev.vfyjxf.cloudlib.api.event.context.InterruptibleContext;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.event.InputEvents;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.api.ui.texture.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetZero;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Z0 spatial DAG canvas: three layouts, three LODs, drag/pan/marquee input and hover tracing.
 */
public class SpatialDagCanvasWidget extends CompositeWidget<Widget> implements OreUiTooltipProvider {

    public static final int capsuleHeight = 20;
    public static final int capsuleInset = 6;
    public static final int capsuleHorizontalMargin = 8;
    public static final int capsuleChipHeight = 14;
    public static final int chipGap = 4;
    public static final int chipPadding = 4;
    public static final float guideLineWidth = 1.0f;
    public static final int guideDash = 4;
    public static final long hoverPublishIntervalMs = 40L;
    public static final long cameraEaseMillis = 180L;

    private static final String keyZoomPercent = "cirrus.ui.workbench.flow.zoom_percent";

    public enum ScrollNavigationMode {
        auto, trackpadPan, mouseZoom
    }

    private static ScrollNavigationMode scrollNavMode = ScrollNavigationMode.auto;

    private @Nullable SpatialDagHost host;
    private @Nullable PlanVisualResolver resolver;
    private @Nullable CompiledRecipeGraph graph;
    private @Nullable Supplier<@Nullable SessionTabState> tabStateSupplier;
    private @Nullable Runnable onOpenTargetPicker;
    private @Nullable Runnable onOpenGuidedDecisions;
    private WorkbenchThemeMode themeMode = WorkbenchThemeMode.defaultMode();
    private final DagViewport viewport = new DagViewport();
    private final DagDragState drag = new DagDragState();
    private final DagMinimapWidget minimap;
    private final DagLocatorRulerWidget locatorRuler;
    private final MultiSelectActionBarWidget actionBar;
    private @Nullable Consumer<FlowViewMode> onViewRequested;
    private @Nullable Consumer<String> onToggleLoopExpanded;
    private MultiSelectActionBarWidget.@Nullable FeedbackSink feedbackSink;
    private @Nullable ViewSettingsPopover viewSettingsPopover;
    private boolean embeddedInStageHeader;

    private PlanDagLayoutResult layout = PlanDagLayoutResult.empty;
    private boolean layoutDirty = true;
    private String layoutSignature = "";
    private Map<String, StepNodeCardRenderer.StepVisuals> visuals = Map.of();
    private Map<String, FloatPos> previewOffsets = Map.of();
    private List<DagSelectionMath.GuideLine> guides = List.of();
    private Set<String> selectedStepIds = Set.of();
    private Set<String> criticalStepIds = Set.of();
    private Set<String> draggingStepIds = Set.of();
    private Map<String, String> inlineBeacons = Map.of();
    private @Nullable String hoveredStepId;
    private @Nullable DagEdgeRoute hoveredEdge;
    private @Nullable String dropHighlightStepId;
    private DagLayoutMode layoutMode = DagLayoutMode.horizontalLr;
    private @Nullable DagLayoutMode lastFittedLayoutMode;
    private DagEdgeStyle edgeStyle = ExecutionOverlayClientConfig.workbenchDefaultDagEdgeStyle();
    private @Nullable Set<String> previewStepIds;
    private @Nullable PreviewKind previewKindValue;
    private @Nullable String autoFittedPlanId;
    private @Nullable String boundTabId;
    private @Nullable SessionTabState boundTabState;
    private boolean cameraInitializedForTab;
    private boolean minimapUserVisible = true;
    private boolean locatorUserVisible = true;
    private @Nullable String anchorStepId;
    private @Nullable ResourceKey anchorResource;
    private Map<String, FloatPos> lastAppliedOffsets = Map.of();
    private long frameTimeMs;
    private long lastHoverPublishMs;
    private boolean reducedMotion;
    private @Nullable Boolean reducedMotionOverride;
    private boolean cameraAnimating;
    private float camStartX;
    private float camStartY;
    private float camStartZoom = DagViewport.defaultZoom;
    private float camTargetX;
    private float camTargetY;
    private float camTargetZoom = DagViewport.defaultZoom;
    private long camAnimStartNanos;
    private long camAnimClockOffsetNanos;
    private DagSelectionMath.TraceSets traceSets = new DagSelectionMath.TraceSets(Set.of(), Set.of(), Set.of());
    private String traceFocus = "";
    private final OreUiMotion.SmoothValue previewAnchorX = new OreUiMotion.SmoothValue();
    private final OreUiMotion.SmoothValue previewAnchorY = new OreUiMotion.SmoothValue();
    private long cursorPreviewShownAtMs;
    private boolean cursorPreviewWasVertical;
    private long selectionBarShownAtMs;
    private @Nullable String selectionBarPrimaryId;

    private SpatialDagCanvasWidget() {
        useStyle(sizeFull(), positionAbsolute(), insetZero());
        setFocusable(true);
        setTickable(true);
        setClickGroup(this);
        onUnmount(this::closeViewSettingsPopover);
        minimap = addWidget(DagMinimapWidget.of(viewport));
        minimap.palette(this::palette).onClose(() -> setMinimapUserVisible(false)).onCameraChanged(() -> {
            cancelCameraAnimation();
            persistCameraToTabState();
        });
        locatorRuler = addWidget(DagLocatorRulerWidget.of(viewport));
        locatorRuler.palette(this::palette).onFocusStage(this::focusStageNodes).onFocusNode(this::focusAndSelectNode)
                .onToggleCollapsed(() -> setLocatorUserVisible(!locatorUserVisible)).onCameraChanged(() -> {
                    cancelCameraAnimation();
                    persistCameraToTabState();
                });
        actionBar = addWidget(MultiSelectActionBarWidget.create());
        actionBar.palette(this::palette).onAlign(this::applyAlign).onClearSelection(this::applySelection);
        actionBar.setVisible(false);
        onMouseClicked(this::handleMouseClicked);
        onMouseDragged(this::handleMouseDragged);
        onMouseReleased(this::handleMouseReleased);
        onMouseScrolled(this::handleMouseScrolled);
        onKeyPressed(this::handleKeyPressed);
        onEvent(
            InputEvents.onMouseMoved,
            (double mx, double my, InterruptibleContext ignored) -> handleMouseMoved(mx, my)
        );
    }

    public static SpatialDagCanvasWidget create() {
        return new SpatialDagCanvasWidget();
    }

    //region wiring

    public SpatialDagCanvasWidget host(@Nullable SpatialDagHost host) {
        this.host = host;
        layoutDirty = true;
        return this;
    }

    public SpatialDagCanvasWidget resolver(@Nullable PlanVisualResolver resolver) {
        this.resolver = resolver;
        layoutDirty = true;
        return this;
    }

    public SpatialDagCanvasWidget graph(@Nullable CompiledRecipeGraph graph) {
        this.graph = graph;
        layoutDirty = true;
        return this;
    }

    public SpatialDagCanvasWidget themeMode(@Nullable WorkbenchThemeMode themeMode) {
        this.themeMode = themeMode == null ? WorkbenchThemeMode.defaultMode() : themeMode;
        return this;
    }

    public SpatialDagCanvasWidget inlineBeacons(@Nullable Map<String, String> beacons) {
        this.inlineBeacons = beacons == null ? Map.of() : Map.copyOf(beacons);
        return this;
    }

    public SpatialDagCanvasWidget onViewRequested(@Nullable Consumer<FlowViewMode> listener) {
        this.onViewRequested = listener;
        return this;
    }

    public SpatialDagCanvasWidget onToggleLoopExpanded(@Nullable Consumer<String> listener) {
        this.onToggleLoopExpanded = listener;
        return this;
    }

    public SpatialDagCanvasWidget feedbackSink(MultiSelectActionBarWidget.@Nullable FeedbackSink sink) {
        this.feedbackSink = sink;
        actionBar.feedbackSink(sink);
        return this;
    }

    public SpatialDagCanvasWidget tabStateSupplier(@Nullable Supplier<@Nullable SessionTabState> supplier) {
        this.tabStateSupplier = supplier;
        return this;
    }

    public SpatialDagCanvasWidget onOpenTargetPicker(@Nullable Runnable listener) {
        this.onOpenTargetPicker = listener;
        return this;
    }

    public SpatialDagCanvasWidget onOpenGuidedDecisions(@Nullable Runnable listener) {
        this.onOpenGuidedDecisions = listener;
        return this;
    }

    public static ScrollNavigationMode scrollNavigationMode() {
        return scrollNavMode;
    }

    public static void setScrollNavigationMode(ScrollNavigationMode mode) {
        scrollNavMode = mode == null ? ScrollNavigationMode.auto : mode;
    }

    public boolean minimapUserVisible() {
        return minimapUserVisible;
    }

    public void setMinimapUserVisible(boolean visible) {
        this.minimapUserVisible = visible;
        minimap.setVisible(visible);
        SessionTabState state = resolveActiveTabState();
        if (state != null) {
            state.setMinimapVisible(visible);
        }
    }

    public boolean locatorUserVisible() {
        return locatorUserVisible;
    }

    public void setLocatorUserVisible(boolean visible) {
        this.locatorUserVisible = visible;
        locatorRuler.setCollapsed(!visible);
        SessionTabState state = resolveActiveTabState();
        if (state != null) {
            state.setLocatorRulerVisible(visible);
        }
    }

    public SpatialDagCanvasWidget embeddedInStageHeader(boolean embedded) {
        this.embeddedInStageHeader = embedded;
        return this;
    }

    public boolean embeddedInStageHeader() {
        return embeddedInStageHeader;
    }

    public boolean viewSettingsOpen() {
        return viewSettingsPopover != null;
    }

    public boolean isViewSettingsPopoverOpen() {
        return viewSettingsPopover != null;
    }

    public void closeViewSettingsPopover() {
        ViewSettingsPopover active = viewSettingsPopover;
        viewSettingsPopover = null;
        if (active != null && active.parent() instanceof WidgetGroup<?> group) {
            group.remove(active);
        }
    }

    public void toggleViewSettingsPopover() {
        if (viewSettingsPopover != null) {
            closeViewSettingsPopover();
            return;
        }
        CapsuleLayout capsule = capsuleLayout(safeMinecraftFont(), width(), height());
        Rect anchor = capsule.box();
        for (CapsuleChip chip : capsule.chips()) {
            if (chip.action() == CapsuleAction.viewMenu) {
                anchor = new Rect(
                    Math.round(chip.bounds().x()),
                    Math.round(chip.bounds().y()),
                    Math.round(chip.bounds().width()),
                    Math.round(chip.bounds().height())
                );
                break;
            }
        }
        FloatPos scenePt = localToScene(anchor.right(), anchor.bottom() + 4);
        toggleViewSettingsPopoverAt((int) Math.round(scenePt.x), (int) Math.round(scenePt.y));
    }

    public void toggleViewSettingsPopoverAt(int sceneAnchorRightX, int sceneAnchorBottomY) {
        if (viewSettingsPopover != null) {
            closeViewSettingsPopover();
            return;
        }
        if (!lifecycle().mounted() || scene() == null) {
            return;
        }
        int popW = 236;
        int popH = 212;
        int sceneW = Math.max(popW + 8, scene().root().width());
        int sceneH = Math.max(popH + 8, scene().root().height());
        Rect canvasBounds = absoluteBounds();
        int minX = canvasBounds.width() >= popW + 12 ? canvasBounds.x() + 6 : 4;
        int maxX = canvasBounds.width() >= popW + 12 ? canvasBounds.right() - popW - 6 : sceneW - popW - 4;
        int desiredX = sceneAnchorRightX - popW;
        int px = Math.max(4, Math.min(sceneW - popW - 4, Math.max(minX, Math.min(maxX, desiredX))));
        int minY = Math.max(4, canvasBounds.y() + 4);
        int py = Math.max(minY, Math.min(sceneH - popH - 4, sceneAnchorBottomY));
        ViewSettingsPopover popup = new ViewSettingsPopover(px, py, popW, popH);
        addRootWidget(scene().root(), popup);
        viewSettingsPopover = popup;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    public DagViewport camera() {
        return viewport;
    }

    public DagMinimapWidget minimap() {
        return minimap;
    }

    public DagLocatorRulerWidget locatorRuler() {
        return locatorRuler;
    }

    public MultiSelectActionBarWidget actionBar() {
        return actionBar;
    }

    public @Nullable SpatialDagHost host() {
        return host;
    }

    public Map<String, StepNodeCardRenderer.StepVisuals> visuals() {
        dagLayoutInternal();
        return visuals;
    }

    public @Nullable String hoveredStepId() {
        return hoveredStepId;
    }

    public @Nullable DagEdgeRoute hoveredEdge() {
        return hoveredEdge;
    }

    //endregion

    //region commands

    public void invalidate() {
        layoutDirty = true;
    }

    public void fitToView() {
        PlanDagLayoutResult result = dagLayoutInternal();
        if (result.isEmpty()) {
            return;
        }
        DagViewport target = new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
        target.fit(
            result.contentMinX(),
            result.contentMinY(),
            result.contentMaxX(),
            result.contentMaxY(),
            Math.max(1, width()),
            Math.max(1, height()),
            DagViewport.defaultPadding * 2.0f
        );
        animateCameraTo(target.camX(), target.camY(), target.zoom());
    }

    public void focusHome() {
        PlanDagLayoutResult result = dagLayoutInternal();
        if (result.isEmpty()) {
            return;
        }
        DagNodeBox targetNode = null;
        int bestLayer = -1;
        for (DagNodeBox node : result.nodes()) {
            if (node.layer() > bestLayer
                    || (node.layer() == bestLayer
                            && targetNode != null
                            && node.stepId().compareTo(targetNode.stepId()) < 0)) {
                bestLayer = node.layer();
                targetNode = node;
            }
        }
        if (targetNode != null) {
            DagViewport target = new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
            target.focusOn(targetNode, Math.max(1, width()), Math.max(1, height()));
            animateCameraTo(target.camX(), target.camY(), target.zoom());
        }
    }

    public boolean focusStep(@Nullable String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return false;
        }
        DagNodeBox node = nodeById(stepId);
        if (node == null) {
            return false;
        }
        DagViewport target = new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
        target.focusOn(node, Math.max(1, width()), Math.max(1, height()));
        animateCameraTo(target.camX(), target.camY(), target.zoom());
        return true;
    }

    public void focusStageNodes(List<DagNodeBox> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (DagNodeBox node : nodes) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.maxX());
            maxY = Math.max(maxY, node.maxY());
        }
        DagViewport target = new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
        target.centerOnWorld((minX + maxX) * 0.5, (minY + maxY) * 0.5, Math.max(1, width()), Math.max(1, height()));
        animateCameraTo(target.camX(), target.camY(), target.zoom());
    }

    private void focusAndSelectNode(DagNodeBox node) {
        if (node == null) {
            return;
        }
        applySelection(Set.of(node.stepId()));
        focusStep(node.stepId());
    }

    public SpatialDagCanvasWidget reducedMotion(@Nullable Boolean reducedMotion) {
        this.reducedMotionOverride = reducedMotion;
        if (reducedMotion != null) {
            this.reducedMotion = reducedMotion;
        }
        return this;
    }

    public boolean cameraAnimating() {
        return cameraAnimating;
    }

    public void cancelCameraAnimation() {
        cameraAnimating = false;
    }

    public void advanceCameraAnimation(long deltaMillis) {
        if (deltaMillis > 0L) {
            camAnimClockOffsetNanos += deltaMillis * 1_000_000L;
        }
        stepCameraAnimation(currentAnimNanos());
    }

    private void animateCameraTo(float targetX, float targetY, float targetZoom) {
        boolean instant = effectiveReducedMotion() || cameraEaseMillis <= 0L;
        float clampedZoom = DagViewport.clampZoom(targetZoom);
        if (instant) {
            cameraAnimating = false;
            viewport.setCamera(targetX, targetY);
            viewport.setZoom(clampedZoom);
            cameraInitializedForTab = true;
            persistCameraToTabState();
            return;
        }
        camStartX = viewport.camX();
        camStartY = viewport.camY();
        camStartZoom = viewport.zoom();
        camTargetX = targetX;
        camTargetY = targetY;
        camTargetZoom = clampedZoom;
        camAnimStartNanos = currentAnimNanos();
        cameraAnimating = true;
        cameraInitializedForTab = true;
        persistCameraToTabState();
    }

    private void stepCameraAnimation(long nowNanos) {
        if (!cameraAnimating) {
            return;
        }
        if (effectiveReducedMotion()) {
            viewport.setCamera(camTargetX, camTargetY);
            viewport.setZoom(camTargetZoom);
            cameraAnimating = false;
            persistCameraToTabState();
            return;
        }
        long elapsed = Math.max(0L, nowNanos - camAnimStartNanos);
        float raw = Math.min(1f, elapsed / (cameraEaseMillis * 1_000_000.0f));
        float eased = Easing.CUBIC_OUT.apply(raw);
        viewport.setCamera(camStartX + (camTargetX - camStartX) * eased, camStartY + (camTargetY - camStartY) * eased);
        viewport.setZoom(camStartZoom + (camTargetZoom - camStartZoom) * eased);
        if (raw >= 1f) {
            viewport.setCamera(camTargetX, camTargetY);
            viewport.setZoom(camTargetZoom);
            cameraAnimating = false;
            persistCameraToTabState();
        }
    }

    private boolean effectiveReducedMotion() {
        if (reducedMotionOverride != null) {
            return reducedMotionOverride;
        }
        SpatialDagHost dagHost = host;
        if (dagHost != null) {
            reducedMotion = dagHost.isReducedMotion();
        }
        return reducedMotion;
    }

    private long currentAnimNanos() {
        return System.nanoTime() + camAnimClockOffsetNanos;
    }

    public DagEdgeStyle edgeStyle() {
        return edgeStyle;
    }

    public SpatialDagCanvasWidget setEdgeStyle(DagEdgeStyle style) {
        this.edgeStyle = style == null ? DagEdgeStyle.orthogonal : style;
        SessionTabState state = resolveActiveTabState();
        if (state != null) {
            state.setDagEdgeStyle(this.edgeStyle);
        }
        return this;
    }

    public void cycleLayoutMode() {
        SpatialDagHost dagHost = host;
        if (dagHost == null) {
            return;
        }
        DagLayoutMode next = switch (dagHost.snapshot().dagLayoutMode()) {
            case horizontalLr -> DagLayoutMode.verticalTb;
            case verticalTb -> DagLayoutMode.horizontalRl;
            case horizontalRl -> DagLayoutMode.verticalBt;
            case verticalBt -> DagLayoutMode.systemSwimlane;
            case systemSwimlane -> DagLayoutMode.horizontalLr;
        };
        dagHost.onSetLayoutMode(next);
        layoutDirty = true;
    }

    public void showView(FlowViewMode mode) {
        if (onViewRequested != null && mode != null) {
            onViewRequested.accept(mode);
        }
    }

    public void clearManualOffsets() {
        previewOffsets = Map.of();
        drag.cancel();
        guides = List.of();
        anchorStepId = null;
        anchorResource = null;
        SessionTabState state = resolveActiveTabState();
        if (state != null) {
            state.setAnchor(null, null);
        }
        layoutDirty = true;
        SpatialDagHost dagHost = host;
        if (dagHost != null) {
            dagHost.onClearManualOffsets();
        }
    }

    public void resetTabPlanAndRules() {
        closeViewSettingsPopover();
        cameraInitializedForTab = false;
        autoFittedPlanId = null;
        anchorStepId = null;
        anchorResource = null;
        lastFittedLayoutMode = null;
        locatorRuler.setScrollOffset(0f);
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        if (shell != null) {
            shell.resetActiveTabSession();
        } else {
            clearManualOffsets();
        }
        layoutDirty = true;
    }

    public void applySelection(Set<String> stepIds) {
        Set<String> next = stepIds == null ? Set.of() : Set.copyOf(stepIds);
        selectedStepIds = next;
        if (!next.isEmpty()) {
            String primary = next.iterator().next();
            anchorStepId = primary;
            StepNodeCardRenderer.StepVisuals v = visuals.get(primary);
            if (v != null && v.primaryOutput() != null) {
                anchorResource = v.primaryOutput();
            }
            SessionTabState state = resolveActiveTabState();
            if (state != null) {
                state.setAnchor(anchorStepId, anchorResource);
            }
        }
        layoutDirty = true;
        SpatialDagHost dagHost = host;
        if (dagHost != null) {
            dagHost.onSelectSteps(next);
        }
    }

    public void setSelectedSteps(Set<String> stepIds) {
        selectedStepIds = stepIds == null ? Set.of() : Set.copyOf(stepIds);
    }

    public Set<String> selectedStepIds() {
        return selectedStepIds;
    }

    public Map<String, FloatPos> dragPreviewOffsets() {
        return Map.copyOf(previewOffsets);
    }

    public PlanDagLayoutResult dagLayout() {
        return dagLayoutInternal();
    }

    private PlanDagLayoutResult dagLayoutInternal() {
        SpatialDagHost dagHost = host;
        if (dagHost != null) {
            WorkbenchUiSnapshot currentSnapshot = dagHost.snapshot();
            syncTabStateIfNeeded(currentSnapshot);
            if (!signatureOf(currentSnapshot).equals(layoutSignature)) {
                layoutDirty = true;
            }
        }
        if (!layoutDirty) {
            return layout;
        }
        if (dagHost == null) {
            layout = PlanDagLayoutResult.empty;
            visuals = Map.of();
            criticalStepIds = Set.of();
            layoutSignature = "";
            layoutDirty = false;
            return layout;
        }
        WorkbenchUiSnapshot snapshot = dagHost.snapshot();
        boolean sameDirection = lastFittedLayoutMode == snapshot.dagLayoutMode();
        boolean offsetsChanged = !Objects.equals(lastAppliedOffsets, snapshot.manualNodeOffsets());
        AnchorSnapshot prevAnchor = (!layout.isEmpty()
                && cameraInitializedForTab
                && sameDirection
                && !offsetsChanged
                && drag.mode() != DagDragState.Mode.nodeDrag) ? captureAnchor(layout, visuals) : null;
        ProductionPlan plan = snapshot.selectedPlan().orElse(null);
        ProductionPlan compare = snapshot.comparePlan().orElse(null);
        CompiledRecipeGraph recipeGraph = graph == null ? EmptyRecipeGraph.instance : graph;
        layoutMode = snapshot.dagLayoutMode();
        criticalStepIds = plan == null ? Set.of() : CriticalPathCalculator.compute(plan).criticalStepIds();
        PlanDagLayoutResult nextLayout = PlanDagLayout.compute(
            plan,
            compare,
            recipeGraph,
            snapshot.dagLayoutMode(),
            snapshot.expandedLoopStepIds(),
            snapshot.manualNodeOffsets(),
            criticalStepIds
        );
        Map<String, StepNodeCardRenderer.StepVisuals> nextVisuals = buildVisuals(
            nextLayout,
            recipeGraph,
            snapshot.activeSessionRules()
        );
        layout = nextLayout;
        visuals = nextVisuals;
        lastAppliedOffsets = snapshot.manualNodeOffsets();
        layoutSignature = signatureOf(snapshot);
        layoutDirty = false;
        if (prevAnchor != null && !nextLayout.isEmpty()) {
            restoreAnchorCamera(prevAnchor, nextLayout, nextVisuals);
        }
        return layout;
    }

    private record AnchorSnapshot(String stepId, @Nullable ResourceKey resource, float worldX, float worldY) {}

    private @Nullable AnchorSnapshot captureAnchor(
        PlanDagLayoutResult current,
        Map<String, StepNodeCardRenderer.StepVisuals> currentVisuals
    ) {
        DagNodeBox chosen = null;
        if (!selectedStepIds.isEmpty()) {
            String first = selectedStepIds.iterator().next();
            for (DagNodeBox node : current.nodes()) {
                if (node.stepId().equals(first)) {
                    chosen = node;
                    break;
                }
            }
        }
        if (chosen == null && anchorStepId != null) {
            for (DagNodeBox node : current.nodes()) {
                if (node.stepId().equals(anchorStepId)) {
                    chosen = node;
                    break;
                }
            }
        }
        if (chosen == null && anchorResource != null) {
            for (DagNodeBox node : current.nodes()) {
                StepNodeCardRenderer.StepVisuals v = currentVisuals.get(node.stepId());
                if (v != null && anchorResource.equals(v.primaryOutput())) {
                    chosen = node;
                    break;
                }
            }
        }
        if (chosen == null) {
            int bestLayer = -1;
            for (DagNodeBox node : current.nodes()) {
                if (node.layer() > bestLayer) {
                    bestLayer = node.layer();
                    chosen = node;
                }
            }
        }
        if (chosen == null) {
            return null;
        }
        StepNodeCardRenderer.StepVisuals v = currentVisuals.get(chosen.stepId());
        ResourceKey res = v == null ? anchorResource : v.primaryOutput();
        return new AnchorSnapshot(chosen.stepId(), res, chosen.centerX(), chosen.centerY());
    }

    private void restoreAnchorCamera(
        AnchorSnapshot prev,
        PlanDagLayoutResult nextLayout,
        Map<String, StepNodeCardRenderer.StepVisuals> nextVisuals
    ) {
        DagNodeBox matched = null;
        for (DagNodeBox node : nextLayout.nodes()) {
            if (node.stepId().equals(prev.stepId())) {
                matched = node;
                break;
            }
        }
        if (matched == null && prev.resource() != null) {
            for (DagNodeBox node : nextLayout.nodes()) {
                StepNodeCardRenderer.StepVisuals v = nextVisuals.get(node.stepId());
                if (v != null && prev.resource().equals(v.primaryOutput())) {
                    matched = node;
                    break;
                }
            }
        }
        if (matched == null) {
            return;
        }
        float dx = matched.centerX() - prev.worldX();
        float dy = matched.centerY() - prev.worldY();
        if (Math.abs(dx) >= 1e-4f || Math.abs(dy) >= 1e-4f) {
            cancelCameraAnimation();
            viewport.setCamera(viewport.camX() + dx, viewport.camY() + dy);
            persistCameraToTabState();
        }
        anchorStepId = matched.stepId();
        StepNodeCardRenderer.StepVisuals v = nextVisuals.get(matched.stepId());
        anchorResource = v != null && v.primaryOutput() != null ? v.primaryOutput() : prev.resource();
    }

    private @Nullable SessionTabState resolveActiveTabState() {
        if (tabStateSupplier != null) {
            return tabStateSupplier.get();
        }
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        if (shell == null) {
            return null;
        }
        PlannerWorkbenchController controller = shell.activeController();
        return controller == null ? null : controller.state();
    }

    private void syncTabStateIfNeeded(WorkbenchUiSnapshot snapshot) {
        String nextTabId = snapshot.tabId();
        SessionTabState nextState = resolveActiveTabState();
        if (Objects.equals(nextTabId, boundTabId) && nextState == boundTabState) {
            if (nextState != null && !nextState.cameraInitialized() && cameraInitializedForTab) {
                cancelCameraAnimation();
                cameraInitializedForTab = false;
                autoFittedPlanId = null;
                lastFittedLayoutMode = null;
                layoutDirty = true;
            }
            return;
        }
        if (boundTabState != null) {
            if (cameraInitializedForTab) {
                boundTabState.setCameraState(
                    cameraAnimating ? camTargetX : viewport.camX(),
                    cameraAnimating ? camTargetY : viewport.camY(),
                    cameraAnimating ? camTargetZoom : viewport.zoom()
                );
            }
            boundTabState.setDagEdgeStyle(edgeStyle);
            boundTabState.setMinimapVisible(minimapUserVisible);
            boundTabState.setLocatorRulerVisible(locatorUserVisible);
            boundTabState.setLocatorScrollOffset(locatorRuler.scrollOffset());
            boundTabState.setAnchor(anchorStepId, anchorResource);
        }
        boundTabId = nextTabId;
        boundTabState = nextState;
        closeViewSettingsPopover();
        if (nextState != null) {
            edgeStyle = nextState.dagEdgeStyle();
            minimapUserVisible = nextState.minimapVisible();
            minimap.setVisible(minimapUserVisible);
            locatorUserVisible = nextState.locatorRulerVisible();
            locatorRuler.setCollapsed(!locatorUserVisible);
            anchorStepId = nextState.anchorStepId();
            anchorResource = nextState.anchorResource();
            locatorRuler.setScrollOffset(nextState.locatorScrollOffset());
            if (nextState.cameraInitialized()) {
                cancelCameraAnimation();
                viewport.setCamera(nextState.cameraX(), nextState.cameraY());
                viewport.setZoom(nextState.cameraZoom());
                cameraInitializedForTab = true;
                lastFittedLayoutMode = snapshot.dagLayoutMode();
                autoFittedPlanId = snapshot.selectedPlanId();
            } else {
                cameraInitializedForTab = false;
                autoFittedPlanId = null;
                lastFittedLayoutMode = null;
            }
        } else {
            cameraInitializedForTab = false;
            autoFittedPlanId = null;
            lastFittedLayoutMode = null;
        }
        layoutDirty = true;
    }

    private void persistCameraToTabState() {
        SessionTabState state = resolveActiveTabState();
        if (state == null || !cameraInitializedForTab) {
            return;
        }
        state.setCameraState(
            cameraAnimating ? camTargetX : viewport.camX(),
            cameraAnimating ? camTargetY : viewport.camY(),
            cameraAnimating ? camTargetZoom : viewport.zoom()
        );
        state.setAnchor(anchorStepId, anchorResource);
        state.setLocatorScrollOffset(locatorRuler.scrollOffset());
    }

    private String signatureOf(WorkbenchUiSnapshot snapshot) {
        return snapshot.tabId() + "|" + snapshot.runtimeGraphVersion() + "|" + System.identityHashCode(graph) + "|"
                + System.identityHashCode(snapshot.result()) + "|" + snapshot.dagLayoutMode() + "|"
                + snapshot.expandedLoopStepIds() + "|" + snapshot.manualNodeOffsets() + "|" + snapshot.selectedPlanId()
                + "|" + snapshot.comparePlanId() + "|" + snapshot.targets() + "|" + snapshot.phase();
    }

    private Map<String, StepNodeCardRenderer.StepVisuals> buildVisuals(
        PlanDagLayoutResult result,
        CompiledRecipeGraph recipeGraph,
        List<GuidanceRule> rules
    ) {
        if (result.isEmpty()) {
            return Map.of();
        }
        Map<String, ResourceKey> inferred = new TreeMap<>();
        Map<String, Long> produced = new TreeMap<>();
        for (DagEdgeRoute edge : result.edges()) {
            if (edge.kind() != DagEdgeRoute.EdgeKind.material || edge.resource() == null) {
                continue;
            }
            long amount = edge.sourceTotalProduced();
            if (amount >= produced.getOrDefault(edge.fromStepId(), -1L)) {
                produced.put(edge.fromStepId(), amount);
                inferred.put(edge.fromStepId(), edge.resource());
            }
        }
        Map<String, StepNodeCardRenderer.StepVisuals> out = new TreeMap<>();
        for (DagNodeBox node : result.nodes()) {
            out.put(
                node.stepId(),
                StepNodeCardRenderer
                        .resolve(node.step(), recipeGraph, inferred.get(node.stepId()), hardPinned(node.step(), rules))
            );
            for (DagNodeBox inner : node.innerLoopNodes()) {
                out.put(
                    inner.stepId(),
                    StepNodeCardRenderer.resolve(
                        inner.step(),
                        recipeGraph,
                        inferred.get(inner.stepId()),
                        hardPinned(inner.step(), rules)
                    )
                );
            }
        }
        return out;
    }

    private static boolean hardPinned(PlanStep step, List<GuidanceRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        CompiledRecipeKey key = switch (step) {
            case CraftStep craft -> craft.recipeKey();
            case ConvertStep convert -> convert.conversionRecipeKey();
            default -> null;
        };
        if (key == null) {
            return false;
        }
        for (GuidanceRule rule : rules) {
            if (rule instanceof RecipeRule recipeRule
                    && recipeRule.strength() == RuleStrength.hardPin
                    && key.equals(recipeRule.recipeKey())) {
                return true;
            }
        }
        return false;
    }

    private @Nullable DagNodeBox nodeById(String stepId) {
        for (DagNodeBox node : dagLayoutInternal().nodes()) {
            if (node.stepId().equals(stepId)) {
                return node;
            }
            for (DagNodeBox inner : node.innerLoopNodes()) {
                if (inner.stepId().equals(stepId)) {
                    return inner;
                }
            }
        }
        return null;
    }

    public @Nullable String dropTargetAt(double sceneX, double sceneY) {
        FloatPos local = sceneToLocal(sceneX, sceneY);
        double worldX = viewport.screenToWorldX(local.x);
        double worldY = viewport.screenToWorldY(local.y);
        DagNodeBox node = DagHitTester.nodeAt(worldX, worldY, dagLayoutInternal().nodes());
        if (node == null) {
            return null;
        }
        DagNodeBox outer = DagHitTester.outerNodeOf(node, dagLayoutInternal().nodes());
        return outer == null ? node.stepId() : outer.stepId();
    }

    public void setDropHighlight(@Nullable String stepId) {
        this.dropHighlightStepId = stepId;
    }

    /**
     * Rehearses a Co-Pilot action on the canvas without touching plan state.
     */
    public void previewSteps(@Nullable Set<String> stepIds, @Nullable PreviewKind kind) {
        if (stepIds == null || stepIds.isEmpty() || kind == null) {
            previewStepIds = null;
            previewKindValue = null;
            return;
        }
        previewStepIds = Set.copyOf(stepIds);
        previewKindValue = kind;
    }

    public void clearPreview() {
        previewStepIds = null;
        previewKindValue = null;
    }

    /**
     * Returns the previewed steps that actually exist in the current layout, or {@code null} when no preview is active.
     */
    public @Nullable Set<String> previewedStepIds() {
        if (previewStepIds == null || previewKindValue == null) {
            return null;
        }
        PlanDagLayoutResult result = dagLayoutInternal();
        if (result.isEmpty()) {
            return Set.of();
        }
        Set<String> present = new TreeSet<>();
        for (DagNodeBox node : result.nodes()) {
            if (previewStepIds.contains(node.stepId())) {
                present.add(node.stepId());
            }
            for (DagNodeBox inner : node.innerLoopNodes()) {
                if (previewStepIds.contains(inner.stepId())) {
                    present.add(inner.stepId());
                }
            }
        }
        return Set.copyOf(present);
    }

    public @Nullable PreviewKind previewKind() {
        return previewKindValue;
    }

    public void onDropPayload(DragPayload payload, @Nullable String targetStepId) {
        SpatialDagHost dagHost = host;
        if (dagHost != null) {
            dagHost.onDrop(payload, targetStepId);
        }
    }

    //endregion

    //region input

    private EventDispatch handleMouseClicked(InputContext input, BubbleContext context) {
        SpatialDagHost dagHost = host;
        if (dagHost == null) {
            return EventDispatch.pass;
        }
        requestCanvasFocus();
        FloatPos local = sceneToLocal(input.mouseX(), input.mouseY());
        if (input.isRightClick()) {
            DagNodeBox node = nodeAtLocal(local.x, local.y);
            if (node != null) {
                dagHost.onNodeContextMenu(node.stepId(), input.mouseX(), input.mouseY(), input.isShiftDown());
            } else {
                dagHost.onCanvasContextMenu(input.mouseX(), input.mouseY());
            }
            context.consume();
            return EventDispatch.consumed;
        }
        if (input.isMiddleClick()) {
            cancelCameraAnimation();
            drag.press(DagDragState.Mode.panning, local.x, local.y);
            context.consume();
            return EventDispatch.consumed;
        }
        if (!input.isLeftClick()) {
            return EventDispatch.pass;
        }
        if (!embeddedInStageHeader && clickCapsule(dagHost, local.x, local.y)) {
            context.consume();
            return EventDispatch.consumed;
        }
        if (viewSettingsPopover != null) {
            closeViewSettingsPopover();
        }
        if (dagLayoutInternal().isEmpty() && clickEmptyStateCta(local.x, local.y)) {
            context.consume();
            return EventDispatch.consumed;
        }
        if (clickGuidedCheckpointBanner(local.x, local.y)) {
            context.consume();
            return EventDispatch.consumed;
        }
        if (clickSelectionPreview(local.x, local.y)) {
            context.consume();
            return EventDispatch.consumed;
        }
        DagNodeBox node = nodeAtLocal(local.x, local.y);
        if (node != null && clickHotspot(dagHost, node, local.x, local.y)) {
            context.consume();
            return EventDispatch.consumed;
        }
        if (input.isShiftDown()) {
            drag.press(DagDragState.Mode.marquee, local.x, local.y);
            context.consume();
            return EventDispatch.consumed;
        }
        if (node != null) {
            Set<String> nextSelection = selectionFor(node.stepId(), input.isShiftDown());
            applySelection(nextSelection);
            draggingStepIds = Set.of(node.stepId());
            drag.press(DagDragState.Mode.nodeDrag, local.x, local.y);
            captureDragOffsets(draggingStepIds);
            drag.anchor(node.stepId());
            context.consume();
            return EventDispatch.consumed;
        }
        cancelCameraAnimation();
        drag.press(DagDragState.Mode.panning, local.x, local.y);
        context.consume();
        return EventDispatch.consumed;
    }

    private void requestCanvasFocus() {
        scene().requestFocus(this);
    }

    private Set<String> selectionFor(String stepId, boolean shift) {
        if (!shift) {
            return Set.of(stepId);
        }
        Set<String> next = new TreeSet<>(selectedStepIds);
        if (!next.add(stepId)) {
            next.remove(stepId);
        }
        return next;
    }

    private void captureDragOffsets(Set<String> stepIds) {
        Map<String, FloatPos> offsets = currentOffsets();
        for (String stepId : stepIds) {
            drag.captureOriginal(stepId, offsets.get(stepId));
        }
    }

    private Map<String, FloatPos> currentOffsets() {
        SpatialDagHost dagHost = host;
        return dagHost == null ? Map.of() : dagHost.snapshot().manualNodeOffsets();
    }

    private boolean clickHotspot(SpatialDagHost dagHost, DagNodeBox node, double localX, double localY) {
        float[] world = viewport.screenToWorld((float) localX, (float) localY);
        if (node.step() instanceof CycleLoopStep) {
            DagHitTester.RectF expand = StepNodeCardRenderer.expandButton(node);
            if (expand.contains(world[0], world[1])) {
                if (onToggleLoopExpanded != null) {
                    onToggleLoopExpanded.accept(node.stepId());
                }
                layoutDirty = true;
                return true;
            }
        }
        if (node.step() instanceof ProvisionMachineStep provision) {
            DagHitTester.RectF owned = StepNodeCardRenderer.declareOwnedButton(node);
            if (owned.contains(world[0], world[1]) && feedbackSink != null) {
                feedbackSink.send(new DeclareOwnedMachineFeedback(provision.machineId(), 1, false));
                return true;
            }
        }
        String promptId = inlineBeacons.get(node.stepId());
        if (promptId != null && beaconBounds(node).contains(world[0], world[1])) {
            dagHost.onInlineBeaconClick(promptId, node.stepId());
            return true;
        }
        return false;
    }

    private static DagHitTester.RectF beaconBounds(DagNodeBox node) {
        return new DagHitTester.RectF(node.maxX() - 12.0f, node.y() - 12.0f, 12.0f, 12.0f);
    }

    private boolean clickCapsule(SpatialDagHost dagHost, double localX, double localY) {
        CapsuleLayout layout = capsuleLayout(safeMinecraftFont(), width(), height());
        for (CapsuleChip chip : layout.chips()) {
            if (chip.bounds().contains(localX, localY) && chip.action().interactive()) {
                activateCapsule(dagHost, chip.action());
                return true;
            }
        }
        return false;
    }

    private void activateCapsule(SpatialDagHost dagHost, CapsuleAction action) {
        switch (action) {
            case viewDag -> showView(FlowViewMode.spatialDag);
            case viewLedger -> showView(FlowViewMode.preparationLedger);
            case viewChecklist -> showView(FlowViewMode.executionChecklist);
            case layoutLr -> dagHost.onSetLayoutMode(DagLayoutMode.horizontalLr);
            case layoutTb -> dagHost.onSetLayoutMode(DagLayoutMode.verticalTb);
            case layoutRl -> dagHost.onSetLayoutMode(DagLayoutMode.horizontalRl);
            case layoutBt -> dagHost.onSetLayoutMode(DagLayoutMode.verticalBt);
            case layoutSwimlane -> dagHost.onSetLayoutMode(DagLayoutMode.systemSwimlane);
            case edgeOrthogonal -> setEdgeStyle(DagEdgeStyle.orthogonal);
            case edgeStraight -> setEdgeStyle(DagEdgeStyle.straight);
            case edgeCurved -> setEdgeStyle(DagEdgeStyle.curved);
            case fit -> fitToView();
            case autoLayout -> clearManualOffsets();
            case viewMenu -> toggleViewSettingsPopover();
            case zoomReadout -> {}
        }
        layoutDirty = true;
    }

    private EventDispatch handleMouseDragged(InputContext input, double deltaX, double deltaY, BubbleContext context) {
        FloatPos local = sceneToLocal(input.mouseX(), input.mouseY());
        drag.move(local.x, local.y, System.currentTimeMillis());
        if (!drag.dragging()) {
            return EventDispatch.consumed;
        }
        switch (drag.mode()) {
            case panning -> {
                cancelCameraAnimation();
                viewport.panScreen(deltaX, deltaY);
                cameraInitializedForTab = true;
                persistCameraToTabState();
            }
            case nodeDrag -> {
                updateNodeDrag(input.isAltDown());
                hoverQuickDrop(input.mouseX(), input.mouseY());
            }
            default -> {}
        }
        context.consume();
        return EventDispatch.consumed;
    }

    private @Nullable ResourceKey draggedPrimaryResource() {
        String anchor = drag.anchorStepId();
        if (anchor == null && !draggingStepIds.isEmpty()) {
            anchor = draggingStepIds.iterator().next();
        }
        if (anchor == null) {
            return null;
        }
        StepNodeCardRenderer.StepVisuals vis = visuals.get(anchor);
        if (vis != null && vis.primaryOutput() != null) {
            return vis.primaryOutput();
        }
        DagNodeBox node = nodeById(anchor);
        if (node != null) {
            return DagRichTooltips
                    .primaryIconOf(node.step(), vis == null ? StepNodeCardRenderer.StepVisuals.empty() : vis);
        }
        return null;
    }

    private void hoverQuickDrop(double sceneX, double sceneY) {
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        if (shell instanceof WorkbenchShellWidget shellWidget) {
            RightCoPilotDockWidget dock = shellWidget.rightDockWidget();
            if (dock != null) {
                dock.hoverExternalResourceDrag(sceneX, sceneY, draggedPrimaryResource());
            }
        }
    }

    private boolean dropOnQuickDrop(double sceneX, double sceneY) {
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        if (shell instanceof WorkbenchShellWidget shellWidget) {
            RightCoPilotDockWidget dock = shellWidget.rightDockWidget();
            if (dock != null) {
                return dock.dropExternalResource(sceneX, sceneY, draggedPrimaryResource());
            }
        }
        return false;
    }

    private void clearQuickDropHover() {
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        if (shell instanceof WorkbenchShellWidget shellWidget) {
            RightCoPilotDockWidget dock = shellWidget.rightDockWidget();
            if (dock != null) {
                dock.clearExternalResourceDrag();
            }
        }
    }

    public boolean finishExternalDrag(double sceneX, double sceneY) {
        DagDragState.Mode mode = drag.mode();
        if (mode != DagDragState.Mode.nodeDrag
                && mode != DagDragState.Mode.panning
                && mode != DagDragState.Mode.marquee) {
            return false;
        }
        boolean wasDragging = drag.dragging();
        boolean consumed = false;
        if (mode == DagDragState.Mode.nodeDrag && wasDragging) {
            if (dropOnQuickDrop(sceneX, sceneY)) {
                previewOffsets = Map.of();
                consumed = true;
            } else if (host != null) {
                for (Map.Entry<String, FloatPos> entry : previewOffsets.entrySet()) {
                    host.onNodeMoved(entry.getKey(), entry.getValue());
                }
                previewOffsets = Map.of();
                layoutDirty = true;
            }
        }
        clearQuickDropHover();
        guides = List.of();
        drag.release();
        return consumed;
    }

    private void updateNodeDrag(boolean altDown) {
        if (draggingStepIds.isEmpty()) {
            return;
        }
        float rawX = drag.worldDeltaX(viewport.zoom());
        float rawY = drag.worldDeltaY(viewport.zoom());
        FloatPos snapped = DagSelectionMath.snappedOffset(rawX, rawY, DagSelectionMath.grid, !altDown);
        if (!altDown) {
            DagNodeBox anchor = nodeById(drag.anchorStepId() == null ? "" : drag.anchorStepId());
            if (anchor != null && !layout.isEmpty()) {
                List<DagHitTester.RectF> others = new ArrayList<>();
                for (DagNodeBox node : layout.nodes()) {
                    if (!draggingStepIds.contains(node.stepId())) {
                        others.add(new DagHitTester.RectF(node.x(), node.y(), node.width(), node.height()));
                    }
                }
                DagHitTester.RectF dragged = new DagHitTester.RectF(
                    anchor.x() + (float) snapped.x,
                    anchor.y() + (float) snapped.y,
                    anchor.width(),
                    anchor.height()
                );
                snapped = DagSelectionMath.magneticSnap(dragged, others, snapped, DagSelectionMath.guideThreshold);
            }
        }
        Map<String, FloatPos> original = drag.originalOffsets();
        Map<String, FloatPos> next = new TreeMap<>();
        for (String stepId : draggingStepIds) {
            FloatPos base = original.getOrDefault(stepId, new FloatPos(0.0, 0.0));
            next.put(stepId, new FloatPos(base.x + snapped.x, base.y + snapped.y));
        }
        previewOffsets = next;
        guides = computeGuides(snapped, altDown);
    }

    private List<DagSelectionMath.GuideLine> computeGuides(FloatPos snapped, boolean altDown) {
        if (altDown || layout.isEmpty() || draggingStepIds.isEmpty()) {
            return List.of();
        }
        List<DagHitTester.RectF> others = new ArrayList<>();
        for (DagNodeBox node : layout.nodes()) {
            if (!draggingStepIds.contains(node.stepId())) {
                others.add(new DagHitTester.RectF(node.x(), node.y(), node.width(), node.height()));
            }
        }
        List<DagSelectionMath.GuideLine> lines = new ArrayList<>();
        for (String stepId : draggingStepIds) {
            DagNodeBox node = nodeById(stepId);
            if (node == null) {
                continue;
            }
            DagHitTester.RectF dragged = new DagHitTester.RectF(
                node.x() + (float) snapped.x,
                node.y() + (float) snapped.y,
                node.width(),
                node.height()
            );
            lines.addAll(DagSelectionMath.guideLines(dragged, others, DagSelectionMath.guideThreshold));
        }
        return lines;
    }

    private EventDispatch handleMouseReleased(InputContext input, BubbleContext context) {
        SpatialDagHost dagHost = host;
        DagDragState.Mode mode = drag.mode();
        boolean wasDragging = drag.dragging();
        FloatPos local = sceneToLocal(input.mouseX(), input.mouseY());
        if (dagHost != null) {
            if (mode == DagDragState.Mode.marquee && wasDragging) {
                float[] press = viewport.screenToWorld((float) drag.pressScreenX(), (float) drag.pressScreenY());
                float[] release = viewport.screenToWorld((float) local.x, (float) local.y);
                applySelection(
                    DagSelectionMath
                            .marqueeSelect(press[0], press[1], release[0], release[1], dagLayoutInternal().nodes())
                );
            } else if (mode == DagDragState.Mode.nodeDrag && wasDragging) {
                if (dropOnQuickDrop(input.mouseX(), input.mouseY())) {
                    previewOffsets = Map.of();
                } else {
                    for (Map.Entry<String, FloatPos> entry : previewOffsets.entrySet()) {
                        dagHost.onNodeMoved(entry.getKey(), entry.getValue());
                    }
                    previewOffsets = Map.of();
                    layoutDirty = true;
                }
            } else if (mode == DagDragState.Mode.panning && !wasDragging) {
                if (hoveredEdge != null) {
                    String targetId = (input.isShiftDown() || selectedStepIds.contains(hoveredEdge.toStepId()))
                            ? hoveredEdge.fromStepId()
                            : hoveredEdge.toStepId();
                    applySelection(Set.of(targetId));
                    focusStep(targetId);
                } else {
                    applySelection(Set.of());
                }
            }
        }
        clearQuickDropHover();
        guides = List.of();
        drag.release();
        context.consume();
        return EventDispatch.consumed;
    }

    private EventDispatch handleMouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY,
        BubbleContext context
    ) {
        FloatPos local = sceneToLocal(mouseX, mouseY);
        if (!handleScroll(local.x, local.y, scrollX, scrollY, isZoomModifierDown(), isShiftModifierDown())) {
            return EventDispatch.pass;
        }
        context.consume();
        return EventDispatch.consumed;
    }

    public boolean handleScroll(
        double localX,
        double localY,
        double scrollX,
        double scrollY,
        boolean zoomModifierDown,
        boolean shiftDown
    ) {
        if (Math.abs(scrollX) < 1e-6 && Math.abs(scrollY) < 1e-6) {
            return false;
        }
        boolean mac = isMacOs();
        boolean panMode = switch (scrollNavMode) {
            case trackpadPan -> !zoomModifierDown;
            case mouseZoom -> Math.abs(scrollX) > 1e-4 || shiftDown;
            case auto -> mac ? !zoomModifierDown : (Math.abs(scrollX) > 1e-4 || shiftDown);
        };
        if (panMode) {
            cancelCameraAnimation();
            if (!mac && scrollNavMode != ScrollNavigationMode.trackpadPan && Math.abs(scrollX) <= 1e-4 && shiftDown) {
                viewport.panScreen(scrollY * 32.0, 0.0);
            } else {
                viewport.panScreen(scrollX * 26.0, scrollY * 26.0);
            }
            cameraInitializedForTab = true;
            persistCameraToTabState();
            return true;
        }
        if (Math.abs(scrollY) < 1e-6) {
            return false;
        }
        DagViewport target = cameraAnimating
                ? new DagViewport(camTargetX, camTargetY, camTargetZoom)
                : new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
        double clampedDelta = Math.max(-4.0, Math.min(4.0, scrollY));
        double factor = Math.pow(DagViewport.wheelStep, clampedDelta);
        target.zoomAt(localX, localY, factor);
        animateCameraTo(target.camX(), target.camY(), target.zoom());
        return true;
    }

    private static boolean isMacOs() {
        try {
            return Minecraft.ON_OSX;
        } catch (RuntimeException | LinkageError ignored) {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        }
    }

    public void applyPinchZoom(double localX, double localY, double magnificationDelta) {
        if (!Double.isFinite(magnificationDelta) || Math.abs(magnificationDelta) < 1e-5) {
            return;
        }
        cancelCameraAnimation();
        double clamped = Math.max(-0.5, Math.min(0.5, magnificationDelta));
        double factor = Math.exp(clamped * 1.65);
        viewport.zoomAt(localX, localY, factor);
        cameraInitializedForTab = true;
        persistCameraToTabState();
    }

    private void pollMacTrackpadPinch(int mouseX, int mouseY, int canvasWidth, int canvasHeight) {
        if (!isMacOs()) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return;
            }
            double mag = MacTrackpadPinchBridge.drainMagnification(mc.getWindow().getWindow());
            if (Math.abs(mag) > 1e-5 && mouseX >= 0 && mouseX < canvasWidth && mouseY >= 0 && mouseY < canvasHeight) {
                applyPinchZoom(mouseX, mouseY, mag);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Headless tests run without an active GLFW window.
        }
    }

    private static boolean isZoomModifierDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return false;
            }
            long win = mc.getWindow().getWindow();
            return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_CONTROL)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SUPER)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SUPER)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isShiftModifierDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return false;
            }
            long win = mc.getWindow().getWindow();
            return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SHIFT);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private void handleMouseMoved(double mouseX, double mouseY) {
        if (drag.active()) {
            return;
        }
        FloatPos local = sceneToLocal(mouseX, mouseY);
        float[] world = viewport.screenToWorld((float) local.x, (float) local.y);
        PlanDagLayoutResult result = dagLayoutInternal();
        DagNodeBox node = DagHitTester.nodeAt(world[0], world[1], result.nodes());
        String nextHover = node == null ? null : node.stepId();
        if (!Objects.equals(nextHover, hoveredStepId)) {
            hoveredStepId = nextHover;
            traceFocus = "";
        }
        hoveredEdge = DagHitTester.edgeAt(
            local.x,
            local.y,
            result.edges(),
            viewport,
            DagHitTester.edgeTolerancePx,
            DagHitTester.bezierSamples,
            edgeStyle,
            layoutMode
        );
        SpatialDagHost dagHost = host;
        if (dagHost != null) {
            long now = System.currentTimeMillis();
            if (now - lastHoverPublishMs > hoverPublishIntervalMs) {
                lastHoverPublishMs = now;
                dagHost.onHoverStep(hoveredStepId);
                dagHost.onHoverEdge(
                    hoveredEdge == null ? null : hoveredEdge.fromStepId(),
                    hoveredEdge == null ? null : hoveredEdge.toStepId(),
                    hoveredEdge == null ? null : hoveredEdge.resource()
                );
            }
        }
    }

    private EventDispatch handleKeyPressed(InputContext input, BubbleContext context) {
        if (input.isKey(GLFW.GLFW_KEY_ESCAPE)) {
            if (viewSettingsPopover != null) {
                closeViewSettingsPopover();
                context.consume();
                return EventDispatch.consumed;
            }
            if (drag.escape()) {
                previewOffsets = Map.of();
                guides = List.of();
                layoutDirty = true;
                context.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        }
        SpatialDagHost dagHost = host;
        if (dagHost == null) {
            return EventDispatch.pass;
        }
        if (input.isKey(GLFW.GLFW_KEY_F)) {
            fitToView();
            context.consume();
            return EventDispatch.consumed;
        }
        if (input.isKey(GLFW.GLFW_KEY_EQUAL) || input.isKey(GLFW.GLFW_KEY_KP_ADD)) {
            DagViewport target = new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
            target.zoomAt(width() * 0.5, height() * 0.5, DagViewport.wheelStep * 1.12);
            animateCameraTo(target.camX(), target.camY(), target.zoom());
            context.consume();
            return EventDispatch.consumed;
        }
        if (input.isKey(GLFW.GLFW_KEY_MINUS) || input.isKey(GLFW.GLFW_KEY_KP_SUBTRACT)) {
            DagViewport target = new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
            target.zoomAt(width() * 0.5, height() * 0.5, 1.0 / (DagViewport.wheelStep * 1.12));
            animateCameraTo(target.camX(), target.camY(), target.zoom());
            context.consume();
            return EventDispatch.consumed;
        }
        if (input.isKey(GLFW.GLFW_KEY_0) || input.isKey(GLFW.GLFW_KEY_KP_0)) {
            DagViewport target = new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
            target.zoomAt(width() * 0.5, height() * 0.5, 1.0 / Math.max(0.01, viewport.zoom()));
            animateCameraTo(target.camX(), target.camY(), 1.0f);
            context.consume();
            return EventDispatch.consumed;
        }
        if (input.isKey(GLFW.GLFW_KEY_HOME)) {
            focusHome();
            context.consume();
            return EventDispatch.consumed;
        }
        if (input.isKey(GLFW.GLFW_KEY_L)) {
            clearManualOffsets();
            context.consume();
            return EventDispatch.consumed;
        }
        if (input.isKey(GLFW.GLFW_KEY_R)) {
            cycleLayoutMode();
            context.consume();
            return EventDispatch.consumed;
        }
        if (handleViewShortcut(input, GLFW.GLFW_KEY_1, FlowViewMode.spatialDag)
                || handleViewShortcut(input, GLFW.GLFW_KEY_2, FlowViewMode.preparationLedger)
                || handleViewShortcut(input, GLFW.GLFW_KEY_3, FlowViewMode.executionChecklist)) {
            context.consume();
            return EventDispatch.consumed;
        }
        return EventDispatch.pass;
    }

    private boolean handleViewShortcut(InputContext input, int key, FlowViewMode mode) {
        if (!input.isKey(key)) {
            return false;
        }
        showView(mode);
        return true;
    }

    //endregion

    //region tick

    @Override
    public void tick() {
        super.tick();
        frameTimeMs = System.currentTimeMillis();
        stepCameraAnimation(currentAnimNanos());
        if (width() > 0 && height() > 0) {
            minimap.adaptToCanvas(width(), height());
        }
        SpatialDagHost dagHost = host;
        if (dagHost == null) {
            return;
        }
        if (reducedMotionOverride == null) {
            reducedMotion = dagHost.isReducedMotion();
        }
        WorkbenchUiSnapshot snapshot = dagHost.snapshot();
        selectedStepIds = snapshot.selectedStepIds();
        hoveredStepId = snapshot.hoveredStepId();
        if (!signatureOf(snapshot).equals(layoutSignature)) {
            layoutDirty = true;
        }
        PlanDagLayoutResult result = dagLayoutInternal();
        minimap.theme(resolvedThemeMode()).setLayoutContent(result);
        syncLocatorRuler(result);
        actionBar.selection(selectedNodes(result));
    }

    private void syncLocatorRuler(PlanDagLayoutResult result) {
        minimap.setVisible(minimapUserVisible && width() >= 220 && height() >= 120);
        locatorRuler.setCollapsed(!locatorUserVisible);
        int topClear = embeddedInStageHeader ? 10 : (isZenMode() ? 32 : capsuleHeight + capsuleInset + 6);
        locatorRuler.syncState(
            result,
            layoutMode,
            visuals,
            resolver,
            selectedStepIds,
            criticalStepIds,
            inlineBeacons,
            width(),
            height(),
            10,
            topClear
        );
    }

    private boolean isZenMode() {
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        return shell != null && shell.zenMode();
    }

    private WorkbenchThemeMode resolvedThemeMode() {
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        return shell != null ? shell.theme() : themeMode;
    }

    private List<DagNodeBox> selectedNodes(PlanDagLayoutResult result) {
        List<DagNodeBox> nodes = new ArrayList<>();
        if (selectedStepIds.isEmpty()) {
            return nodes;
        }
        for (DagNodeBox node : result.nodes()) {
            if (selectedStepIds.contains(node.stepId())) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    //endregion

    //region render

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        stepCameraAnimation(currentAnimNanos());
        PlanDagLayoutResult result = dagLayoutInternal();
        CirrusPalette palette = palette();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        pollMacTrackpadPinch(mouseX, mouseY, width, height);
        autoFitNewPlan(result, width, height);
        syncLocatorRuler(result);
        canvas.withClip(0, 0, width, height, () -> {
            canvas.fill(0, 0, width, height, palette.canvasVoid());
            WorkbenchPaint.dotGrid(canvas, width, height, viewport.zoom(), palette);
            if (!result.isEmpty()) {
                float zoom = viewport.zoom();
                float camX = viewport.camX();
                float camY = viewport.camY();
                DagLodLevel lod = DagLodLevel.forZoom(zoom);
                Set<String> moved = previewOffsets.isEmpty() ? Set.of() : previewOffsets.keySet();
                canvas.pushTransform();
                canvas.translate(-camX * zoom, -camY * zoom);
                canvas.scale(zoom);
                try {
                    drawSwimlanes(canvas, result, palette, lod);
                    drawLocatorHighlights(canvas, result, palette);
                    drawEdges(canvas, result, palette, lod, moved);
                    drawNodes(canvas, result, palette, lod);
                    drawPreviews(canvas, result, palette);
                    for (DagSelectionMath.GuideLine guide : guides) {
                        drawDashed(canvas, guide, palette);
                    }
                    drawBeacons(canvas, result, palette);
                } finally {
                    canvas.popTransform();
                }
                drawMarquee(canvas, palette);
                drawGuidedCheckpointBanner(canvas, palette, width, height, mouseX, mouseY);
                drawSelectionPreviewBar(canvas, result, palette, width, height, mouseX, mouseY);
                drawLocatorCursorPreview(canvas, palette, width, height);
            } else {
                drawEmptyStateCard(canvas, palette, width, height, mouseX, mouseY);
            }
            if (!embeddedInStageHeader) {
                drawCapsule(canvas, palette, width, height, mouseX, mouseY);
            }
        });
    }

    public Rect emptyStateBounds() {
        return emptyStateBounds(width(), height());
    }

    public Rect emptyStateBounds(int canvasWidth, int canvasHeight) {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return new Rect(0, 0, 0, 0);
        }
        int cardW = Math.min(Math.max(140, canvasWidth - 32), 288);
        int cardH = Math.min(86, Math.max(54, canvasHeight - capsuleHeight - capsuleInset - 24));
        int x = Math.max(48, (canvasWidth - cardW) / 2);
        if (x + cardW > canvasWidth - 8) {
            x = Math.max(8, (canvasWidth - cardW) / 2);
        }
        int topStart = Math.max(48, capsuleHeight + capsuleInset + 12);
        int availableHeight = Math.max(cardH, canvasHeight - topStart - 12);
        int y = topStart + Math.max(0, (availableHeight - cardH) / 2);
        if (y + cardH > canvasHeight - 8) {
            y = Math.max(4, canvasHeight - cardH - 8);
        }
        return new Rect(x, y, Math.min(cardW, Math.max(0, canvasWidth - x - 4)), cardH);
    }

    public Rect emptyStateCtaBounds(int canvasWidth, int canvasHeight) {
        Rect card = emptyStateBounds(canvasWidth, canvasHeight);
        if (card.width() < 100 || card.height() < 70) {
            return new Rect(0, 0, 0, 0);
        }
        int btnW = Math.min(184, card.width() - 28);
        int btnH = 20;
        return new Rect(card.x() + (card.width() - btnW) / 2, card.bottom() - btnH - 8, btnW, btnH);
    }

    private boolean clickEmptyStateCta(double localX, double localY) {
        Rect cta = emptyStateCtaBounds(width(), height());
        if (cta.width() <= 0 || cta.height() <= 0) {
            return false;
        }
        if (!ChromeText.hit(localX, localY, cta.x(), cta.y(), cta.width(), cta.height())) {
            return false;
        }
        if (onOpenTargetPicker != null) {
            onOpenTargetPicker.run();
            return true;
        }
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        if (shell instanceof WorkbenchShellWidget shellWidget) {
            shellWidget.openTargetCatalogPicker();
            return true;
        }
        return false;
    }

    private void drawEmptyStateCard(
        SceneCanvas canvas,
        CirrusPalette palette,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        Rect card = emptyStateBounds(width, height);
        if (card.width() <= 0 || card.height() <= 0) {
            return;
        }
        canvas.shadow(card.x(), card.y(), card.width(), card.height(), 3f, 3f, 6f, palette.dropShadow());
        OreUiPaint.raisedCard(canvas, card.x(), card.y(), card.width(), card.height(), palette, false, false);

        int iconSize = 14;
        int iconX = card.x() + (card.width() - iconSize) / 2;
        int iconY = card.y() + 8;
        var targetIcon = CirrusThemeIcons.resolve(CirrusTextures.Icon.calculatorTarget, themeMode);
        canvas.texture(targetIcon, iconX, iconY, iconSize, iconSize);

        Component title = emptyStateTitle();
        int maxTextWidth = Math.max(0, card.width() - 16);
        Component fittedTitle = ChromeText.fit(canvas, title, maxTextWidth);
        int titleX = card.x() + Math.max(8, (card.width() - ChromeText.width(canvas, fittedTitle)) / 2);
        int titleY = ChromeText.centeredY(iconY + iconSize + 3, 13, canvas);
        ChromeText.draw(canvas, fittedTitle, titleX, titleY, palette.textPrimary());

        Component hint = CirrusLangs.Workbench.Topbar.cmdPaletteHint.get();
        Component fittedHint = ChromeText.fit(canvas, hint, maxTextWidth);
        int hintX = card.x() + Math.max(8, (card.width() - ChromeText.width(canvas, fittedHint)) / 2);
        int hintY = ChromeText.centeredY(iconY + iconSize + 17, 13, canvas);
        ChromeText.draw(canvas, fittedHint, hintX, hintY, palette.textMuted());

        Rect cta = emptyStateCtaBounds(width, height);
        if (cta.width() > 0 && cta.height() > 0) {
            boolean hover = ChromeText.hit(mouseX, mouseY, cta.x(), cta.y(), cta.width(), cta.height());
            OreUiPaint.raisedButton(
                canvas,
                cta.x(),
                cta.y(),
                cta.width(),
                cta.height(),
                palette,
                false,
                hover,
                true,
                false
            );
            Component ctaLabel = Component.literal("Choose Target & Recipe");
            Component fittedCta = ChromeText.fit(canvas, ctaLabel, cta.width() - 12);
            int cx = cta.x() + Math.max(6, (cta.width() - ChromeText.width(canvas, fittedCta)) / 2);
            ChromeText.draw(
                canvas,
                fittedCta,
                cx,
                ChromeText.centeredButtonY(cta.y(), cta.height(), canvas),
                palette.textOnAccent()
            );
        }
    }

    public Rect guidedCheckpointBounds(int canvasWidth, int canvasHeight) {
        if (!shouldShowGuidedCheckpoint() || canvasWidth < 220 || canvasHeight < 120) {
            return new Rect(0, 0, 0, 0);
        }
        int toastH = 26;
        int toastW = Math.min(244, Math.max(176, canvasWidth - 28));
        int x = Math.max(10, canvasWidth - toastW - 10);
        int bottomReserve = layoutMode.isVertical() ? 10 : DagLocatorRulerWidget.horizontalHeight + 16;
        int y = Math.max(10, canvasHeight - bottomReserve - toastH);
        return new Rect(x, y, toastW, toastH);
    }

    private boolean shouldShowGuidedCheckpoint() {
        SpatialDagHost dagHost = host;
        if (dagHost == null) {
            return false;
        }
        var result = dagHost.snapshot().result();
        if (result == null || result.decisionPrompts().isEmpty()) {
            return false;
        }
        SessionTabState state = resolveActiveTabState();
        if (state != null && (!state.guidedPauseEnabled() || state.guidedPauseDismissed())) {
            return false;
        }
        return true;
    }

    private boolean clickGuidedCheckpointBanner(double localX, double localY) {
        Rect banner = guidedCheckpointBounds(width(), height());
        if (banner.width() <= 0 || banner.height() <= 0) {
            return false;
        }
        if (!ChromeText.hit(localX, localY, banner.x(), banner.y(), banner.width(), banner.height())) {
            return false;
        }
        int skipW = 42;
        if (localX >= banner.right() - skipW - 4) {
            SessionTabState state = resolveActiveTabState();
            if (state != null) {
                state.setGuidedPauseDismissed(true);
            }
            return true;
        }
        if (onOpenGuidedDecisions != null) {
            onOpenGuidedDecisions.run();
            return true;
        }
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        if (shell instanceof WorkbenchShellWidget shellWidget) {
            shellWidget.openGuidedDecisionWorkbench();
            return true;
        }
        return false;
    }

    private void drawGuidedCheckpointBanner(
        SceneCanvas canvas,
        CirrusPalette palette,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        Rect banner = guidedCheckpointBounds(width, height);
        if (banner.width() <= 0 || banner.height() <= 0) {
            return;
        }
        SpatialDagHost dagHost = host;
        int count = dagHost == null || dagHost.snapshot().result() == null
                ? 0
                : dagHost.snapshot().result().decisionPrompts().size();
        if (count <= 0) {
            return;
        }
        canvas.shadow(banner.x(), banner.y(), banner.width(), banner.height(), 2f, 3f, 8f, palette.dropShadow());
        OreUiPaint.popover(canvas, banner.x(), banner.y(), banner.width(), banner.height(), palette);
        canvas.strokeRect(banner.x(), banner.y(), banner.width(), banner.height(), palette.costH(), 1);
        canvas.fill(banner.x() + 6, banner.y() + (banner.height() - 6) / 2, 6, 6, palette.costH());
        int skipW = 42;
        int skipX = banner.right() - skipW - 4;
        int skipY = banner.y() + 4;
        int skipH = Math.max(14, banner.height() - 8);
        boolean skipHover = ChromeText.hit(mouseX, mouseY, skipX, skipY, skipW, skipH);
        OreUiPaint.raisedButton(canvas, skipX, skipY, skipW, skipH, palette, false, skipHover, false, false);
        Component skipComp = Component.literal("Skip");
        ChromeText.draw(
            canvas,
            skipComp,
            skipX + Math.max(3, (skipW - ChromeText.width(canvas, skipComp)) / 2),
            ChromeText.centeredButtonY(skipY, skipH, canvas),
            skipHover ? palette.textPrimary() : palette.textSecondary()
        );
        int reviewW = 48;
        int reviewX = skipX - reviewW - 3;
        boolean reviewHover = ChromeText
                .hit(mouseX, mouseY, banner.x(), banner.y(), reviewX + reviewW - banner.x(), banner.height());
        if (reviewX > banner.x() + 56) {
            OreUiPaint.raisedButton(canvas, reviewX, skipY, reviewW, skipH, palette, false, reviewHover, true, false);
            Component revComp = Component.literal("Guide");
            ChromeText.draw(
                canvas,
                revComp,
                reviewX + Math.max(3, (reviewW - ChromeText.width(canvas, revComp)) / 2),
                ChromeText.centeredButtonY(skipY, skipH, canvas),
                palette.textOnAccent()
            );
        }
        int labelRight = reviewX > banner.x() + 56 ? reviewX - 5 : skipX - 5;
        String text = count == 1 ? "1 branch choice" : count + " branch choices";
        ChromeText.drawFitted(
            canvas,
            Component.literal(text),
            banner.x() + 16,
            ChromeText.centeredY(banner.y(), banner.height(), canvas),
            Math.max(16, labelRight - (banner.x() + 16)),
            palette.costH()
        );
    }

    private Component emptyStateTitle() {
        SpatialDagHost dagHost = host;
        if (dagHost == null) {
            return CirrusLangs.Workbench.Flow.bannerNoTarget.get();
        }
        WorkbenchUiSnapshot snapshot = dagHost.snapshot();
        WorkbenchPhase phase = snapshot.phase();
        if (phase == null) {
            return CirrusLangs.Workbench.Flow.bannerNoTarget.get();
        }
        return switch (phase) {
            case planning -> CirrusLangs.Workbench.Flow.bannerPlanning.get();
            case conflictInfeasible -> CirrusLangs.Workbench.Flow.bannerConflict.get();
            case unreachable -> CirrusLangs.Workbench.Flow.bannerUnreachable.get();
            case readyPareto,
                    readyBudgetHit ->
                snapshot.targets().isEmpty()
                        ? CirrusLangs.Workbench.Flow.bannerNoTarget.get()
                        : CirrusLangs.Workbench.Flow.bannerSatisfiedByStock.get();
            default -> CirrusLangs.Workbench.Flow.bannerNoTarget.get();
        };
    }

    private void drawSwimlanes(SceneCanvas canvas, PlanDagLayoutResult result, CirrusPalette palette, DagLodLevel lod) {
        if (result.swimlanes().isEmpty()) {
            return;
        }
        int bandLeft = Math.round(result.contentMinX()) - 40;
        int bandWidth = Math.round(result.contentWidth()) + 240;
        int index = 0;
        for (SwimlaneBand band : result.swimlanes()) {
            int y = Math.round(band.y());
            int height = Math.round(band.height());
            canvas.fill(
                bandLeft,
                y,
                bandWidth,
                height,
                StepNodeCardRenderer
                        .withAlpha((index % 2 == 0) ? palette.cardSurface() : palette.recessedSurface(), 0.22f)
            );
            canvas.fill(bandLeft, y, bandWidth, 1, StepNodeCardRenderer.withAlpha(palette.hairlineBorder(), 0.55f));
            String title = Component.translatable(band.titleKey()).getString();
            canvas.drawString(
                Component.literal(StepNodeCardRenderer.ellipsize(canvas.font(), title, 150)),
                bandLeft + 6,
                y + 6,
                palette.textSecondary()
            );
            if (lod != DagLodLevel.macro) {
                String stats = "Work " + WorkbenchFormatters.formatSeconds(band.totalH()) + " · Wait "
                        + WorkbenchFormatters.formatSeconds(band.totalP());
                canvas.drawString(stats, bandLeft + 6, y + 17, palette.textMuted());
            }
            index++;
        }
    }

    private void drawEdges(
        SceneCanvas canvas,
        PlanDagLayoutResult result,
        CirrusPalette palette,
        DagLodLevel lod,
        Set<String> moved
    ) {
        String focusId = activeTraceFocusId();
        boolean tracingActive = focusId != null || hoveredEdge != null;
        DagSelectionMath.TraceSets trace = traceSets(result);
        Map<DagEdgeRoute, Float> channelRatios = edgeStyle == DagEdgeStyle.orthogonal
                ? DagHitTester.orthogonalChannelRatios(result.edges(), layoutMode)
                : Map.of();
        // Pass 0 draws background/dimmed wires first; Pass 1 draws active traced/hovered wires cleanly on top.
        for (int pass = 0; pass < 2; pass++) {
            for (DagEdgeRoute edge : result.edges()) {
                boolean hovered = hoveredEdge != null && sameEdge(hoveredEdge, edge);
                boolean upstream = tracingActive
                        && trace.upstream().contains(edge.fromStepId())
                        && trace.upstream().contains(edge.toStepId());
                boolean downstream = tracingActive
                        && trace.downstream().contains(edge.fromStepId())
                        && trace.downstream().contains(edge.toStepId());
                boolean onEdgeFocus = hoveredEdge != null
                        && (hovered
                                || (trace.upstream().contains(edge.fromStepId())
                                        && trace.upstream().contains(edge.toStepId()))
                                || (trace.downstream().contains(edge.fromStepId())
                                        && trace.downstream().contains(edge.toStepId())));
                boolean highlighted = hovered || upstream || downstream || onEdgeFocus;
                if ((pass == 0 && highlighted) || (pass == 1 && !highlighted)) {
                    continue;
                }
                DagEdgeRoute route = edge;
                if (!moved.isEmpty()) {
                    FloatPos delta = visualDragDelta(edge.fromStepId());
                    if (delta == null) {
                        delta = visualDragDelta(edge.toStepId());
                    }
                    if (delta != null) {
                        route = DagEdgeRenderer.shifted(edge, moved, (float) delta.x, (float) delta.y, layoutMode);
                    }
                }
                boolean dimmed = tracingActive && !highlighted;
                boolean critical = edge.criticalPath()
                        || (criticalStepIds.contains(edge.fromStepId()) && criticalStepIds.contains(edge.toStepId()));
                DagEdgeRenderer.drawEdge(
                    canvas,
                    route,
                    palette,
                    lod,
                    new DagEdgeRenderer.EdgeState(hovered, upstream, downstream, critical, dimmed),
                    frameTimeMs,
                    reducedMotion,
                    0.0f,
                    0.0f,
                    edgeStyle,
                    layoutMode,
                    channelRatios.getOrDefault(edge, 0.5f)
                );
            }
        }
    }

    private @Nullable FloatPos visualDragDelta(String stepId) {
        FloatPos total = previewOffsets.get(stepId);
        if (total == null) {
            return null;
        }
        FloatPos base = drag.originalOffsets().get(stepId);
        if (base == null) {
            return total;
        }
        return new FloatPos(total.x - base.x, total.y - base.y);
    }

    private static boolean sameEdge(DagEdgeRoute left, DagEdgeRoute right) {
        return left.fromStepId().equals(right.fromStepId())
                && left.toStepId().equals(right.toStepId())
                && left.kind() == right.kind()
                && Objects.equals(left.resource(), right.resource());
    }

    private void drawNodes(SceneCanvas canvas, PlanDagLayoutResult result, CirrusPalette palette, DagLodLevel lod) {
        String focusId = activeTraceFocusId();
        boolean tracingActive = focusId != null || hoveredEdge != null;
        DagSelectionMath.TraceSets trace = traceSets(result);
        for (DagNodeBox node : result.nodes()) {
            FloatPos delta = visualDragDelta(node.stepId());
            DagNodeBox box = delta == null ? node : shifted(node, delta);
            StepNodeCardRenderer.StepVisuals data = visuals
                    .getOrDefault(node.stepId(), StepNodeCardRenderer.StepVisuals.empty());
            boolean hovered = Objects.equals(hoveredStepId, node.stepId());
            boolean selected = selectedStepIds.contains(node.stepId());
            boolean traced = tracingActive
                    && (trace.upstream().contains(node.stepId())
                            || trace.downstream().contains(node.stepId())
                            || (hoveredEdge != null
                                    && (hoveredEdge.fromStepId().equals(node.stepId())
                                            || hoveredEdge.toStepId().equals(node.stepId()))));
            boolean previewed = previewStepIds != null && previewStepIds.contains(node.stepId());
            boolean dimmed = tracingActive && !hovered && !selected && !traced && !previewed;
            StepNodeCardRenderer.CardState state = new StepNodeCardRenderer.CardState(
                hovered,
                selected,
                dimmed,
                tracingActive && trace.upstream().contains(node.stepId()),
                tracingActive && trace.downstream().contains(node.stepId())
            );
            WorkbenchThemeMode activeTheme = resolvedThemeMode();
            StepNodeCardRenderer.draw(canvas, box, lod, state, data, resolver, palette, activeTheme, 1.0f);
            if (Objects.equals(dropHighlightStepId, node.stepId())) {
                canvas.strokeRect(
                    Math.round(box.x()) - 2,
                    Math.round(box.y()) - 2,
                    Math.round(box.width()) + 4,
                    Math.round(box.height()) + 4,
                    palette.costS(),
                    2
                );
            }
            if (node.diffStatus() == DagNodeBox.DiffStatus.removed) {
                canvas.fill(
                    Math.round(box.x()),
                    Math.round(box.y()),
                    Math.round(box.width()),
                    Math.round(box.height()),
                    StepNodeCardRenderer.withAlpha(palette.diffRemoved(), 0.5f)
                );
            }
            if (node.expandedLoop()) {
                for (DagNodeBox inner : node.innerLoopNodes()) {
                    DagNodeBox innerBox = delta == null ? inner : shifted(inner, delta);
                    StepNodeCardRenderer.draw(
                        canvas,
                        innerBox,
                        lod,
                        StepNodeCardRenderer.CardState.idle,
                        visuals.getOrDefault(inner.stepId(), StepNodeCardRenderer.StepVisuals.empty()),
                        resolver,
                        palette,
                        activeTheme,
                        0.9f
                    );
                }
            }
        }
    }

    private static DagNodeBox shifted(DagNodeBox node, FloatPos offset) {
        List<DagNodeBox> inner = new ArrayList<>();
        for (DagNodeBox child : node.innerLoopNodes()) {
            inner.add(shifted(child, offset));
        }
        return new DagNodeBox(
            node.stepId(),
            node.step(),
            node.layer(),
            node.laneIndex(),
            node.swimlaneId(),
            node.x() + (float) offset.x,
            node.y() + (float) offset.y,
            node.width(),
            node.height(),
            node.expandedLoop(),
            inner,
            node.diffStatus()
        );
    }

    private void drawPreviews(SceneCanvas canvas, PlanDagLayoutResult result, CirrusPalette palette) {
        if (previewStepIds == null || previewKindValue == null || previewStepIds.isEmpty()) {
            return;
        }
        for (DagNodeBox node : result.nodes()) {
            FloatPos delta = visualDragDelta(node.stepId());
            DagNodeBox box = delta == null ? node : shifted(node, delta);
            if (previewStepIds.contains(node.stepId())) {
                drawPreviewOutline(canvas, box, previewKindValue, palette);
            }
            if (node.expandedLoop()) {
                for (DagNodeBox inner : node.innerLoopNodes()) {
                    if (!previewStepIds.contains(inner.stepId())) {
                        continue;
                    }
                    DagNodeBox innerBox = delta == null ? inner : shifted(inner, delta);
                    drawPreviewOutline(canvas, innerBox, previewKindValue, palette);
                }
            }
        }
    }

    private static void drawPreviewOutline(
        SceneCanvas canvas,
        DagNodeBox box,
        PreviewKind kind,
        CirrusPalette palette
    ) {
        int x = Math.round(box.x()) - 2;
        int y = Math.round(box.y()) - 2;
        int width = Math.round(box.width()) + 4;
        int height = Math.round(box.height()) + 4;
        if (width <= 0 || height <= 0) {
            return;
        }
        switch (kind) {
            case banRecipe -> strokeDashedRect(canvas, x, y, width, height, palette.ruleHardBan());
            case ownMachine -> {
                canvas.strokeRect(x, y, width, height, palette.costS(), 2);
                int midY = y + height / 2;
                canvas.line(x + 3, midY, x + width - 3, midY, 1.5f, palette.costS());
            }
            case buildMachine -> {
                int halo = StepNodeCardRenderer.withAlpha(palette.ruleHardPin(), 0.22f);
                canvas.strokeRect(x - 4, y - 4, width + 8, height + 8, halo, 3);
                canvas.strokeRect(x, y, width, height, palette.ruleHardPin(), 2);
            }
            case pickVariant, treatFree -> strokeDashedRect(canvas, x, y, width, height, palette.advisoryDiscouraged());
        }
    }

    private static void strokeDashedRect(SceneCanvas canvas, int x, int y, int width, int height, int color) {
        int dash = 4;
        int gap = 3;
        for (int cursor = 0; cursor < width; cursor += dash + gap) {
            int length = Math.min(dash, width - cursor);
            canvas.fill(x + cursor, y, length, 1, color);
            canvas.fill(x + cursor, y + height - 1, length, 1, color);
        }
        for (int cursor = 0; cursor < height; cursor += dash + gap) {
            int length = Math.min(dash, height - cursor);
            canvas.fill(x, y + cursor, 1, length, color);
            canvas.fill(x + width - 1, y + cursor, 1, length, color);
        }
    }

    private static void drawDashed(SceneCanvas canvas, DagSelectionMath.GuideLine guide, CirrusPalette palette) {
        int lineColor = StepNodeCardRenderer.withAlpha(palette.focusRing(), 0.67f);
        double start = guide.start() - 12.0f;
        double end = guide.end() + 12.0f;
        for (double cursor = start; cursor < end; cursor += guideDash * 2.0f) {
            float segmentEnd = (float) Math.min(end, cursor + guideDash);
            if (guide.vertical()) {
                canvas.line(guide.position(), (float) cursor, guide.position(), segmentEnd, guideLineWidth, lineColor);
            } else {
                canvas.line((float) cursor, guide.position(), segmentEnd, guide.position(), guideLineWidth, lineColor);
            }
        }
    }

    private void drawBeacons(SceneCanvas canvas, PlanDagLayoutResult result, CirrusPalette palette) {
        if (inlineBeacons.isEmpty()) {
            return;
        }
        double alpha = WorkbenchPaint.pulseAlpha(frameTimeMs, reducedMotion);
        int color = StepNodeCardRenderer.withAlpha(palette.advisoryDiscouraged(), (float) alpha);
        for (DagNodeBox node : result.nodes()) {
            if (!inlineBeacons.containsKey(node.stepId())) {
                continue;
            }
            DagHitTester.RectF bounds = beaconBounds(node);
            canvas.circle(bounds.centerX(), bounds.centerY(), 4.0f, color);
            canvas.circle(bounds.centerX(), bounds.centerY(), 1.6f, palette.textOnDark());
        }
    }

    private void drawMarquee(SceneCanvas canvas, CirrusPalette palette) {
        if (drag.mode() != DagDragState.Mode.marquee || !drag.dragging()) {
            return;
        }
        DagHitTester.RectF rect = drag.marqueeScreenRect();
        canvas.fill(
            Math.round(rect.x()),
            Math.round(rect.y()),
            Math.round(rect.width()),
            Math.round(rect.height()),
            StepNodeCardRenderer.withAlpha(palette.focusRing(), 0.14f)
        );
        canvas.strokeRect(
            Math.round(rect.x()),
            Math.round(rect.y()),
            Math.round(rect.width()),
            Math.round(rect.height()),
            palette.focusRing(),
            1
        );
    }

    public Rect capsuleBounds() {
        return capsuleLayout(safeMinecraftFont(), width(), height()).box();
    }

    public Rect capsuleBounds(int canvasWidth, int canvasHeight) {
        return capsuleLayout(safeMinecraftFont(), canvasWidth, canvasHeight).box();
    }

    private void drawCapsule(SceneCanvas canvas, CirrusPalette palette, int width, int height, int mouseX, int mouseY) {
        CapsuleLayout capsule = capsuleLayout(ChromeText.font(canvas), width, height);
        Rect box = capsule.box();
        if (capsule.chips().isEmpty() || box.width() <= 0 || box.height() <= 0) {
            return;
        }
        OreUiPaint.popover(canvas, box.x(), box.y(), box.width(), box.height(), palette);
        for (int dividerX : capsule.dividers()) {
            canvas.fill(dividerX, box.y() + 4, 1, Math.max(1, box.height() - 8), OreUiPaint.cardDivider(palette));
        }
        for (CapsuleChip chip : capsule.chips()) {
            int chipX = Math.round(chip.bounds().x());
            int chipY = Math.round(chip.bounds().y());
            int chipWidth = Math.round(chip.bounds().width());
            int chipHeight = Math.round(chip.bounds().height());
            boolean hovered = mouseX >= chipX
                    && mouseX < chipX + chipWidth
                    && mouseY >= chipY
                    && mouseY < chipY + chipHeight;
            boolean activeChip = chip.active()
                    || (chip.action() == CapsuleAction.viewMenu && viewSettingsPopover != null);
            if (activeChip) {
                canvas.fill(chipX, chipY, chipWidth, chipHeight, palette.cardSurfaceSelected());
                canvas.strokeRect(chipX, chipY, chipWidth, chipHeight, palette.focusRing(), 1);
            } else if (hovered && chip.action().interactive()) {
                canvas.fill(chipX, chipY, chipWidth, chipHeight, palette.cardSurfaceHover());
                canvas.strokeRect(chipX, chipY, chipWidth, chipHeight, OreUiPaint.cardDivider(palette), 1);
            }
            int innerPad = Math.min(capsule.chipPad(), Math.max(1, chipWidth / 6));
            int caretReserve = chip.action() == CapsuleAction.viewMenu && chipWidth >= 28 ? 10 : 0;
            int avail = Math.max(0, chipWidth - innerPad * 2 - caretReserve);
            Component fitted = ChromeText.fit(canvas, chip.label(), avail);
            int contentW = ChromeText.width(canvas, fitted) + caretReserve;
            int textX = chipX + Math.max(innerPad, (chipWidth - contentW) / 2);
            int textInk = activeChip
                    ? palette.textPrimary()
                    : hovered ? palette.textPrimary() : palette.textSecondary();
            ChromeText.draw(canvas, fitted, textX, ChromeText.centeredY(chipY, chipHeight, canvas), textInk);
            if (caretReserve > 0) {
                ChromeButtons.caretDown(
                    canvas,
                    textX + ChromeText.width(canvas, fitted) + 3,
                    chipY + Math.max(2, (chipHeight - 4) / 2),
                    activeChip ? palette.focusRing() : textInk
                );
            }
        }
    }

    /**
     * Co-Pilot rehearsal kinds rendered as outline-only overlays on the canvas.
     */
    public enum PreviewKind {
        banRecipe, ownMachine, buildMachine, pickVariant, treatFree
    }

    public enum CapsuleAction {
        viewDag(true), viewLedger(true), viewChecklist(true), layoutLr(true), layoutTb(true), layoutRl(true), layoutBt(
            true
        ), layoutSwimlane(true), edgeOrthogonal(
            true
        ), edgeStraight(true), edgeCurved(true), fit(true), zoomReadout(false), autoLayout(true), viewMenu(true);

        private final boolean interactive;

        CapsuleAction(boolean interactive) {
            this.interactive = interactive;
        }

        public boolean interactive() {
            return interactive;
        }
    }

    public record CapsuleChip(DagHitTester.RectF bounds, CapsuleAction action, Component label, boolean active) {}

    private record CapsuleSpec(CapsuleAction action, Component label, boolean active, int group) {}

    private record CapsuleLayout(Rect box, List<CapsuleChip> chips, List<Integer> dividers, int chipPad) {}

    private void autoFitNewPlan(PlanDagLayoutResult result, int canvasWidth, int canvasHeight) {
        if (result.isEmpty()) {
            autoFittedPlanId = null;
            return;
        }
        boolean layoutChanged = lastFittedLayoutMode != null && lastFittedLayoutMode != layoutMode;
        if (cameraInitializedForTab && !layoutChanged) {
            return;
        }
        SpatialDagHost dagHost = host;
        String planId = dagHost == null ? "static" : dagHost.snapshot().selectedPlanId();
        if (planId == null || planId.isBlank()) {
            planId = "plan-" + result.nodes().size();
        }
        String fitKey = planId + "|" + layoutMode.name() + "|" + (width() / 8) + "x" + (height() / 8);
        if (fitKey.equals(autoFittedPlanId) && !layoutChanged) {
            return;
        }
        autoFittedPlanId = fitKey;
        lastFittedLayoutMode = layoutMode;
        boolean vertical = layoutMode.isVertical();
        int topReserve = embeddedInStageHeader ? 12 : capsuleHeight + capsuleInset + 10;
        int leftReserve = vertical
                ? (locatorUserVisible
                        ? DagLocatorRulerWidget.verticalWidth + 18
                        : DagLocatorRulerWidget.collapsedVerticalWidth + 14)
                : 0;
        int rightReserve = vertical && minimap.visible() ? minimap.width() + 18 : 0;
        int bottomReserve = vertical
                ? 12
                : (locatorUserVisible
                        ? DagLocatorRulerWidget.horizontalHeight + 16
                        : DagLocatorRulerWidget.collapsedHorizontalHeight + 14);
        int usableWidth = Math.max(60, canvasWidth - leftReserve - rightReserve);
        int usableHeight = Math.max(40, canvasHeight - topReserve - bottomReserve);
        viewport.fit(
            result.contentMinX(),
            result.contentMinY(),
            result.contentMaxX(),
            result.contentMaxY(),
            usableWidth,
            usableHeight,
            DagViewport.defaultPadding * 1.5f
        );
        if (viewport.zoom() > 1.0f) {
            viewport.setZoom(1.0f);
        }
        float zoom = viewport.zoom();
        float contentW = result.contentMaxX() - result.contentMinX();
        float contentH = result.contentMaxY() - result.contentMinY();
        float targetScreenLeft = leftReserve + Math.max(0f, (usableWidth - contentW * zoom) * 0.5f);
        float targetScreenTop = topReserve + Math.max(0f, (usableHeight - contentH * zoom) * 0.5f);
        viewport.setCamera(
            result.contentMinX() - targetScreenLeft / zoom,
            result.contentMinY() - targetScreenTop / zoom
        );
        cameraInitializedForTab = true;
        persistCameraToTabState();
    }

    //region locator highlights, selection preview & rich tooltips

    private void drawLocatorHighlights(SceneCanvas canvas, PlanDagLayoutResult result, CirrusPalette palette) {
        Set<String> highlighted = locatorRuler.hoveredHighlightStepIds();
        if (highlighted.isEmpty()) {
            return;
        }
        float pulse = OreUiMotion.pulse01(OreUiMotion.nowMs(), 1300L, effectiveReducedMotion());
        int haloFill = OreUiPaint
                .withAlpha(palette.focusRing(), (OreUiPaint.isDark(palette) ? 0.11f : 0.09f) + 0.08f * pulse);
        int haloBorder = OreUiPaint.withAlpha(palette.focusRing(), 0.56f + 0.34f * pulse);
        for (DagNodeBox node : result.nodes()) {
            if (!highlighted.contains(node.stepId())) {
                continue;
            }
            int hx = Math.round(node.x()) - 5;
            int hy = Math.round(node.y()) - 5;
            int hw = Math.round(node.width()) + 10;
            int hh = Math.round(node.height()) + 10;
            canvas.fill(hx, hy, hw, hh, haloFill);
            canvas.strokeRect(hx, hy, hw, hh, haloBorder, 1);
        }
    }

    /**
     * Renders an axis-aligned floating cursor neighborhood preview strip beside the hovered locator stage/cursor.
     */
    private void drawLocatorCursorPreview(
        SceneCanvas canvas,
        CirrusPalette palette,
        int canvasWidth,
        int canvasHeight
    ) {
        Integer hoveredIdx = locatorRuler.hoveredStageIndex();
        if (hoveredIdx == null || !locatorRuler.visible()) {
            cursorPreviewShownAtMs = 0L;
            previewAnchorX.reset();
            previewAnchorY.reset();
            return;
        }
        List<DagLocatorRulerWidget.StageCell> cells = locatorRuler.cachedStageCells();
        if (cells.isEmpty() || hoveredIdx < 0 || hoveredIdx >= cells.size()) {
            cursorPreviewShownAtMs = 0L;
            return;
        }
        boolean vertical = locatorRuler.isVertical();
        long nowMs = OreUiMotion.nowMs();
        if (cursorPreviewShownAtMs == 0L || cursorPreviewWasVertical != vertical) {
            cursorPreviewShownAtMs = nowMs;
            cursorPreviewWasVertical = vertical;
            previewAnchorX.reset();
            previewAnchorY.reset();
        }
        float appear = OreUiMotion.cubicOut(nowMs - cursorPreviewShownAtMs, 115L, effectiveReducedMotion());
        int start = Math.max(0, hoveredIdx - 1);
        int end = Math.min(cells.size() - 1, hoveredIdx + 1);
        double planTime = locatorRuler.totalPlanTime();

        if (vertical) {
            float slideX = -(1f - appear) * 5f;
            canvas.pushTransform();
            canvas.translate(slideX, 0f);
            try {
                drawVerticalCursorNeighborhood(
                    canvas,
                    palette,
                    cells,
                    hoveredIdx,
                    start,
                    end,
                    planTime,
                    canvasWidth,
                    canvasHeight
                );
            } finally {
                canvas.popTransform();
            }
        } else {
            float slideY = (1f - appear) * 5f;
            canvas.pushTransform();
            canvas.translate(0f, slideY);
            try {
                drawHorizontalCursorNeighborhood(
                    canvas,
                    palette,
                    cells,
                    hoveredIdx,
                    start,
                    end,
                    planTime,
                    canvasWidth,
                    canvasHeight
                );
            } finally {
                canvas.popTransform();
            }
        }
    }

    private void drawHorizontalCursorNeighborhood(
        SceneCanvas canvas,
        CirrusPalette palette,
        List<DagLocatorRulerWidget.StageCell> cells,
        int hoveredIdx,
        int start,
        int end,
        double planTime,
        int canvasWidth,
        int canvasHeight
    ) {
        int count = end - start + 1;
        int focusedW = 156;
        int neighborW = 110;
        int arrowW = 14;
        int pad = 6;
        int totalW = pad * 2 + (count - 1) * arrowW;
        for (int i = start; i <= end; i++) {
            totalW += i == hoveredIdx ? focusedW : neighborW;
        }
        int maxRight = Math.max(180, canvasWidth - 10);
        int maxAllowed = Math.max(150, maxRight - 8);
        if (totalW > maxAllowed) {
            neighborW = Math.max(78, (maxAllowed - pad * 2 - focusedW - (count - 1) * arrowW) / Math.max(1, count - 1));
            totalW = pad * 2 + (count - 1) * arrowW;
            for (int i = start; i <= end; i++) {
                totalW += i == hoveredIdx ? focusedW : neighborW;
            }
        }
        int stripH = 52;
        DagLocatorRulerWidget.StageCell focusCell = cells.get(hoveredIdx);
        int targetAnchorX = locatorRuler.appliedX() + focusCell.x() + focusCell.width() / 2;
        int anchorCenterX = Math
                .round(previewAnchorX.update(targetAnchorX, 20f, System.nanoTime(), effectiveReducedMotion()));
        int stripX = Math.max(8, Math.min(Math.max(8, maxRight - totalW), anchorCenterX - totalW / 2));
        int stripY = Math.max(32, locatorRuler.appliedY() - stripH - 6);

        OreUiPaint.popover(canvas, stripX, stripY, totalW, stripH, palette);
        // Subtle downward pointer notch aimed at the hovered stage on the horizontal ruler.
        int notchX = Math.max(stripX + 10, Math.min(stripX + totalW - 10, anchorCenterX));
        DagEdgeRenderer.drawTriangleArrow(
            canvas,
            notchX,
            stripY + stripH - 2,
            notchX,
            stripY + stripH + 5,
            5.5f,
            4.5f,
            palette.focusRing()
        );

        int cx = stripX + pad;
        for (int i = start; i <= end; i++) {
            DagLocatorRulerWidget.StageCell cell = cells.get(i);
            boolean isFocused = i == hoveredIdx;
            int cw = isFocused ? focusedW : neighborW;
            drawCursorNeighborhoodCard(
                canvas,
                palette,
                cell,
                hoveredIdx,
                isFocused,
                planTime,
                cx,
                stripY + 5,
                cw,
                stripH - 10
            );
            cx += cw;
            if (i < end) {
                int midY = stripY + stripH / 2;
                canvas.fill(cx + 2, midY - 1, 5, 2, palette.focusRing());
                DagEdgeRenderer.drawTriangleArrow(
                    canvas,
                    cx + 2,
                    midY,
                    cx + arrowW - 2,
                    midY,
                    5.4f,
                    2.7f,
                    palette.focusRing()
                );
                cx += arrowW;
            }
        }
    }

    private void drawVerticalCursorNeighborhood(
        SceneCanvas canvas,
        CirrusPalette palette,
        List<DagLocatorRulerWidget.StageCell> cells,
        int hoveredIdx,
        int start,
        int end,
        double planTime,
        int canvasWidth,
        int canvasHeight
    ) {
        int count = end - start + 1;
        int stripW = Math.min(162, Math.max(126, canvasWidth - locatorRuler.appliedWidth() - 32));
        int focusedH = 44;
        int neighborH = 38;
        int arrowH = 10;
        int pad = 6;
        int stripH = pad * 2 + (count - 1) * arrowH;
        for (int i = start; i <= end; i++) {
            stripH += i == hoveredIdx ? focusedH : neighborH;
        }
        DagLocatorRulerWidget.StageCell focusCell = cells.get(hoveredIdx);
        int targetAnchorY = locatorRuler.appliedY() + focusCell.y() + focusCell.height() / 2;
        int anchorCenterY = Math
                .round(previewAnchorY.update(targetAnchorY, 20f, System.nanoTime(), effectiveReducedMotion()));
        int stripX = Math.min(canvasWidth - stripW - 8, locatorRuler.appliedX() + locatorRuler.appliedWidth() + 8);
        int stripY = Math.max(32, Math.min(Math.max(32, canvasHeight - stripH - 8), anchorCenterY - stripH / 2));

        OreUiPaint.popover(canvas, stripX, stripY, stripW, stripH, palette);
        int notchY = Math.max(stripY + 10, Math.min(stripY + stripH - 10, anchorCenterY));
        DagEdgeRenderer
                .drawTriangleArrow(canvas, stripX + 2, notchY, stripX - 5, notchY, 5.5f, 4.5f, palette.focusRing());

        int cy = stripY + pad;
        for (int i = start; i <= end; i++) {
            DagLocatorRulerWidget.StageCell cell = cells.get(i);
            boolean isFocused = i == hoveredIdx;
            int ch = isFocused ? focusedH : neighborH;
            drawCursorNeighborhoodCard(
                canvas,
                palette,
                cell,
                hoveredIdx,
                isFocused,
                planTime,
                stripX + 5,
                cy,
                stripW - 10,
                ch
            );
            cy += ch;
            if (i < end) {
                int midX = stripX + stripW / 2;
                canvas.fill(midX - 1, cy + 1, 2, 4, palette.focusRing());
                DagEdgeRenderer.drawTriangleArrow(
                    canvas,
                    midX,
                    cy + 1,
                    midX,
                    cy + arrowH - 1,
                    4.6f,
                    2.5f,
                    palette.focusRing()
                );
                cy += arrowH;
            }
        }
    }

    private void drawCursorNeighborhoodCard(
        SceneCanvas canvas,
        CirrusPalette palette,
        DagLocatorRulerWidget.StageCell cell,
        int hoveredIdx,
        boolean focused,
        double planTime,
        int x,
        int y,
        int w,
        int h
    ) {
        if (focused) {
            OreUiPaint.raisedCard(canvas, x, y, w, h, palette, false, true);
        } else {
            canvas.fill(x, y, w, h, palette.recessedSurface());
            canvas.strokeRect(x, y, w, h, OreUiPaint.cardDivider(palette), 1);
        }
        int accent = locatorRuler.dominantAccent(cell, palette);
        String rel = DagLocatorRulerWidget.relativeOffsetLabel(cell.displayIndex(), hoveredIdx);
        String hdr = String.format("%02d", cell.displayIndex() + 1) + " " + rel;
        double work = DagLocatorRulerWidget.stageWorkOf(cell);
        double wait = DagLocatorRulerWidget.stageWaitOf(cell);
        double total = work + wait;
        String dur = WorkbenchFormatters.formatSeconds(total);

        Component hdrComp = Component.literal(hdr);
        ChromeText.draw(canvas, hdrComp, x + 5, y + 4, focused ? palette.focusRing() : palette.textSecondary());
        int glyphX = x + 5 + ChromeText.width(canvas, hdrComp) + 4;
        PlanStep leadStep = locatorRuler.leadStepOf(cell);
        if (leadStep != null && glyphX + 10 < x + w - 30) {
            DagLocatorRulerWidget.drawStepKindGlyph(canvas, leadStep, glyphX, y + 4, accent);
        }
        ChromeText.drawRight(
            canvas,
            Component.literal(dur),
            x + w - 5,
            y + 4,
            cell.hasCritical() ? palette.edgeCriticalPath() : palette.textMuted()
        );

        int slotDim = 16;
        int step = slotDim - 1;
        int sx = x + 5;
        int sy = y + 15;
        int maxSlots = Math.max(1, (w - 30) / step);
        int shown = Math
                .min(cell.nodes().size(), cell.nodes().size() > maxSlots ? Math.max(1, maxSlots - 1) : maxSlots);
        for (int k = 0; k < shown; k++) {
            DagNodeBox node = cell.nodes().get(k);
            StepNodeCardRenderer.StepVisuals data = visuals
                    .getOrDefault(node.stepId(), StepNodeCardRenderer.StepVisuals.empty());
            ResourceKey icon = DagRichTooltips.primaryIconOf(node.step(), data);
            long amt = data.outputAmount() > 0L ? data.outputAmount() : data.count();
            OreUiSlotAmount.drawSlot(canvas, icon, amt, sx, sy, slotDim, palette);
            sx += step;
        }
        int hidden = cell.nodes().size() - shown;
        if (hidden > 0 && sx + slotDim <= x + w - 4) {
            OreUiPaint.slot(canvas, sx, sy, slotDim, palette);
            Component more = Component.literal("+" + hidden);
            ChromeText.draw(
                canvas,
                more,
                sx + Math.max(1, (slotDim - ChromeText.width(canvas, more)) / 2),
                ChromeText.centeredY(sy, slotDim, canvas),
                palette.focusRing()
            );
            sx += slotDim;
        }
        if (x + w - sx >= 22) {
            String stat = cell.nodes().size() > 1
                    ? "\u00d7" + cell.nodes().size()
                    : (planTime > 0.01 ? Math.round((total / planTime) * 100.0) + "%" : "");
            if (!stat.isEmpty()) {
                ChromeText.drawRight(
                    canvas,
                    Component.literal(stat),
                    x + w - 5,
                    ChromeText.centeredY(sy, slotDim, canvas),
                    focused ? palette.textPrimary() : palette.textMuted()
                );
            }
        }

        if (h >= 38) {
            double share = planTime > 0.01 ? total / planTime : 0.0;
            DagLocatorRulerWidget.drawSplitTimeBar(
                canvas,
                x + 5,
                y + h - 7,
                Math.max(12, w - 10),
                4,
                planTime > 0.01 ? work / planTime : 0.0,
                planTime > 0.01 ? wait / planTime : 0.0,
                share,
                palette
            );
        }
    }

    public Rect selectionPreviewBounds() {
        return selectionPreviewBounds(width(), height());
    }

    private Rect selectionPreviewBounds(int canvasWidth, int canvasHeight) {
        if (selectedStepIds.isEmpty() || canvasWidth < 220 || canvasHeight < 140) {
            return new Rect(0, 0, 0, 0);
        }
        int x = layoutMode.isVertical() ? DagLocatorRulerWidget.verticalWidth + 16 : 10;
        int y = embeddedInStageHeader ? 10 : capsuleHeight + capsuleInset + 6;
        int rightReserve = minimap.visible() ? minimap.width() + 18 : 12;
        int avail = Math.max(0, canvasWidth - x - rightReserve);
        if (avail < 160) {
            return new Rect(0, 0, 0, 0);
        }
        int w = Math.min(316, avail);
        return new Rect(x, y, w, 46);
    }

    private Rect navButtonRect(Rect card, int indexFromRight) {
        int size = 16;
        int gap = 3;
        int bx = card.x() + card.width() - 6 - (indexFromRight + 1) * size - indexFromRight * gap;
        int by = card.y() + 6;
        return new Rect(bx, by, size, size);
    }

    private void drawSelectionPreviewBar(
        SceneCanvas canvas,
        PlanDagLayoutResult result,
        CirrusPalette palette,
        int canvasWidth,
        int canvasHeight,
        int mouseX,
        int mouseY
    ) {
        List<DagNodeBox> selected = selectedNodes(result);
        if (selected.isEmpty()) {
            selectionBarPrimaryId = null;
            selectionBarShownAtMs = 0L;
            return;
        }
        Rect card = selectionPreviewBounds(canvasWidth, canvasHeight);
        if (card.width() <= 0 || card.height() <= 0) {
            return;
        }
        DagNodeBox primary = selected.get(0);
        long nowMs = OreUiMotion.nowMs();
        if (!Objects.equals(selectionBarPrimaryId, primary.stepId())) {
            selectionBarPrimaryId = primary.stepId();
            selectionBarShownAtMs = nowMs;
        }
        float barEase = OreUiMotion.cubicOut(nowMs - selectionBarShownAtMs, 115L, effectiveReducedMotion());
        float barSlideY = -(1f - barEase) * 5f;
        canvas.pushTransform();
        canvas.translate(0f, barSlideY);
        try {
            drawSelectionPreviewBarBody(canvas, result, selected, primary, card, palette, mouseX, mouseY);
        } finally {
            canvas.popTransform();
        }
    }

    private void drawSelectionPreviewBarBody(
        SceneCanvas canvas,
        PlanDagLayoutResult result,
        List<DagNodeBox> selected,
        DagNodeBox primary,
        Rect card,
        CirrusPalette palette,
        int mouseX,
        int mouseY
    ) {
        StepNodeCardRenderer.StepVisuals data = visuals
                .getOrDefault(primary.stepId(), StepNodeCardRenderer.StepVisuals.empty());
        CirrusPalette.StepTint tint = palette.stepTint(primary.step());

        OreUiPaint.popover(canvas, card.x(), card.y(), card.width(), card.height(), palette);
        int highlight = OreUiPaint.isDark(palette)
                ? OreUiPaint.lighten(palette.popoverSurface(), 0.14f)
                : OreUiPaint.lighten(palette.cardSurface(), 0.35f);
        canvas.fill(card.x() + 1, card.y() + 1, card.width() - 2, 1, highlight);

        // Recessed primary item slot.
        int slotX = card.x() + 7;
        int slotY = card.y() + (card.height() - 24) / 2;
        CoPilotPaint.renderSlotItem(
            canvas,
            CoPilotPaint.stackOf(DagRichTooltips.primaryIconOf(primary.step(), data), 1L),
            slotX,
            slotY,
            24,
            palette
        );

        // Quick-jump nav buttons on right: [◀ prev] [⌖ center] [▶ next].
        String prevId = adjacentStepId(result, primary.stepId(), true);
        String nextId = adjacentStepId(result, primary.stepId(), false);
        Rect nextBtn = navButtonRect(card, 0);
        Rect centerBtn = navButtonRect(card, 1);
        Rect prevBtn = navButtonRect(card, 2);
        drawNavButton(canvas, prevBtn, "\u25c0", prevId != null, mouseX, mouseY, palette);
        drawNavButton(canvas, centerBtn, "\u2316", true, mouseX, mouseY, palette);
        drawNavButton(canvas, nextBtn, "\u25b6", nextId != null, mouseX, mouseY, palette);

        // Top row: Stage badge + Title.
        int textX = slotX + 30;
        int topY = card.y() + 7;
        String stageBadge = String.format("S%02d", primary.layer() + 1);
        int badgeW = OreUiPaint.tagPill(
            canvas,
            textX,
            topY,
            OreUiPaint.minPillHeight,
            Component.literal(stageBadge),
            tint.accent(),
            palette
        );
        Component heading = selected.size() == 1
                ? DagRichTooltips.stepTitle(primary.step(), data, resolver)
                : Component.literal(selected.size() + " Steps Selected");
        int titleAvail = Math.max(16, prevBtn.x() - (textX + badgeW + 5) - 6);
        ChromeText.draw(
            canvas,
            ChromeText.fit(canvas, heading, titleAvail),
            textX + badgeW + 5,
            ChromeText.centeredY(topY, OreUiPaint.minPillHeight, canvas),
            palette.textPrimary()
        );

        // Bottom row: compact mini-icon formula on left + Work/Wait readout on right.
        int botY = card.y() + 25;
        double work = 0.0;
        double wait = 0.0;
        for (DagNodeBox box : selected) {
            work += box.step().stepCost().humanActiveSeconds();
            wait += box.step().stepCost().passiveWaitSeconds();
        }
        String timing = "Work " + WorkbenchFormatters.formatSeconds(work) + " \u00b7 Wait "
                + WorkbenchFormatters.formatSeconds(wait);
        Component timingComp = Component.literal(timing);
        int timingW = ChromeText.width(canvas, timingComp);
        int rightEdge = card.x() + card.width() - 7;
        ChromeText.drawRight(
            canvas,
            timingComp,
            rightEdge,
            ChromeText.centeredY(botY, 14, canvas),
            palette.textSecondary()
        );

        int flowLimit = rightEdge - timingW - 8;
        int fx = textX;
        if (selected.size() == 1 && !data.inputs().isEmpty()) {
            int slotDim = 16;
            int step = slotDim - 1;
            int miniSlotY = botY - 1;
            int outReserve = data.primaryOutput() != null ? slotDim + 16 : 0;
            int maxIn = Math.min(3, data.inputs().size());
            int shownIn = 0;
            for (int i = 0; i < maxIn; i++) {
                if (fx + slotDim > flowLimit - outReserve) {
                    break;
                }
                long amt = i < data.inputAmounts().size() ? data.inputAmounts().get(i).amount() : 1L;
                OreUiSlotAmount.drawSlot(canvas, data.inputs().get(i), amt, fx, miniSlotY, slotDim, palette);
                fx += step;
                shownIn++;
            }
            if (shownIn > 0) {
                fx += 1;
            }
            if (fx + outReserve <= flowLimit && data.primaryOutput() != null) {
                int midY = miniSlotY + slotDim / 2;
                OreUiRecipeGrid.drawRecipeArrow(canvas, fx + 2, fx + 13, midY, palette, false);
                fx += 15;
                long outAmt = data.outputAmount() > 0L ? data.outputAmount() : Math.max(1L, data.count());
                OreUiSlotAmount.drawSlot(canvas, data.primaryOutput(), outAmt, fx, miniSlotY, slotDim, palette);
            }
        } else {
            Component kind = DagRichTooltips.kindTag(primary.step());
            ChromeText.draw(
                canvas,
                ChromeText.fit(canvas, kind, Math.max(12, flowLimit - fx)),
                fx,
                ChromeText.centeredY(botY, 14, canvas),
                tint.accent()
            );
        }
    }

    private static void drawNavButton(
        SceneCanvas canvas,
        Rect rect,
        String glyph,
        boolean enabled,
        int mouseX,
        int mouseY,
        CirrusPalette palette
    ) {
        boolean hovered = enabled
                && mouseX >= rect.x()
                && mouseX < rect.x() + rect.width()
                && mouseY >= rect.y()
                && mouseY < rect.y() + rect.height();
        OreUiPaint.raisedButton(
            canvas,
            rect.x(),
            rect.y(),
            rect.width(),
            rect.height(),
            palette,
            false,
            hovered,
            false,
            false
        );
        Component comp = Component.literal(glyph);
        int gx = rect.x() + (rect.width() - ChromeText.width(canvas, comp)) / 2;
        ChromeText.draw(
            canvas,
            comp,
            gx,
            ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
            enabled ? (hovered ? palette.focusRing() : palette.textPrimary()) : palette.textDisabled()
        );
    }

    private boolean clickSelectionPreview(double localX, double localY) {
        PlanDagLayoutResult result = dagLayoutInternal();
        List<DagNodeBox> selected = selectedNodes(result);
        if (selected.isEmpty()) {
            return false;
        }
        Rect card = selectionPreviewBounds(width(), height());
        if (!ChromeText.hit(localX, localY, card.x(), card.y(), card.width(), card.height())) {
            return false;
        }
        DagNodeBox primary = selected.get(0);
        Rect nextBtn = navButtonRect(card, 0);
        Rect centerBtn = navButtonRect(card, 1);
        Rect prevBtn = navButtonRect(card, 2);
        if (ChromeText.hit(localX, localY, prevBtn.x(), prevBtn.y(), prevBtn.width(), prevBtn.height())) {
            String prevId = adjacentStepId(result, primary.stepId(), true);
            if (prevId != null) {
                applySelection(Set.of(prevId));
                focusStep(prevId);
            }
            return true;
        }
        if (ChromeText.hit(localX, localY, centerBtn.x(), centerBtn.y(), centerBtn.width(), centerBtn.height())) {
            focusStep(primary.stepId());
            return true;
        }
        if (ChromeText.hit(localX, localY, nextBtn.x(), nextBtn.y(), nextBtn.width(), nextBtn.height())) {
            String nextId = adjacentStepId(result, primary.stepId(), false);
            if (nextId != null) {
                applySelection(Set.of(nextId));
                focusStep(nextId);
            }
            return true;
        }
        focusStep(primary.stepId());
        return true;
    }

    private @Nullable String adjacentStepId(PlanDagLayoutResult result, String stepId, boolean upstream) {
        for (DagEdgeRoute edge : result.edges()) {
            if (upstream && edge.toStepId().equals(stepId)) {
                return edge.fromStepId();
            }
            if (!upstream && edge.fromStepId().equals(stepId)) {
                return edge.toStepId();
            }
        }
        return null;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (!visible() || drag.active()) {
            return null;
        }
        PlanDagLayoutResult result = dagLayoutInternal();
        CirrusPalette palette = palette();

        if (!embeddedInStageHeader) {
            CapsuleLayout capsule = capsuleLayout(safeMinecraftFont(), width(), height());
            for (CapsuleChip chip : capsule.chips()) {
                if (chip.bounds().contains(localX, localY)) {
                    return DagRichTooltips.capsuleAction(chip.action());
                }
            }
        }

        // 2. Selection preview bar & nav buttons.
        List<DagNodeBox> selected = selectedNodes(result);
        if (!selected.isEmpty()) {
            Rect card = selectionPreviewBounds(width(), height());
            if (ChromeText.hit(localX, localY, card.x(), card.y(), card.width(), card.height())) {
                DagNodeBox primary = selected.get(0);
                Rect nextBtn = navButtonRect(card, 0);
                Rect centerBtn = navButtonRect(card, 1);
                Rect prevBtn = navButtonRect(card, 2);
                if (ChromeText.hit(localX, localY, prevBtn.x(), prevBtn.y(), prevBtn.width(), prevBtn.height())) {
                    String prevId = adjacentStepId(result, primary.stepId(), true);
                    return OreUiRichTooltip.builder("Jump Upstream").badge("\u25c0 PREV", OreUiRichTooltip.Tone.accent)
                            .text(
                                prevId == null ? "No upstream dependency." : "Focus upstream step: " + prevId,
                                OreUiRichTooltip.Tone.secondary
                            ).build();
                }
                if (ChromeText
                        .hit(localX, localY, centerBtn.x(), centerBtn.y(), centerBtn.width(), centerBtn.height())) {
                    return OreUiRichTooltip.builder("Recenter Selected Step")
                            .badge("\u2316 FOCUS", OreUiRichTooltip.Tone.positive)
                            .text("Smoothly centers camera on the selected node.", OreUiRichTooltip.Tone.secondary)
                            .build();
                }
                if (ChromeText.hit(localX, localY, nextBtn.x(), nextBtn.y(), nextBtn.width(), nextBtn.height())) {
                    String nextId = adjacentStepId(result, primary.stepId(), false);
                    return OreUiRichTooltip.builder("Jump Downstream")
                            .badge("NEXT \u25b6", OreUiRichTooltip.Tone.accent)
                            .text(
                                nextId == null ? "No downstream consumer." : "Focus downstream step: " + nextId,
                                OreUiRichTooltip.Tone.secondary
                            ).build();
                }
                return stepRichTooltip(result, primary, palette);
            }
        }

        // 3. Hovered DAG node.
        DagNodeBox node = nodeAtLocal(localX, localY);
        if (node != null) {
            return stepRichTooltip(result, node, palette);
        }

        // 4. Hovered DAG edge.
        DagEdgeRoute edge = DagHitTester.edgeAt(
            localX,
            localY,
            result.edges(),
            viewport,
            DagHitTester.edgeTolerancePx,
            DagHitTester.bezierSamples,
            edgeStyle,
            layoutMode
        );
        if (edge != null) {
            return DagEdgeRenderer.edgeTooltip(edge, resolver, palette);
        }
        return null;
    }

    private OreUiRichTooltip stepRichTooltip(PlanDagLayoutResult result, DagNodeBox node, CirrusPalette palette) {
        int up = 0;
        int down = 0;
        for (DagEdgeRoute edge : result.edges()) {
            if (edge.toStepId().equals(node.stepId())) {
                up++;
            } else if (edge.fromStepId().equals(node.stepId())) {
                down++;
            }
        }
        StepNodeCardRenderer.StepVisuals data = visuals
                .getOrDefault(node.stepId(), StepNodeCardRenderer.StepVisuals.empty());
        return DagRichTooltips.stepNode(
            node,
            data,
            resolver,
            palette,
            criticalStepIds.contains(node.stepId()),
            inlineBeacons.containsKey(node.stepId()),
            up,
            down
        );
    }

    //endregion

    private CapsuleLayout capsuleLayout(@Nullable Font font, int canvasWidth, int canvasHeight) {
        int leftStart = isZenMode() ? 32 : capsuleHorizontalMargin;
        int rightReserve = minimap.visible() && canvasWidth >= 420
                ? Math.max(DagMinimapWidget.wideWidth, minimap.width())
                        + Math.round(DagMinimapWidget.rightClearance)
                        + capsuleHorizontalMargin
                : capsuleHorizontalMargin;
        int maxCapsuleWidth = Math.max(0, canvasWidth - leftStart - rightReserve);
        if (maxCapsuleWidth < 40 || canvasHeight <= capsuleHeight + capsuleInset) {
            return new CapsuleLayout(new Rect(0, 0, 0, 0), List.of(), List.of(), 2);
        }
        List<CapsuleSpec> fullSpecs = buildCapsuleSpecs(false);
        CapsuleLayout full = packCapsule(fullSpecs, font, leftStart, canvasHeight, maxCapsuleWidth, 5, 2, 6, false);
        if (full != null) {
            return full;
        }
        List<CapsuleSpec> compactSpecs = buildCapsuleSpecs(true);
        CapsuleLayout compact = packCapsule(
            compactSpecs,
            font,
            leftStart,
            canvasHeight,
            maxCapsuleWidth,
            3,
            2,
            5,
            false
        );
        if (compact != null) {
            return compact;
        }
        return Objects.requireNonNull(
            packCapsule(compactSpecs, font, leftStart, canvasHeight, maxCapsuleWidth, 2, 1, 3, true)
        );
    }

    private List<CapsuleSpec> buildCapsuleSpecs(boolean compact) {
        FlowViewMode view = currentViewMode();
        DagLayoutMode activeLayout = currentLayoutMode();
        String dirShort = switch (activeLayout) {
            case horizontalLr -> "LR";
            case verticalTb -> "TB";
            case horizontalRl -> "RL";
            case verticalBt -> "BT";
            case systemSwimlane -> "Lane";
        };
        int zoomPct = Math.round(viewport.zoom() * 100.0f);
        Component menuLabel = compact
                ? Component.literal(dirShort)
                : Component.literal(dirShort + " \u00b7 " + zoomPct + "%");
        return List.of(
            new CapsuleSpec(
                CapsuleAction.viewDag,
                compact ? Component.literal("DAG") : CirrusLangs.Workbench.Flow.dag.get(),
                view == FlowViewMode.spatialDag,
                0
            ),
            new CapsuleSpec(
                CapsuleAction.viewLedger,
                compact ? Component.literal("Ledger") : CirrusLangs.Workbench.Flow.ledger.get(),
                view == FlowViewMode.preparationLedger,
                0
            ),
            new CapsuleSpec(
                CapsuleAction.viewChecklist,
                compact ? Component.literal("List") : CirrusLangs.Workbench.Flow.checklist.get(),
                view == FlowViewMode.executionChecklist,
                0
            ),
            new CapsuleSpec(CapsuleAction.viewMenu, menuLabel, viewSettingsPopover != null, 1)
        );
    }

    private @Nullable CapsuleLayout packCapsule(
        List<CapsuleSpec> specs,
        @Nullable Font font,
        int leftStart,
        int canvasHeight,
        int maxCapsuleWidth,
        int pad,
        int gap,
        int groupGap,
        boolean forceFit
    ) {
        int[] widths = new int[specs.size()];
        int outerPad = Math.max(3, pad);
        int total = outerPad * 2;
        int prevGroup = -1;
        for (int index = 0; index < specs.size(); index++) {
            CapsuleSpec spec = specs.get(index);
            if (prevGroup >= 0) {
                total += spec.group() != prevGroup ? groupGap : gap;
            }
            int extraCaret = spec.action() == CapsuleAction.viewMenu ? 10 : 0;
            widths[index] = Math.max(18, measureChipText(font, spec.label()) + pad * 2 + extraCaret);
            total += widths[index];
            prevGroup = spec.group();
        }
        if (total > maxCapsuleWidth) {
            if (!forceFit) {
                return null;
            }
            int fixedOverhead = total;
            for (int chipWidth : widths) {
                fixedOverhead -= chipWidth;
            }
            int availableForChips = Math.max(specs.size() * 10, maxCapsuleWidth - fixedOverhead);
            int sum = Math.max(1, total - fixedOverhead);
            total = fixedOverhead;
            for (int index = 0; index < widths.length; index++) {
                widths[index] = Math.max(10, (int) Math.floor(widths[index] * (availableForChips / (double) sum)));
                total += widths[index];
            }
            while (total > maxCapsuleWidth) {
                int widest = -1;
                for (int index = 0; index < widths.length; index++) {
                    if (widths[index] > 10 && (widest < 0 || widths[index] > widths[widest])) {
                        widest = index;
                    }
                }
                if (widest < 0) {
                    break;
                }
                widths[widest]--;
                total--;
            }
        }
        int boxWidth = Math.min(maxCapsuleWidth, total);
        int boxX = leftStart;
        int boxY = capsuleInset;
        int chipY = boxY + (capsuleHeight - capsuleChipHeight) / 2;
        int cursor = boxX + outerPad;
        List<CapsuleChip> chips = new ArrayList<>(specs.size());
        List<Integer> dividers = new ArrayList<>(2);
        prevGroup = -1;
        for (int index = 0; index < specs.size(); index++) {
            CapsuleSpec spec = specs.get(index);
            if (prevGroup >= 0) {
                if (spec.group() != prevGroup) {
                    dividers.add(cursor + groupGap / 2);
                    cursor += groupGap;
                } else {
                    cursor += gap;
                }
            }
            int chipWidth = Math.min(widths[index], Math.max(0, boxX + boxWidth - outerPad - cursor));
            if (chipWidth <= 0) {
                break;
            }
            chips.add(
                new CapsuleChip(
                    new DagHitTester.RectF(cursor, chipY, chipWidth, capsuleChipHeight),
                    spec.action(),
                    spec.label(),
                    spec.active()
                )
            );
            cursor += chipWidth;
            prevGroup = spec.group();
        }
        return new CapsuleLayout(
            new Rect(boxX, boxY, boxWidth, capsuleHeight),
            List.copyOf(chips),
            List.copyOf(dividers),
            pad
        );
    }

    private final class ViewSettingsPopover extends Widget {
        private static final int pad = 8;
        private static final int btnH = 18;
        private static final int gap = 3;
        private final long openedAtNanos = System.nanoTime();

        private ViewSettingsPopover(int x, int y, int w, int h) {
            setInteractive(true);
            useStyle(positionAbsolute(), insetLeft(x), insetTop(y), sizeOf(w, h), UIStyles.zIndex(2400));
            onClickOutside(ignored -> {
                if (System.nanoTime() - openedAtNanos < 180_000_000L) {
                    return;
                }
                closeViewSettingsPopover();
            });
            onMouseClick((input, clickCount, clickCtx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                if (handleClick((int) local.x(), (int) local.y())) {
                    clickCtx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
        }

        private Rect[] rowRects(int y, int count) {
            int innerW = Math.max(40, width() - pad * 2);
            int each = Math.max(16, (innerW - gap * (count - 1)) / count);
            Rect[] rects = new Rect[count];
            int cx = pad;
            for (int i = 0; i < count; i++) {
                int w = i == count - 1 ? Math.max(16, pad + innerW - cx) : each;
                rects[i] = new Rect(cx, y, w, btnH);
                cx += w + gap;
            }
            return rects;
        }

        private boolean handleClick(int lx, int ly) {
            SpatialDagHost dagHost = host;
            DagLayoutMode[] layouts = {DagLayoutMode.horizontalLr, DagLayoutMode.verticalTb, DagLayoutMode.horizontalRl,
                    DagLayoutMode.verticalBt, DagLayoutMode.systemSwimlane};
            Rect[] r1 = rowRects(19, layouts.length);
            for (int i = 0; i < layouts.length; i++) {
                if (r1[i].contains(lx, ly)) {
                    if (dagHost != null) {
                        dagHost.onSetLayoutMode(layouts[i]);
                    }
                    layoutDirty = true;
                    return true;
                }
            }
            DagEdgeStyle[] edges = {DagEdgeStyle.orthogonal, DagEdgeStyle.straight, DagEdgeStyle.curved};
            Rect[] r2 = rowRects(55, edges.length);
            for (int i = 0; i < edges.length; i++) {
                if (r2[i].contains(lx, ly)) {
                    setEdgeStyle(edges[i]);
                    return true;
                }
            }
            ScrollNavigationMode[] navs = {ScrollNavigationMode.auto, ScrollNavigationMode.trackpadPan,
                    ScrollNavigationMode.mouseZoom};
            Rect[] r3 = rowRects(91, navs.length);
            for (int i = 0; i < navs.length; i++) {
                if (r3[i].contains(lx, ly)) {
                    setScrollNavigationMode(navs[i]);
                    return true;
                }
            }
            Rect[] r4 = rowRects(127, 3);
            if (r4[0].contains(lx, ly)) {
                setMinimapUserVisible(!minimapUserVisible);
                return true;
            }
            if (r4[1].contains(lx, ly)) {
                setLocatorUserVisible(!locatorUserVisible);
                return true;
            }
            if (r4[2].contains(lx, ly)) {
                SessionTabState state = resolveActiveTabState();
                if (state != null) {
                    boolean next = !state.guidedPauseEnabled();
                    state.setGuidedPauseEnabled(next);
                    if (next) {
                        state.setGuidedPauseDismissed(false);
                    }
                }
                return true;
            }
            Rect[] r5 = rowRects(157, 3);
            if (r5[0].contains(lx, ly)) {
                fitToView();
                closeViewSettingsPopover();
                return true;
            }
            if (r5[1].contains(lx, ly)) {
                DagViewport target = new DagViewport(viewport.camX(), viewport.camY(), viewport.zoom());
                target.zoomAt(
                    SpatialDagCanvasWidget.this.width() * 0.5,
                    SpatialDagCanvasWidget.this.height() * 0.5,
                    1.0 / Math.max(0.01, viewport.zoom())
                );
                animateCameraTo(target.camX(), target.camY(), 1.0f);
                return true;
            }
            if (r5[2].contains(lx, ly)) {
                clearManualOffsets();
                closeViewSettingsPopover();
                return true;
            }
            Rect[] r6 = rowRects(184, 1);
            if (r6[0].contains(lx, ly)) {
                resetTabPlanAndRules();
                return true;
            }
            return false;
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            if (width() <= 0 || height() <= 0) {
                return;
            }
            CirrusPalette p = palette();
            canvas.shadow(0, 0, width(), height(), 2f, 4f, 10f, p.dropShadow());
            OreUiPaint.popover(canvas, 0, 0, width(), height(), p);

            ChromeText.draw(canvas, Component.literal("Flow Direction"), pad, 7, p.textMuted());
            DagLayoutMode activeLayout = currentLayoutMode();
            String[] dirLabels = {"LR", "TB", "RL", "BT", "Lane"};
            DagLayoutMode[] layouts = {DagLayoutMode.horizontalLr, DagLayoutMode.verticalTb, DagLayoutMode.horizontalRl,
                    DagLayoutMode.verticalBt, DagLayoutMode.systemSwimlane};
            Rect[] r1 = rowRects(19, layouts.length);
            for (int i = 0; i < layouts.length; i++) {
                drawOpt(canvas, r1[i], dirLabels[i], activeLayout == layouts[i], false, mouseX, mouseY, p);
            }

            ChromeText.draw(canvas, Component.literal("Conduit Style"), pad, 43, p.textMuted());
            String[] edgeLabels = {"Orthogonal", "Straight", "Curved"};
            DagEdgeStyle[] edges = {DagEdgeStyle.orthogonal, DagEdgeStyle.straight, DagEdgeStyle.curved};
            Rect[] r2 = rowRects(55, edges.length);
            for (int i = 0; i < edges.length; i++) {
                drawOpt(canvas, r2[i], edgeLabels[i], edgeStyle == edges[i], false, mouseX, mouseY, p);
            }

            ChromeText.draw(
                canvas,
                Component.literal(isMacOs() ? "Scroll / Trackpad (macOS)" : "Scroll Navigation"),
                pad,
                79,
                p.textMuted()
            );
            String[] navLabels = {"Auto", "Trackpad", "Mouse"};
            ScrollNavigationMode[] navs = {ScrollNavigationMode.auto, ScrollNavigationMode.trackpadPan,
                    ScrollNavigationMode.mouseZoom};
            Rect[] r3 = rowRects(91, navs.length);
            for (int i = 0; i < navs.length; i++) {
                drawOpt(canvas, r3[i], navLabels[i], scrollNavMode == navs[i], false, mouseX, mouseY, p);
            }

            ChromeText.draw(canvas, Component.literal("Overlays & Guidance"), pad, 115, p.textMuted());
            Rect[] r4 = rowRects(127, 3);
            SessionTabState state = resolveActiveTabState();
            boolean guidedOn = state == null || state.guidedPauseEnabled();
            drawOpt(
                canvas,
                r4[0],
                minimapUserVisible ? "Map: On" : "Map: Off",
                minimapUserVisible,
                false,
                mouseX,
                mouseY,
                p
            );
            drawOpt(
                canvas,
                r4[1],
                locatorUserVisible ? "Ruler: On" : "Ruler: Off",
                locatorUserVisible,
                false,
                mouseX,
                mouseY,
                p
            );
            drawOpt(canvas, r4[2], guidedOn ? "Pause: On" : "Pause: Off", guidedOn, false, mouseX, mouseY, p);

            canvas.fill(pad, 151, Math.max(0, width() - pad * 2), 1, OreUiPaint.cardDivider(p));
            Rect[] r5 = rowRects(157, 3);
            drawOpt(canvas, r5[0], "Fit (F)", false, false, mouseX, mouseY, p);
            drawOpt(canvas, r5[1], "100%", Math.abs(viewport.zoom() - 1.0f) < 0.03f, false, mouseX, mouseY, p);
            drawOpt(canvas, r5[2], "Reset Pos", false, false, mouseX, mouseY, p);

            Rect[] r6 = rowRects(184, 1);
            drawOpt(canvas, r6[0], "Reset Tab Plan & Rules", false, true, mouseX, mouseY, p);
        }

        private void drawOpt(
            SceneCanvas canvas,
            Rect rect,
            String label,
            boolean active,
            boolean danger,
            int mouseX,
            int mouseY,
            CirrusPalette p
        ) {
            boolean hover = rect.contains(mouseX, mouseY);
            if (active) {
                OreUiPaint.selectedToggle(canvas, rect.x(), rect.y(), rect.width(), rect.height(), p, hover);
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    p,
                    false,
                    hover,
                    false,
                    false
                );
                if (danger) {
                    canvas.strokeRect(rect.x(), rect.y(), rect.width(), rect.height(), p.ruleHardBan(), 1);
                }
            }
            Component comp = ChromeText.fit(canvas, Component.literal(label), Math.max(8, rect.width() - 6));
            int tx = rect.x() + Math.max(3, (rect.width() - ChromeText.width(canvas, comp)) / 2);
            int ink = danger ? p.ruleHardBan() : active ? p.focusRing() : hover ? p.textPrimary() : p.textSecondary();
            ChromeText.draw(
                canvas,
                comp,
                tx,
                active
                        ? ChromeText.centeredY(rect.y(), rect.height(), canvas)
                        : ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
                ink
            );
        }
    }

    private static int measureChipText(@Nullable Font font, Component label) {
        String text = label == null ? "" : label.getString();
        if (text.isEmpty()) {
            return 0;
        }
        if (font != null) {
            return font.width(text);
        }
        int width = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            width += (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) ? 9 : 6;
            i += Character.charCount(cp);
        }
        return width;
    }

    private static @Nullable Font safeMinecraftFont() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null ? null : minecraft.font;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private FlowViewMode currentViewMode() {
        SpatialDagHost dagHost = host;
        return dagHost == null ? FlowViewMode.spatialDag : dagHost.snapshot().flowViewMode();
    }

    private DagLayoutMode currentLayoutMode() {
        SpatialDagHost dagHost = host;
        return dagHost == null ? DagLayoutMode.horizontalLr : dagHost.snapshot().dagLayoutMode();
    }

    //endregion

    //region helpers

    private CirrusPalette palette() {
        SpatialDagHost dagHost = host;
        return dagHost == null ? CirrusPalette.obsidianBlueprint : dagHost.palette();
    }

    private @Nullable String activeTraceFocusId() {
        if (hoveredStepId != null) {
            return hoveredStepId;
        }
        if (hoveredEdge != null) {
            return null;
        }
        if (!selectedStepIds.isEmpty()) {
            return selectedStepIds.iterator().next();
        }
        return null;
    }

    private DagSelectionMath.TraceSets traceSets(PlanDagLayoutResult result) {
        String focus = activeTraceFocusId();
        if (focus != null) {
            String key = "step|" + focus + "|" + layoutSignature;
            if (!key.equals(traceFocus)) {
                traceSets = DagSelectionMath.traceSets(focus, result.edges(), stepIdsOf(result));
                traceFocus = key;
            }
            return traceSets;
        }
        if (hoveredEdge != null) {
            String key = "edge|" + hoveredEdge.fromStepId() + "->" + hoveredEdge.toStepId() + "|" + layoutSignature;
            if (!key.equals(traceFocus)) {
                Set<String> allIds = stepIdsOf(result);
                DagSelectionMath.TraceSets fromTrace = DagSelectionMath
                        .traceSets(hoveredEdge.fromStepId(), result.edges(), allIds);
                DagSelectionMath.TraceSets toTrace = DagSelectionMath
                        .traceSets(hoveredEdge.toStepId(), result.edges(), allIds);
                Set<String> up = new TreeSet<>(fromTrace.upstream());
                up.add(hoveredEdge.fromStepId());
                Set<String> down = new TreeSet<>(toTrace.downstream());
                down.add(hoveredEdge.toStepId());
                Set<String> unrelated = new TreeSet<>(allIds);
                unrelated.removeAll(up);
                unrelated.removeAll(down);
                traceSets = new DagSelectionMath.TraceSets(up, down, unrelated);
                traceFocus = key;
            }
            return traceSets;
        }
        return new DagSelectionMath.TraceSets(Set.of(), Set.of(), Set.of());
    }

    private static Set<String> stepIdsOf(PlanDagLayoutResult result) {
        Set<String> ids = new TreeSet<>();
        for (DagNodeBox node : result.nodes()) {
            ids.add(node.stepId());
        }
        return ids;
    }

    private @Nullable DagNodeBox nodeAtLocal(double localX, double localY) {
        float[] world = viewport.screenToWorld((float) localX, (float) localY);
        return DagHitTester.nodeAt(world[0], world[1], dagLayoutInternal().nodes());
    }

    private void applyAlign(DagSelectionMath.AlignKind kind) {
        Map<String, FloatPos> offsets = DagSelectionMath.alignOffsets(actionBar.selection(), kind);
        if (offsets.isEmpty()) {
            return;
        }
        SpatialDagHost dagHost = host;
        if (dagHost != null) {
            for (Map.Entry<String, FloatPos> entry : offsets.entrySet()) {
                dagHost.onNodeMoved(entry.getKey(), entry.getValue());
            }
        }
        layoutDirty = true;
    }

    //endregion

    private static final class EmptyRecipeGraph implements CompiledRecipeGraph {

        private static final EmptyRecipeGraph instance = new EmptyRecipeGraph();

        @Override
        public long catalogVersion() {
            return 0L;
        }

        @Override
        public SystemAdmission admissionOf(String systemId) {
            return SystemAdmission.enabled;
        }

        @Override
        public List<CompiledRecipe> producersOf(ResourceKey output) {
            return List.of();
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
            return Set.of();
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
}
