package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayNode;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayNodeKind;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayViewModel;
import dev.vfyjxf.cirrus.ui.widget.flowchart.FlowChartTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialType;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollDirection;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Read-only left-side overlay that renders execution progress inside other screens.
 */
public class ExecutionOverlayWidget extends CompositeWidget<Widget> {

    enum WorkbenchQueueRowState {
        CURRENT,
        UPCOMING
    }

    record WorkbenchQueueRow(
        ExecutionOverlayNode node,
        WorkbenchQueueRowState state,
        int batchCount,
        @Nullable InteractionStep.ExecuteRecipeStep previewStep,
        int order
    ) {
    }

    private record WorkbenchQueueGroup(String key, ExecutionOverlayNode representative, ImmutableList<String> nodeIds, int batchCount) {
    }

    enum WorkbenchDockWindow {
        recipe("recipe", "cirrus.ui.execution.overlay.dock.recipe"),
        timeline("timeline", "cirrus.ui.execution.overlay.dock.timeline"),
        inspect("inspect", "cirrus.ui.execution.overlay.dock.inspect");

        private final String id;
        private final String labelKey;

        WorkbenchDockWindow(String id, String labelKey) {
            this.id = id;
            this.labelKey = labelKey;
        }

        String id() {
            return id;
        }

        String label() {
            return Component.translatable(labelKey).getString();
        }
    }

    //region layout constants

    /**
     * Match JEI 1.21.1 bookmark width:
     * IngredientGrid uses 18px slots and IngredientGridConfig defaults to 9 columns.
     */
    public static final int jeiBookmarkAlignedPanelWidth = 18 * 9;
    public static final int minPanelWidth = jeiBookmarkAlignedPanelWidth;
    public static final int maxPanelWidth = jeiBookmarkAlignedPanelWidth;
    public static final int headerHeight = 32;
    public static final int footerHeight = 30;

    private static final int panelPadding = 7;
    private static final int graphPaddingX = 10;
    private static final int graphPaddingY = 8;
    private static final int graphPanelGap = 6;
    private static final int nodeWidth = 138;
    private static final int nodeHeight = 62;
    private static final int nodeGapX = 24;
    private static final int nodeGapY = 20;
    private static final int connectorThickness = 1;
    private static final int focusScrollPadding = 10;
    private static final int infoPanelMinWidth = 148;
    private static final float infoPanelWidthRatio = 0.34f;
    private static final int infoPanelMaxWidth = 220;
    private static final int infoPanelMinGraphWidth = 168;

    private static final float defaultZoom = 0.9f;
    private static final float minZoom = 0.04f;
    private static final float maxZoom = 3.4f;
    private static final float zoomStepFactor = 1.16f;
    private static final float zoomSnap = 0.0015f;
    private static final float zoomSmoothSpeed = 0.58f;

    //endregion

    private static final int connectorColor = 0xFF6A6A6A;
    private static final int activeConnectorColor = 0xFF5C9AF2;
    private static final int scrollOverscan = 18;
    private static final boolean useWorkbenchLayout = true;
    private static final int workbenchDockRailWidth = 18;
    private static final int workbenchDockRailGap = 0;
    private static final int workbenchDockButtonSize = 14;
    private static final int workbenchDockButtonGap = 4;
    private static final int workbenchDockButtonMinWidth = 40;
    private static final int workbenchDockBarInset = 2;
    private static final int workbenchPopupWidth = 182;
    private static final int workbenchPopupHeight = 168;
    private static final int workbenchPopupTitleHeight = 18;
    private static final int workbenchPopupVisibleGrip = 26;
    private static final int workbenchConsoleRightColumnWidth = 20;
    private static final int workbenchControlButtonSize = 16;
    private static final int workbenchControlButtonGap = 2;
    private static final int workbenchControlButtonInset = 3;
    private static final int workbenchRecipeHeroHeight = 134;
    private static final int workbenchRecipeInputVisibleLimit = 5;
    private static final int workbenchMaterialVisibleLimit = 5;
    private static final int workbenchMaterialRowHeight = 16;
    private static final int workbenchMaterialRowGap = 2;
    private static final int workbenchInputPagerButtonWidth = 10;
    private static final int workbenchInputPagerButtonGap = 2;
    private static final int workbenchSpeedValueWidth = 34;
    private static final int workbenchSpeedThumbWidth = 8;
    private static final int workbenchPathRowHeight = 20;
    private static final int workbenchStatusRailChipHeight = 14;
    private static final int workbenchStageRailChipHeight = 20;
    private static final int workbenchStatusRailChipGap = 3;
    private static final int workbenchStatusRailMinChipWidth = 52;
    private static final int workbenchStageRailMinChipWidth = 68;
    private static final int overlayFrameInset = 4;
    private static final boolean workbenchCountBadgesUseStandaloneSlotChrome = false;
    private static final boolean workbenchStatusChipsUseStandaloneSlotChrome = false;

    private @Nullable ExecutionSession session;
    private @Nullable ExecutionTelemetrySnapshot telemetrySnapshot;
    private @Nullable ExecutionOverlayViewModel viewModel;
    private int scrollX;
    private int scrollY;
    private int targetScrollX;
    private int targetScrollY;
    private boolean hasTargetScroll;
    private float zoom = defaultZoom;
    private float targetZoom = defaultZoom;
    private @Nullable String lastFocusNodeId;
    private @Nullable String lastLayoutRootId;
    private @Nullable String pendingFocusNodeId;
    private int pendingFocusTicks;
    private int focusRetargetCooldownTicks;
    private boolean dragging;
    private double dragStartX;
    private double dragStartY;
    private int dragStartScrollX;
    private int dragStartScrollY;
    private double smoothScrollX;
    private double smoothScrollY;
    private double scrollVelocityX;
    private double scrollVelocityY;
    private double zoomAnchorUnitX;
    private double zoomAnchorUnitY;
    private double zoomAnchorScreenX;
    private double zoomAnchorScreenY;
    private boolean hasZoomAnchor;
    private boolean followFocus = true;
    private LayoutMode layoutMode = LayoutMode.timeline;
    private WorkbenchDockWindow activeWorkbenchWindow = WorkbenchDockWindow.recipe;
    private int workbenchInputStripScrollX;
    private int workbenchPathScrollX;
    private int workbenchRecipeScrollY;
    private int workbenchTimelineScrollY;
    private int workbenchInspectScrollY;
    private int workbenchDockPageStart;
    private boolean workbenchPopupOpen;
    private boolean workbenchPopupDragging;
    private boolean workbenchSpeedDragging;
    private boolean workbenchRateEditing;
    private int workbenchPopupX = 18;
    private int workbenchPopupY = 72;
    private int workbenchPopupDragOffsetX;
    private int workbenchPopupDragOffsetY;
    private double workbenchSpeedDragOffsetX = workbenchSpeedThumbWidth * 0.5d;
    private String workbenchRateEditBuffer = "";
    private @Nullable ExecutionOverlayController workbenchController;
    private final dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState workbenchMainScrollState = createSmoothScrollState(ScrollDirection.vertical, 0.32f);
    private final dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState workbenchPathScrollState = createSmoothScrollState(ScrollDirection.horizontal, 0.35f);
    private final dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState workbenchInputStripScrollState = createSmoothScrollState(ScrollDirection.horizontal, 0.35f);
    private final dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState workbenchConsoleStatusScrollState = createSmoothScrollState(ScrollDirection.vertical, 0.3f);
    private final dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState workbenchConsoleMetricsScrollState = createSmoothScrollState(ScrollDirection.vertical, 0.3f);
    private final CompositeWidget<Widget> workbenchMainScrollEffectHost = new CompositeWidget<>();
    private final CompositeWidget<Widget> workbenchPathScrollEffectHost = new CompositeWidget<>();
    private final CompositeWidget<Widget> workbenchInputScrollEffectHost = new CompositeWidget<>();
    private final CompositeWidget<Widget> workbenchStatusTextScrollEffectHost = new CompositeWidget<>();
    private final CompositeWidget<Widget> workbenchMetricsTextScrollEffectHost = new CompositeWidget<>();

    private static dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState createSmoothScrollState(ScrollDirection direction, float smoothSpeed) {
        return dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState.create(direction)
            .autoContentSize(false)
            .showScrollbar(false)
            .draggable(false)
            .smooth(true)
            .smoothSpeed(smoothSpeed)
            .scrollSpeed(12);
    }

    private void attachScrollEffectHost(CompositeWidget<Widget> host, dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState state) {
        host.setInteractive(false);
        host.setActive(false);
        host.useEffect(UIEffects.scrollable(state));
        addWidget(host);
    }

    public ExecutionOverlayWidget() {
        setFocusable(true);
        attachScrollEffectHost(workbenchMainScrollEffectHost, workbenchMainScrollState);
        attachScrollEffectHost(workbenchPathScrollEffectHost, workbenchPathScrollState);
        attachScrollEffectHost(workbenchInputScrollEffectHost, workbenchInputStripScrollState);
        attachScrollEffectHost(workbenchStatusTextScrollEffectHost, workbenchConsoleStatusScrollState);
        attachScrollEffectHost(workbenchMetricsTextScrollEffectHost, workbenchConsoleMetricsScrollState);
        onMouseClicked((input, ctx) -> {
            if (useWorkbenchLayout) {
                return handleWorkbenchMouseClicked(input, ctx);
            }
            if (viewModel == null) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            var body = bodyLayout();
            if (!insideGraphViewport(local.x(), local.y(), body)) {
                return EventDispatch.pass;
            }
            if (input.isLeftClick() || input.key().getValue() == 2) {
                dragging = true;
                followFocus = false;
                hasZoomAnchor = false;
                dragStartX = local.x();
                dragStartY = local.y();
                dragStartScrollX = scrollX;
                dragStartScrollY = scrollY;
                targetScrollX = scrollX;
                targetScrollY = scrollY;
                hasTargetScroll = true;
                snapSmoothingToCurrent();
                ctx.cancel();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onMouseReleased((input, ctx) -> {
            if (useWorkbenchLayout) {
                if (workbenchSpeedDragging) {
                    workbenchSpeedDragging = false;
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                if (workbenchPopupDragging) {
                    workbenchPopupDragging = false;
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            if (!dragging) {
                return EventDispatch.pass;
            }
            dragging = false;
            ctx.cancel();
            return EventDispatch.consumed;
        });
        onMouseDragged((input, deltaX, deltaY, ctx) -> {
            if (useWorkbenchLayout) {
                if (workbenchSpeedDragging) {
                    FloatPos local = input.mouseRelative(this);
                    updateWorkbenchRateFromMouse(local.x(), local.y());
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                if (!workbenchPopupDragging) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                workbenchPopupX = (int) Math.round(local.x() - workbenchPopupDragOffsetX);
                workbenchPopupY = (int) Math.round(local.y() - workbenchPopupDragOffsetY);
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (!dragging || viewModel == null) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            scrollX = dragStartScrollX - (int) Math.round(local.x() - dragStartX);
            scrollY = dragStartScrollY - (int) Math.round(local.y() - dragStartY);
            targetScrollX = scrollX;
            targetScrollY = scrollY;
            hasTargetScroll = true;
            snapSmoothingToCurrent();
            ctx.cancel();
            return EventDispatch.consumed;
        });
        onMouseLeave((mouseX, mouseY, ctx) -> scene().setHoverTooltip(Tooltip.create()));
        onMouseScrolled((mouseX, mouseY, wheelX, wheelY, ctx) -> {
            if (mouseX < 0 || mouseY < 0 || mouseX > width() || mouseY > height()) {
                return EventDispatch.pass;
            }
            var body = bodyLayout();
            if (!insideGraphViewport(mouseX, mouseY, body)) {
                return EventDispatch.pass;
            }
            if (viewModel == null) {
                return EventDispatch.pass;
            }
            if (useWorkbenchLayout) {
                int localMouseX = (int) Math.round(mouseX);
                int localMouseY = (int) Math.round(mouseY);
                WorkbenchInputStripLayout recipeInputLayout = currentWorkbenchRecipeInputStripLayout(body);
                if (recipeInputLayout != null
                    && recipeInputLayout.paged()
                    && (insideRect(recipeInputLayout.stripBounds(), localMouseX, localMouseY)
                        || insideRect(recipeInputLayout.prevButton(), localMouseX, localMouseY)
                        || insideRect(recipeInputLayout.nextButton(), localMouseX, localMouseY))) {
                    int pageDelta = wheelY > 0 ? -recipeInputLayout.pageStep() : (wheelY < 0 ? recipeInputLayout.pageStep() : 0);
                    if (pageDelta != 0) {
                        scrollWorkbenchRecipeInputStripBy(recipeInputLayout, pageDelta);
                        ctx.cancel();
                        return EventDispatch.consumed;
                    }
                }
                if (Screen.hasShiftDown()) {
                    int delta = wheelY > 0 ? -18 : (wheelY < 0 ? 18 : 0);
                    Rect2i pathRow = workbenchPathRowBounds(body);
                    if (localMouseY >= pathRow.getY() && localMouseY <= pathRow.getY() + pathRow.getHeight()) {
                        configureHorizontalScrollRange(workbenchPathScrollState, 480);
                        workbenchPathScrollState.scrollBy(delta, 0.0f);
                    } else {
                        int inputMax = recipeInputLayout == null ? 320 : recipeInputLayout.maxScroll();
                        configureHorizontalScrollRange(workbenchInputStripScrollState, inputMax);
                        workbenchInputStripScrollState.scrollBy(delta, 0.0f);
                    }
                } else {
                    int delta = wheelY > 0 ? -16 : (wheelY < 0 ? 16 : 0);
                    configureVerticalScrollRange(workbenchMainScrollState, Math.max(0, workbenchContentHeight() - workbenchScrollViewportHeight(body)));
                    workbenchMainScrollState.scrollBy(0.0f, delta);
                }
                ctx.cancel();
                return EventDispatch.consumed;
            }

            if (Screen.hasControlDown()) {
                float previousTargetZoom = targetZoom;
                if (wheelY > 0) {
                    targetZoom = Math.min(maxZoom, targetZoom * zoomStepFactor);
                } else if (wheelY < 0) {
                    targetZoom = Math.max(minZoom, targetZoom / zoomStepFactor);
                }
                if (Math.abs(previousTargetZoom - targetZoom) < 0.0001f) {
                    return EventDispatch.pass;
                }
                double screenX = mouseX - body.graphX();
                double screenY = mouseY - body.bodyY();
                double safeZoom = Math.max(0.0001f, zoom);
                zoomAnchorUnitX = (scrollX + screenX) / safeZoom;
                zoomAnchorUnitY = (scrollY + screenY) / safeZoom;
                zoomAnchorScreenX = screenX;
                zoomAnchorScreenY = screenY;
                hasZoomAnchor = true;
                followFocus = false;
                scrollVelocityX = 0.0d;
                scrollVelocityY = 0.0d;
                ctx.cancel();
                return EventDispatch.consumed;
            }

            var layout = buildLayout(body.graphWidth());
            if (layout == null) {
                return EventDispatch.pass;
            }
            LayoutMetrics metrics = layoutMetrics();
            int minScrollX = minScrollX(body.graphWidth());
            int minScrollY = minScrollY(body.bodyHeight());
            int maxScrollX = maxScrollX(layout, body.graphWidth());
            int maxScrollY = maxScrollY(layout, body.bodyHeight());
            int step = Math.max(12, metrics.nodeHeight() / 2);
            if (Math.abs(wheelX) > Math.abs(wheelY) && maxScrollX > minScrollX) {
                followFocus = false;
                if (wheelX > 0) {
                    this.scrollX -= step;
                } else if (wheelX < 0) {
                    this.scrollX += step;
                }
            } else if (maxScrollY > minScrollY) {
                followFocus = false;
                if (wheelY > 0) {
                    this.scrollY -= step;
                } else if (wheelY < 0) {
                    this.scrollY += step;
                }
            }
            targetScrollX = scrollX;
            targetScrollY = scrollY;
            hasTargetScroll = true;
            snapSmoothingToCurrent();
            ctx.cancel();
            return EventDispatch.consumed;
        });
        onKeyPressed((input, ctx) -> {
            if (!useWorkbenchLayout || !workbenchRateEditing || !focused()) {
                return EventDispatch.pass;
            }
            return handleWorkbenchRateKeyPressed(input.key().getValue()) ? EventDispatch.consumed : EventDispatch.pass;
        });
        onCharTyped((codePoint, modifiers, ctx) -> {
            if (!useWorkbenchLayout || !workbenchRateEditing || !focused()) {
                return EventDispatch.pass;
            }
            return handleWorkbenchRateCharTyped(codePoint) ? EventDispatch.consumed : EventDispatch.pass;
        });
    }

    //region state

    public void setSession(@Nullable ExecutionSession session) {
        var previousFocus = lastFocusNodeId;
        this.session = session;
        this.telemetrySnapshot = null;
        this.viewModel = session == null ? null : ExecutionOverlayViewModel.derive(session);
        if (viewModel == null) {
            scrollX = 0;
            scrollY = 0;
            targetScrollX = 0;
            targetScrollY = 0;
            hasTargetScroll = false;
            zoom = defaultZoom;
            targetZoom = defaultZoom;
            hasZoomAnchor = false;
            scrollVelocityX = 0.0d;
            scrollVelocityY = 0.0d;
            lastFocusNodeId = null;
            lastLayoutRootId = null;
            pendingFocusNodeId = null;
            pendingFocusTicks = 0;
            focusRetargetCooldownTicks = 0;
            followFocus = true;
            workbenchRateEditing = false;
            workbenchRateEditBuffer = "";
            workbenchSpeedDragging = false;
            workbenchInputStripScrollX = 0;
            workbenchPathScrollX = 0;
            workbenchMainScrollState.resetScroll();
            workbenchPathScrollState.resetScroll();
            workbenchInputStripScrollState.resetScroll();
            workbenchConsoleStatusScrollState.resetScroll();
            workbenchConsoleMetricsScrollState.resetScroll();
            snapSmoothingToCurrent();
            return;
        }
        if (session != null && session.nextStepIndex() <= 0 && previousFocus == null && !hasTargetScroll) {
            followFocus = true;
        }
        if (focusRetargetCooldownTicks > 0) {
            focusRetargetCooldownTicks--;
        }
        var body = bodyLayout();
        var layout = buildLayout(body.graphWidth());
        if (layout == null) {
            return;
        }
        if (followFocus && !dragging) {
            clampScroll(layout, body.graphWidth(), body.bodyHeight());
        }
        if (followFocus) {
            boolean focusChanged = !Objects.equals(previousFocus, viewModel.focusNodeId());
            boolean centerOnFocus = focusChanged && session != null && session.nextStepIndex() <= 2;
            if (!focusChanged) {
                pendingFocusNodeId = null;
                pendingFocusTicks = 0;
            } else {
                String focusId = viewModel.focusNodeId();
                if (Objects.equals(pendingFocusNodeId, focusId)) {
                    pendingFocusTicks++;
                } else {
                    pendingFocusNodeId = focusId;
                    pendingFocusTicks = 1;
                }
            }
            boolean forceRetarget = focusNodeFarOutsideViewport(layout, body.graphWidth(), body.bodyHeight(), scrollX, scrollY);
            boolean stableFocus = pendingFocusTicks >= 2 && focusRetargetCooldownTicks <= 0;
            if (forceRetarget || stableFocus || !hasTargetScroll) {
                ScrollState aligned = focusAlignedScroll(layout, centerOnFocus, body.graphWidth(), body.bodyHeight(), scrollX, scrollY);
                targetScrollX = aligned.x();
                targetScrollY = aligned.y();
                hasTargetScroll = true;
                if (focusChanged) {
                    focusRetargetCooldownTicks = 3;
                }
            }
        } else if (!hasTargetScroll) {
            targetScrollX = scrollX;
            targetScrollY = scrollY;
            hasTargetScroll = true;
        }
        if (Math.abs(smoothScrollX) < 0.001d && Math.abs(smoothScrollY) < 0.001d && scrollX == 0 && scrollY == 0) {
            snapSmoothingToCurrent();
        }
        lastFocusNodeId = viewModel.focusNodeId();
    }

    public void refreshFromHost(ExecutionOverlayHost host) {
        setSession(host.executionOverlaySession());
        this.telemetrySnapshot = host instanceof ExecutionOverlayController controller
            ? controller.overlayTelemetrySnapshot()
            : null;
        if (host instanceof ExecutionOverlayController controller) {
            bindController(controller);
        }
    }

    public void bindController(@Nullable ExecutionOverlayController controller) {
        this.workbenchController = controller;
    }

    public @Nullable ExecutionOverlayViewModel viewModel() {
        return viewModel;
    }

    public @Nullable ExecutionSession session() {
        return session;
    }

    //endregion

    //region layout helpers

    public static int preferredPanelWidth(int availableWidth) {
        return jeiBookmarkAlignedPanelWidth;
    }

    static String debugDefaultWorkbenchDockId() {
        return WorkbenchDockWindow.recipe.id();
    }

    static int debugWorkbenchDockRailWidth() {
        return workbenchDockRailWidth;
    }

    static int debugWorkbenchRecipeHeroHeight() {
        return workbenchRecipeHeroHeight;
    }

    static boolean debugWorkbenchCountBadgesUseStandaloneSlotChrome() {
        return workbenchCountBadgesUseStandaloneSlotChrome;
    }

    static boolean debugWorkbenchStatusChipsUseStandaloneSlotChrome() {
        return workbenchStatusChipsUseStandaloneSlotChrome;
    }

    public static int bodyHeight(int totalHeight, int headerHeight, int footerHeight) {
        return Math.max(0, totalHeight - headerHeight - footerHeight);
    }

    private int activeFooterHeight() {
        return useWorkbenchLayout ? 0 : footerHeight;
    }

    private LayoutMetrics layoutMetrics() {
        int scaledNodeWidth = Math.max(30, Math.round(nodeWidth * zoom));
        int scaledNodeHeight = Math.max(18, Math.round(nodeHeight * zoom));
        int scaledGapX = Math.max(3, Math.round(nodeGapX * zoom));
        int scaledGapY = Math.max(2, Math.round(nodeGapY * zoom));
        int scaledPaddingX = Math.max(2, Math.round(graphPaddingX * zoom));
        int scaledPaddingY = Math.max(2, Math.round(graphPaddingY * zoom));
        return new LayoutMetrics(scaledPaddingX, scaledPaddingY, scaledNodeWidth, scaledNodeHeight, scaledGapX, scaledGapY);
    }

    //endregion

    //region rendering

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        canvas.texture(CirrusTextures.Background.Outlined.flat, 0, 0, width(), height());
        if (viewModel == null) {
            drawEmpty(canvas);
            return;
        }

        Font font = Minecraft.getInstance().font;
        if (useWorkbenchLayout) {
            var body = bodyLayout();
            syncWorkbenchEffectScrollSnapshots();
            clampWorkbenchScroll(body);
            drawWorkbenchScrollArea(canvas, font, body);
            drawWorkbenchPopup(canvas, font, body);
            updateWorkbenchTooltip(mouseX, mouseY, body);
            drawFooter(canvas, font);
            return;
        }
        drawHeader(canvas, font);
        var body = bodyLayout();
        animateZoom(body);
        var layout = buildLayout(body.graphWidth());
        if (layout != null) {
            if (followFocus && !dragging) {
                clampScroll(layout, body.graphWidth(), body.bodyHeight());
            }
            if (hasTargetScroll) {
                if (followFocus && !dragging) {
                    targetScrollX = Math.max(minScrollX(body.graphWidth()), Math.min(maxScrollX(layout, body.graphWidth()), targetScrollX));
                    targetScrollY = Math.max(minScrollY(body.bodyHeight()), Math.min(maxScrollY(layout, body.bodyHeight()), targetScrollY));
                }
                smoothScrollTowardsTarget();
                if (followFocus && !dragging) {
                    clampScroll(layout, body.graphWidth(), body.bodyHeight());
                }
            }
            drawBody(canvas, font, layout, body);
            if (body.infoWidth() > 0) {
                drawExecutionInfoPanel(canvas, font, layout, body);
            } else {
                drawCompactMonitorStrip(canvas, font, layout, body);
            }
            updateHoverTooltip(mouseX, mouseY, layout, body);
        } else {
            scene().setHoverTooltip(Tooltip.create());
        }
        drawFooter(canvas, font);
    }

    private void drawEmpty(SceneCanvas canvas) {
        canvas.drawString(Component.translatable("cirrus.ui.execution.overlay.empty").getString(), panelPadding, panelPadding, ExecutionOverlayComponentRenderer.textDim, false);
    }

    private EventDispatch handleWorkbenchMouseClicked(dev.vfyjxf.cloudlib.api.ui.InputContext input, dev.vfyjxf.cloudlib.api.event.context.BubbleContext ctx) {
        if (viewModel == null || !input.isLeftClick()) {
            return EventDispatch.pass;
        }
        FloatPos local = input.mouseRelative(this);
        int mouseX = (int) Math.round(local.x());
        int mouseY = (int) Math.round(local.y());
        BodyLayout body = bodyLayout();
        Rect2i speedValue = workbenchSpeedValueBounds(body);
        if (workbenchRateEditing && !insideRect(speedValue, mouseX, mouseY)) {
            finishWorkbenchRateEditing(true);
        }
        WorkbenchDockLayout dock = workbenchDockLayout(body);
        if (dock.paged() && dock.canPrev() && insideRect(dock.prevButton(), mouseX, mouseY)) {
            workbenchDockPageStart = Math.max(0, workbenchDockPageStart - 1);
            ctx.cancel();
            return EventDispatch.consumed;
        }
        if (dock.paged() && dock.canNext(WorkbenchDockWindow.values().length) && insideRect(dock.nextButton(), mouseX, mouseY)) {
            workbenchDockPageStart = Math.min(WorkbenchDockWindow.values().length - 1, workbenchDockPageStart + 1);
            ctx.cancel();
            return EventDispatch.consumed;
        }
        for (WorkbenchDockWindow window : WorkbenchDockWindow.values()) {
            if (insideRect(workbenchDockButtonBounds(body, window), mouseX, mouseY)) {
                switchWorkbenchDockWindow(window);
                ctx.cancel();
                return EventDispatch.consumed;
            }
        }
        WorkbenchInputStripLayout inputLayout = currentWorkbenchRecipeInputStripLayout(body);
        if (inputLayout != null && inputLayout.paged()) {
            if (insideRect(inputLayout.prevButton(), mouseX, mouseY) && inputLayout.canPrev(workbenchInputStripScrollX)) {
                scrollWorkbenchRecipeInputStripBy(inputLayout, -inputLayout.pageStep());
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (insideRect(inputLayout.nextButton(), mouseX, mouseY) && inputLayout.canNext(workbenchInputStripScrollX)) {
                scrollWorkbenchRecipeInputStripBy(inputLayout, inputLayout.pageStep());
                ctx.cancel();
                return EventDispatch.consumed;
            }
        }
        if (handleWorkbenchConsoleControlsClick(mouseX, mouseY, body)) {
            ctx.cancel();
            return EventDispatch.consumed;
        }
        if (!workbenchPopupOpen) {
            return EventDispatch.pass;
        }
        Rect2i popup = currentWorkbenchPopupBounds();
        if (!insideRect(popup, mouseX, mouseY)) {
            return EventDispatch.pass;
        }
        Rect2i closeButton = workbenchPopupCloseButtonBounds(popup);
        if (insideRect(closeButton, mouseX, mouseY)) {
            workbenchPopupOpen = false;
            ctx.cancel();
            return EventDispatch.consumed;
        }
        if (mouseY <= popup.getY() + workbenchPopupTitleHeight) {
            workbenchPopupDragging = true;
            workbenchPopupDragOffsetX = mouseX - popup.getX();
            workbenchPopupDragOffsetY = mouseY - popup.getY();
        }
        ctx.cancel();
        return EventDispatch.consumed;
    }

    private void drawHeader(SceneCanvas canvas, Font font) {
        if (useWorkbenchLayout) {
            return;
        }
        var model = ExecutionOverlayComponentRenderer.headerModelOf(viewModel);
        ExecutionOverlayComponentRenderer.drawHeader(canvas, font, model, 2, 2, width() - 4, headerHeight - 2, panelPadding);
    }

    private void drawFooter(SceneCanvas canvas, Font font) {
        if (useWorkbenchLayout) {
            return;
        }
        int footerY = height() - footerHeight;
        int footerX = 2;
        int footerW = Math.max(0, width() - 4);
        int footerH = Math.max(0, footerHeight - 2);
        canvas.texture(CirrusTextures.Background.Outlined.flat, footerX, footerY, footerW, footerH);
        canvas.texture(CirrusTextures.Background.flat, footerX + 1, footerY + 1, Math.max(0, footerW - 2), Math.max(0, footerH - 2));

        var model = ExecutionOverlayComponentRenderer.footerModelOf(viewModel, zoom);
        String summary = trimLabel(font, model.primaryText(), Math.max(40, footerW - panelPadding * 2));
        canvas.drawString(summary, footerX + panelPadding, footerY + 4, model.warning()
            ? ExecutionOverlayComponentRenderer.textWarning
            : ExecutionOverlayComponentRenderer.textSecondary, false);
        String trace = compactTraceTailFromPath();
        String branch = compactBranchLabel();
        String modeText = switch (layoutMode) {
            case timeline -> "Trace";
            case balanced -> "Tree";
            case depthFirst -> "Depth";
            case depthLanes -> "Depth+";
        };
        String statusLine = "[" + modeText + "] " + branch + (trace.isBlank() ? "" : " | " + trace);
        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
            statusLine = "ERR " + telemetrySnapshot.errorSummary();
        }
        String hintLine = trimLabel(font, statusLine + " | Drag LMB/Middle · Wheel Scroll · Ctrl+Wheel Zoom", Math.max(36, footerW - panelPadding * 2));
        canvas.drawString(hintLine, footerX + panelPadding, footerY + 18, telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null
            ? ExecutionOverlayComponentRenderer.textWarning
            : ExecutionOverlayComponentRenderer.textDim, false);
    }

    private boolean handleWorkbenchConsoleControlsClick(int mouseX, int mouseY, BodyLayout body) {
        if (workbenchController == null) {
            return false;
        }
        Rect2i step = workbenchStepButtonBounds(body);
        if (insideRect(step, mouseX, mouseY)) {
            workbenchController.overlayAdvanceSteps(1);
            return true;
        }
        Rect2i playback = workbenchPlaybackButtonBounds(body);
        if (insideRect(playback, mouseX, mouseY)) {
            workbenchController.overlayToggleAutoAdvance();
            return true;
        }
        Rect2i details = workbenchDetailsButtonBounds(body);
        if (insideRect(details, mouseX, mouseY)) {
            workbenchPopupOpen = !workbenchPopupOpen;
            return true;
        }
        Rect2i value = workbenchSpeedValueBounds(body);
        if (insideRect(value, mouseX, mouseY)) {
            beginWorkbenchRateEditing();
            return true;
        }
        Rect2i slider = workbenchSpeedSliderInteractiveBounds(body);
        if (insideRect(slider, mouseX, mouseY)) {
            workbenchSpeedDragging = true;
            workbenchRateEditing = false;
            workbenchSpeedDragOffsetX = resolveWorkbenchSpeedDragOffset(mouseX, mouseY, body);
            updateWorkbenchRateFromMouse(mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void updateWorkbenchRateFromMouse(double mouseX, double mouseY) {
        if (workbenchController == null) {
            return;
        }
        Rect2i slider = workbenchSpeedSliderBounds(bodyLayout());
        double trackWidth = Math.max(1.0d, slider.getWidth() - workbenchSpeedThumbWidth);
        double progress = (mouseX - workbenchSpeedDragOffsetX - slider.getX()) / trackWidth;
        progress = Math.max(0.0d, Math.min(1.0d, progress));
        workbenchController.overlaySetRatePerSecond(ExecutionOverlayControlsWidget.sliderToRate(progress));
    }

    private void beginWorkbenchRateEditing() {
        workbenchRateEditing = true;
        workbenchSpeedDragging = false;
        workbenchRateEditBuffer = formatWorkbenchRate(workbenchController == null ? 1.0d : workbenchController.overlayRatePerSecond());
    }

    private void finishWorkbenchRateEditing(boolean commit) {
        if (!workbenchRateEditing) {
            return;
        }
        if (commit) {
            applyWorkbenchRateBuffer();
        }
        workbenchRateEditing = false;
        workbenchRateEditBuffer = "";
    }

    private boolean applyWorkbenchRateBuffer() {
        if (workbenchController == null) {
            return false;
        }
        String value = workbenchRateEditBuffer.trim();
        if (value.isEmpty() || ".".equals(value)) {
            return false;
        }
        try {
            double parsed = Double.parseDouble(value);
            double clamped = ExecutionOverlayControlsWidget.clampRate(parsed);
            workbenchController.overlaySetRatePerSecond(clamped);
            workbenchRateEditBuffer = formatWorkbenchRate(clamped);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean handleWorkbenchRateKeyPressed(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                finishWorkbenchRateEditing(false);
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                finishWorkbenchRateEditing(true);
                yield true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!workbenchRateEditBuffer.isEmpty()) {
                    workbenchRateEditBuffer = workbenchRateEditBuffer.substring(0, workbenchRateEditBuffer.length() - 1);
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                workbenchRateEditBuffer = "";
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleWorkbenchRateCharTyped(int codePoint) {
        char c = (char) codePoint;
        if (c >= '0' && c <= '9') {
            workbenchRateEditBuffer += c;
            return true;
        }
        if (c == '.' && !workbenchRateEditBuffer.contains(".")) {
            if (workbenchRateEditBuffer.isEmpty()) {
                workbenchRateEditBuffer = "0";
            }
            workbenchRateEditBuffer += ".";
            return true;
        }
        return false;
    }

    private String formatWorkbenchRate(double rate) {
        return String.format(java.util.Locale.ROOT, "%.1f", ExecutionOverlayControlsWidget.clampRate(rate));
    }

    private double resolveWorkbenchSpeedDragOffset(double mouseX, double mouseY, BodyLayout body) {
        if (workbenchController == null) {
            return workbenchSpeedThumbWidth * 0.5d;
        }
        Rect2i slider = workbenchSpeedSliderBounds(body);
        int thumbX = workbenchSpeedThumbLeft(slider, workbenchController.overlayRatePerSecond());
        Rect2i thumb = new Rect2i(thumbX, slider.getY() - 2, workbenchSpeedThumbWidth, 8);
        if (insideRect(thumb, (int) Math.round(mouseX), (int) Math.round(mouseY))) {
            return mouseX - thumbX;
        }
        return workbenchSpeedThumbWidth * 0.5d;
    }

    private int workbenchSpeedThumbLeft(Rect2i slider, double rate) {
        double progress = ExecutionOverlayControlsWidget.rateToSlider(rate);
        return slider.getX() + (int) Math.round((slider.getWidth() - workbenchSpeedThumbWidth) * progress);
    }

    private void switchWorkbenchDockWindow(WorkbenchDockWindow next) {
        if (next == activeWorkbenchWindow) {
            return;
        }
        storeWorkbenchScrollForActiveWindow((int) Math.round(workbenchMainScrollState.scrollY()));
        activeWorkbenchWindow = next;
        workbenchDockPageStart = Math.max(0, next.ordinal());
        int restored = storedWorkbenchScrollForActiveWindow();
        workbenchMainScrollState.jumpTo(0.0f, restored);
        scrollY = restored;
        targetScrollY = restored;
        if (next != WorkbenchDockWindow.inspect) {
            workbenchPopupOpen = false;
        }
    }

    private int storedWorkbenchScrollForActiveWindow() {
        return switch (activeWorkbenchWindow) {
            case recipe -> workbenchRecipeScrollY;
            case timeline -> workbenchTimelineScrollY;
            case inspect -> workbenchInspectScrollY;
        };
    }

    private void storeWorkbenchScrollForActiveWindow(int value) {
        switch (activeWorkbenchWindow) {
            case recipe -> workbenchRecipeScrollY = value;
            case timeline -> workbenchTimelineScrollY = value;
            case inspect -> workbenchInspectScrollY = value;
        }
    }

    private static void configureHorizontalScrollRange(dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState state, int maxScroll) {
        int clamped = Math.max(0, maxScroll);
        int contentWidth = Math.max(0, state.viewportWidth()) + clamped;
        state.contentSize(contentWidth, state.contentHeight());
        state.scrollX(state.targetScrollX());
    }

    private static void configureVerticalScrollRange(dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState state, int maxScroll) {
        int clamped = Math.max(0, maxScroll);
        int contentHeight = Math.max(0, state.viewportHeight()) + clamped;
        state.contentSize(state.contentWidth(), contentHeight);
        state.scrollY(state.targetScrollY());
    }

    private void syncWorkbenchEffectScrollSnapshots() {
        BodyLayout body = bodyLayout();
        WorkbenchInputStripLayout recipeInputLayout = currentWorkbenchRecipeInputStripLayout(body);
        int inputMax = recipeInputLayout == null ? 320 : recipeInputLayout.maxScroll();
        configureHorizontalScrollRange(workbenchPathScrollState, 480);
        configureHorizontalScrollRange(workbenchInputStripScrollState, inputMax);
        workbenchPathScrollX = Math.max(0, Math.min(480, (int) Math.round(workbenchPathScrollState.scrollX())));
        workbenchInputStripScrollX = Math.max(0, Math.min(inputMax, (int) Math.round(workbenchInputStripScrollState.scrollX())));
    }

    private @Nullable WorkbenchInputStripLayout currentWorkbenchRecipeInputStripLayout(BodyLayout body) {
        if (viewModel == null || activeWorkbenchWindow != WorkbenchDockWindow.recipe) {
            return null;
        }
        ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        ExecutionOverlayNode inputNode = recipeFocus != null ? recipeFocus : focus;
        @Nullable InteractionStep.ExecuteRecipeStep previewStep = resolveWorkbenchPreviewStep();
        ImmutableList<WorkbenchInputEntry> inputs = previewStep == null
            ? (inputNode == null ? Lists.immutable.empty() : mergedWorkbenchInputs(inputNode))
            : mergedWorkbenchInputs(previewStep.inputs());
        ImmutableList<WorkbenchOutputEntry> outputs = previewStep == null
            ? workbenchSingleOutputEntries(recipeFocus == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(recipeFocus))
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());
        Rect2i content = workbenchContentBounds(body);
        int heroY = content.getY() + workbenchFixedConsoleHeight() + workbenchSectionGap() - scrollY;
        int outputRailW = Math.min(Math.max(20, content.getWidth() - 36), workbenchOutputStripWidth(outputs, 20, 3));
        int outputRailX = content.getX() + content.getWidth() - 8 - outputRailW;
        int inputRailX = content.getX() + 8;
        int inputRailY = heroY + 46;
        int inputRailW = Math.max(26, outputRailX - inputRailX - 12);
        return workbenchInputStripLayout(inputs, inputRailX, inputRailY, inputRailW, 20, workbenchRecipeInputVisibleLimit, true);
    }

    private void scrollWorkbenchRecipeInputStripBy(WorkbenchInputStripLayout layout, int delta) {
        if (delta == 0) {
            return;
        }
        configureHorizontalScrollRange(workbenchInputStripScrollState, layout.maxScroll());
        workbenchInputStripScrollState.scrollBy(delta, 0.0f);
    }

    private Rect2i workbenchDockRailBounds(BodyLayout body) {
        int x = body.graphX() + 1;
        int y = body.bodyY();
        int w = Math.max(0, body.graphWidth() - 2);
        int h = workbenchDockRailWidth;
        return new Rect2i(x, y, w, h);
    }

    private Rect2i workbenchWindowBounds(BodyLayout body) {
        Rect2i dockBar = workbenchDockRailBounds(body);
        int x = dockBar.getX();
        int y = dockBar.getY() + dockBar.getHeight() + workbenchDockRailGap;
        int h = Math.max(0, body.bodyHeight() - dockBar.getHeight() - workbenchDockRailGap);
        return new Rect2i(x, y, dockBar.getWidth(), h);
    }

    private Rect2i workbenchDockButtonBounds(BodyLayout body, WorkbenchDockWindow window) {
        WorkbenchDockLayout layout = workbenchDockLayout(body);
        if (!layout.shows(window)) {
            return new Rect2i(-1024, -1024, 0, 0);
        }
        int localIndex = window.ordinal() - layout.visibleStart();
        int x = layout.tabsX() + localIndex * (layout.tabWidth() + workbenchDockButtonGap);
        return new Rect2i(x, layout.tabY(), layout.tabWidth(), layout.tabHeight());
    }

    private WorkbenchDockLayout workbenchDockLayout(BodyLayout body) {
        Rect2i bar = workbenchDockRailBounds(body);
        WorkbenchDockWindow[] windows = WorkbenchDockWindow.values();
        int total = windows.length;
        int tabHeight = Math.max(12, workbenchDockButtonSize);
        int tabY = bar.getY() + Math.max(1, (bar.getHeight() - tabHeight) / 2);
        int pad = workbenchDockBarInset;
        int fullTabNeed = total * workbenchDockButtonMinWidth + Math.max(0, total - 1) * workbenchDockButtonGap + pad * 2;
        boolean paged = fullTabNeed > bar.getWidth();
        int pagerWidth = workbenchDockButtonSize;
        int tabsX = bar.getX() + pad;
        int availableW = Math.max(0, bar.getWidth() - pad * 2);
        Rect2i prev = new Rect2i(-1024, -1024, 0, 0);
        Rect2i next = new Rect2i(-1024, -1024, 0, 0);
        if (paged) {
            prev = new Rect2i(bar.getX() + pad, tabY, pagerWidth, tabHeight);
            next = new Rect2i(bar.getX() + bar.getWidth() - pad - pagerWidth, tabY, pagerWidth, tabHeight);
            availableW = Math.max(0, bar.getWidth() - pad * 2 - pagerWidth * 2 - workbenchDockButtonGap * 2);
            tabsX = prev.getX() + prev.getWidth() + workbenchDockButtonGap;
        }
        int visibleCount = Math.max(1, (availableW + workbenchDockButtonGap) / (workbenchDockButtonMinWidth + workbenchDockButtonGap));
        visibleCount = Math.min(total, visibleCount);
        int maxStart = Math.max(0, total - visibleCount);
        int start = paged ? Math.max(0, Math.min(maxStart, workbenchDockPageStart)) : 0;
        int tabWidth = Math.max(
            workbenchDockButtonMinWidth,
            (availableW - Math.max(0, visibleCount - 1) * workbenchDockButtonGap) / Math.max(1, visibleCount)
        );
        int usedW = visibleCount * tabWidth + Math.max(0, visibleCount - 1) * workbenchDockButtonGap;
        if (!paged) {
            tabsX = bar.getX() + Math.max(pad, (bar.getWidth() - usedW) / 2);
        }
        return new WorkbenchDockLayout(bar, prev, next, tabsX, tabY, tabWidth, tabHeight, start, visibleCount, paged);
    }

    private void clampWorkbenchScroll(BodyLayout body) {
        int maxY = Math.max(0, workbenchContentHeight() - workbenchScrollViewportHeight(body));
        configureVerticalScrollRange(workbenchMainScrollState, maxY);
        scrollY = Math.max(0, Math.min(maxY, (int) Math.round(workbenchMainScrollState.scrollY())));
        targetScrollY = Math.max(0, Math.min(maxY, (int) Math.round(workbenchMainScrollState.targetScrollY())));
        storeWorkbenchScrollForActiveWindow(scrollY);
    }

    private void drawWorkbenchScrollArea(SceneCanvas canvas, Font font, BodyLayout body) {
        Rect2i rail = workbenchDockRailBounds(body);
        drawWorkbenchDockRail(canvas, font, body, rail);
        int sectionGap = workbenchSectionGap();

        Rect2i window = workbenchWindowBounds(body);
        Rect2i content = workbenchContentBounds(body);
        int consoleX = content.getX();
        int consoleY = content.getY();
        int consoleW = content.getWidth();
        int consoleH = workbenchFixedConsoleHeight();
        drawWorkbenchConsole(canvas, font, consoleX, consoleY, consoleW, consoleH);

        int scrollX0 = content.getX();
        int scrollY0 = consoleY + consoleH + sectionGap;
        int scrollW = content.getWidth();
        int contentW = workbenchScrollContentWidth(body);
        int scrollH = Math.max(0, window.getHeight() - consoleH - sectionGap);
        var abs = absolutePos();
        canvas.withClip(abs.x() + scrollX0, abs.y() + scrollY0, scrollW, scrollH, () -> {
            int y = scrollY0 - scrollY;
            switch (activeWorkbenchWindow) {
                case recipe -> {
                    y = drawWorkbenchRecipeWindow(canvas, font, scrollX0, y, contentW);
                    y += sectionGap;
                    y = drawWorkbenchMaterialDemandSection(canvas, font, scrollX0, y, contentW);
                    y += sectionGap;
                    drawWorkbenchUpcomingSection(canvas, font, scrollX0, y, contentW);
                }
                case timeline -> {
                    y = drawWorkbenchProgressSection(canvas, font, scrollX0, y, contentW);
                    y += sectionGap;
                    y = drawWorkbenchLaneSection(canvas, font, scrollX0, y, contentW);
                    y += sectionGap;
                    drawWorkbenchUpcomingSection(canvas, font, scrollX0, y, contentW);
                }
                case inspect -> drawWorkbenchInspectWindow(canvas, font, scrollX0, y, contentW);
            }
        });
    }

    private void drawWorkbenchDockRail(SceneCanvas canvas, Font font, BodyLayout body, Rect2i rail) {
        WorkbenchDockLayout dock = workbenchDockLayout(body);
        canvas.fill(rail.getX(), rail.getY(), rail.getWidth(), rail.getHeight(), 0x18000000);
        if (dock.paged()) {
            Rect2i prev = dock.prevButton();
            Rect2i next = dock.nextButton();
            boolean canPrev = dock.canPrev();
            boolean canNext = dock.canNext(WorkbenchDockWindow.values().length);
            canvas.fill(prev.getX(), prev.getY(), prev.getWidth(), prev.getHeight(), canPrev ? 0x2A000000 : 0x14000000);
            canvas.fill(next.getX(), next.getY(), next.getWidth(), next.getHeight(), canNext ? 0x2A000000 : 0x14000000);
            canvas.drawCenteredString("<", prev.getX() + prev.getWidth() / 2, prev.getY() + 3, canPrev ? 0xFFDDE7F1 : 0xFF8A95A2);
            canvas.drawCenteredString(">", next.getX() + next.getWidth() / 2, next.getY() + 3, canNext ? 0xFFDDE7F1 : 0xFF8A95A2);
        }
        for (WorkbenchDockWindow window : WorkbenchDockWindow.values()) {
            Rect2i button = workbenchDockButtonBounds(body, window);
            if (button.getWidth() <= 0 || button.getHeight() <= 0) {
                continue;
            }
            boolean active = window == activeWorkbenchWindow;
            canvas.fill(button.getX(), button.getY(), button.getWidth(), button.getHeight(), active ? 0x2D5E86AE : 0x1F000000);
            int iconX = button.getX() + 3;
            int iconY = button.getY() + Math.max(1, (button.getHeight() - 12) / 2);
            int textX = button.getX() + 17;
            int textW = Math.max(8, button.getWidth() - 20);
            switch (window) {
                case recipe -> {
                    ExecutionOverlayNode focus = focusedWorkbenchRecipeNode();
                    ItemStack stack = focus == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(focus);
                    if (stack != null && !stack.isEmpty()) {
                        drawWorkbenchInlineItem(canvas, stack, iconX, iconY, 12);
                    } else {
                        canvas.drawCenteredString("R", iconX + 6, iconY + 2, active ? 0xFFEFF6FB : 0xFFD5E0EA);
                    }
                    canvas.drawString(trimLabel(font, window.label(), textW), textX, button.getY() + 3, active ? 0xFFEFF6FB : 0xFFD5E0EA, false);
                }
                case timeline -> {
                    canvas.texture(CirrusTextures.Icon.Calc.Path.branch, iconX, iconY, 12, 12);
                    canvas.drawString(trimLabel(font, window.label(), textW), textX, button.getY() + 3, active ? 0xFFEFF6FB : 0xFFD5E0EA, false);
                }
                case inspect -> {
                    canvas.drawCenteredString("i", iconX + 6, iconY + 2, active ? 0xFFEFF6FB : 0xFFD5E0EA);
                    canvas.drawString(trimLabel(font, window.label(), textW), textX, button.getY() + 3, active ? 0xFFEFF6FB : 0xFFD5E0EA, false);
                }
            }
        }
    }

    private int drawWorkbenchProgressSection(SceneCanvas canvas, Font font, int x, int y, int w) {
        int h = workbenchProgressSectionHeight();
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
        canvas.fill(x, y, w, h, 0x26FFFFFF);
        double progress = telemetrySnapshot == null
            ? (viewModel.totalStepCount() <= 0 ? 0.0d : viewModel.completedStepCount() / (double) viewModel.totalStepCount())
            : telemetrySnapshot.progress();
        int percent = (int) Math.round(progress * 100.0d);
        int slotSize = 20;
        int slotX = x + 6;
        int slotY = y + 8;
        ItemStack leadStack = recipeFocus == null
            ? (focus == null ? ItemStack.EMPTY : focus.iconStack())
            : workbenchRecipeDisplayStack(recipeFocus);
        drawWorkbenchDecorativeIcon(canvas, leadStack, slotX, slotY, slotSize);
        String percentText = percent + "%";
        canvas.drawString(percentText, x + 32, y + 8, ExecutionOverlayComponentRenderer.textPrimary, false);
        String countText = viewModel.completedStepCount() + "/" + viewModel.totalStepCount();
        canvas.drawString(countText, x + w - 8 - font.width(countText), y + 8, ExecutionOverlayComponentRenderer.textSecondary, false);
        drawProgressTrack(canvas, x + 32, y + 19, Math.max(36, w - 40), progress);
        String current = telemetrySnapshot != null && telemetrySnapshot.current() != null
            ? telemetrySnapshot.current().label()
            : (focus == null ? viewModel.title().getString() : focus.label().getString());
        canvas.drawString(trimLabel(font, current, Math.max(42, w - 38)), x + 32, y + 31, ExecutionOverlayComponentRenderer.textSecondary, false);
        int stageY = y + 43;
        drawWorkbenchStageStatusRail(canvas, font, x + 6, stageY, Math.max(24, w - 12));
        drawWorkbenchOperationalStatusRail(canvas, font, x + 6, stageY + workbenchStageRailChipHeight + 2, Math.max(24, w - 12));
        return y + h;
    }

    private int drawWorkbenchRecipeWindow(SceneCanvas canvas, Font font, int x, int y, int w) {
        int heroH = workbenchRecipeHeroHeight;
        ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        ExecutionOverlayNode inputNode = recipeFocus != null ? recipeFocus : focus;
        @Nullable InteractionStep.ExecuteRecipeStep previewStep = resolveWorkbenchPreviewStep();
        ImmutableList<WorkbenchInputEntry> inputs = previewStep == null
            ? (inputNode == null ? Lists.immutable.empty() : mergedWorkbenchInputs(inputNode))
            : mergedWorkbenchInputs(previewStep.inputs());
        ImmutableList<WorkbenchOutputEntry> outputs = previewStep == null
            ? workbenchSingleOutputEntries(recipeFocus == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(recipeFocus))
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());
        ItemStack outputStack = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).stack();
        double progress = telemetrySnapshot == null
            ? (viewModel.totalStepCount() <= 0 ? 0.0d : viewModel.completedStepCount() / (double) viewModel.totalStepCount())
            : telemetrySnapshot.progress();
        int completed = telemetrySnapshot == null ? viewModel.completedStepCount() : telemetrySnapshot.completedSteps();
        int total = telemetrySnapshot == null ? viewModel.totalStepCount() : telemetrySnapshot.totalSteps();
        ExecutionOverlayController.OverlayTargetProgress targetProgress = workbenchController == null
            ? null
            : workbenchController.overlayTargetProgress();
        long requiredOutput = targetProgress == null ? Math.max(0L, total) : targetProgress.required();
        long currentOutput = targetProgress == null ? Math.max(0L, completed) : targetProgress.current();
        long missingOutput = Math.max(0L, requiredOutput - currentOutput);

        canvas.texture(CirrusTextures.Background.Outlined.flat, x, y, w, heroH);

        int heroSlotSize = 24;
        int heroSlotX = x + 8;
        int heroSlotY = y + 14;
        drawWorkbenchItemSlot(canvas, outputStack, heroSlotX, heroSlotY, heroSlotSize, "");

        int textX = heroSlotX + heroSlotSize + 7;
        int textW = Math.max(24, w - (textX - x) - 8);
        drawWorkbenchOutputStrip(canvas, font, outputs, textX, y + 18, textW, 16, 4, 0);

        int outputRailW = Math.min(Math.max(20, w - 36), workbenchOutputStripWidth(outputs, 20, 3));
        int outputRailX = x + w - 8 - outputRailW;
        int inputRailX = x + 8;
        int inputRailY = y + 48;
        int inputRailW = Math.max(26, outputRailX - inputRailX - 12);
        WorkbenchInputStripLayout inputLayout = workbenchInputStripLayout(
            inputs,
            inputRailX,
            inputRailY,
            inputRailW,
            22,
            workbenchRecipeInputVisibleLimit,
            true
        );
        int clampedInputScroll = inputLayout.clampScroll(workbenchInputStripScrollX);
        drawWorkbenchInputPagerButtons(canvas, inputLayout, clampedInputScroll);
        Rect2i inputStripBounds = inputLayout.stripBounds();
        drawWorkbenchInputStrip(
            canvas,
            font,
            inputs,
            inputStripBounds.getX(),
            inputStripBounds.getY(),
            inputStripBounds.getWidth(),
            inputStripBounds.getHeight(),
            inputLayout.visibleSlots(),
            clampedInputScroll,
            false
        );
        canvas.texture(CirrusTextures.Icon.Small.arrowRight, outputRailX - 8, inputRailY + 8, 5, 6);
        drawWorkbenchOutputStrip(canvas, font, outputs, outputRailX, inputRailY, outputRailW, 22, 3, 0);

        String targetValue = compactMetricNumber(currentOutput) + "/" + compactMetricNumber(requiredOutput);
        String missingValue = compactMetricNumber(missingOutput);
        int nowY = y + 78;
        int segmentW = font.width(missingValue);
        int nowTextW = Math.max(22, w - 16 - segmentW - 8);
        canvas.drawString(trimLabel(font, targetValue, nowTextW), x + 8, nowY, 0xFF334A62, false);
        canvas.drawString(missingValue, x + w - 8 - segmentW, nowY, missingOutput > 0L ? 0xFFB86D6D : 0xFF6D85A0, false);
        drawProgressTrack(canvas, x + 4, y + 91, Math.max(24, w - 8), recipeFocus == null ? progress : laneRatio(recipeFocus));
        drawWorkbenchStageStatusRail(canvas, font, x + 8, y + 102, Math.max(24, w - 16));
        return y + heroH;
    }

    private int drawWorkbenchMaterialDemandSection(SceneCanvas canvas, Font font, int x, int y, int w) {
        ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> requirements = workbenchMaterialRequirements();
        int h = workbenchMaterialSectionHeight(requirements);
        canvas.fill(x, y, w, h, 0x16000000);
        int rowX = x + 4;
        int rowW = Math.max(18, w - 8);
        if (requirements.isEmpty()) {
            canvas.drawString(
                Component.translatable("cirrus.ui.execution.overlay.material.none").getString(),
                rowX + 2,
                y + Math.max(5, (h - 8) / 2),
                0xFF9FB3C7,
                false
            );
            return y + h;
        }
        int visible = Math.min(workbenchMaterialVisibleLimit, requirements.size());
        int rowY = y + 4;
        for (int i = 0; i < visible; i++) {
            ExecutionOverlayController.OverlayMaterialRequirement requirement = requirements.get(i);
            boolean shortage = requirement.missing() > 0L;
            canvas.fill(rowX, rowY, rowW, workbenchMaterialRowHeight, shortage ? 0x1E5B1D1D : 0x12000000);
            int iconSize = Math.max(12, workbenchMaterialRowHeight - 2);
            drawWorkbenchInlineItem(canvas, requirement.stack(), rowX + 1, rowY + 1, iconSize);
            int metricX = rowX + iconSize + 4;
            int metricY = rowY + 4;
            int metricMaxX = rowX + rowW - 3;
            metricX = drawWorkbenchMaterialMetric(canvas, font, metricX, metricY, metricMaxX, 0xFF6EAADF, compactMetricNumber(requirement.required()));
            metricX = drawWorkbenchMaterialMetric(canvas, font, metricX, metricY, metricMaxX, 0xFF86C59A, compactMetricNumber(requirement.available()));
            drawWorkbenchMaterialMetric(canvas, font, metricX, metricY, metricMaxX, shortage ? 0xFFE08A8A : 0xFF89D7A0, compactMetricNumber(requirement.missing()));
            rowY += workbenchMaterialRowHeight + workbenchMaterialRowGap;
        }
        if (requirements.size() > visible) {
            int hidden = requirements.size() - visible;
            String moreText = Component.translatable("cirrus.ui.execution.overlay.material.more", hidden).getString();
            canvas.drawString(trimLabel(font, moreText, Math.max(16, rowW - 4)), rowX + 2, rowY + 1, 0xFF9FB3C7, false);
        }
        return y + h;
    }

    private int drawWorkbenchMaterialMetric(SceneCanvas canvas, Font font, int x, int y, int maxX, int iconColor, String text) {
        if (x >= maxX) {
            return x;
        }
        int iconSize = 4;
        int textColor = 0xFFD7E4F1;
        canvas.fill(x, y + 1, iconSize, iconSize, iconColor);
        int textX = x + iconSize + 2;
        int maxTextW = Math.max(4, maxX - textX);
        String rendered = trimLabel(font, text, maxTextW);
        canvas.drawString(rendered, textX, y - 1, textColor, false);
        return textX + font.width(rendered) + 6;
    }

    private static String compactMetricNumber(long value) {
        long clamped = Math.max(0L, value);
        if (clamped >= 1_000_000L) {
            return (clamped / 1_000_000L) + "M";
        }
        if (clamped >= 1_000L) {
            return (clamped / 1_000L) + "k";
        }
        return Long.toString(clamped);
    }

    private void drawWorkbenchInspectWindow(SceneCanvas canvas, Font font, int x, int y, int w) {
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        int sectionGap = workbenchSectionGap();
        int summaryH = workbenchInspectSummaryHeight();
        int pathH = workbenchInspectPathHeight();
        int diagnosticsH = workbenchInspectDiagnosticsHeight();

        String nowLabel = telemetrySnapshot != null && telemetrySnapshot.current() != null
            ? telemetrySnapshot.current().label()
            : (focus == null ? "-" : focus.label().getString());
        String stepLabel = focus == null ? "-" : resolveCurrentStepLabel(focus);
        String rawStatus = telemetrySnapshot == null ? viewModel.statusLabel().getString() : telemetrySnapshot.status();
        String fallbackStatus = viewModel == null ? "running" : statusTextOf(viewModel.status());
        String statusLabel = isAbstractStatusWord(rawStatus) ? rawStatus : fallbackStatus;
        int completed = telemetrySnapshot == null ? viewModel.completedStepCount() : telemetrySnapshot.completedSteps();
        int total = telemetrySnapshot == null ? viewModel.totalStepCount() : telemetrySnapshot.totalSteps();
        double rate = telemetrySnapshot == null ? 1.0d : telemetrySnapshot.ratePerSecond();

        canvas.fill(x, y, w, summaryH, 0x26FFFFFF);
        int textX = x + 6;
        int textW = Math.max(22, w - 12);
        int rowY = y + 6;
        rowY = drawWrappedText(canvas, font, nowLabel, textX, rowY, textW, 2, 0xFFF0F6FB);
        rowY = drawWrappedText(canvas, font, stepLabel, textX, rowY, textW, 2, 0xFFD5E0EA);
        String statLine = trimLabel(
            font,
            statusLabel + "  " + completed + "/" + total + "  x" + formatWorkbenchRate(rate),
            textW
        );
        canvas.drawString(statLine, textX, Math.min(y + summaryH - 10, rowY + 1), 0xFFBDD4EC, false);
        y += summaryH + sectionGap;

        canvas.fill(x, y, w, pathH, 0x20000000);
        drawWorkbenchPathRail(canvas, font, x + 6, y + 6, Math.max(20, w - 12));
        y += pathH + sectionGap;

        canvas.fill(x, y, w, diagnosticsH, 0x1E000000);
        int diagnosticsX = x + 5;
        int diagnosticsW = Math.max(20, w - 10);
        int diagnosticsY = y + 4;
        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
            String errorTitle = Component.translatable("cirrus.ui.execution.overlay.summary.error").getString();
            diagnosticsY = drawWrappedText(
                canvas,
                font,
                errorTitle + ": " + telemetrySnapshot.errorSummary(),
                diagnosticsX,
                diagnosticsY,
                diagnosticsW,
                2,
                0xFFE26B6B
            );
            if (telemetrySnapshot.errorDetail() != null && !telemetrySnapshot.errorDetail().isBlank()) {
                drawWrappedText(canvas, font, telemetrySnapshot.errorDetail(), diagnosticsX, diagnosticsY, diagnosticsW, 2, 0xFFD9E3EE);
            }
            return;
        }

        ImmutableList<String> warnings = telemetrySnapshot == null ? Lists.immutable.empty() : telemetrySnapshot.warnings();
        String warnTitle = Component.translatable("cirrus.ui.execution.overlay.summary.warn").getString();
        if (warnings.isEmpty()) {
            canvas.drawString(warnTitle + " 0", diagnosticsX, diagnosticsY, 0xFF9FB3C7, false);
            return;
        }

        canvas.drawString(warnTitle + " " + warnings.size(), diagnosticsX, diagnosticsY, 0xFFE3B56B, false);
        diagnosticsY += 9;
        int warningLines = Math.min(2, warnings.size());
        for (int i = 0; i < warningLines; i++) {
            diagnosticsY = drawWrappedText(canvas, font, warnings.get(i), diagnosticsX, diagnosticsY, diagnosticsW, 2, 0xFFD9E3EE);
        }
    }

    private int drawWorkbenchLaneSection(SceneCanvas canvas, Font font, int x, int y, int w) {
        ImmutableList<ExecutionOverlayNode> laneNodes = workbenchLaneNodes();
        int sectionH = workbenchLaneSectionHeight(laneNodes);
        canvas.fill(x, y, w, sectionH, 0x20000000);
        int rowY = y + 4;
        for (ExecutionOverlayNode node : laneNodes) {
            int rowH = workbenchLaneRowHeight(node);
            drawWorkbenchLaneRow(canvas, font, node, x + 4, rowY, w - 8, rowH);
            rowY += rowH + workbenchLaneRowGap();
        }
        return y + sectionH;
    }

    private void drawWorkbenchConsoleControls(SceneCanvas canvas, Font font, BodyLayout body) {
        Rect2i details = workbenchDetailsButtonBounds(body);
        Rect2i playback = workbenchPlaybackButtonBounds(body);
        Rect2i step = workbenchStepButtonBounds(body);
        Rect2i speed = workbenchSpeedRowBounds(body);
        Rect2i slider = workbenchSpeedSliderBounds(body);
        Rect2i value = workbenchSpeedValueBounds(body);

        canvas.fill(step.getX(), step.getY(), step.getWidth(), step.getHeight(), 0x2A000000);
        int stepLabelY = step.getY() + Math.max(3, (step.getHeight() - 8) / 2);
        canvas.drawCenteredString(">>", step.getX() + step.getWidth() / 2, stepLabelY, 0xFFDDE7F1);

        boolean auto = workbenchController != null && workbenchController.overlayAutoAdvanceEnabled();
        canvas.fill(playback.getX(), playback.getY(), playback.getWidth(), playback.getHeight(), auto ? 0x3F73552A : 0x2A000000);
        int playbackLabelY = playback.getY() + Math.max(3, (playback.getHeight() - 8) / 2);
        canvas.drawCenteredString(auto ? "||" : ">", playback.getX() + playback.getWidth() / 2, playbackLabelY, auto ? 0xFFECCF86 : 0xFFDDE7F1);

        canvas.fill(details.getX(), details.getY(), details.getWidth(), details.getHeight(), workbenchPopupOpen ? 0x355A86AE : 0x2A000000);
        int detailsLabelY = details.getY() + Math.max(3, (details.getHeight() - 8) / 2);
        canvas.drawCenteredString("i", details.getX() + details.getWidth() / 2, detailsLabelY, 0xFFDDE7F1);

        canvas.fill(speed.getX(), speed.getY(), speed.getWidth(), speed.getHeight(), 0x1E000000);
        double rate = workbenchController == null ? 1.0d : workbenchController.overlayRatePerSecond();
        String rateText = workbenchRateEditing ? workbenchRateEditBuffer : formatWorkbenchRate(rate);
        canvas.texture(CirrusTextures.Icon.Sign.Energy.time, speed.getX() + 4, speed.getY() + 4, 9, 9);
        canvas.fill(slider.getX(), slider.getY(), slider.getWidth(), slider.getHeight(), 0x3348505A);
        int fillW = Math.max(0, Math.min(slider.getWidth(), (int) Math.round(slider.getWidth() * ExecutionOverlayControlsWidget.rateToSlider(rate))));
        if (fillW > 0) {
            canvas.fill(slider.getX(), slider.getY(), fillW, slider.getHeight(), 0xFF6EAAE6);
        }
        int thumbX = workbenchSpeedThumbLeft(slider, rate);
        canvas.fill(thumbX, slider.getY() - 2, workbenchSpeedThumbWidth, 8, 0xFFBFD8EE);
        canvas.fill(value.getX(), value.getY(), value.getWidth(), value.getHeight(), workbenchRateEditing ? 0x2E5A86AE : 0x26000000);
        int rateTextWidth = font.width(rateText);
        int rateTextX = value.getX() + Math.max(4, (value.getWidth() - rateTextWidth) / 2);
        canvas.drawString(rateText, rateTextX, value.getY() + 2, 0xFFDDF2FF, false);
        if (workbenchRateEditing && focused()) {
            int caretX = Math.min(value.getX() + value.getWidth() - 4, rateTextX + rateTextWidth + 1);
            canvas.fill(caretX, value.getY() + 2, 1, 8, 0xFFEAF4FF);
        }
    }

    private void drawWorkbenchPathRail(SceneCanvas canvas, Font font, int x, int y, int w) {
        ImmutableList<ExecutionOverlayNode> trail = pathContextNodes();
        if (trail.isEmpty() && viewModel != null) {
            trail = Lists.immutable.of(viewModel.root());
        }
        final ImmutableList<ExecutionOverlayNode> finalTrail = trail;
        int clipX = x;
        int clipW = Math.max(20, w);
        int textY = y + 2;
        int px = x - workbenchPathScrollX;
        String currentRootId = currentSubgraphRootId();
        var abs = absolutePos();
        canvas.withClip(abs.x() + clipX, abs.y() + y, clipW, 16, () -> {
            canvas.texture(finalTrail.size() > 1 ? CirrusTextures.Icon.Calc.Path.branch : CirrusTextures.Icon.Calc.Path.tree, px, y + 1, 12, 12);
            int chipX = px + 14;
            for (int i = 0; i < finalTrail.size(); i++) {
                ExecutionOverlayNode node = finalTrail.get(i);
                boolean active = Objects.equals(node.nodeId(), currentRootId);
                if (i > 0) {
                    canvas.texture(CirrusTextures.Icon.Small.arrowRight, chipX, textY + 1, 5, 6);
                    chipX += 9;
                }
                String label = compactWorkbenchLabel(node.label().getString(), 2);
                int labelW = font.width(label);
                if (active) {
                    canvas.fill(chipX - 2, textY - 1, labelW + 4, 11, 0x557FAFD6);
                }
                canvas.drawString(label, chipX, textY, active ? 0xFF7FAFD6 : 0xFFCBD6E1, false);
                chipX += labelW + 1;
            }
        });
    }

    private void drawWorkbenchUpcomingSection(SceneCanvas canvas, Font font, int x, int y, int w) {
        ImmutableList<WorkbenchQueueRow> rows = workbenchQueueRows();
        int cardCount = Math.max(1, rows.size());
        int h = 6 + cardCount * workbenchUpcomingCardHeight() + Math.max(0, cardCount - 1) * workbenchUpcomingCardGap() + 4;
        canvas.texture(CirrusTextures.Background.inset, x, y, w, h);
        int rowY = y + 4;
        if (rows.isEmpty()) {
            canvas.drawString(
                Component.translatable("cirrus.ui.execution.overlay.status.completed").getString(),
                x + 6,
                rowY + 7,
                ExecutionOverlayComponentRenderer.textSecondary,
                false
            );
            return;
        }
        for (WorkbenchQueueRow row : rows) {
            drawWorkbenchUpcomingCard(canvas, font, row, x + 3, rowY, w - 6, workbenchUpcomingCardHeight());
            rowY += workbenchUpcomingCardHeight() + workbenchUpcomingCardGap();
        }
    }

    private void drawWorkbenchConsole(SceneCanvas canvas, Font font, int x, int y, int w, int h) {
        canvas.texture(CirrusTextures.Background.Outlined.flat, x, y, w, h);
        Rect2i summaryBounds = workbenchConsoleTargetSummaryBounds(bodyLayout());
        drawWorkbenchConsoleTargetSummary(
            canvas,
            font,
            summaryBounds.getX(),
            summaryBounds.getY(),
            summaryBounds.getWidth(),
            summaryBounds.getHeight()
        );
        drawWorkbenchConsoleControls(canvas, font, bodyLayout());
        Rect2i pathBounds = workbenchPathRowBounds(bodyLayout());
        canvas.fill(pathBounds.getX(), pathBounds.getY(), pathBounds.getWidth(), pathBounds.getHeight(), 0x22000000);
        drawWorkbenchPathRail(
            canvas,
            font,
            pathBounds.getX() + 3,
            pathBounds.getY() + Math.max(1, (pathBounds.getHeight() - 14) / 2),
            Math.max(20, pathBounds.getWidth() - 6)
        );
    }

    private void drawWorkbenchConsoleTargetSummary(SceneCanvas canvas, Font font, int x, int y, int w, int h) {
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
        if (focus == null && recipeFocus == null) {
            return;
        }

        @Nullable InteractionStep.ExecuteRecipeStep previewStep = resolveWorkbenchPreviewStep();
        ItemStack fallbackOutput = recipeFocus == null
            ? (focus == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(focus))
            : workbenchRecipeDisplayStack(recipeFocus);
        if (fallbackOutput == null) {
            fallbackOutput = ItemStack.EMPTY;
        }

        ImmutableList<WorkbenchOutputEntry> previewOutputs = previewStep == null
            ? workbenchSingleOutputEntries(fallbackOutput)
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());
        if (previewOutputs.isEmpty() && !fallbackOutput.isEmpty()) {
            previewOutputs = workbenchSingleOutputEntries(fallbackOutput);
        }

        int completed = telemetrySnapshot == null ? viewModel.completedStepCount() : telemetrySnapshot.completedSteps();
        int total = telemetrySnapshot == null ? viewModel.totalStepCount() : telemetrySnapshot.totalSteps();
        double progress = telemetrySnapshot == null
            ? (total <= 0 ? 0.0d : completed / (double) total)
            : telemetrySnapshot.progress();
        int queued = Math.max(0, workbenchQueueRows().size() - 1);
        double rate = workbenchController != null
            ? workbenchController.overlayRatePerSecond()
            : (telemetrySnapshot == null ? 1.0d : telemetrySnapshot.ratePerSecond());
        int progressPercent = Math.max(0, Math.min(100, (int) Math.round(progress * 100.0d)));

        ExecutionOverlayController.OverlayTargetProgress targetProgress = workbenchController == null
            ? null
            : workbenchController.overlayTargetProgress();
        long requiredOutput = targetProgress == null ? Math.max(0L, total) : targetProgress.required();
        long currentOutput = targetProgress == null
            ? Math.max(0L, completed)
            : Math.min(targetProgress.required(), targetProgress.current());
        long missingOutput = Math.max(0L, requiredOutput - currentOutput);

        canvas.texture(CirrusTextures.Background.flat, x, y, w, h);

        int stripX = x + 4;
        int stripW = Math.max(20, w - 8);
        int stripY = y + 6;
        drawWorkbenchOutputStrip(canvas, font, previewOutputs, stripX, stripY, stripW, 14, 4, 0);

        drawProgressTrack(canvas, stripX, y + 23, stripW, progress);

        String targetValue = compactMetricNumber(currentOutput) + "/" + compactMetricNumber(requiredOutput);
        String runValue = "x" + formatWorkbenchRate(rate) + " · " + queued;
        String progressValue = compactMetricNumber(missingOutput) + " · " + progressPercent + "%";
        drawWorkbenchSummaryMetricRow(canvas, font, stripX, y + 31, stripW, targetValue, 0xFF6FA6DA, 0xFFCFDBE7);
        drawWorkbenchSummaryMetricRow(canvas, font, stripX, y + 41, stripW, runValue, 0xFF8BC09A, 0xFFC7D8EA);
        drawWorkbenchSummaryMetricRow(
            canvas,
            font,
            stripX,
            y + 51,
            stripW,
            progressValue,
            missingOutput > 0L ? 0xFFD98A8A : 0xFF7DA8E3,
            0xFFD3E1F2
        );
    }
    private void drawWorkbenchSummaryMetricRow(
        SceneCanvas canvas,
        Font font,
        int x,
        int y,
        int w,
        String value,
        int iconColor,
        int textColor
    ) {
        if (w <= 10) {
            return;
        }
        int iconSize = 10;
        int iconY = y - 1;
        canvas.fill(x, iconY, iconSize, iconSize, iconColor);
        int textX = x + iconSize + 4;
        int textW = Math.max(8, w - (textX - x));
        canvas.drawString(trimLabel(font, value, textW), textX, y, textColor, false);
    }

    private void drawWorkbenchLaneRow(SceneCanvas canvas, Font font, ExecutionOverlayNode node, int x, int y, int w, int h) {
        boolean focused = isWorkbenchFocusedNode(node);
        int topY = y + 4;
        int stripH = focused ? 18 : 16;
        int statusSize = focused ? 18 : 16;
        int statusX = x + 6;
        int inputX = statusX + statusSize + 6;

        canvas.fill(x, y, w, h, focused ? 0x243E5A77 : 0x16000000);
        if (focused) {
            int dividerX = x + 3;
            int dividerY = y + 3;
            int dividerW = Math.max(20, w - 6);
            // Keep timeline separators clean and readable in-motion: draw a simple 2px line instead of textured noise.
            canvas.fill(dividerX, dividerY, dividerW, 1, 0x59FFFFFF);
            canvas.fill(dividerX, dividerY + 1, dividerW, 1, 0x1F000000);
        }

        String statusGlyph = workbenchStatusGlyph(node.status());
        int glyphX = statusX + Math.max(2, statusSize / 2 - font.width(statusGlyph) / 2);
        canvas.drawString(statusGlyph, glyphX, topY + Math.max(4, (statusSize - 8) / 2), ExecutionOverlayComponentRenderer.statusColor(node.status()), false);

        ImmutableList<WorkbenchInputEntry> inputs = mergedWorkbenchInputs(node);
        boolean recipeNode = isWorkbenchRecipeNode(node, inputs);
        if (recipeNode) {
            @Nullable InteractionStep.ExecuteRecipeStep previewStep = focused ? resolveWorkbenchPreviewStep() : null;
            ImmutableList<WorkbenchOutputEntry> outputs = previewStep == null
                ? workbenchSingleOutputEntries(workbenchRecipeDisplayStack(node))
                : mergedWorkbenchOutputs(previewStep.expectedOutputs());
            int availableW = Math.max(26, w - (inputX - x) - 14);
            int outputRailW = Math.min(
                availableW,
                workbenchOutputStripWidth(outputs, stripH, focused ? 3 : 2)
            );
            int inputIdealW = Math.min(
                Math.max(24, availableW - outputRailW - 8),
                workbenchInputStripWidth(inputs, stripH, focused ? 6 : 5)
            );
            int compactClusterW = inputIdealW + 8 + outputRailW;
            int inputW;
            int outputRailX;
            if (compactClusterW <= availableW) {
                inputW = inputIdealW;
                outputRailX = inputX + inputW + 8;
            } else {
                outputRailX = x + w - outputRailW - 6;
                inputW = Math.max(26, outputRailX - inputX - 8);
            }
            int arrowX = outputRailX - 6;
            drawWorkbenchInputStrip(canvas, font, inputs, inputX, topY, inputW, stripH);
            canvas.texture(CirrusTextures.Icon.Small.arrowRight, arrowX, topY + Math.max(4, (stripH - 6) / 2), 5, 6);
            drawWorkbenchOutputStrip(canvas, font, outputs, outputRailX, topY, outputRailW, stripH, focused ? 3 : 2, 0);
            if (focused) {
                drawProgressTrack(canvas, x + 6, y + h - 8, Math.max(24, w - 12), laneRatio(node));
            } else {
                drawProgressTrack(canvas, x + 8, y + h - 7, Math.max(24, w - 16), laneRatio(node));
            }
        } else {
            drawWorkbenchStatusLaneStrip(canvas, font, node, inputX, topY, Math.max(26, w - (inputX - x) - 8), stripH, focused);
            if (focused) {
                drawProgressTrack(canvas, x + 6, y + h - 8, Math.max(24, w - 12), laneRatio(node));
            }
        }
    }

    private void drawWorkbenchInputStrip(SceneCanvas canvas, Font font, ImmutableList<WorkbenchInputEntry> inputs, int x, int y, int w, int h) {
        drawWorkbenchInputStrip(canvas, font, inputs, x, y, w, h, 6, 0, true);
    }

    private void drawWorkbenchInputStrip(SceneCanvas canvas, Font font, ImmutableList<WorkbenchInputEntry> inputs, int x, int y, int w, int h, int visibleLimit, int scrollOffset) {
        drawWorkbenchInputStrip(canvas, font, inputs, x, y, w, h, visibleLimit, scrollOffset, true);
    }

    private void drawWorkbenchInputStrip(SceneCanvas canvas, Font font, ImmutableList<WorkbenchInputEntry> inputs, int x, int y, int w, int h, int visibleLimit, int scrollOffset, boolean drawOverflowBadge) {
        canvas.fill(x, y, w, h, 0x18000000);
        int slotSize = Math.max(12, h - 2);
        int slotY = y + Math.max(1, (h - slotSize) / 2);
        int clipW = Math.max(0, w - 2);
        int visible = Math.max(1, Math.min(visibleLimit, Math.max(1, clipW / slotSize)));
        int maxScroll = Math.max(0, (Math.max(0, inputs.size() - visible)) * slotSize);
        int clampedScroll = Math.max(0, Math.min(maxScroll, scrollOffset));
        int startIndex = inputs.isEmpty() ? 0 : Math.min(inputs.size() - 1, clampedScroll / slotSize);
        int intraOffset = clampedScroll - startIndex * slotSize;
        var abs = absolutePos();
        canvas.withClip(abs.x() + x + 1, abs.y() + y + 1, clipW, Math.max(0, h - 2), () -> {
            int slotX = x + 1 - intraOffset;
            int rendered = 0;
            for (int i = startIndex; i < inputs.size() && rendered < visible && slotX < x + w - 1; i++, rendered++) {
                WorkbenchInputEntry entry = inputs.get(i);
                if (rendered > 0) {
                    canvas.fill(slotX - 1, y + 3, 1, Math.max(1, h - 6), 0x335F6570);
                }
                drawWorkbenchInlineItem(canvas, entry.stack(), slotX, slotY, slotSize);
                slotX += slotSize;
            }
            if (drawOverflowBadge && startIndex + rendered < inputs.size()) {
                if (rendered > 0) {
                    canvas.fill(slotX - 1, y + 3, 1, Math.max(1, h - 6), 0x335F6570);
                }
                drawWorkbenchRailTextCell(canvas, font, slotX, slotY, slotSize, "+" + (inputs.size() - (startIndex + rendered)));
            }
            if (inputs.isEmpty()) {
                drawWorkbenchRailTextCell(canvas, font, slotX, slotY, slotSize, "-");
            }
        });
    }

    private WorkbenchInputStripLayout workbenchInputStripLayout(
        ImmutableList<WorkbenchInputEntry> inputs,
        int x,
        int y,
        int w,
        int h,
        int visibleLimit,
        boolean paged
    ) {
        int slotSize = Math.max(12, h - 2);
        int stripX = x;
        int stripW = Math.max(slotSize + 2, w);
        Rect2i prev = new Rect2i(-1024, -1024, 0, 0);
        Rect2i next = new Rect2i(-1024, -1024, 0, 0);
        int visibleByWidth = Math.max(1, Math.max(0, stripW - 2) / slotSize);
        int visibleSlots = Math.max(1, Math.min(visibleLimit, visibleByWidth));
        boolean showPager = paged && inputs.size() > visibleSlots;
        if (showPager) {
            int buttonW = Math.min(Math.max(8, h), workbenchInputPagerButtonWidth);
            int reserved = buttonW * 2 + workbenchInputPagerButtonGap * 2;
            stripW = Math.max(slotSize + 2, w - reserved);
            stripX = x + buttonW + workbenchInputPagerButtonGap;
            prev = new Rect2i(x, y, buttonW, h);
            next = new Rect2i(x + w - buttonW, y, buttonW, h);
            visibleByWidth = Math.max(1, Math.max(0, stripW - 2) / slotSize);
            visibleSlots = Math.max(1, Math.min(visibleLimit, visibleByWidth));
        }
        int maxScroll = Math.max(0, (Math.max(0, inputs.size() - visibleSlots)) * slotSize);
        int pageStep = Math.max(slotSize, visibleSlots * slotSize);
        return new WorkbenchInputStripLayout(new Rect2i(stripX, y, stripW, h), prev, next, visibleSlots, maxScroll, pageStep, showPager);
    }

    private void drawWorkbenchInputPagerButtons(SceneCanvas canvas, WorkbenchInputStripLayout layout, int scrollOffset) {
        if (!layout.paged()) {
            return;
        }
        Rect2i prev = layout.prevButton();
        Rect2i next = layout.nextButton();
        boolean canPrev = layout.canPrev(scrollOffset);
        boolean canNext = layout.canNext(scrollOffset);
        canvas.fill(prev.getX(), prev.getY(), prev.getWidth(), prev.getHeight(), canPrev ? 0x2A000000 : 0x12000000);
        canvas.fill(next.getX(), next.getY(), next.getWidth(), next.getHeight(), canNext ? 0x2A000000 : 0x12000000);
        canvas.drawCenteredString("<", prev.getX() + prev.getWidth() / 2, prev.getY() + Math.max(3, (prev.getHeight() - 8) / 2), canPrev ? 0xFFDDE7F1 : 0xFF8A95A2);
        canvas.drawCenteredString(">", next.getX() + next.getWidth() / 2, next.getY() + Math.max(3, (next.getHeight() - 8) / 2), canNext ? 0xFFDDE7F1 : 0xFF8A95A2);
    }

    private void drawWorkbenchOutputStrip(SceneCanvas canvas, Font font, ImmutableList<WorkbenchOutputEntry> outputs, int x, int y, int w, int h, int visibleLimit, int scrollOffset) {
        canvas.fill(x, y, w, h, 0x18000000);
        int slotSize = Math.max(12, h - 2);
        int slotY = y + Math.max(1, (h - slotSize) / 2);
        var abs = absolutePos();
        canvas.withClip(abs.x() + x + 1, abs.y() + y + 1, Math.max(0, w - 2), Math.max(0, h - 2), () -> {
            int slotX = x + 1 - scrollOffset;
            if (outputs.isEmpty()) {
                drawWorkbenchRailTextCell(canvas, font, slotX, slotY, slotSize, "-");
                return;
            }
            int visible = Math.min(Math.max(1, visibleLimit), outputs.size());
            for (int i = 0; i < visible; i++) {
                WorkbenchOutputEntry entry = outputs.get(i);
                if (i > 0) {
                    canvas.fill(slotX - 1, y + 3, 1, Math.max(1, h - 6), 0x335F6570);
                }
                drawWorkbenchInlineItem(canvas, entry.stack(), slotX, slotY, slotSize);
                slotX += slotSize;
            }
            if (outputs.size() > visible) {
                if (visible > 0) {
                    canvas.fill(slotX - 1, y + 3, 1, Math.max(1, h - 6), 0x335F6570);
                }
                drawWorkbenchRailTextCell(canvas, font, slotX, slotY, slotSize, "+" + (outputs.size() - visible));
            }
        });
    }

    private static int workbenchOutputStripWidth(ImmutableList<WorkbenchOutputEntry> outputs, int h, int visibleLimit) {
        int slotSize = Math.max(12, h - 2);
        int visible = outputs.isEmpty() ? 1 : Math.min(Math.max(1, visibleLimit), outputs.size());
        int cells = visible + (outputs.size() > visible ? 1 : 0);
        return Math.max(slotSize + 2, cells * slotSize + 2);
    }

    private static int workbenchInputStripWidth(ImmutableList<WorkbenchInputEntry> inputs, int h, int visibleLimit) {
        int slotSize = Math.max(12, h - 2);
        int visible = inputs.isEmpty() ? 1 : Math.min(Math.max(1, visibleLimit), inputs.size());
        int cells = visible + (inputs.size() > visible ? 1 : 0);
        return Math.max(slotSize + 2, cells * slotSize + 2);
    }

    private void drawWorkbenchStatusLaneStrip(SceneCanvas canvas, Font font, ExecutionOverlayNode node, int x, int y, int w, int h, boolean focused) {
        canvas.fill(x, y, w, h, 0x1D000000);
        int iconSize = Math.max(12, h - 2);
        int iconY = y + Math.max(1, (h - iconSize) / 2);
        int textX = x + 3;
        if (node.iconStack() != null && !node.iconStack().isEmpty()) {
            drawWorkbenchDecorativeIcon(canvas, node.iconStack(), x + 1, iconY, iconSize);
            textX += iconSize + 3;
        }
        String primary = trimLabel(font, compactWorkbenchLabel(resolveCurrentStepLabel(node), 2), Math.max(18, w - (textX - x) - 4));
        String secondary = compactWorkbenchLabel(workbenchNodeMeta(node), 2);
        canvas.drawString(primary, textX, y + 3, focused ? 0xFF1F334A : 0xFF2E465F, false);
        if (!secondary.isBlank()) {
            canvas.drawString(trimLabel(font, secondary, Math.max(14, w - (textX - x) - 4)), textX, y + 11, 0xFF5A7188, false);
        }
    }

    private void drawWorkbenchStatusStrip(SceneCanvas canvas, Font font, ExecutionOverlayNode node, int x, int y, int w, int h) {
        canvas.texture(CirrusTextures.Background.inset, x, y, w, h);
        canvas.texture(CirrusTextures.Background.Sign.bar, x + 3, y + 3, Math.max(18, w - 6), 2);
        int iconSize = 24;
        int iconX = x + 6;
        int iconY = y + 12;
        drawWorkbenchDecorativeIcon(canvas, node.iconStack(), iconX, iconY, iconSize);
        String title = resolveCurrentStepLabel(node);
        String meta = workbenchNodeMeta(node);
        String progress = segmentProgressOf(node);
        int textX = iconX + iconSize + 6;
        canvas.drawString(trimLabel(font, title, Math.max(18, w - 76)), textX, y + 9, 0xFFF0F6FB, false);
        if (!meta.isBlank()) {
            canvas.drawString(trimLabel(font, meta, Math.max(18, w - 76)), textX, y + 20, 0xFFD9E3EE, false);
        }
        canvas.drawString(progress, x + w - 8 - font.width(progress), y + 9, 0xFFBFD9F7, false);
        drawProgressTrack(canvas, textX, y + 34, Math.max(20, w - (textX - x) - 8), laneRatio(node));
    }

    private void drawWorkbenchUpcomingCard(SceneCanvas canvas, Font font, WorkbenchQueueRow row, int x, int y, int w, int h) {
        ExecutionOverlayNode node = row.node();
        @Nullable InteractionStep.ExecuteRecipeStep previewStep = row.previewStep();
        boolean current = row.state() == WorkbenchQueueRowState.CURRENT;
        ItemStack recipeStack = previewStep == null ? workbenchRecipeDisplayStack(node) : workbenchPrimaryOutputStack(previewStep);
        if (recipeStack == null || recipeStack.isEmpty()) {
            recipeStack = workbenchRecipeDisplayStack(node);
        }
        ImmutableList<WorkbenchInputEntry> previewInputs = previewStep == null
            ? mergedWorkbenchInputs(node)
            : resolveWorkbenchPreviewInputs(previewStep, mergedWorkbenchInputs(node));
        ImmutableList<WorkbenchOutputEntry> previewOutputs = previewStep == null
            ? workbenchSingleOutputEntries(workbenchRecipeDisplayStack(node))
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());

        canvas.texture(CirrusTextures.Background.flat, x, y, w, h);
        if (current) {
            canvas.fill(x + 1, y + 1, Math.max(0, w - 2), Math.max(0, h - 2), 0x1D4C6E8F);
        }
        drawWorkbenchDecorativeIcon(canvas, recipeStack, x + 5, y + 5, 18);

        String badgeText = "#" + Math.max(1, row.order());
        int badgeW = Math.max(14, font.width(badgeText) + 6);
        int badgeH = 10;
        int badgeX = x + w - badgeW - 5;
        int badgeY = y + 5;
        canvas.fill(badgeX, badgeY, badgeW, badgeH, 0x332E3640);
        canvas.drawCenteredString(badgeText, badgeX + badgeW / 2, badgeY + 2, 0xFFD7E2ED);

        int railY = y + 10;
        int railH = 14;
        int railX = x + 28;
        int railMaxX = Math.max(railX + 18, badgeX - 4);
        int availableW = Math.max(18, railMaxX - railX);
        int outputW = Math.min(Math.max(14, availableW / 3), workbenchOutputStripWidth(previewOutputs, railH, 2));
        int outputX = railMaxX - outputW;
        int inputW = Math.max(10, outputX - railX - 8);

        if (current) {
            canvas.fill(railX - 4, railY + 4, 3, 3, 0xFF6EAAE6);
        }
        drawWorkbenchInputStrip(canvas, font, previewInputs, railX, railY, inputW, railH, 3, 0, false);
        canvas.texture(CirrusTextures.Icon.Small.arrowRight, outputX - 7, railY + 3, 5, 6);
        drawWorkbenchOutputStrip(canvas, font, previewOutputs, outputX, railY, outputW, railH, 2, 0);

        int progressX = x + 2;
        int progressY = y + h - 6;
        int progressW = Math.max(24, w - 4);
        drawProgressTrack(canvas, progressX, progressY, progressW, subtreeProgressRatio(node));
    }
    private void drawWorkbenchStageStatusRail(SceneCanvas canvas, Font font, int x, int y, int w) {
        ImmutableList<ExecutionOverlayNode> stages = workbenchStatusStageNodes();
        if (stages.isEmpty() || w <= 8) {
            return;
        }
        int visibleCount = workbenchRailVisibleCount(stages.size(), w, workbenchStageRailMinChipWidth);
        if (visibleCount <= 0) {
            return;
        }
        int chipW = workbenchRailChipWidth(w, visibleCount, workbenchStageRailMinChipWidth);
        for (int i = 0; i < visibleCount; i++) {
            ExecutionOverlayNode node = stages.get(i);
            boolean current = i == 0;
            int chipX = x + i * (chipW + workbenchStatusRailChipGap);
            canvas.fill(chipX, y, chipW, workbenchStageRailChipHeight, current ? 0x3A76A4CF : 0x24000000);
            drawWorkbenchDecorativeIcon(canvas, node.iconStack(), chipX + 1, y + 2, 12);
            int labelW = Math.max(10, chipW - 16);
            String[] lines = wrapLabelLines(font, node.label().getString(), labelW);
            int line1Y = y + (lines[1].isEmpty() ? 7 : 3);
            canvas.drawString(trimLabel(font, lines[0], labelW), chipX + 15, line1Y, current ? 0xFFEAF4FF : ExecutionOverlayComponentRenderer.textSecondary, false);
            if (!lines[1].isEmpty()) {
                canvas.drawString(trimLabel(font, lines[1], labelW), chipX + 15, y + 11, current ? 0xFFDCE9F6 : 0xFFCFDBE7, false);
            }
        }
    }

    private void drawWorkbenchOperationalStatusRail(SceneCanvas canvas, Font font, int x, int y, int w) {
        ImmutableList<ExecutionOverlayNode> nodes = workbenchOperationalStatusNodes();
        if (nodes.isEmpty() || w <= 8) {
            return;
        }
        int visibleCount = workbenchRailVisibleCount(nodes.size(), w);
        if (visibleCount <= 0) {
            return;
        }
        int chipW = workbenchRailChipWidth(w, visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            ExecutionOverlayNode node = nodes.get(i);
            boolean current = Objects.equals(node.nodeId(), viewModel == null ? null : viewModel.focusNodeId());
            String suffix = segmentProgressOf(node);
            int chipX = x + i * (chipW + workbenchStatusRailChipGap);
            canvas.fill(chipX, y, chipW, workbenchStatusRailChipHeight, current ? 0x3A76A4CF : 0x24000000);
            if (node.iconStack() != null && !node.iconStack().isEmpty()) {
                drawWorkbenchDecorativeIcon(canvas, node.iconStack(), chipX + 1, y + 1, 12);
            }
            int suffixW = font.width(suffix);
            boolean drawSuffix = chipW >= 64 && suffixW <= chipW / 2;
            int textX = chipX + 15;
            int labelMaxW = Math.max(10, chipW - (textX - chipX) - (drawSuffix ? suffixW + 6 : 4));
            String label = trimLabel(font, resolveCurrentStepLabel(node), labelMaxW);
            canvas.drawString(label, chipX + 15, y + 4, current ? 0xFFEAF4FF : 0xFFDDE7F1, false);
            if (drawSuffix) {
                canvas.drawString(suffix, chipX + chipW - 3 - suffixW, y + 4, current ? 0xFFBFD9F7 : ExecutionOverlayComponentRenderer.textDim, false);
            }
        }
    }

    private static int workbenchRailVisibleCount(int total, int width) {
        return workbenchRailVisibleCount(total, width, workbenchStatusRailMinChipWidth);
    }

    private static int workbenchRailVisibleCount(int total, int width, int minChipWidth) {
        if (total <= 0 || width <= 0) {
            return 0;
        }
        int maxByWidth = Math.max(1, (width + workbenchStatusRailChipGap) / (minChipWidth + workbenchStatusRailChipGap));
        return Math.min(total, maxByWidth);
    }

    private static int workbenchRailChipWidth(int totalWidth, int visibleCount) {
        return workbenchRailChipWidth(totalWidth, visibleCount, workbenchStatusRailMinChipWidth);
    }

    private static int workbenchRailChipWidth(int totalWidth, int visibleCount, int minChipWidth) {
        return Math.max(
            minChipWidth,
            (totalWidth - Math.max(0, visibleCount - 1) * workbenchStatusRailChipGap) / Math.max(1, visibleCount)
        );
    }

    private @Nullable ExecutionOverlayNode hoveredWorkbenchStageStatusNode(int mouseX, int mouseY, int x, int y, int w) {
        ImmutableList<ExecutionOverlayNode> stages = workbenchStatusStageNodes();
        if (stages.isEmpty() || mouseY < y || mouseY > y + workbenchStageRailChipHeight || mouseX < x || mouseX > x + w) {
            return null;
        }
        int visibleCount = workbenchRailVisibleCount(stages.size(), w, workbenchStageRailMinChipWidth);
        int chipW = workbenchRailChipWidth(w, visibleCount, workbenchStageRailMinChipWidth);
        for (int i = 0; i < visibleCount; i++) {
            int chipX = x + i * (chipW + workbenchStatusRailChipGap);
            if (mouseX >= chipX && mouseX <= chipX + chipW) {
                return stages.get(i);
            }
        }
        return null;
    }

    private @Nullable ExecutionOverlayNode hoveredWorkbenchOperationalStatusNode(Font font, int mouseX, int mouseY, int x, int y, int w) {
        ImmutableList<ExecutionOverlayNode> nodes = workbenchOperationalStatusNodes();
        if (nodes.isEmpty() || mouseY < y || mouseY > y + workbenchStatusRailChipHeight || mouseX < x || mouseX > x + w) {
            return null;
        }
        int visibleCount = workbenchRailVisibleCount(nodes.size(), w);
        int chipW = workbenchRailChipWidth(w, visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            int chipX = x + i * (chipW + workbenchStatusRailChipGap);
            if (mouseX >= chipX && mouseX <= chipX + chipW) {
                return nodes.get(i);
            }
        }
        return null;
    }

    private ImmutableList<ExecutionOverlayNode> workbenchStatusStageNodes() {
        MutableList<ExecutionOverlayNode> out = Lists.mutable.empty();
        ExecutionOverlayNode currentRoot = currentSubgraphRoot();
        if (currentRoot != null) {
            out.add(currentRoot);
        }
        for (ExecutionOverlayNode node : workbenchUpcomingStageRoots()) {
            if (currentRoot != null && Objects.equals(currentRoot.nodeId(), node.nodeId())) {
                continue;
            }
            out.add(node);
            if (out.size() >= 3) {
                break;
            }
        }
        return out.toImmutable();
    }

    private void drawWorkbenchItemSlot(SceneCanvas canvas, @Nullable ItemStack stack, int x, int y, int size, String overlay) {
        canvas.fill(x, y, size, size, 0x21000000);
        if (size > 3) {
            canvas.fill(x + 1, y + 1, size - 2, size - 2, 0x222A3440);
        }
        if (stack != null && !stack.isEmpty()) {
            canvas.pushTransform();
            canvas.translate(x + 1, y + 1);
            canvas.scale((size - 2) / 16f);
            canvas.renderItem(stack, 0, 0);
            canvas.renderItemDecorations(stack, 0, 0);
            canvas.popTransform();
        }
    }

    private void drawWorkbenchInlineItem(SceneCanvas canvas, @Nullable ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        float scale = Math.max(0.65f, size / 16f);
        float inset = Math.max(0.0f, (size - 16f * scale) * 0.5f);
        canvas.pushTransform();
        canvas.translate(x + inset, y + inset);
        canvas.scale(scale);
        canvas.renderItem(stack, 0, 0);
        canvas.renderItemDecorations(stack, 0, 0);
        canvas.popTransform();
    }

    private void drawWorkbenchDecorativeIcon(SceneCanvas canvas, @Nullable ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        canvas.pushTransform();
        canvas.translate(x, y);
        canvas.scale(size / 16f);
        canvas.renderItem(stack, 0, 0);
        if (size >= 14) {
            canvas.renderItemDecorations(stack, 0, 0);
        }
        canvas.popTransform();
    }

    private void drawWorkbenchTextSlot(SceneCanvas canvas, Font font, int x, int y, int size, String text) {
        if (workbenchCountBadgesUseStandaloneSlotChrome) {
            canvas.texture(CirrusTextures.Background.Slot.dark, x, y, size, size);
        } else {
            canvas.fill(x, y, size, size, 0x22000000);
        }
        canvas.drawCenteredString(trimLabel(font, text, Math.max(6, size - 2)), x + size / 2, y + Math.max(3, size / 2 - 4), ExecutionOverlayComponentRenderer.textSecondary);
    }

    private void drawWorkbenchRailTextCell(SceneCanvas canvas, Font font, int x, int y, int size, String text) {
        canvas.drawCenteredString(trimLabel(font, text, Math.max(6, size - 2)), x + size / 2, y + Math.max(4, size / 2 - 4), ExecutionOverlayComponentRenderer.textSecondary);
    }

    private static String stackOverlayText(@Nullable ItemStack stack) {
        return stackOverlayText(stack, stack == null ? 0 : stack.getCount());
    }

    private static String stackOverlayText(@Nullable ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 1) {
            return "";
        }
        if (count >= 1000) {
            return count / 1000 + "k";
        }
        return Integer.toString(count);
    }

    static String workbenchItemOverlayText(@Nullable ItemStack stack, int count) {
        return "";
    }

    private ImmutableList<WorkbenchInputEntry> mergedWorkbenchInputs(ExecutionOverlayNode node) {
        MutableList<WorkbenchInputEntry> out = Lists.mutable.empty();
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, ItemStack> stacks = new java.util.LinkedHashMap<>();
        for (ExecutionOverlayNode child : node.children()) {
            ItemStack stack = child.iconStack();
            String key = workbenchInputKey(child, stack);
            counts.merge(key, stack == null || stack.isEmpty() ? 1 : Math.max(1, stack.getCount()), Integer::sum);
            if (!stacks.containsKey(key)) {
                stacks.put(key, stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }
        for (var entry : counts.entrySet()) {
            ItemStack baseStack = stacks.get(entry.getKey());
            ItemStack displayStack = baseStack == null ? ItemStack.EMPTY : baseStack.copy();
            if (!displayStack.isEmpty()) {
                displayStack.setCount(Math.min(99, Math.max(1, entry.getValue())));
            }
            out.add(new WorkbenchInputEntry(displayStack, entry.getValue()));
        }
        return out.toImmutable();
    }

    private ImmutableList<WorkbenchOutputEntry> workbenchSingleOutputEntries(@Nullable ItemStack output) {
        if (output == null || output.isEmpty()) {
            return Lists.immutable.empty();
        }
        ItemStack display = output.copy();
        int count = Math.max(1, output.getCount());
        display.setCount(Math.min(99, count));
        return Lists.immutable.of(new WorkbenchOutputEntry(display, count));
    }

    private ImmutableList<WorkbenchOutputEntry> mergedWorkbenchOutputs(ImmutableList<PlanAction.OutputBinding> bindings) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        LinkedHashMap<String, ItemStack> stacks = new LinkedHashMap<>();
        for (PlanAction.OutputBinding binding : bindings) {
            ItemStack stack = stackFromContentResource(binding.resource());
            String key = stack != null && !stack.isEmpty()
                ? stack.getDescriptionId()
                : "output:" + binding.resource().getClass().getSimpleName();
            counts.merge(key, safeDisplayAmount(binding.amount()), Integer::sum);
            if (!stacks.containsKey(key)) {
                stacks.put(key, stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }
        MutableList<WorkbenchOutputEntry> out = Lists.mutable.empty();
        for (var entry : counts.entrySet()) {
            ItemStack base = stacks.get(entry.getKey());
            ItemStack display = base == null ? ItemStack.EMPTY : base.copy();
            if (!display.isEmpty()) {
                display.setCount(Math.min(99, Math.max(1, entry.getValue())));
            }
            out.add(new WorkbenchOutputEntry(display, entry.getValue()));
        }
        return out.toImmutable();
    }

    private ImmutableList<WorkbenchInputEntry> mergedWorkbenchInputs(ImmutableList<PlanAction.InputBinding> bindings) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        LinkedHashMap<String, ItemStack> stacks = new LinkedHashMap<>();
        for (PlanAction.InputBinding binding : bindings) {
            ItemStack stack = stackFromInputContent(binding.input());
            String key = stack != null && !stack.isEmpty()
                ? stack.getDescriptionId()
                : "input:" + binding.input().getClass().getSimpleName();
            counts.merge(key, safeDisplayAmount(binding.amount()), Integer::sum);
            if (!stacks.containsKey(key)) {
                stacks.put(key, stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }
        MutableList<WorkbenchInputEntry> out = Lists.mutable.empty();
        for (var entry : counts.entrySet()) {
            ItemStack baseStack = stacks.get(entry.getKey());
            ItemStack displayStack = baseStack == null ? ItemStack.EMPTY : baseStack.copy();
            if (!displayStack.isEmpty()) {
                displayStack.setCount(Math.min(99, Math.max(1, entry.getValue())));
            }
            out.add(new WorkbenchInputEntry(displayStack, entry.getValue()));
        }
        return out.toImmutable();
    }

    private ItemStack workbenchPrimaryOutputStack(@Nullable InteractionStep.ExecuteRecipeStep previewStep) {
        if (previewStep == null) {
            return ItemStack.EMPTY;
        }
        ImmutableList<WorkbenchOutputEntry> outputs = mergedWorkbenchOutputs(previewStep.expectedOutputs());
        if (outputs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = outputs.getFirst().stack();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private String workbenchQueueRecipeTitle(@Nullable InteractionStep.ExecuteRecipeStep previewStep, ExecutionOverlayNode fallbackNode) {
        ItemStack outputStack = workbenchPrimaryOutputStack(previewStep);
        if (!outputStack.isEmpty()) {
            return outputStack.getHoverName().getString();
        }
        return workbenchRecipeTitle(fallbackNode);
    }

    private List<MaterialSlot> workbenchTooltipRecipeCatalystSlots(@Nullable InteractionStep.ExecuteRecipeStep previewStep) {
        ImmutableList<WorkbenchInputEntry> catalysts = resolveWorkbenchCatalystInputs(previewStep);
        if (catalysts.isEmpty()) {
            return List.of();
        }
        List<MaterialSlot> slots = new ArrayList<>();
        for (WorkbenchInputEntry entry : catalysts) {
            ItemStack stack = entry.stack();
            if (stack != null && !stack.isEmpty()) {
                slots.add(MaterialSlot.catalyst(stack, Math.max(1, entry.count())));
            }
        }
        return slots;
    }

    private ImmutableList<WorkbenchInputEntry> resolveWorkbenchCatalystInputs(@Nullable InteractionStep.ExecuteRecipeStep previewStep) {
        if (previewStep == null || session == null || session.plan() == null || session.plan().steps().isEmpty()) {
            return Lists.immutable.empty();
        }
        ImmutableList<InteractionStep> planSteps = session.plan().steps();
        int executeIndex = -1;
        for (int i = 0; i < planSteps.size(); i++) {
            InteractionStep step = planSteps.get(i);
            if (step == previewStep || step.equals(previewStep)) {
                executeIndex = i;
                break;
            }
        }
        if (executeIndex < 0) {
            return Lists.immutable.empty();
        }
        MutableList<PlanAction.InputBinding> catalystBindings = Lists.mutable.empty();
        for (int i = executeIndex - 1; i >= 0; i--) {
            InteractionStep step = planSteps.get(i);
            if (step instanceof InteractionStep.ExecuteRecipeStep) {
                break;
            }
            if (step instanceof InteractionStep.OpenInteractionStep || step instanceof InteractionStep.ManualCheckpointStep) {
                break;
            }
            if (step instanceof InteractionStep.ReserveCatalystStep reserveCatalystStep) {
                catalystBindings.add(new PlanAction.InputBinding(
                    reserveCatalystStep.input(),
                    Math.max(1L, reserveCatalystStep.amount())
                ));
            }
        }
        if (catalystBindings.isEmpty()) {
            return Lists.immutable.empty();
        }
        return mergedWorkbenchInputs(catalystBindings.toImmutable());
    }

    private ImmutableList<WorkbenchInputEntry> mergedWorkbenchInputsFromRecipeDefinition(@Nullable InteractionStep.ExecuteRecipeStep previewStep) {
        if (previewStep == null || previewStep.recipe() == null) {
            return Lists.immutable.empty();
        }
        Object rawRecipe = previewStep.recipe().recipe();
        if (!(rawRecipe instanceof RecipeHolder<?> holder)) {
            return Lists.immutable.empty();
        }
        if (!(holder.value() instanceof CraftingRecipe craftingRecipe)) {
            return Lists.immutable.empty();
        }
        return mergedWorkbenchInputsFromIngredients(craftingRecipe.getIngredients());
    }

    private ImmutableList<WorkbenchInputEntry> mergedWorkbenchInputsFromIngredients(List<Ingredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Lists.immutable.empty();
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        LinkedHashMap<String, ItemStack> stacks = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            ItemStack stack = firstIngredientStack(ingredient);
            if (stack.isEmpty()) {
                continue;
            }
            String key = stack.getDescriptionId();
            counts.merge(key, 1, Integer::sum);
            if (!stacks.containsKey(key)) {
                stacks.put(key, stack.copy());
            }
        }
        MutableList<WorkbenchInputEntry> out = Lists.mutable.empty();
        for (var entry : counts.entrySet()) {
            ItemStack baseStack = stacks.get(entry.getKey());
            ItemStack displayStack = baseStack == null ? ItemStack.EMPTY : baseStack.copy();
            if (!displayStack.isEmpty()) {
                displayStack.setCount(Math.min(99, Math.max(1, entry.getValue())));
            }
            out.add(new WorkbenchInputEntry(displayStack, entry.getValue()));
        }
        return out.toImmutable();
    }

    private static ItemStack firstIngredientStack(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] options = ingredient.getItems();
        if (options == null || options.length == 0) {
            return ItemStack.EMPTY;
        }
        for (ItemStack option : options) {
            if (option == null || option.isEmpty()) {
                continue;
            }
            ItemStack copy = option.copy();
            copy.setCount(1);
            return copy;
        }
        return ItemStack.EMPTY;
    }

    private ImmutableList<WorkbenchInputEntry> resolveWorkbenchPreviewInputs(
        @Nullable InteractionStep.ExecuteRecipeStep previewStep,
        ImmutableList<WorkbenchInputEntry> fallbackInputs
    ) {
        if (previewStep == null) {
            return fallbackInputs;
        }
        ImmutableList<WorkbenchInputEntry> fromRecipe = mergedWorkbenchInputsFromRecipeDefinition(previewStep);
        if (fromRecipe.notEmpty()) {
            return fromRecipe;
        }
        ImmutableList<WorkbenchInputEntry> fromStep = mergedWorkbenchInputs(previewStep.inputs());
        return fromStep.isEmpty() ? fallbackInputs : fromStep;
    }

    private static String workbenchInputKey(ExecutionOverlayNode node, @Nullable ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            return stack.getDescriptionId();
        }
        return "label:" + node.label().getString();
    }

    private static String workbenchRecipeTitle(ExecutionOverlayNode node) {
        ItemStack output = workbenchRecipeDisplayStack(node);
        if (output != null && !output.isEmpty()) {
            return output.getHoverName().getString();
        }
        return resolveCurrentStepLabel(node);
    }

    private static @Nullable ItemStack workbenchRecipeDisplayStack(ExecutionOverlayNode node) {
        for (int i = node.children().size() - 1; i >= 0; i--) {
            ItemStack childStack = workbenchRecipeDisplayStack(node.children().get(i));
            if (childStack != null && !childStack.isEmpty()) {
                return childStack;
            }
        }
        return node.iconStack();
    }

    static ImmutableList<ExecutionOverlayNode> selectWorkbenchLaneRecipeNodes(ImmutableList<ExecutionOverlayNode> stageNodes) {
        return stageNodes.select(node -> node.kind() == ExecutionOverlayNodeKind.RECIPE).toImmutable();
    }

    private static String workbenchQueueGroupKey(ExecutionOverlayNode node) {
        if (node == null) {
            return "";
        }
        String label = node.label() == null ? "" : node.label().getString().trim().toLowerCase(java.util.Locale.ROOT);
        String step = resolveCurrentStepLabel(node).trim().toLowerCase(java.util.Locale.ROOT);
        String icon = node.iconStack() == null || node.iconStack().isEmpty() ? "" : node.iconStack().getDescriptionId();
        return node.kind() + "|" + label + "|" + step + "|" + icon;
    }

    private static ImmutableList<ExecutionOverlayNode> compressAdjacentRecipeNodes(ImmutableList<ExecutionOverlayNode> recipeNodes) {
        if (recipeNodes == null || recipeNodes.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<ExecutionOverlayNode> compacted = Lists.mutable.empty();
        String previousKey = null;
        for (ExecutionOverlayNode node : recipeNodes) {
            String key = workbenchQueueGroupKey(node);
            if (compacted.isEmpty() || !Objects.equals(previousKey, key)) {
                compacted.add(node);
                previousKey = key;
            }
        }
        return compacted.toImmutable();
    }

    private static ImmutableList<WorkbenchQueueGroup> groupAdjacentRecipeNodes(ImmutableList<ExecutionOverlayNode> recipeNodes) {
        if (recipeNodes == null || recipeNodes.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<WorkbenchQueueGroup> groups = Lists.mutable.empty();
        String currentKey = null;
        ExecutionOverlayNode representative = null;
        MutableList<String> ids = Lists.mutable.empty();
        int count = 0;
        for (ExecutionOverlayNode node : recipeNodes) {
            String key = workbenchQueueGroupKey(node);
            if (representative == null || !Objects.equals(currentKey, key)) {
                if (representative != null) {
                    groups.add(new WorkbenchQueueGroup(currentKey, representative, ids.toImmutable(), count));
                }
                currentKey = key;
                representative = node;
                ids = Lists.mutable.empty();
                count = 0;
            }
            ids.add(node.nodeId());
            count++;
        }
        if (representative != null) {
            groups.add(new WorkbenchQueueGroup(currentKey, representative, ids.toImmutable(), count));
        }
        return groups.toImmutable();
    }

    static ImmutableList<ExecutionOverlayNode> selectWorkbenchStatusNodes(ImmutableList<ExecutionOverlayNode> stageNodes, int limit) {
        if (limit <= 0) {
            return Lists.immutable.empty();
        }
        return stageNodes.select(node -> node.kind() == ExecutionOverlayNodeKind.OPERATIONAL).take(limit).toImmutable();
    }

    static @Nullable ExecutionOverlayNode resolveWorkbenchRecipeFocus(ExecutionOverlayNode root, @Nullable String focusId) {
        ExecutionOverlayNode focus = focusId == null ? root : findNodeById(root, focusId);
        if (focus == null) {
            focus = root;
        }
        if (focus.kind() == ExecutionOverlayNodeKind.RECIPE) {
            return focus;
        }
        ImmutableList<ExecutionOverlayNode> path = focusPathNodes(root, focus.nodeId());
        for (int i = path.size() - 1; i >= 0; i--) {
            ExecutionOverlayNode candidate = path.get(i);
            if (candidate.kind() == ExecutionOverlayNodeKind.RECIPE) {
                return candidate;
            }
        }
        MutableList<ExecutionOverlayNode> flattened = Lists.mutable.empty();
        collectOverlayNodes(root, flattened);
        return flattened.detect(node -> node.kind() == ExecutionOverlayNodeKind.RECIPE);
    }

    private static boolean isWorkbenchRecipeNode(ExecutionOverlayNode node, ImmutableList<WorkbenchInputEntry> inputs) {
        return node.kind() == ExecutionOverlayNodeKind.RECIPE && inputs.notEmpty();
    }

    private static String workbenchNodeMeta(ExecutionOverlayNode node) {
        if (node.subtitle() != null && !node.subtitle().getString().isBlank()) {
            return node.subtitle().getString();
        }
        return statusTextOf(node.status());
    }

    private void drawWorkbenchPopup(SceneCanvas canvas, Font font, BodyLayout body) {
        if (!workbenchPopupOpen) {
            return;
        }
        Rect2i popup = currentWorkbenchPopupBounds();
        canvas.texture(CirrusTextures.Background.Outlined.flat, popup.getX(), popup.getY(), popup.getWidth(), popup.getHeight());
        canvas.fill(popup.getX() + 1, popup.getY() + 1, popup.getWidth() - 2, popup.getHeight() - 2, 0x1D000000);
        canvas.fill(popup.getX() + 2, popup.getY() + 2, popup.getWidth() - 4, workbenchPopupTitleHeight - 1, 0x26000000);

        ExecutionOverlayNode focus = focusedWorkbenchNode();
        ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
        @Nullable InteractionStep.ExecuteRecipeStep previewStep = resolveWorkbenchPreviewStep();
        ImmutableList<WorkbenchOutputEntry> outputs = previewStep == null
            ? workbenchSingleOutputEntries(recipeFocus == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(recipeFocus))
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());
        ItemStack heroStack = outputs.notEmpty()
            ? outputs.get(0).stack()
            : (recipeFocus != null ? workbenchRecipeDisplayStack(recipeFocus) : (focus == null ? ItemStack.EMPTY : focus.iconStack()));
        String heroTitle = recipeFocus != null ? workbenchRecipeTitle(recipeFocus) : (focus == null ? "Details" : resolveCurrentStepLabel(focus));
        String rawStatus = telemetrySnapshot == null ? viewModel.statusLabel().getString() : telemetrySnapshot.status();
        String fallbackStatus = statusTextOf(viewModel.status());
        String statusText = isAbstractStatusWord(rawStatus) ? rawStatus : fallbackStatus;
        int runStatusColor = statusColor(statusText);
        double progress = telemetrySnapshot == null
            ? (viewModel.totalStepCount() <= 0 ? 0.0d : viewModel.completedStepCount() / (double) viewModel.totalStepCount())
            : telemetrySnapshot.progress();
        int completed = telemetrySnapshot == null ? viewModel.completedStepCount() : telemetrySnapshot.completedSteps();
        int total = telemetrySnapshot == null ? viewModel.totalStepCount() : telemetrySnapshot.totalSteps();
        String doneText = completed + "/" + total;
        String nextText = telemetrySnapshot != null && telemetrySnapshot.next() != null
            ? telemetrySnapshot.next().label()
            : (focus == null ? "END" : compactWorkbenchLabel(resolveCurrentStepLabel(focus), 2));
        ImmutableList<WorkbenchInputEntry> inputs = previewStep == null
            ? (recipeFocus == null ? Lists.immutable.empty() : mergedWorkbenchInputs(recipeFocus))
            : mergedWorkbenchInputs(previewStep.inputs());
        int warningCount = telemetrySnapshot == null ? 0 : telemetrySnapshot.warnings().size();
        double rate = telemetrySnapshot == null ? 1.0d : telemetrySnapshot.ratePerSecond();
        int score = gamifiedScore(progress, telemetrySnapshot);
        String rank = gamifiedRank(score);

        if (heroStack != null && !heroStack.isEmpty()) {
            drawWorkbenchInlineItem(canvas, heroStack, popup.getX() + 5, popup.getY() + 4, 12);
        }
        canvas.drawString("Run Status", popup.getX() + 20, popup.getY() + 5, 0xFFEFF6FB, false);
        canvas.drawString(
            trimLabel(font, compactWorkbenchLabel(heroTitle, 3), Math.max(18, popup.getWidth() - 78)),
            popup.getX() + 20,
            popup.getY() + 13,
            0xFFD5E0EA,
            false
        );
        drawWorkbenchPopupChip(canvas, font, popup.getX() + popup.getWidth() - 43, popup.getY() + 5, 20, 10, rank, rankColor(rank));

        Rect2i close = workbenchPopupCloseButtonBounds(popup);
        canvas.fill(close.getX(), close.getY(), close.getWidth(), close.getHeight(), 0x2A000000);
        canvas.drawCenteredString("x", close.getX() + close.getWidth() / 2, close.getY() + 3, 0xFFE7EDF3);

        int rowX = popup.getX() + 4;
        int rowY = popup.getY() + workbenchPopupTitleHeight + 4;
        int rowW = popup.getWidth() - 8;

        canvas.fill(rowX, rowY, rowW, 38, 0x17000000);
        if (heroStack != null && !heroStack.isEmpty()) {
            drawWorkbenchItemSlot(canvas, heroStack, rowX + 4, rowY + 5, 18, "");
        }
        int summaryTextX = rowX + (heroStack != null && !heroStack.isEmpty() ? 25 : 6);
        drawWorkbenchPopupChip(canvas, font, rowX + rowW - 40, rowY + 5, 34, 10, statusText, runStatusColor);
        canvas.drawString(trimLabel(font, doneText, 32), rowX + rowW - 39, rowY + 18, 0xFFD5E0EA, false);
        canvas.drawString(trimLabel(font, compactWorkbenchLabel(heroTitle, 3), Math.max(16, rowW - (summaryTextX - rowX) - 44)), summaryTextX, rowY + 7, 0xFFF2F7FB, false);
        String nowText = focus == null ? "-" : resolveCurrentStepLabel(focus);
        canvas.drawString(trimLabel(font, compactWorkbenchLabel(nowText, 3), Math.max(16, rowW - (summaryTextX - rowX) - 10)), summaryTextX, rowY + 28, 0xFFD8E3EE, false);
        drawProgressTrack(canvas, rowX + 4, rowY + 18, rowW - 8, focus == null ? progress : laneRatio(focus));
        rowY += 42;

        int cardW = (rowW - 4) / 3;
        drawWorkbenchPopupMetricCard(canvas, font, rowX, rowY, cardW, 24, ">", 0xFFBFD9F7, "Next", compactWorkbenchLabel(nextText, 2), 0xFFE9EEF2);
        drawWorkbenchPopupMetricCard(
            canvas,
            font,
            rowX + cardW + 2,
            rowY,
            cardW,
            24,
            "x",
            0xFF7FC2F5,
            "Rate",
            String.format(java.util.Locale.ROOT, "%.1f/s", rate),
            0xFFDDF2FF
        );
        drawWorkbenchPopupMetricCard(
            canvas,
            font,
            rowX + (cardW + 2) * 2,
            rowY,
            rowW - (cardW + 2) * 2,
            24,
            "!",
            warningCount > 0 ? 0xFFE3B56B : 0xFF89D7A0,
            "Warn",
            Integer.toString(warningCount),
            warningCount > 0 ? 0xFFF0D38F : 0xFF9FE1B3
        );
        rowY += 27;

        canvas.fill(rowX, rowY, rowW, 37, 0x14000000);
        int infoY = rowY + 5;
        infoY = drawPopupInfoLine(canvas, font, rowX + 5, infoY, rowW - 10, "Status", statusText, runStatusColor);
        infoY = drawPopupInfoLine(canvas, font, rowX + 5, infoY, rowW - 10, "Done", doneText, 0xFFE9EEF2);
        drawPopupInfoLine(canvas, font, rowX + 5, infoY, rowW - 10, "Rank", rank, rankColor(rank));
        rowY += 40;

        canvas.fill(rowX, rowY, rowW, 18, 0x13000000);
        canvas.texture(CirrusTextures.Icon.Calc.Path.branch, rowX + 5, rowY + 4, 12, 12);
        canvas.drawString(trimLabel(font, compactWorkbenchPopupPath(font, rowW - 24), Math.max(24, rowW - 24)), rowX + 18, rowY + 7, 0xFFD5E0EA, false);
        rowY += 21;

        if (inputs.notEmpty()) {
            canvas.fill(rowX, rowY, rowW, 24, 0x15000000);
            canvas.drawString("IN", rowX + 5, rowY + 5, 0xFFBFD9F7, false);
            drawWorkbenchInputStrip(canvas, font, inputs, rowX + 22, rowY + 3, Math.max(28, rowW - 26), 18, 6, 0);
            rowY += 27;
        }

        if (outputs.notEmpty() && rowY + 24 <= popup.getY() + popup.getHeight() - 3) {
            canvas.fill(rowX, rowY, rowW, 24, 0x15000000);
            canvas.drawString("OUT", rowX + 5, rowY + 5, 0xFFBFD9F7, false);
            drawWorkbenchOutputStrip(canvas, font, outputs, rowX + 30, rowY + 3, Math.max(28, rowW - 34), 18, 4, 0);
            rowY += 27;
        }

        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null && rowY + 18 <= popup.getY() + popup.getHeight() - 3) {
            canvas.fill(rowX, rowY, rowW, 18, 0x2A3A1010);
            canvas.drawString(trimLabel(font, telemetrySnapshot.errorSummary(), Math.max(20, rowW - 10)), rowX + 5, rowY + 5, 0xFFE26B6B, false);
        }
    }

    private void drawWorkbenchPopupChip(SceneCanvas canvas, Font font, int x, int y, int w, int h, String text, int textColor) {
        if (workbenchStatusChipsUseStandaloneSlotChrome) {
            canvas.texture(CirrusTextures.Background.Slot.dark, x, y, w, h);
        } else {
            canvas.fill(x, y, w, h, 0x22000000);
        }
        canvas.drawCenteredString(trimLabel(font, text, Math.max(8, w - 4)), x + w / 2, y + 2, textColor);
    }

    private void drawWorkbenchPopupMetricCard(SceneCanvas canvas, Font font, int x, int y, int w, int h, String glyph, int glyphColor, String label, String value, int valueColor) {
        canvas.fill(x, y, w, h, 0x18000000);
        canvas.drawString(glyph, x + 4, y + 4, glyphColor, false);
        canvas.drawString(label, x + 14, y + 4, ExecutionOverlayComponentRenderer.textDim, false);
        canvas.drawString(trimLabel(font, value, Math.max(12, w - 18)), x + 4, y + 14, valueColor, false);
    }

    private String compactWorkbenchPopupPath(Font font, int maxWidth) {
        ImmutableList<ExecutionOverlayNode> trail = pathContextNodes();
        if (trail.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < trail.size(); i++) {
            if (i > 0) {
                out.append(" > ");
            }
            out.append(compactWorkbenchLabel(trail.get(i).label().getString(), 2));
        }
        return trimLabel(font, out.toString(), maxWidth);
    }

    private int drawPopupInfoLine(SceneCanvas canvas, Font font, int x, int y, int w, String key, String value, int valueColor) {
        canvas.drawString(key, x, y, ExecutionOverlayComponentRenderer.textDim, false);
        canvas.drawString(trimLabel(font, value, Math.max(18, w - 36)), x + 32, y, valueColor, false);
        return y + 11;
    }

    private Rect2i workbenchHeaderInfoButtonBounds() {
        return new Rect2i(Math.max(4, width() - 24), 6, 14, 14);
    }

    private Rect2i currentWorkbenchPopupBounds() {
        return new Rect2i(workbenchPopupX, workbenchPopupY, workbenchPopupWidth, workbenchPopupHeight);
    }

    private Rect2i workbenchPopupCloseButtonBounds(Rect2i popup) {
        return new Rect2i(popup.getX() + popup.getWidth() - 16, popup.getY() + 2, 12, 12);
    }

    static Rect2i clampWorkbenchPopupBounds(Rect2i popup, Rect2i container) {
        int width = Math.max(48, popup.getWidth());
        int height = Math.max(48, popup.getHeight());
        int minX = container.getX() - width + workbenchPopupVisibleGrip;
        int minY = container.getY() - height + workbenchPopupTitleHeight + 4;
        int maxX = container.getX() + container.getWidth() - workbenchPopupVisibleGrip;
        int maxY = container.getY() + container.getHeight() - workbenchPopupTitleHeight;
        int x = Math.max(minX, Math.min(maxX, popup.getX()));
        int y = Math.max(minY, Math.min(maxY, popup.getY()));
        return new Rect2i(x, y, width, height);
    }

    private static boolean insideRect(Rect2i rect, int mouseX, int mouseY) {
        return mouseX >= rect.getX()
            && mouseX <= rect.getX() + rect.getWidth()
            && mouseY >= rect.getY()
            && mouseY <= rect.getY() + rect.getHeight();
    }

    private int workbenchLaneSectionHeight(ImmutableList<ExecutionOverlayNode> laneNodes) {
        int total = 6;
        for (ExecutionOverlayNode node : laneNodes) {
            total += workbenchLaneRowHeight(node) + workbenchLaneRowGap();
        }
        if (laneNodes.notEmpty()) {
            total -= workbenchLaneRowGap();
        }
        return total;
    }

    private int workbenchLaneRowHeight(ExecutionOverlayNode node) {
        if (isWorkbenchFocusedNode(node)) {
            return 46;
        }
        return node.status() instanceof ExecutionFlowStatus.Completed ? 30 : 34;
    }

    private int workbenchLaneRowGap() {
        return 4;
    }

    private int workbenchUpcomingCardHeight() {
        return 50;
    }

    private int workbenchUpcomingCardGap() {
        return 3;
    }

    private int workbenchProgressSectionHeight() {
        return 82;
    }

    private boolean isWorkbenchFocusedNode(ExecutionOverlayNode node) {
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        return focus != null && Objects.equals(focus.nodeId(), node.nodeId());
    }

    private String workbenchStatusGlyph(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Running ignored -> ">";
            case ExecutionFlowStatus.Paused ignored -> "||";
            case ExecutionFlowStatus.Completed ignored -> "+";
            case ExecutionFlowStatus.Failed ignored -> "!";
            case ExecutionFlowStatus.Pending ignored -> "...";
        };
    }

    private float subtreeProgressRatio(ExecutionOverlayNode node) {
        int total = countSubtreeNodes(node);
        if (total <= 0) {
            return 0.0f;
        }
        int done = countCompletedSubtreeNodes(node);
        return Math.max(0.0f, Math.min(1.0f, done / (float) total));
    }

    private int countCompletedSubtreeNodes(ExecutionOverlayNode node) {
        int total = node.status() instanceof ExecutionFlowStatus.Completed ? 1 : 0;
        for (ExecutionOverlayNode child : node.children()) {
            total += countCompletedSubtreeNodes(child);
        }
        return total;
    }

    private int workbenchInspectSummaryHeight() {
        return 54;
    }

    private int workbenchInspectPathHeight() {
        return 26;
    }

    private int workbenchInspectDiagnosticsHeight() {
        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
            int lines = telemetrySnapshot.errorDetail() == null || telemetrySnapshot.errorDetail().isBlank() ? 2 : 4;
            return Math.max(24, 8 + lines * 8);
        }
        ImmutableList<String> warnings = telemetrySnapshot == null ? Lists.immutable.empty() : telemetrySnapshot.warnings();
        int lines = warnings.isEmpty() ? 1 : 1 + Math.min(2, warnings.size());
        return Math.max(24, 8 + lines * 8);
    }

    private int workbenchInspectContentHeight() {
        int sectionGap = workbenchSectionGap();
        return workbenchInspectSummaryHeight()
            + sectionGap
            + workbenchInspectPathHeight()
            + sectionGap
            + workbenchInspectDiagnosticsHeight()
            + 8;
    }

    private int workbenchContentHeight() {
        ImmutableList<ExecutionOverlayNode> laneNodes = workbenchLaneNodes();
        ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> materialRequirements = workbenchMaterialRequirements();
        int queueRows = Math.max(1, workbenchQueueRows().size());
        int queueHeight = 6 + queueRows * workbenchUpcomingCardHeight() + Math.max(0, queueRows - 1) * workbenchUpcomingCardGap() + 4;
        int sectionGap = workbenchSectionGap();
        return switch (activeWorkbenchWindow) {
            case recipe -> workbenchRecipeHeroHeight
                + sectionGap
                + workbenchMaterialSectionHeight(materialRequirements)
                + sectionGap
                + queueHeight
                + 8;
            case timeline -> workbenchProgressSectionHeight()
                + sectionGap
                + workbenchLaneSectionHeight(laneNodes)
                + sectionGap
                + queueHeight
                + 8;
            case inspect -> workbenchInspectContentHeight();
        };
    }

    private ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> workbenchMaterialRequirements() {
        if (workbenchController == null) {
            return Lists.immutable.empty();
        }
        ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> requirements = workbenchController.overlayMaterialRequirements();
        return requirements == null ? Lists.immutable.empty() : requirements;
    }

    private int workbenchMaterialSectionHeight(ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> requirements) {
        int visible = Math.max(1, Math.min(workbenchMaterialVisibleLimit, requirements.size()));
        int rowsHeight = visible * workbenchMaterialRowHeight + Math.max(0, visible - 1) * workbenchMaterialRowGap;
        int tailHeight = requirements.size() > visible ? 10 : 0;
        return 8 + rowsHeight + tailHeight;
    }

    private int workbenchScrollContentWidth(BodyLayout body) {
        return workbenchContentBounds(body).getWidth();
    }

    private int workbenchScrollViewportHeight(BodyLayout body) {
        Rect2i window = workbenchWindowBounds(body);
        return Math.max(0, window.getHeight() - workbenchFixedConsoleHeight() - workbenchSectionGap());
    }

    private int workbenchFixedConsoleHeight() {
        return 104;
    }

    private Rect2i workbenchDetailsButtonBounds(BodyLayout body) {
        Rect2i content = workbenchContentBounds(body);
        int x = content.getX() + content.getWidth() - workbenchControlButtonSize - workbenchControlButtonInset;
        int y = workbenchControlButtonsTop(body) + (workbenchControlButtonSize + workbenchControlButtonGap) * 2;
        return new Rect2i(x, y, workbenchControlButtonSize, workbenchControlButtonSize);
    }

    private Rect2i workbenchPlaybackButtonBounds(BodyLayout body) {
        Rect2i content = workbenchContentBounds(body);
        int x = content.getX() + content.getWidth() - workbenchControlButtonSize - workbenchControlButtonInset;
        int y = workbenchControlButtonsTop(body) + workbenchControlButtonSize + workbenchControlButtonGap;
        return new Rect2i(x, y, workbenchControlButtonSize, workbenchControlButtonSize);
    }

    private Rect2i workbenchStepButtonBounds(BodyLayout body) {
        Rect2i content = workbenchContentBounds(body);
        int x = content.getX() + content.getWidth() - workbenchControlButtonSize - workbenchControlButtonInset;
        int y = workbenchControlButtonsTop(body);
        return new Rect2i(x, y, workbenchControlButtonSize, workbenchControlButtonSize);
    }

    private Rect2i workbenchSpeedRowBounds(BodyLayout body) {
        Rect2i content = workbenchContentBounds(body);
        Rect2i summary = workbenchConsoleTargetSummaryBounds(body);
        int y = summary.getY() + summary.getHeight() + 2;
        return new Rect2i(content.getX() + 1, y, Math.max(24, content.getWidth() - 2), 14);
    }

    private Rect2i workbenchConsoleTargetSummaryBounds(BodyLayout body) {
        Rect2i content = workbenchContentBounds(body);
        return new Rect2i(content.getX() + 3, content.getY() + 4, Math.max(40, content.getWidth() - workbenchConsoleRightColumnWidth - 5), 60);
    }

    private int workbenchControlButtonsTop(BodyLayout body) {
        Rect2i summary = workbenchConsoleTargetSummaryBounds(body);
        int stackH = workbenchControlButtonSize * 3 + workbenchControlButtonGap * 2;
        return summary.getY() + Math.max(0, (summary.getHeight() - stackH) / 2);
    }

    private Rect2i workbenchContentBounds(BodyLayout body) {
        Rect2i window = workbenchWindowBounds(body);
        return new Rect2i(window.getX() + 1, window.getY(), Math.max(40, window.getWidth() - 2), window.getHeight());
    }

    private Rect2i workbenchConsoleBounds(BodyLayout body) {
        Rect2i content = workbenchContentBounds(body);
        return new Rect2i(content.getX(), content.getY(), content.getWidth(), workbenchFixedConsoleHeight());
    }

    private Rect2i workbenchPathRowBounds(BodyLayout body) {
        Rect2i console = workbenchConsoleBounds(body);
        return new Rect2i(
            console.getX(),
            console.getY() + console.getHeight() - workbenchPathRowHeight,
            Math.max(24, console.getWidth()),
            workbenchPathRowHeight
        );
    }

    private int workbenchSectionGap() {
        // Keep medium containers visually fused instead of card-separated.
        return 0;
    }

    private Rect2i workbenchSpeedValueBounds(BodyLayout body) {
        Rect2i speed = workbenchSpeedRowBounds(body);
        return new Rect2i(speed.getX() + Math.max(0, speed.getWidth() - workbenchSpeedValueWidth - 2), speed.getY() + 1, workbenchSpeedValueWidth, 12);
    }

    private Rect2i workbenchSpeedSliderBounds(BodyLayout body) {
        Rect2i speed = workbenchSpeedRowBounds(body);
        Rect2i value = workbenchSpeedValueBounds(body);
        int sliderX = speed.getX() + 14;
        return new Rect2i(sliderX, speed.getY() + 5, Math.max(18, value.getX() - sliderX - 5), 4);
    }

    private Rect2i workbenchSpeedSliderInteractiveBounds(BodyLayout body) {
        Rect2i slider = workbenchSpeedSliderBounds(body);
        return new Rect2i(slider.getX(), slider.getY() - 3, slider.getWidth(), 10);
    }

    private @Nullable ExecutionOverlayNode focusedWorkbenchNode() {
        if (viewModel == null) {
            return null;
        }
        String focusId = viewModel.focusNodeId();
        if (focusId == null) {
            return viewModel.root();
        }
        return findNodeById(viewModel.root(), focusId);
    }

    private @Nullable ExecutionOverlayNode focusedWorkbenchRecipeNode() {
        if (viewModel == null) {
            return null;
        }
        return resolveWorkbenchRecipeFocus(viewModel.root(), viewModel.focusNodeId());
    }

    private ImmutableList<ExecutionOverlayNode> workbenchLaneNodes() {
        ImmutableList<ExecutionOverlayNode> stageNodes = workbenchStageNodes();
        if (stageNodes.isEmpty()) {
            return Lists.immutable.empty();
        }
        ImmutableList<ExecutionOverlayNode> recipeNodes = compressAdjacentRecipeNodes(selectWorkbenchLaneRecipeNodes(stageNodes));
        if (recipeNodes.isEmpty()) {
            return Lists.immutable.empty();
        }
        ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
        String focusKey = recipeFocus == null ? null : workbenchQueueGroupKey(recipeFocus);
        int focusIndex = 0;
        if (focusKey != null) {
            for (int i = 0; i < recipeNodes.size(); i++) {
                if (Objects.equals(workbenchQueueGroupKey(recipeNodes.get(i)), focusKey)) {
                    focusIndex = i;
                    break;
                }
            }
        }
        int start = Math.max(0, focusIndex - 1);
        int end = Math.min(recipeNodes.size(), Math.max(focusIndex + 4, start + 4));
        return recipeNodes.subList(start, end).toImmutable();
    }

    private ImmutableList<ExecutionOverlayNode> workbenchOperationalStatusNodes() {
        ImmutableList<ExecutionOverlayNode> stageNodes = workbenchStageNodes();
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        return collectWorkbenchOperationalStatusNodes(stageNodes, focus, 4);
    }

    static ImmutableList<ExecutionOverlayNode> collectWorkbenchOperationalStatusNodes(
        ImmutableList<ExecutionOverlayNode> stageNodes,
        @Nullable ExecutionOverlayNode focus,
        int limit
    ) {
        MutableList<ExecutionOverlayNode> prioritized = Lists.mutable.empty();
        if (focus != null && focus.kind() == ExecutionOverlayNodeKind.OPERATIONAL) {
            prioritized.add(focus);
        }
        for (ExecutionOverlayNode node : stageNodes) {
            if (node.kind() != ExecutionOverlayNodeKind.OPERATIONAL) {
                continue;
            }
            if (prioritized.noneSatisfy(existing -> Objects.equals(existing.nodeId(), node.nodeId()))) {
                prioritized.add(node);
            }
        }
        return selectWorkbenchStatusNodes(prioritized.toImmutable(), limit);
    }

    private ImmutableList<ExecutionOverlayNode> workbenchStageNodes() {
        if (viewModel == null) {
            return Lists.immutable.empty();
        }
        ExecutionOverlayNode root = currentSubgraphRoot();
        if (root == null) {
            root = viewModel.root();
        }
        MutableList<ExecutionOverlayNode> out = Lists.mutable.empty();
        collectOverlayNodes(root, out);
        return out.toImmutable();
    }

    private ImmutableList<ExecutionOverlayNode> workbenchUpcomingStageRoots() {
        if (viewModel == null || viewModel.visibleNodes().isEmpty()) {
            return Lists.immutable.empty();
        }
        String currentStageId = currentSubgraphRoot() == null ? null : currentSubgraphRoot().nodeId();
        MutableList<ExecutionOverlayNode> roots = Lists.mutable.empty();
        MutableList<String> seen = Lists.mutable.empty();
        int start = Math.max(0, viewModel.focusVisibleIndex() + 1);
        for (int i = start; i < viewModel.visibleNodes().size(); i++) {
            ExecutionOverlayNode candidate = viewModel.visibleNodes().get(i);
            ExecutionOverlayNode stageRoot = stageRootOf(candidate.nodeId());
            ExecutionOverlayNode recipeRoot = stageRoot == null ? null : resolveWorkbenchRecipeFocus(stageRoot, candidate.nodeId());
            String resolvedId = recipeRoot == null ? null : recipeRoot.nodeId();
            if (recipeRoot == null || Objects.equals(resolvedId, currentStageId) || seen.contains(resolvedId)) {
                continue;
            }
            seen.add(resolvedId);
            roots.add(recipeRoot);
            if (roots.size() >= 2) {
                break;
            }
        }
        return roots.toImmutable();
    }

    private ImmutableList<WorkbenchQueueRow> workbenchQueueRows() {
        if (viewModel == null) {
            return Lists.immutable.empty();
        }
        ImmutableList<ExecutionOverlayNode> nodes = viewModel.visibleNodes();
        ExecutionOverlayNode current = focusedWorkbenchRecipeNode();
        ImmutableList<WorkbenchQueueRow> orderedFromPlan = projectWorkbenchQueueRowsFromPlan(
            nodes,
            current == null ? null : current.nodeId(),
            14
        );
        if (orderedFromPlan.notEmpty()) {
            return orderedFromPlan;
        }
        return projectWorkbenchQueueRows(
            nodes,
            current == null ? null : current.nodeId(),
            14
        );
    }

    static ImmutableList<ExecutionOverlayNode> reduceWorkbenchPathTrail(
        ImmutableList<ExecutionOverlayNode> path,
        @Nullable String currentStageRootId
    ) {
        if (path.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<ExecutionOverlayNode> reduced = Lists.mutable.empty();
        for (int i = 0; i < path.size(); i++) {
            ExecutionOverlayNode node = path.get(i);
            boolean include = i == 0
                || node.children().size() > 1
                || Objects.equals(node.nodeId(), currentStageRootId)
                || (i == path.size() - 1 && currentStageRootId == null);
            if (include && (reduced.isEmpty() || !Objects.equals(reduced.getLast().nodeId(), node.nodeId()))) {
                reduced.add(node);
            }
        }
        if (reduced.isEmpty()) {
            return Lists.immutable.of(path.getFirst());
        }
        if (currentStageRootId == null) {
            ExecutionOverlayNode last = path.getLast();
            if (!Objects.equals(reduced.getLast().nodeId(), last.nodeId())) {
                reduced.add(last);
            }
        }
        return reduced.toImmutable();
    }

    static ImmutableList<WorkbenchQueueRow> projectWorkbenchQueueRows(
        ImmutableList<ExecutionOverlayNode> stageNodes,
        @Nullable String currentRecipeNodeId,
        int limit
    ) {
        if (limit <= 0) {
            return Lists.immutable.empty();
        }
        ImmutableList<WorkbenchQueueGroup> groups = groupAdjacentRecipeNodes(selectWorkbenchLaneRecipeNodes(stageNodes));
        if (groups.isEmpty()) {
            return Lists.immutable.empty();
        }
        int start = 0;
        if (currentRecipeNodeId != null) {
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).nodeIds().contains(currentRecipeNodeId)) {
                    start = i;
                    break;
                }
            }
        }
        MutableList<WorkbenchQueueRow> rows = Lists.mutable.empty();
        for (int i = start; i < groups.size() && rows.size() < limit; i++) {
            WorkbenchQueueGroup group = groups.get(i);
            rows.add(new WorkbenchQueueRow(
                group.representative(),
                rows.isEmpty() ? WorkbenchQueueRowState.CURRENT : WorkbenchQueueRowState.UPCOMING,
                Math.max(1, group.batchCount()),
                null,
                rows.size() + 1
            ));
        }
        return rows.toImmutable();
    }

    private ImmutableList<WorkbenchQueueRow> projectWorkbenchQueueRowsFromPlan(
        ImmutableList<ExecutionOverlayNode> stageNodes,
        @Nullable String currentRecipeNodeId,
        int limit
    ) {
        if (session == null || session.plan() == null || session.plan().steps().isEmpty() || limit <= 0) {
            return Lists.immutable.empty();
        }
        ImmutableList<InteractionStep> planSteps = session.plan().steps();
        int startIndex = Math.max(0, Math.min(session.nextStepIndex(), planSteps.size() - 1));
        MutableList<InteractionStep.ExecuteRecipeStep> pendingRecipeSteps = Lists.mutable.empty();
        for (int i = startIndex; i < planSteps.size(); i++) {
            if (planSteps.get(i) instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep) {
                pendingRecipeSteps.add(executeRecipeStep);
            }
        }
        if (pendingRecipeSteps.isEmpty() && session.currentStep() instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep) {
            pendingRecipeSteps.add(executeRecipeStep);
        }
        if (pendingRecipeSteps.isEmpty()) {
            return Lists.immutable.empty();
        }
        ImmutableList<ExecutionOverlayNode> recipeNodes = selectWorkbenchLaneRecipeNodes(stageNodes);
        int recipeStart = 0;
        if (currentRecipeNodeId != null && recipeNodes.notEmpty()) {
            for (int i = 0; i < recipeNodes.size(); i++) {
                if (Objects.equals(recipeNodes.get(i).nodeId(), currentRecipeNodeId)) {
                    recipeStart = i;
                    break;
                }
            }
        }
        MutableList<WorkbenchQueueRow> rows = Lists.mutable.empty();
        for (int i = 0; i < pendingRecipeSteps.size() && rows.size() < limit; i++) {
            InteractionStep.ExecuteRecipeStep step = pendingRecipeSteps.get(i);
            ExecutionOverlayNode mapped = mapQueueRecipeNode(recipeNodes, recipeStart + i, step);
            rows.add(new WorkbenchQueueRow(
                mapped,
                i == 0 ? WorkbenchQueueRowState.CURRENT : WorkbenchQueueRowState.UPCOMING,
                Math.max(1, step.times()),
                step,
                i + 1
            ));
        }
        return rows.toImmutable();
    }

    private ExecutionOverlayNode mapQueueRecipeNode(
        ImmutableList<ExecutionOverlayNode> recipeNodes,
        int preferredIndex,
        InteractionStep.ExecuteRecipeStep step
    ) {
        if (recipeNodes.isEmpty()) {
            return focusedWorkbenchRecipeNode() != null
                ? focusedWorkbenchRecipeNode()
                : viewModel.root();
        }
        if (preferredIndex >= 0 && preferredIndex < recipeNodes.size()) {
            return recipeNodes.get(preferredIndex);
        }
        ItemStack output = workbenchPrimaryOutputStack(step);
        if (!output.isEmpty()) {
            for (ExecutionOverlayNode node : recipeNodes) {
                ItemStack nodeOutput = workbenchRecipeDisplayStack(node);
                if (nodeOutput != null && !nodeOutput.isEmpty() && ItemStack.isSameItemSameComponents(nodeOutput, output)) {
                    return node;
                }
            }
        }
        return recipeNodes.getLast();
    }

    private @Nullable ExecutionOverlayNode stageRootOf(String nodeId) {
        if (viewModel == null) {
            return null;
        }
        MutableList<ExecutionOverlayNode> path = Lists.mutable.empty();
        if (!appendPath(viewModel.root(), nodeId, path) || path.isEmpty()) {
            return viewModel.root();
        }
        ExecutionOverlayNode root = path.getFirst();
        for (ExecutionOverlayNode node : path) {
            if (node.children().size() > 1) {
                root = node;
            }
        }
        return root;
    }

    private int countSubtreeNodes(ExecutionOverlayNode node) {
        int total = 1;
        for (ExecutionOverlayNode child : node.children()) {
            total += countSubtreeNodes(child);
        }
        return total;
    }

    private static void collectOverlayNodes(ExecutionOverlayNode node, MutableList<ExecutionOverlayNode> out) {
        out.add(node);
        for (ExecutionOverlayNode child : node.children()) {
            collectOverlayNodes(child, out);
        }
    }

    private static @Nullable ExecutionOverlayNode findNodeById(ExecutionOverlayNode node, String nodeId) {
        if (Objects.equals(node.nodeId(), nodeId)) {
            return node;
        }
        for (ExecutionOverlayNode child : node.children()) {
            ExecutionOverlayNode found = findNodeById(child, nodeId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void updateWorkbenchTooltip(int mouseX, int mouseY, BodyLayout body) {
        if (mouseX < 0 || mouseY < 0 || mouseX > width() || mouseY > height()) {
            scene().setHoverTooltip(Tooltip.create());
            return;
        }
        WorkbenchDockLayout dock = workbenchDockLayout(body);
        if (dock.paged() && insideRect(dock.prevButton(), mouseX, mouseY)) {
            scene().setHoverTooltip(buildWorkbenchDockPageTooltip(false, dock.canPrev()));
            return;
        }
        if (dock.paged() && insideRect(dock.nextButton(), mouseX, mouseY)) {
            scene().setHoverTooltip(buildWorkbenchDockPageTooltip(true, dock.canNext(WorkbenchDockWindow.values().length)));
            return;
        }
        for (WorkbenchDockWindow window : WorkbenchDockWindow.values()) {
            if (insideRect(workbenchDockButtonBounds(body, window), mouseX, mouseY)) {
                scene().setHoverTooltip(buildWorkbenchDockTooltip(window));
                return;
            }
        }
        Rect2i stepButton = workbenchStepButtonBounds(body);
        if (insideRect(stepButton, mouseX, mouseY)) {
            scene().setHoverTooltip(buildWorkbenchControlTooltip(
                "Step",
                "Advance one execution step",
                "Queued",
                Integer.toString(Math.max(0, workbenchQueueRows().size() - 1)),
                0xFFBFD9F7
            ));
            return;
        }
        Rect2i playbackButton = workbenchPlaybackButtonBounds(body);
        if (insideRect(playbackButton, mouseX, mouseY)) {
            boolean auto = workbenchController != null && workbenchController.overlayAutoAdvanceEnabled();
            scene().setHoverTooltip(buildWorkbenchControlTooltip(
                "Auto Advance",
                auto ? "Pause automatic stepping" : "Enable automatic stepping",
                "State",
                auto ? "Running" : "Paused",
                auto ? 0xFF89D7A0 : 0xFFE3B56B
            ));
            return;
        }
        Rect2i detailsButton = workbenchDetailsButtonBounds(body);
        if (insideRect(detailsButton, mouseX, mouseY)) {
            scene().setHoverTooltip(buildWorkbenchControlTooltip(
                "Details",
                "Open execution diagnostics popup",
                "Panel",
                workbenchPopupOpen ? "Opened" : "Closed",
                workbenchPopupOpen ? 0xFF89D7A0 : 0xFFB4BBC3
            ));
            return;
        }
        Rect2i speedValue = workbenchSpeedValueBounds(body);
        Rect2i speedSlider = workbenchSpeedSliderInteractiveBounds(body);
        Rect2i speedRow = workbenchSpeedRowBounds(body);
        if (insideRect(speedValue, mouseX, mouseY) || insideRect(speedSlider, mouseX, mouseY) || insideRect(speedRow, mouseX, mouseY)) {
            scene().setHoverTooltip(buildWorkbenchRateTooltip());
            return;
        }
        if (workbenchPopupOpen && insideRect(currentWorkbenchPopupBounds(), mouseX, mouseY)) {
            scene().setHoverTooltip(buildWorkbenchStatusTooltip());
            return;
        }
        Rect2i content = workbenchContentBounds(body);
        Rect2i console = workbenchConsoleBounds(body);
        Rect2i pathRow = workbenchPathRowBounds(body);
        int contentRight = content.getX() + content.getWidth();
        if (mouseX >= console.getX() && mouseX <= console.getX() + console.getWidth()
            && mouseY >= console.getY() && mouseY <= console.getY() + console.getHeight()) {
            if (insideRect(workbenchConsoleTargetSummaryBounds(body), mouseX, mouseY)) {
                scene().setHoverTooltip(buildWorkbenchTargetTooltip());
                return;
            }
            ExecutionOverlayNode hoveredPathNode = hoveredWorkbenchPathNode(
                mouseX,
                mouseY,
                pathRow.getX() + 3,
                pathRow.getY() + Math.max(1, (pathRow.getHeight() - 14) / 2),
                Math.max(20, pathRow.getWidth() - 6)
            );
            if (hoveredPathNode != null) {
                scene().setHoverTooltip(buildNodeTooltip(hoveredPathNode));
                return;
            }
            if (insideRect(pathRow, mouseX, mouseY)) {
                scene().setHoverTooltip(buildWorkbenchPathTooltip());
                return;
            }
            ExecutionOverlayNode focus = focusedWorkbenchNode();
            if (focus != null) {
                scene().setHoverTooltip(buildNodeTooltip(focus));
                return;
            }
            scene().setHoverTooltip(buildWorkbenchHelpTooltip());
            return;
        }
        int progressStartY = content.getY() + workbenchFixedConsoleHeight() + workbenchSectionGap() - scrollY;
        if (activeWorkbenchWindow == WorkbenchDockWindow.recipe) {
            Rect2i recipeHero = new Rect2i(content.getX(), progressStartY, content.getWidth(), workbenchRecipeHeroHeight);
            if (insideRect(recipeHero, mouseX, mouseY)) {
                ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
                ExecutionOverlayNode inputNode = recipeFocus == null ? focusedWorkbenchNode() : recipeFocus;
                @Nullable InteractionStep.ExecuteRecipeStep previewStep = resolveWorkbenchPreviewStep();
                ImmutableList<WorkbenchInputEntry> inputs = previewStep == null
                    ? (inputNode == null ? Lists.immutable.empty() : mergedWorkbenchInputs(inputNode))
                    : mergedWorkbenchInputs(previewStep.inputs());
                ImmutableList<WorkbenchOutputEntry> outputs = previewStep == null
                    ? workbenchSingleOutputEntries(recipeFocus == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(recipeFocus))
                    : mergedWorkbenchOutputs(previewStep.expectedOutputs());
                ExecutionOverlayNode hoveredStageNode = hoveredWorkbenchStageStatusNode(
                    mouseX,
                    mouseY,
                    content.getX() + 8,
                    progressStartY + 92,
                    Math.max(24, content.getWidth() - 16)
                );
                if (hoveredStageNode != null) {
                    scene().setHoverTooltip(buildNodeTooltip(hoveredStageNode));
                    return;
                }
                int outputRailW = Math.min(Math.max(20, content.getWidth() - 36), workbenchOutputStripWidth(outputs, 20, 3));
                int outputRailX = content.getX() + content.getWidth() - 8 - outputRailW;
                int inputRailX = content.getX() + 8;
                int inputRailW = Math.max(26, outputRailX - inputRailX - 12);
                WorkbenchInputStripLayout inputLayout = workbenchInputStripLayout(
                    inputs,
                    inputRailX,
                    progressStartY + 46,
                    inputRailW,
                    20,
                    workbenchRecipeInputVisibleLimit,
                    true
                );
                Rect2i outputStrip = new Rect2i(outputRailX, progressStartY + 46, outputRailW, 20);
                if (inputLayout.paged() && insideRect(inputLayout.prevButton(), mouseX, mouseY)) {
                    scene().setHoverTooltip(buildWorkbenchInputPageTooltip(false, inputLayout.canPrev(workbenchInputStripScrollX)));
                    return;
                }
                if (inputLayout.paged() && insideRect(inputLayout.nextButton(), mouseX, mouseY)) {
                    scene().setHoverTooltip(buildWorkbenchInputPageTooltip(true, inputLayout.canNext(workbenchInputStripScrollX)));
                    return;
                }
                if (insideRect(inputLayout.stripBounds(), mouseX, mouseY)) {
                    scene().setHoverTooltip(buildWorkbenchInputsTooltip(inputs));
                    return;
                }
                if (insideRect(outputStrip, mouseX, mouseY) && recipeFocus != null) {
                    scene().setHoverTooltip(buildNodeTooltip(recipeFocus));
                    return;
                }
                scene().setHoverTooltip(buildWorkbenchRecipeTooltip(recipeFocus, inputNode, inputs));
                return;
            }
            ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> materialRequirements = workbenchMaterialRequirements();
            int materialY = progressStartY + workbenchRecipeHeroHeight + workbenchSectionGap();
            Rect2i materialRect = new Rect2i(
                content.getX(),
                materialY,
                content.getWidth(),
                workbenchMaterialSectionHeight(materialRequirements)
            );
            if (insideRect(materialRect, mouseX, mouseY)) {
                ExecutionOverlayController.OverlayMaterialRequirement hovered = hoveredWorkbenchMaterialRequirement(
                    mouseX,
                    mouseY,
                    materialRect,
                    materialRequirements
                );
                scene().setHoverTooltip(buildWorkbenchMaterialTooltip(materialRequirements, hovered));
                return;
            }
        }
        if (activeWorkbenchWindow == WorkbenchDockWindow.timeline
            && mouseX >= content.getX() && mouseX <= contentRight
            && mouseY >= progressStartY && mouseY <= progressStartY + workbenchProgressSectionHeight()) {
            int stageY = progressStartY + 43;
            int operationalY = stageY + workbenchStageRailChipHeight + 2;
            ExecutionOverlayNode hoveredStageNode = hoveredWorkbenchStageStatusNode(
                mouseX,
                mouseY,
                content.getX() + 6,
                stageY,
                Math.max(24, content.getWidth() - 12)
            );
            if (hoveredStageNode != null) {
                scene().setHoverTooltip(buildNodeTooltip(hoveredStageNode));
                return;
            }
            ExecutionOverlayNode hoveredStatusNode = hoveredWorkbenchOperationalStatusNode(
                Minecraft.getInstance().font,
                mouseX,
                mouseY,
                content.getX() + 6,
                operationalY,
                Math.max(24, content.getWidth() - 12)
            );
            if (hoveredStatusNode != null) {
                scene().setHoverTooltip(buildNodeTooltip(hoveredStatusNode));
                return;
            }
            ExecutionOverlayNode focus = focusedWorkbenchNode();
            if (focus != null) {
                scene().setHoverTooltip(buildNodeTooltip(focus));
            } else {
                scene().setHoverTooltip(buildWorkbenchProgressTooltip());
            }
            return;
        }
        if (activeWorkbenchWindow == WorkbenchDockWindow.inspect
            && mouseX >= content.getX() && mouseX <= contentRight
            && mouseY >= progressStartY
            && mouseY <= progressStartY + workbenchInspectContentHeight()) {
            int pathY = progressStartY + workbenchInspectSummaryHeight() + workbenchSectionGap();
            Rect2i pathRect = new Rect2i(content.getX(), pathY, content.getWidth(), workbenchInspectPathHeight());
            if (insideRect(pathRect, mouseX, mouseY)) {
                ExecutionOverlayNode hoveredPathNode = hoveredWorkbenchPathNode(
                    mouseX,
                    mouseY,
                    pathRect.getX() + 6,
                    pathRect.getY() + 6,
                    Math.max(20, pathRect.getWidth() - 12)
                );
                if (hoveredPathNode != null) {
                    scene().setHoverTooltip(buildNodeTooltip(hoveredPathNode));
                } else {
                    scene().setHoverTooltip(buildWorkbenchPathTooltip());
                }
                return;
            }
            scene().setHoverTooltip(buildWorkbenchStatusTooltip());
            return;
        }
        ImmutableList<ExecutionOverlayNode> laneNodes = workbenchLaneNodes();
        int laneSectionY = progressStartY + workbenchProgressSectionHeight() + workbenchSectionGap();
        int rowY = laneSectionY + 4;
        if (activeWorkbenchWindow == WorkbenchDockWindow.timeline) {
            for (int i = 0; i < laneNodes.size(); i++) {
                int rowH = workbenchLaneRowHeight(laneNodes.get(i));
                if (mouseX >= content.getX() + 4 && mouseX <= contentRight - 4 && mouseY >= rowY && mouseY <= rowY + rowH) {
                    scene().setHoverTooltip(buildNodeTooltip(laneNodes.get(i)));
                    return;
                }
                rowY += rowH + workbenchLaneRowGap();
            }
        }
        if (activeWorkbenchWindow != WorkbenchDockWindow.inspect) {
            int queueY = switch (activeWorkbenchWindow) {
                case recipe -> progressStartY
                    + workbenchRecipeHeroHeight
                    + workbenchSectionGap()
                    + workbenchMaterialSectionHeight(workbenchMaterialRequirements())
                    + workbenchSectionGap();
                case timeline -> laneSectionY + workbenchLaneSectionHeight(laneNodes) + workbenchSectionGap();
                case inspect -> progressStartY;
            };
            ImmutableList<WorkbenchQueueRow> queueRows = workbenchQueueRows();
            for (WorkbenchQueueRow row : queueRows) {
                if (mouseX >= content.getX() + 4 && mouseX <= contentRight - 4 && mouseY >= queueY && mouseY <= queueY + workbenchUpcomingCardHeight()) {
                    scene().setHoverTooltip(buildWorkbenchQueueTooltip(row));
                    return;
                }
                queueY += workbenchUpcomingCardHeight() + workbenchUpcomingCardGap();
            }
        }
        if (activeFooterHeight() > 0 && mouseY >= height() - activeFooterHeight() && mouseY <= height()) {
            scene().setHoverTooltip(buildWorkbenchStatusTooltip());
            return;
        }
        scene().setHoverTooltip(Tooltip.create());
    }

    private Tooltip buildWorkbenchDockTooltip(WorkbenchDockWindow window) {
        Tooltip tip = Tooltip.create();
        var builder = FlowChartTooltipComponent.builder()
            .section(window.label(), 0xFF7D8791);
        for (String line : workbenchDockDescription(window)) {
            if (!line.isBlank() && !line.equals(window.label())) {
                builder.text(line, 0xFFD4DBE3, false);
            }
        }
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchDockPageTooltip(boolean next, boolean enabled) {
        Tooltip tip = Tooltip.create();
        var builder = FlowChartTooltipComponent.builder()
            .text(Component.translatable(next
                    ? "cirrus.ui.execution.overlay.dock.page.next"
                    : "cirrus.ui.execution.overlay.dock.page.prev").getString(), 0xFFE9EEF2, false);
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchInputPageTooltip(boolean next, boolean enabled) {
        Tooltip tip = Tooltip.create();
        var builder = FlowChartTooltipComponent.builder()
            .section(Component.translatable("cirrus.ui.execution.overlay.input.title").getString(), 0xFF7D8791)
            .text(Component.translatable(next
                    ? "cirrus.ui.execution.overlay.input.page.next"
                    : "cirrus.ui.execution.overlay.input.page.prev").getString(), 0xFFE9EEF2, false);
        if (!enabled) {
            builder.text(Component.translatable("cirrus.ui.execution.overlay.input.page.edge").getString(), 0xFF9AA8B8, false);
        }
        tip.add(builder.build());
        return tip;
    }

    private static String[] workbenchDockDescription(WorkbenchDockWindow window) {
        return switch (window) {
            case recipe -> new String[]{
                Component.translatable("cirrus.ui.execution.overlay.dock.recipe.desc1").getString(),
                Component.translatable("cirrus.ui.execution.overlay.dock.recipe.desc2").getString()
            };
            case timeline -> new String[]{
                Component.translatable("cirrus.ui.execution.overlay.dock.timeline.desc1").getString(),
                Component.translatable("cirrus.ui.execution.overlay.dock.timeline.desc2").getString()
            };
            case inspect -> new String[]{
                Component.translatable("cirrus.ui.execution.overlay.dock.inspect.desc1").getString(),
                Component.translatable("cirrus.ui.execution.overlay.dock.inspect.desc2").getString()
            };
        };
    }

    private Tooltip buildWorkbenchControlTooltip(String title, String action, String stateKey, String stateValue, int stateColor) {
        Tooltip tip = Tooltip.create();
        var builder = FlowChartTooltipComponent.builder()
            .section(title, 0xFF7D8791)
            .text(action, 0xFFD4DBE3, false)
            .kv(stateKey, 0xFF9AA8B8, stateValue, stateColor);
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchRateTooltip() {
        Tooltip tip = Tooltip.create();
        double rate = workbenchController == null ? 1.0d : workbenchController.overlayRatePerSecond();
        float slider = (float) Math.max(0.0d, Math.min(1.0d, ExecutionOverlayControlsWidget.rateToSlider(rate)));
        String title = Component.translatable("cirrus.ui.execution.overlay.summary.rate").getString();
        String currentKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.current").getString();
        String rangeKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.range").getString();
        String editKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.edit").getString();
        String typingValue = Component.translatable("cirrus.ui.execution.overlay.tooltip.typing").getString();
        var builder = FlowChartTooltipComponent.builder()
            .section(title, 0xFF7FAFD6)
            .bar(slider, 0xFF6EAAE6, 132)
            .kv(currentKey, 0xFF9AA8B8, String.format(java.util.Locale.ROOT, "x%.1f", rate), 0xFFE9EEF2)
            .kv(
                rangeKey,
                0xFF9AA8B8,
                String.format(java.util.Locale.ROOT, "%.1f - %.1f", ExecutionOverlayControlsWidget.minRate(), ExecutionOverlayControlsWidget.maxRate()),
                0xFFD4DBE3
            )
            .text(Component.translatable("cirrus.ui.execution.overlay.rate.hint.drag").getString(), 0xFFD4DBE3, false)
            .text(Component.translatable("cirrus.ui.execution.overlay.rate.hint.type").getString(), 0xFFB8C4D1, false);
        if (workbenchRateEditing) {
            builder.kv(editKey, 0xFF9AA8B8, typingValue, 0xFF89D7A0);
        }
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchPathTooltip() {
        Tooltip tip = Tooltip.create();
        ImmutableList<ExecutionOverlayNode> trail = pathContextNodes();
        if (trail.isEmpty() && viewModel != null) {
            trail = Lists.immutable.of(viewModel.root());
        }
        String current = trail.isEmpty() ? "-" : trail.getLast().label().getString();
        String joined = trail.take(4).collect(node -> compactWorkbenchLabel(node.label().getString(), 2)).makeString(" > ");
        if (trail.size() > 4) {
            joined += " > ...";
        }
        String pathTitle = Component.translatable("cirrus.ui.execution.overlay.tooltip.path").getString();
        String depthKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.depth").getString();
        String currentKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.current").getString();
        var builder = FlowChartTooltipComponent.builder()
            .section(pathTitle, 0xFF7D8791)
            .kv(depthKey, 0xFF9AA8B8, Integer.toString(trail.size()), 0xFFE9EEF2)
            .kv(currentKey, 0xFF9AA8B8, current, 0xFFD4DBE3)
            .text(joined.isBlank() ? "-" : joined, 0xFFB8C4D1, false)
            .text(Component.translatable("cirrus.ui.execution.overlay.path.hint.scroll").getString(), 0xFF9AA8B8, false);
        tip.add(builder.build());
        return tip;
    }

    private @Nullable ExecutionOverlayController.OverlayMaterialRequirement hoveredWorkbenchMaterialRequirement(
        int mouseX,
        int mouseY,
        Rect2i sectionRect,
        ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> requirements
    ) {
        if (requirements.isEmpty() || !insideRect(sectionRect, mouseX, mouseY)) {
            return null;
        }
        int localY = mouseY - sectionRect.getY() - 4;
        if (localY < 0) {
            return null;
        }
        int rowStride = workbenchMaterialRowHeight + workbenchMaterialRowGap;
        int rowIndex = localY / rowStride;
        int visible = Math.min(workbenchMaterialVisibleLimit, requirements.size());
        if (rowIndex < 0 || rowIndex >= visible) {
            return null;
        }
        int rowTop = rowIndex * rowStride;
        if (localY > rowTop + workbenchMaterialRowHeight) {
            return null;
        }
        return requirements.get(rowIndex);
    }

    private Tooltip buildWorkbenchMaterialTooltip(
        ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> requirements,
        @Nullable ExecutionOverlayController.OverlayMaterialRequirement hovered
    ) {
        Tooltip tip = Tooltip.create();
        String title = Component.translatable("cirrus.ui.execution.overlay.material.title").getString();
        String uniqueKey = Component.translatable("cirrus.ui.execution.overlay.material.unique").getString();
        String needKey = Component.translatable("cirrus.ui.execution.overlay.material.need").getString();
        String haveKey = Component.translatable("cirrus.ui.execution.overlay.material.have").getString();
        String missKey = Component.translatable("cirrus.ui.execution.overlay.material.miss").getString();
        var builder = FlowChartTooltipComponent.builder()
            .section(title, 0xFF7D8791)
            .kv(uniqueKey, 0xFF9AA8B8, Integer.toString(requirements.size()), 0xFFE9EEF2);
        if (requirements.isEmpty()) {
            builder.text(Component.translatable("cirrus.ui.execution.overlay.material.none").getString(), 0xFFB8C4D1, false);
            tip.add(builder.build());
            return tip;
        }
        ExecutionOverlayController.OverlayMaterialRequirement selected = hovered == null ? requirements.getFirst() : hovered;
        int previewCount = Math.min(5, requirements.size());
        for (int i = 0; i < previewCount; i++) {
            ExecutionOverlayController.OverlayMaterialRequirement requirement = requirements.get(i);
            String suffix = compactMetricNumber(requirement.required()) + "/"
                + compactMetricNumber(requirement.available()) + "/"
                + compactMetricNumber(requirement.missing());
            int suffixColor = requirement.missing() > 0L ? 0xFFE08A8A : 0xFF9FD2AF;
            builder.iconText(
                requirement.stack(),
                0xFF2A2A2A,
                requirement.stack().getHoverName().getString(),
                0xFFE9EEF2,
                suffix,
                suffixColor
            );
        }
        if (requirements.size() > previewCount) {
            builder.text(
                Component.translatable("cirrus.ui.execution.overlay.material.more", requirements.size() - previewCount).getString(),
                0xFF9AA8B8,
                false
            );
        }
        builder.divider()
            .iconText(
                selected.stack(),
                0xFF2A2A2A,
                selected.stack().getHoverName().getString(),
                0xFFE9EEF2,
                missKey + " " + compactMetricNumber(selected.missing()),
                selected.missing() > 0L ? 0xFFE08A8A : 0xFF89D7A0
            )
            .kv(needKey, 0xFF9AA8B8, compactMetricNumber(selected.required()), 0xFFD4DBE3)
            .kv(haveKey, 0xFF9AA8B8, compactMetricNumber(selected.available()), 0xFFD4DBE3)
            .kv(missKey, 0xFF9AA8B8, compactMetricNumber(selected.missing()), selected.missing() > 0L ? 0xFFE08A8A : 0xFF89D7A0);
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchInputsTooltip(ImmutableList<WorkbenchInputEntry> inputs) {
        Tooltip tip = Tooltip.create();
        var builder = FlowChartTooltipComponent.builder()
            .section(Component.translatable("cirrus.ui.execution.overlay.input.title").getString(), 0xFF7D8791)
            .kv(Component.translatable("cirrus.ui.execution.overlay.material.unique").getString(), 0xFF9AA8B8, Integer.toString(inputs.size()), 0xFFE9EEF2);
        if (inputs.isEmpty()) {
            builder.text(Component.translatable("cirrus.ui.execution.overlay.material.none").getString(), 0xFFB8C4D1, false);
        } else {
            int preview = Math.min(4, inputs.size());
            for (int i = 0; i < preview; i++) {
                WorkbenchInputEntry entry = inputs.get(i);
                String name = entry.stack() == null || entry.stack().isEmpty() ? "-" : entry.stack().getHoverName().getString();
                builder.iconText(entry.stack(), 0xFF2A2A2A, name, 0xFFE9EEF2, "x" + Math.max(1, entry.count()), 0xFFD4DBE3);
            }
            if (inputs.size() > preview) {
                builder.text(
                    Component.translatable("cirrus.ui.execution.overlay.material.more", inputs.size() - preview).getString(),
                    0xFFB8C4D1,
                    false
                );
            }
            builder.text(Component.translatable("cirrus.ui.execution.overlay.input.hint.page").getString(), 0xFF9AA8B8, false);
        }
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchRecipeTooltip(
        @Nullable ExecutionOverlayNode recipeFocus,
        @Nullable ExecutionOverlayNode inputNode,
        ImmutableList<WorkbenchInputEntry> inputs
    ) {
        if (recipeFocus == null && inputNode == null) {
            return buildWorkbenchHelpTooltip();
        }
        @Nullable InteractionStep.ExecuteRecipeStep previewStep = resolveWorkbenchPreviewStep();
        ExecutionOverlayNode node = recipeFocus != null ? recipeFocus : inputNode;
        ImmutableList<WorkbenchInputEntry> previewInputs = resolveWorkbenchPreviewInputs(previewStep, inputs);
        ImmutableList<WorkbenchOutputEntry> previewOutputs = previewStep == null
            ? workbenchSingleOutputEntries(node == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(node))
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());
        Tooltip tip = Tooltip.create();
        var builder = FlowChartTooltipComponent.builder()
            .section(Component.translatable("cirrus.ui.execution.overlay.dock.recipe").getString(), 0xFF7D8791)
            .recipeCard(new FlowChartTooltipComponent.Entry.RecipeCard(
                workbenchTooltipRecipeInputSlots(previewInputs),
                workbenchTooltipRecipeOutputSlotsFromEntries(previewOutputs),
                workbenchTooltipRecipeCatalystSlots(previewStep),
                workbenchTooltipRecipeMachineSlot(previewStep)
            ));
        if (node != null) {
            builder.divider()
                .kv(
                    Component.translatable("cirrus.ui.execution.overlay.node.progress").getString(),
                    0xFF9AA8B8,
                    segmentProgressOf(node),
                    0xFFD4DBE3
                );
        }
        if (telemetrySnapshot != null) {
            builder.kv(
                Component.translatable("cirrus.ui.execution.overlay.summary.rate").getString(),
                0xFF9AA8B8,
                String.format(java.util.Locale.ROOT, "x%.1f", telemetrySnapshot.ratePerSecond()),
                0xFFD4DBE3
            );
        }
        tip.add(builder.build());
        return tip;
    }
    private Tooltip buildWorkbenchProgressTooltip() {
        if (viewModel == null) {
            return buildWorkbenchHelpTooltip();
        }
        int completed = telemetrySnapshot == null ? viewModel.completedStepCount() : telemetrySnapshot.completedSteps();
        int total = telemetrySnapshot == null ? viewModel.totalStepCount() : telemetrySnapshot.totalSteps();
        float progress = telemetrySnapshot == null
            ? (total <= 0 ? 0.0f : (float) (completed / (double) total))
            : (float) Math.max(0.0d, Math.min(1.0d, telemetrySnapshot.progress()));
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        Tooltip tip = Tooltip.create();
        String title = Component.translatable("cirrus.ui.execution.overlay.tooltip.progress").getString();
        String doneKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.done").getString();
        String nowKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.now").getString();
        var builder = FlowChartTooltipComponent.builder()
            .section(title, 0xFF7D8791)
            .bar(progress, 0xFF6EAAE6, 132)
            .kv(doneKey, 0xFF9AA8B8, completed + "/" + total, 0xFFE9EEF2);
        if (focus != null) {
            builder.kv(nowKey, 0xFF9AA8B8, resolveCurrentStepLabel(focus), 0xFFD4DBE3);
        }
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchQueueTooltip(WorkbenchQueueRow row) {
        ExecutionOverlayNode node = row.node();
        @Nullable InteractionStep.ExecuteRecipeStep previewStep = row.previewStep();
        boolean current = row.state() == WorkbenchQueueRowState.CURRENT;
        int batchCount = Math.max(1, row.batchCount());
        int total = countSubtreeNodes(node);
        int done = countCompletedSubtreeNodes(node);
        float ratio = total <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, done / (float) total));
        ImmutableList<WorkbenchInputEntry> previewInputs = previewStep == null
            ? mergedWorkbenchInputs(node)
            : resolveWorkbenchPreviewInputs(previewStep, mergedWorkbenchInputs(node));
        ImmutableList<WorkbenchOutputEntry> previewOutputs = previewStep == null
            ? workbenchSingleOutputEntries(workbenchRecipeDisplayStack(node))
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());
        List<MaterialSlot> catalystSlots = previewStep == null
            ? List.of()
            : workbenchTooltipRecipeCatalystSlots(previewStep);
        @Nullable MaterialSlot machineSlot = workbenchTooltipRecipeMachineSlot(previewStep);
        String title = workbenchQueueRecipeTitle(previewStep, node);
        ItemStack icon = workbenchPrimaryOutputStack(previewStep);
        if (icon.isEmpty()) {
            icon = workbenchRecipeDisplayStack(node);
        }
        String sectionTitle = Component.translatable(
            current ? "cirrus.ui.execution.overlay.tooltip.executing" : "cirrus.ui.execution.overlay.tooltip.queued"
        ).getString();
        String stepKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.step").getString();
        String orderKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.order").getString();
        String batchKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.batch").getString();
        Tooltip tip = Tooltip.create();
        var builder = FlowChartTooltipComponent.builder()
            .section(sectionTitle, current ? 0xFF6EAAE6 : 0xFF7D8791)
            .iconText(icon, 0xFF2A2A2A, title, 0xFFE9EEF2, done + "/" + total, 0xFFD4DBE3)
            .bar(ratio, current ? 0xFF6EAAE6 : 0xFF7E92A6, 132)
            .recipeCard(new FlowChartTooltipComponent.Entry.RecipeCard(
                workbenchTooltipRecipeInputSlots(previewInputs),
                workbenchTooltipRecipeOutputSlotsFromEntries(previewOutputs),
                catalystSlots,
                machineSlot
            ))
            .kv(stepKey, 0xFF9AA8B8, resolveCurrentStepLabel(node), 0xFFD4DBE3);
        builder.kv(orderKey, 0xFF9AA8B8, "#" + Math.max(1, row.order()), 0xFFD4DBE3);
        if (batchCount > 1) {
            builder.kv(batchKey, 0xFF9AA8B8, "x" + batchCount, 0xFFE9EEF2);
        }
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchHelpTooltip() {
        Tooltip tip = Tooltip.create();
        var builder = FlowChartTooltipComponent.builder()
            .section(Component.translatable("cirrus.ui.execution.overlay.tooltip.executor").getString(), 0xFF7D8791)
            .text(Component.translatable("cirrus.ui.execution.overlay.help.dock").getString(), 0xFFD4DBE3, false)
            .text(Component.translatable("cirrus.ui.execution.overlay.help.wheel").getString(), 0xFFD4DBE3, false)
            .text(Component.translatable("cirrus.ui.execution.overlay.help.shift_wheel").getString(), 0xFFD4DBE3, false);
        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
            String errorPrefix = Component.translatable("cirrus.ui.execution.overlay.summary.error").getString();
            builder.divider(0xFF8A4040).text(errorPrefix + ": " + telemetrySnapshot.errorSummary(), 0xFFE26B6B, false);
        }
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchStatusTooltip() {
        Tooltip tip = Tooltip.create();
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
        @Nullable InteractionStep.ExecuteRecipeStep previewStep = resolveWorkbenchPreviewStep();
        ImmutableList<WorkbenchInputEntry> fallbackInputs = recipeFocus != null
            ? mergedWorkbenchInputs(recipeFocus)
            : (focus == null ? Lists.immutable.empty() : mergedWorkbenchInputs(focus));
        ImmutableList<WorkbenchInputEntry> previewInputs = resolveWorkbenchPreviewInputs(previewStep, fallbackInputs);
        ImmutableList<WorkbenchOutputEntry> previewOutputs = previewStep == null
            ? workbenchSingleOutputEntries(recipeFocus == null
                ? (focus == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(focus))
                : workbenchRecipeDisplayStack(recipeFocus))
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());
        ItemStack recipeIcon = previewOutputs.notEmpty()
            ? previewOutputs.get(0).stack()
            : (recipeFocus != null ? workbenchRecipeDisplayStack(recipeFocus) : (focus == null ? ItemStack.EMPTY : focus.iconStack()));
        if (recipeIcon == null) {
            recipeIcon = ItemStack.EMPTY;
        }
        String nowLabel = focus == null ? "-" : resolveCurrentStepLabel(focus);
        String recipeTitle = recipeFocus != null
            ? workbenchRecipeTitle(recipeFocus)
            : (recipeIcon.isEmpty() ? nowLabel : recipeIcon.getHoverName().getString());
        String runTitle = Component.translatable("cirrus.ui.execution.overlay.tooltip.run_status").getString();
        String statusKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.status").getString();
        String doneKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.done").getString();
        String rateKey = Component.translatable("cirrus.ui.execution.overlay.summary.rate").getString();
        String warnKey = Component.translatable("cirrus.ui.execution.overlay.summary.warn").getString();
        String nextKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.next").getString();
        String nowKey = Component.translatable("cirrus.ui.execution.overlay.tooltip.now").getString();
        var builder = FlowChartTooltipComponent.builder()
            .section(runTitle, 0xFF7D8791)
            .kv(statusKey, 0xFF9AA8B8, viewModel.statusLabel().getString(), statusColor(viewModel.statusLabel().getString()))
            .kv(doneKey, 0xFF9AA8B8, viewModel.completedStepCount() + "/" + viewModel.totalStepCount(), 0xFFE9EEF2);
        if (telemetrySnapshot != null) {
            builder.kv(rateKey, 0xFF9AA8B8, String.format(java.util.Locale.ROOT, "%.1f /s", telemetrySnapshot.ratePerSecond()), 0xFFD4DBE3);
            builder.kv(warnKey, 0xFF9AA8B8, Integer.toString(telemetrySnapshot.warnings().size()), telemetrySnapshot.warnings().isEmpty() ? 0xFF89D7A0 : 0xFFE3B56B);
            if (telemetrySnapshot.next() != null) {
                builder.kv(nextKey, 0xFF9AA8B8, telemetrySnapshot.next().label(), 0xFFD4DBE3);
            }
        }
        if (previewInputs.notEmpty() || previewOutputs.notEmpty()) {
            builder
                .divider()
                .iconText(recipeIcon, 0xFF2A2A2A, recipeTitle, 0xFFE9EEF2, nowLabel, 0xFFD4DBE3)
                .recipeCard(new FlowChartTooltipComponent.Entry.RecipeCard(
                    workbenchTooltipRecipeInputSlots(previewInputs),
                    workbenchTooltipRecipeOutputSlotsFromEntries(previewOutputs),
                    workbenchTooltipRecipeCatalystSlots(previewStep),
                    workbenchTooltipRecipeMachineSlot(previewStep)
                ));
        } else {
            builder.kv(nowKey, 0xFF9AA8B8, nowLabel, 0xFFE9EEF2);
        }
        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
            String errorPrefix = Component.translatable("cirrus.ui.execution.overlay.summary.error").getString();
            builder.divider(0xFF8A4040).text(errorPrefix + ": " + telemetrySnapshot.errorSummary(), 0xFFE26B6B, false);
        }
        tip.add(builder.build());
        return tip;
    }

    private Tooltip buildWorkbenchTargetTooltip() {
        if (viewModel == null) {
            return buildWorkbenchHelpTooltip();
        }
        Tooltip tip = Tooltip.create();
        ExecutionOverlayNode focus = focusedWorkbenchNode();
        ExecutionOverlayNode recipeFocus = focusedWorkbenchRecipeNode();
        @Nullable InteractionStep.ExecuteRecipeStep previewStep = resolveWorkbenchPreviewStep();
        ImmutableList<WorkbenchOutputEntry> previewOutputs = previewStep == null
            ? workbenchSingleOutputEntries(recipeFocus == null ? ItemStack.EMPTY : workbenchRecipeDisplayStack(recipeFocus))
            : mergedWorkbenchOutputs(previewStep.expectedOutputs());
        ItemStack recipeOutput = previewOutputs.isEmpty() ? ItemStack.EMPTY : previewOutputs.get(0).stack();
        ItemStack target = recipeFocus == null
            ? (focus == null ? ItemStack.EMPTY : focus.iconStack())
            : workbenchRecipeDisplayStack(recipeFocus);
        if (target == null || target.isEmpty()) {
            target = recipeOutput;
        }
        String rawStatus = telemetrySnapshot == null ? viewModel.statusLabel().getString() : telemetrySnapshot.status();
        String fallbackStatus = viewModel == null ? "running" : statusTextOf(viewModel.status());
        String statusText = isAbstractStatusWord(rawStatus) ? rawStatus : fallbackStatus;
        int completed = telemetrySnapshot == null ? viewModel.completedStepCount() : telemetrySnapshot.completedSteps();
        int total = telemetrySnapshot == null ? viewModel.totalStepCount() : telemetrySnapshot.totalSteps();
        ExecutionOverlayController.OverlayTargetProgress targetProgress = workbenchController == null
            ? null
            : workbenchController.overlayTargetProgress();
        long requiredOutput = targetProgress == null ? Math.max(0L, total) : targetProgress.required();
        long currentOutput = targetProgress == null
            ? Math.max(0L, completed)
            : Math.min(targetProgress.required(), targetProgress.current());
        long missingOutput = Math.max(0L, requiredOutput - currentOutput);
        String doneText = compactMetricNumber(currentOutput) + "/" + compactMetricNumber(requiredOutput);
        float progress = telemetrySnapshot == null
            ? (total <= 0 ? 0.0f : (float) (completed / (double) total))
            : (float) Math.max(0.0d, Math.min(1.0d, telemetrySnapshot.progress()));
        int progressPercent = Math.max(0, Math.min(100, (int) Math.round(progress * 100.0f)));
        String now = telemetrySnapshot != null && telemetrySnapshot.current() != null
            ? telemetrySnapshot.current().label()
            : (focus == null ? "-" : resolveCurrentStepLabel(focus));
        long remaining = missingOutput;
        int queued = Math.max(0, workbenchQueueRows().size() - 1);
        int warningCount = telemetrySnapshot == null ? 0 : telemetrySnapshot.warnings().size();
        boolean hasError = telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null;
        double rate = workbenchController != null
            ? workbenchController.overlayRatePerSecond()
            : (telemetrySnapshot == null ? 1.0d : telemetrySnapshot.ratePerSecond());
        String targetLabel = Component.translatable("cirrus.ui.execution.overlay.summary.target").getString();
        String executionLabel = Component.translatable("cirrus.ui.execution.overlay.summary.execution").getString();
        String alertLabel = Component.translatable("cirrus.ui.execution.overlay.summary.alert").getString();
        String queueLabel = Component.translatable("cirrus.ui.execution.overlay.summary.queue").getString();
        String warnLabel = Component.translatable("cirrus.ui.execution.overlay.summary.warn").getString();
        String leftLabel = Component.translatable("cirrus.ui.execution.overlay.summary.left").getString();
        String needLabel = Component.translatable("cirrus.ui.execution.overlay.summary.need").getString();
        String haveLabel = Component.translatable("cirrus.ui.execution.overlay.summary.have").getString();
        ImmutableList<WorkbenchInputEntry> fallbackInputs = recipeFocus != null
            ? mergedWorkbenchInputs(recipeFocus)
            : (focus == null ? Lists.immutable.empty() : mergedWorkbenchInputs(focus));
        ImmutableList<WorkbenchInputEntry> previewInputs = resolveWorkbenchPreviewInputs(previewStep, fallbackInputs);
        List<MaterialSlot> inputPreviewSlots = workbenchTooltipRecipeInputSlots(previewInputs);
        List<MaterialSlot> outputPreviewSlots = workbenchTooltipRecipeOutputSlotsFromEntries(previewOutputs);
        List<MaterialSlot> catalystPreviewSlots = workbenchTooltipRecipeCatalystSlots(previewStep);
        @Nullable MaterialSlot machinePreviewSlot = workbenchTooltipRecipeMachineSlot(previewStep);

        var builder = FlowChartTooltipComponent.builder()
            .section(targetLabel, statusColor(statusText))
            .recipeCard(new FlowChartTooltipComponent.Entry.RecipeCard(
                inputPreviewSlots,
                outputPreviewSlots,
                catalystPreviewSlots,
                machinePreviewSlot
            ))
            .divider()
            .iconText(target, 0xFF2A2A2A, now, 0xFFE9EEF2, doneText, 0xFFD4DBE3)
            .bar(progress, statusColor(statusText), 132)
            .kv(targetLabel, 0xFF9AA8B8, doneText + " · " + progressPercent + "%", 0xFFE9EEF2)
            .kv(executionLabel, 0xFF9AA8B8, "x" + formatWorkbenchRate(rate) + " · " + queueLabel + " " + queued, 0xFFD4DBE3)
            .kv(
                alertLabel,
                0xFF9AA8B8,
                warnLabel + " " + warningCount + " · " + leftLabel + " " + compactMetricNumber(remaining),
                hasError ? 0xFFE58E8E : 0xFFD4DBE3
            )
            .kv(needLabel, 0xFF9AA8B8, compactMetricNumber(requiredOutput), 0xFFE9EEF2)
            .kv(haveLabel, 0xFF9AA8B8, compactMetricNumber(currentOutput), 0xFFD4DBE3)
            .kv(leftLabel, 0xFF9AA8B8, compactMetricNumber(remaining), remaining == 0L ? 0xFF89D7A0 : 0xFFE3B56B);
        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
            String errorPrefix = Component.translatable("cirrus.ui.execution.overlay.summary.error").getString();
            builder.divider(0xFF8A4040).text(errorPrefix + ": " + telemetrySnapshot.errorSummary(), 0xFFE26B6B, false);
        }
        tip.add(builder.build());
        return tip;
    }

    private @Nullable InteractionStep.ExecuteRecipeStep resolveWorkbenchPreviewStep() {
        if (session == null || session.plan() == null || session.plan().steps().isEmpty()) {
            return null;
        }
        if (session.currentStep() instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep) {
            return executeRecipeStep;
        }
        ImmutableList<InteractionStep> steps = session.plan().steps();
        int start = Math.max(0, Math.min(session.nextStepIndex(), steps.size() - 1));
        for (int i = start; i < steps.size(); i++) {
            if (steps.get(i) instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep) {
                return executeRecipeStep;
            }
        }
        for (int i = start - 1; i >= 0; i--) {
            if (steps.get(i) instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep) {
                return executeRecipeStep;
            }
        }
        return null;
    }

    private static List<MaterialSlot> workbenchTooltipRecipeInputSlots(ImmutableList<WorkbenchInputEntry> inputs) {
        List<MaterialSlot> slots = new ArrayList<>();
        for (WorkbenchInputEntry entry : inputs) {
            ItemStack stack = entry.stack();
            if (stack != null && !stack.isEmpty()) {
                int displayCount = Math.max(1, entry.count());
                slots.add(MaterialSlot.item(stack, displayCount));
            }
        }
        if (slots.isEmpty()) {
            slots.add(new MaterialSlot("-", 1, MaterialType.ITEM));
        }
        return slots;
    }

    private static List<MaterialSlot> workbenchTooltipRecipeOutputSlotsFromEntries(ImmutableList<WorkbenchOutputEntry> outputs) {
        List<MaterialSlot> slots = new ArrayList<>();
        for (WorkbenchOutputEntry entry : outputs) {
            ItemStack stack = entry.stack();
            if (stack != null && !stack.isEmpty()) {
                slots.add(MaterialSlot.item(stack, Math.max(1, entry.count())));
            }
        }
        if (slots.isEmpty()) {
            slots.add(new MaterialSlot("-", 1, MaterialType.ITEM));
        }
        return slots;
    }

    private static ItemStack stackFromInputContent(InputContent input) {
        if (input.isEnumerable()) {
            for (ContentVariant variant : input.enumerate()) {
                if (variant instanceof ContentStack<?, ?> stack) {
                    ItemStack itemStack = stackToItemStack(stack);
                    if (!itemStack.isEmpty()) {
                        return itemStack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack stackFromContentResource(ContentResource resource) {
        if (!(resource instanceof ContentStack<?, ?> stack)) {
            return ItemStack.EMPTY;
        }
        return stackToItemStack(stack);
    }

    private static ItemStack stackToItemStack(ContentStack<?, ?> stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (stack.isItem()) {
            ItemStack itemStack = stack.<Item, ItemStack>cast().value().copy();
            itemStack.setCount(Math.min(99, safeDisplayAmount(stack.amount())));
            return itemStack;
        }
        ContentStack<Item, ItemStack> cheats = stack.cheatsStack();
        if (cheats == null || cheats.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack itemStack = cheats.value().copy();
        if (!itemStack.isEmpty()) {
            itemStack.setCount(Math.min(99, safeDisplayAmount(stack.amount())));
        }
        return itemStack;
    }

    private static int safeDisplayAmount(long amount) {
        if (amount <= 0L) {
            return 1;
        }
        if (amount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) amount;
    }

    private @Nullable MaterialSlot workbenchTooltipRecipeMachineSlot(@Nullable InteractionStep.ExecuteRecipeStep previewStep) {
        if (previewStep == null || previewStep.recipe() == null) {
            return null;
        }
        Object rawRecipe = previewStep.recipe().recipe();
        if (rawRecipe instanceof RecipeHolder<?> holder && holder.value() instanceof CraftingRecipe) {
            return MaterialSlot.item(new ItemStack(Items.CRAFTING_TABLE), 1);
        }
        return null;
    }

    private String summarizeWorkbenchInputsCompact(ImmutableList<WorkbenchInputEntry> inputs, int limit) {
        if (inputs == null || inputs.isEmpty()) {
            return "-";
        }
        int show = Math.max(1, Math.min(limit, inputs.size()));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < show; i++) {
            WorkbenchInputEntry entry = inputs.get(i);
            if (i > 0) {
                out.append(" + ");
            }
            String name = entry.stack() == null || entry.stack().isEmpty()
                ? "-"
                : compactWorkbenchLabel(entry.stack().getHoverName().getString(), 2);
            out.append(Math.max(1, entry.count())).append(" ").append(name);
        }
        if (inputs.size() > show) {
            out.append(" +").append(inputs.size() - show);
        }
        return out.toString();
    }

    private String summarizeWorkbenchOutputsCompact(ImmutableList<WorkbenchOutputEntry> outputs, int limit) {
        if (outputs == null || outputs.isEmpty()) {
            return "-";
        }
        int show = Math.max(1, Math.min(limit, outputs.size()));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < show; i++) {
            WorkbenchOutputEntry entry = outputs.get(i);
            if (i > 0) {
                out.append(" + ");
            }
            String name = entry.stack() == null || entry.stack().isEmpty()
                ? "-"
                : compactWorkbenchLabel(entry.stack().getHoverName().getString(), 2);
            out.append(Math.max(1, entry.count())).append(" ").append(name);
        }
        if (outputs.size() > show) {
            out.append(" +").append(outputs.size() - show);
        }
        return out.toString();
    }

    private String summarizeWorkbenchInputs(ImmutableList<WorkbenchInputEntry> inputs) {
        int limit = Math.min(3, inputs.size());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            WorkbenchInputEntry entry = inputs.get(i);
            if (i > 0) {
                out.append(" | ");
            }
            String name = entry.stack() == null || entry.stack().isEmpty()
                ? "-"
                : entry.stack().getHoverName().getString();
            out.append(name).append(" x").append(Math.max(1, entry.count()));
        }
        if (inputs.size() > limit) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append("+").append(inputs.size() - limit).append(" more");
        }
        return out.length() == 0 ? "-" : out.toString();
    }

    private @Nullable ExecutionOverlayNode hoveredWorkbenchPathNode(int mouseX, int mouseY, int x, int y, int w) {
        if (viewModel == null) {
            return null;
        }
        Font font = Minecraft.getInstance().font;
        ImmutableList<ExecutionOverlayNode> trail = pathContextNodes();
        if (trail.isEmpty()) {
            trail = Lists.immutable.of(viewModel.root());
        }
        int clipX = x;
        int clipW = Math.max(20, w);
        int chipX = clipX - workbenchPathScrollX + 14;
        for (int i = 0; i < trail.size(); i++) {
            if (i > 0) {
                chipX += 9;
            }
            int chipW = font.width(compactWorkbenchLabel(trail.get(i).label().getString(), 2)) + 2;
            if (mouseX >= chipX && mouseX <= chipX + chipW && mouseY >= y && mouseY <= y + 14 && chipX + chipW >= clipX && chipX <= clipX + clipW) {
                return trail.get(i);
            }
            chipX += chipW;
        }
        return null;
    }

    private String compactWorkbenchLabel(String value, int words) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalized = value.replace("...", " ").replace('·', ' ').trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length == 0) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        int count = Math.max(1, words);
        for (int i = 0; i < parts.length && i < count; i++) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(parts[i]);
        }
        return out.toString();
    }

    private String summarizeWorkbenchOutputs(ImmutableList<WorkbenchOutputEntry> outputs) {
        int limit = Math.min(3, outputs.size());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            WorkbenchOutputEntry entry = outputs.get(i);
            if (i > 0) {
                out.append(" | ");
            }
            String name = entry.stack() == null || entry.stack().isEmpty()
                ? "-"
                : entry.stack().getHoverName().getString();
            out.append(name).append(" x").append(Math.max(1, entry.count()));
        }
        if (outputs.size() > limit) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append("+").append(outputs.size() - limit).append(" more");
        }
        return out.toString();
    }

    private void drawBody(SceneCanvas canvas, Font font, GraphLayoutResult layout, BodyLayout body) {
        int collapsedStart = layoutMode == LayoutMode.timeline ? timelineCollapsedStart(layout) : -1;
        var abs = absolutePos();
        canvas.withClip(abs.x() + body.graphX(), abs.y() + body.bodyY(), body.graphWidth(), body.bodyHeight(), () -> {
            drawGraphBackdrop(canvas, body);
            if (layoutMode == LayoutMode.timeline) {
                drawTimelineConnectors(canvas, layout, body.graphX(), body.bodyY(), collapsedStart);
            } else {
                for (GraphLayoutNode node : layout.flattened()) {
                    drawConnectors(canvas, node, body.graphX(), body.bodyY());
                }
            }
            for (int i = 0; i < layout.flattened().size(); i++) {
                if (collapsedStart >= 0 && i >= collapsedStart) {
                    continue;
                }
                GraphLayoutNode node = layout.flattened().get(i);
                int x = body.graphX() + node.x() - scrollX;
                int y = body.bodyY() + node.y() - scrollY;
                if (x + node.width() < body.graphX()
                    || x > body.graphX() + body.graphWidth()
                    || y + node.height() < body.bodyY()
                    || y > body.bodyY() + body.bodyHeight()) {
                    continue;
                }
                drawNode(canvas, font, node.node(), x, y, node.width(), node.height());
            }
            if (collapsedStart >= 0) {
                drawTimelineCollapsedGroup(canvas, font, layout, body, collapsedStart);
            }
        });
        // timeline mode focuses on readable primary viewport; minimap removed by design
    }

    private void drawTimelineConnectors(SceneCanvas canvas, GraphLayoutResult layout, int graphX, int graphY, int collapsedStart) {
        if (layout.flattened().size() <= 1) {
            return;
        }
        int limitExclusive = layout.flattened().size() - 1;
        if (collapsedStart >= 0) {
            limitExclusive = Math.min(limitExclusive, collapsedStart);
        }
        for (int i = 0; i < limitExclusive; i++) {
            GraphLayoutNode current = layout.flattened().get(i);
            GraphLayoutNode next = layout.flattened().get(i + 1);
            int startX = graphX + current.x() + 3 - scrollX;
            int startY = graphY + current.centerY() - scrollY;
            int endX = graphX + next.x() + 3 - scrollX;
            int endY = graphY + next.centerY() - scrollY;
            int color = next.node().activeBranch() ? activeConnectorColor : connectorColor;
            int turnY = shortTurnY(startY, endY);
            drawVertical(canvas, startX, startY, turnY, color);
            drawHorizontal(canvas, startX, turnY, endX, color);
            drawVertical(canvas, endX, turnY, endY, color);
        }
    }

    private int timelineCollapsedStart(GraphLayoutResult layout) {
        if (layoutMode != LayoutMode.timeline || layout.flattened().size() < 6) {
            return -1;
        }
        int start = -1;
        for (int i = 0; i < layout.flattened().size(); i++) {
            if (layout.flattened().get(i).node().depth() >= 2) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            start = Math.max(2, layout.flattened().size() - 4);
        }
        return layout.flattened().size() - start >= 4 ? start : -1;
    }

    private @Nullable GroupBounds timelineCollapsedGroupBounds(GraphLayoutResult layout, BodyLayout body, int collapsedStart) {
        if (collapsedStart < 0 || collapsedStart >= layout.flattened().size()) {
            return null;
        }
        GraphLayoutNode first = layout.flattened().get(collapsedStart);
        int count = layout.flattened().size() - collapsedStart;
        int rows = Math.min(4, count);
        int x = body.graphX() + first.x() - scrollX;
        int y = body.bodyY() + first.y() - scrollY;
        int w = Math.min(Math.max(102, first.width()), Math.max(102, body.graphWidth() - 8));
        int h = 17 + rows * 11 + (count > rows ? 8 : 0);
        return new GroupBounds(x, y, w, h);
    }

    private void drawTimelineCollapsedGroup(SceneCanvas canvas, Font font, GraphLayoutResult layout, BodyLayout body, int collapsedStart) {
        GroupBounds bounds = timelineCollapsedGroupBounds(layout, body, collapsedStart);
        if (bounds == null) {
            return;
        }
        if (bounds.x() > body.graphX() + body.graphWidth()
            || bounds.y() > body.bodyY() + body.bodyHeight()
            || bounds.x() + bounds.w() < body.graphX()
            || bounds.y() + bounds.h() < body.bodyY()) {
            return;
        }

        int count = layout.flattened().size() - collapsedStart;
        canvas.texture(CirrusTextures.Background.Border.light, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        canvas.texture(CirrusTextures.Background.dark, bounds.x() + 2, bounds.y() + 2, Math.max(0, bounds.w() - 4), Math.max(0, bounds.h() - 4));
        canvas.texture(CirrusTextures.Background.Sign.bar, bounds.x() + 4, bounds.y() + 4, Math.max(8, bounds.w() - 8), 3);
        canvas.drawString("Queued x" + count, bounds.x() + 6, bounds.y() + 7, 0xFFD8E2EC, false);

        int lineY = bounds.y() + 16;
        int lineW = Math.max(26, bounds.w() - 12);
        int rows = Math.min(4, count);
        for (int i = 0; i < rows; i++) {
            ExecutionOverlayNode node = layout.flattened().get(collapsedStart + i).node();
            String line = compactStatusTag(node.status()) + " " + node.label().getString();
            canvas.drawString(trimLabel(font, line, lineW), bounds.x() + 6, lineY, 0xFFCFD7E1, false);
            lineY += 11;
        }
        if (count > rows) {
            canvas.drawString("+" + (count - rows) + " more", bounds.x() + 6, lineY, 0xFF9DA8B4, false);
        }
    }

    private static String compactStatusTag(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Running ignored -> "[RUN]";
            case ExecutionFlowStatus.Paused ignored -> "[PAUSE]";
            case ExecutionFlowStatus.Completed ignored -> "[DONE]";
            case ExecutionFlowStatus.Failed ignored -> "[ERR]";
            case ExecutionFlowStatus.Pending ignored -> "[WAIT]";
        };
    }

    private void drawConnectors(SceneCanvas canvas, GraphLayoutNode parent, int graphX, int graphY) {
        if (parent.children().isEmpty()) {
            return;
        }
        drawTopToBottomConnectorGroup(canvas, parent, graphX, graphY);
    }

    private void drawTopToBottomConnectorGroup(SceneCanvas canvas, GraphLayoutNode parent, int graphX, int graphY) {
        int startX = graphX + parent.centerX() - scrollX;
        int startY = graphY + parent.bottom() - scrollY;
        if (parent.children().size() == 1) {
            GraphLayoutNode child = parent.children().getFirst();
            int endX = graphX + child.centerX() - scrollX;
            int endY = graphY + child.y() - scrollY;
            int midY = shortTurnY(startY, endY);
            int color = child.node().activeBranch() ? activeConnectorColor : connectorColor;
            drawVertical(canvas, startX, startY, midY, color);
            drawHorizontal(canvas, startX, midY, endX, color);
            drawVertical(canvas, endX, midY, endY, color);
            return;
        }

        int firstX = Integer.MAX_VALUE;
        int lastX = Integer.MIN_VALUE;
        int nearestChildY = Integer.MAX_VALUE;
        for (GraphLayoutNode child : parent.children()) {
            int childX = graphX + child.centerX() - scrollX;
            firstX = Math.min(firstX, childX);
            lastX = Math.max(lastX, childX);
            nearestChildY = Math.min(nearestChildY, graphY + child.y() - scrollY);
        }
        int trunkY = shortTurnY(startY, nearestChildY);
        drawVertical(canvas, startX, startY, trunkY, connectorColor);
        drawHorizontal(canvas, firstX, trunkY, lastX, connectorColor);

        for (GraphLayoutNode child : parent.children()) {
            int childX = graphX + child.centerX() - scrollX;
            int childY = graphY + child.y() - scrollY;
            int color = child.node().activeBranch() ? activeConnectorColor : connectorColor;
            drawVertical(canvas, childX, trunkY, childY, color);
        }
    }

    private int shortTurnY(int startY, int endY) {
        int delta = endY - startY;
        if (delta == 0) {
            return startY;
        }
        int turn = Math.min(8, Math.max(2, Math.abs(delta) / 3));
        return delta > 0 ? startY + turn : startY - turn;
    }

    private void drawHorizontal(SceneCanvas canvas, int firstX, int y, int secondX, int color) {
        int x = Math.min(firstX, secondX);
        int width = Math.max(connectorThickness, Math.abs(secondX - firstX));
        canvas.fill(x, y, width, connectorThickness, color);
    }

    private void drawVertical(SceneCanvas canvas, int x, int firstY, int secondY, int color) {
        int y = Math.min(firstY, secondY);
        int height = Math.max(connectorThickness, Math.abs(secondY - firstY));
        canvas.fill(x, y, connectorThickness, height, color);
    }

    private void drawNode(SceneCanvas canvas, Font font, ExecutionOverlayNode node, int cardX, int y, int cardW, int cardH) {
        var model = ExecutionOverlayComponentRenderer.cardModelOf(node, cardH);
        ExecutionOverlayComponentRenderer.drawNodeCard(canvas, font, model, cardX, y, cardW, cardH);
    }

    private void drawCompactMonitorStrip(SceneCanvas canvas, Font font, GraphLayoutResult layout, BodyLayout body) {
        var focus = focusContext(layout);
        if (focus == null || body.graphWidth() < 80) {
            return;
        }
        int x = body.graphX() + 3;
        int y = body.bodyY() + 3;
        int w = Math.max(0, body.graphWidth() - 6);
        int h = Math.min(31, Math.max(21, body.bodyHeight() / 4));
        canvas.texture(CirrusTextures.Background.Outlined.flat, x, y, w, h);
        canvas.texture(CirrusTextures.Background.flat, x + 1, y + 1, Math.max(0, w - 2), Math.max(0, h - 2));

        String statusText = telemetrySnapshot == null ? viewModel.statusText() : telemetrySnapshot.status();
        double progress = telemetrySnapshot == null
            ? (viewModel.totalStepCount() <= 0 ? 0.0d : viewModel.completedStepCount() / (double) viewModel.totalStepCount())
            : telemetrySnapshot.progress();
        int done = telemetrySnapshot == null ? viewModel.completedStepCount() : telemetrySnapshot.completedSteps();
        int total = telemetrySnapshot == null ? viewModel.totalStepCount() : telemetrySnapshot.totalSteps();
        int score = gamifiedScore(progress, telemetrySnapshot);
        String rank = gamifiedRank(score);
        String step = resolveCurrentStepLabel(focus.current());
        String top = "[" + rank + "] " + statusText.toUpperCase(java.util.Locale.ROOT) + " " + done + "/" + total;
        canvas.drawString(trimLabel(font, top, Math.max(18, w - 6)), x + 3, y + 2, rankColor(rank), false);

        int barY = y + 10;
        drawProgressTrack(canvas, x + 3, barY, Math.max(10, w - 6), progress);

        String branch = compactBranchText(focus);
        String traceTail = compactTraceTail(focus);
        String detail = branch + " · " + step;
        if (!traceTail.isBlank()) {
            detail = detail + " · " + traceTail;
        }
        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
            detail = "ERR: " + telemetrySnapshot.errorSummary();
        }
        int detailColor = telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null
            ? ExecutionOverlayComponentRenderer.textWarning
            : statusColor(statusText);
        canvas.drawString(trimLabel(font, detail, Math.max(18, w - 6)), x + 3, y + h - 9, detailColor, false);
    }

    private void drawGraphBackdrop(SceneCanvas canvas, BodyLayout body) {
        canvas.texture(CirrusTextures.Background.flat, body.graphX(), body.bodyY(), body.graphWidth(), body.bodyHeight());
        canvas.texture(
            CirrusTextures.Background.inset,
            body.graphX() + 1,
            body.bodyY() + 1,
            Math.max(0, body.graphWidth() - 2),
            Math.max(0, body.bodyHeight() - 2)
        );
        int stripY = body.bodyY() + 2;
        canvas.texture(
            CirrusTextures.Background.Sign.bar,
            body.graphX() + 3,
            stripY,
            Math.max(0, body.graphWidth() - 6),
            3
        );
    }

    private void drawGraphMinimap(SceneCanvas canvas, GraphLayoutResult layout, BodyLayout body) {
        if (layout.contentWidth() <= 0 || layout.contentHeight() <= 0 || body.graphWidth() < 90 || body.bodyHeight() < 70) {
            return;
        }
        if (layoutMode == LayoutMode.timeline) {
            drawTimelineOverview(canvas, layout, body);
            return;
        }
        int miniW = Math.min(136, Math.max(104, body.graphWidth() / 3));
        int miniH = Math.min(92, Math.max(68, body.bodyHeight() / 3));
        int miniX = body.graphX() + body.graphWidth() - miniW - 4;
        int miniY = body.bodyY() + body.bodyHeight() - miniH - 4;
        int contentX = miniX + 3;
        int contentY = miniY + 11;
        int contentW = miniW - 6;
        int contentH = miniH - 14;

        canvas.texture(CirrusTextures.Background.Outlined.flat, miniX, miniY, miniW, miniH);
        canvas.texture(CirrusTextures.Background.dark, contentX, miniY + 1, contentW, 8);
        canvas.drawString("MAP", contentX + 3, miniY + 2, 0xFFB7C9DA, false);
        canvas.texture(CirrusTextures.Background.flat, contentX, contentY, contentW, contentH);

        double scale = Math.min(contentW / (double) Math.max(1, layout.contentWidth()), contentH / (double) Math.max(1, layout.contentHeight()));
        int drawW = Math.max(1, (int) Math.round(layout.contentWidth() * scale));
        int drawH = Math.max(1, (int) Math.round(layout.contentHeight() * scale));
        int drawX = contentX + (contentW - drawW) / 2;
        int drawY = contentY + (contentH - drawH) / 2;

        for (GraphLayoutNode node : layout.flattened()) {
            int px = drawX + (int) Math.round(node.centerX() * scale);
            int py = drawY + (int) Math.round(node.centerY() * scale);
            int color = node.node().activeBranch() ? 0xFF5C9AF2 : 0x889EA4AD;
            int dot = node.node().activeBranch() ? 3 : 2;
            canvas.fill(px, py, dot, dot, color);
            if (layout.focusNode() != null && Objects.equals(layout.focusNode().node().nodeId(), node.node().nodeId())) {
                canvas.fill(px - 1, py - 1, dot + 2, dot + 2, 0x88FFFFFF);
            }
        }

        int viewW = Math.max(3, (int) Math.round(body.graphWidth() * scale));
        int viewH = Math.max(3, (int) Math.round(body.bodyHeight() * scale));
        int minX = minScrollX(body.graphWidth());
        int maxX = maxScrollX(layout, body.graphWidth());
        int minY = minScrollY(body.bodyHeight());
        int maxY = maxScrollY(layout, body.bodyHeight());
        int rangeX = Math.max(1, maxX - minX);
        int rangeY = Math.max(1, maxY - minY);
        int viewX = drawX + (int) Math.round((Math.max(minX, Math.min(maxX, scrollX)) - minX) / (double) rangeX * Math.max(1, drawW - viewW));
        int viewY = drawY + (int) Math.round((Math.max(minY, Math.min(maxY, scrollY)) - minY) / (double) rangeY * Math.max(1, drawH - viewH));
        canvas.fill(viewX, viewY, viewW, 1, 0x99D8E7FF);
        canvas.fill(viewX, viewY + viewH - 1, viewW, 1, 0x99D8E7FF);
        canvas.fill(viewX, viewY, 1, viewH, 0x99D8E7FF);
        canvas.fill(viewX + viewW - 1, viewY, 1, viewH, 0x99D8E7FF);
    }

    private void drawTimelineOverview(SceneCanvas canvas, GraphLayoutResult layout, BodyLayout body) {
        int miniW = Math.min(124, Math.max(96, body.graphWidth() / 2));
        int miniH = Math.min(138, Math.max(78, body.bodyHeight() / 2));
        int miniX = body.graphX() + body.graphWidth() - miniW - 4;
        int miniY = body.bodyY() + body.bodyHeight() - miniH - 4;
        int contentX = miniX + 3;
        int contentY = miniY + 11;
        int contentW = miniW - 6;
        int contentH = miniH - 14;

        canvas.texture(CirrusTextures.Background.Outlined.flat, miniX, miniY, miniW, miniH);
        canvas.texture(CirrusTextures.Background.dark, contentX, miniY + 1, contentW, 8);
        canvas.drawString("TRACE", contentX + 3, miniY + 2, 0xFFB7C9DA, false);
        canvas.texture(CirrusTextures.Background.flat, contentX, contentY, contentW, contentH);

        int total = layout.flattened().size();
        if (total <= 0) {
            return;
        }
        int availableH = Math.max(6, contentH - 4);
        int rowStep = Math.max(1, availableH / total);
        for (int i = 0; i < total; i++) {
            GraphLayoutNode node = layout.flattened().get(i);
            int ry = contentY + 2 + Math.min(availableH - 1, i * rowStep);
            int depthIndent = Math.max(0, node.node().depth() - 1) * 3;
            int rx = contentX + 2 + Math.min(contentW - 8, depthIndent);
            int rw = Math.max(4, contentW - 6 - depthIndent);
            int color = ExecutionOverlayComponentRenderer.statusColor(node.node().status()) & 0x88FFFFFF;
            if (node.node().activeBranch()) {
                color = (ExecutionOverlayComponentRenderer.statusColor(node.node().status()) & 0x00FFFFFF) | 0xCC000000;
            }
            canvas.fill(rx, ry, rw, 2, color);
        }

        int minY = minScrollY(body.bodyHeight());
        int maxY = maxScrollY(layout, body.bodyHeight());
        int rangeY = Math.max(1, maxY - minY);
        int viewH = Math.max(5, (int) Math.round((body.bodyHeight() / (double) Math.max(1, layout.contentHeight())) * contentH));
        int viewY = contentY + (int) Math.round((Math.max(minY, Math.min(maxY, scrollY)) - minY) / (double) rangeY * Math.max(1, contentH - viewH));
        int viewX = contentX + 1;
        int viewW = Math.max(4, contentW - 2);
        canvas.fill(viewX, viewY, viewW, 1, 0xAADDFFFF);
        canvas.fill(viewX, viewY + viewH - 1, viewW, 1, 0xAADDFFFF);
        canvas.fill(viewX, viewY, 1, viewH, 0xAADDFFFF);
        canvas.fill(viewX + viewW - 1, viewY, 1, viewH, 0xAADDFFFF);
    }

    private void drawExecutionInfoPanel(SceneCanvas canvas, Font font, GraphLayoutResult layout, BodyLayout body) {
        var focus = focusContext(layout);
        if (focus == null || body.infoWidth() <= 0) {
            return;
        }
        int panelW = body.infoWidth();
        int panelX = body.infoX();
        int panelY = body.bodyY();
        int panelH = body.bodyHeight();

        canvas.texture(CirrusTextures.Background.Outlined.flat, panelX, panelY, panelW, panelH);
        canvas.texture(CirrusTextures.Background.flat, panelX + 1, panelY + 1, panelW - 2, panelH - 2);

        int rowX = panelX + 6;
        int rowW = panelW - 12;
        int rowY = panelY + 5;
        int rowBottom = panelY + panelH - 7;

        canvas.drawString("Execution Monitor", rowX, rowY, ExecutionOverlayComponentRenderer.textPrimary, false);
        rowY += 11;

        double progress = telemetrySnapshot == null
            ? (viewModel.totalStepCount() <= 0 ? 0.0d : viewModel.completedStepCount() / (double) viewModel.totalStepCount())
            : telemetrySnapshot.progress();
        int done = telemetrySnapshot == null ? viewModel.completedStepCount() : telemetrySnapshot.completedSteps();
        int total = telemetrySnapshot == null ? viewModel.totalStepCount() : telemetrySnapshot.totalSteps();
        int score = gamifiedScore(progress, telemetrySnapshot);
        String rank = gamifiedRank(score);
        drawProgressTrack(canvas, rowX, rowY, rowW, progress);
        rowY += 8;
        canvas.drawString(done + "/" + total + " steps", rowX, rowY, ExecutionOverlayComponentRenderer.textDim, false);
        rowY += 10;

        String statusText = telemetrySnapshot == null ? viewModel.statusText() : telemetrySnapshot.status();
        rowY = drawTagLine(canvas, font, rowX, rowY, rowW, "Status", statusText.toUpperCase(java.util.Locale.ROOT), statusColor(statusText));
        rowY = drawTagLine(canvas, font, rowX, rowY, rowW, "Rank", rank, rankColor(rank));
        rowY = drawTagLine(canvas, font, rowX, rowY, rowW, "Score", Integer.toString(score), ExecutionOverlayComponentRenderer.textSecondary);
        if (telemetrySnapshot != null) {
            rowY = drawTagLine(
                canvas,
                font,
                rowX,
                rowY,
                rowW,
                "Rate",
                String.format(java.util.Locale.ROOT, "%.1f /s", telemetrySnapshot.ratePerSecond()),
                ExecutionOverlayComponentRenderer.textSecondary
            );
            rowY = drawTagLine(
                canvas,
                font,
                rowX,
                rowY,
                rowW,
                "Auto",
                telemetrySnapshot.autoAdvanceEnabled() ? "ON" : "OFF",
                telemetrySnapshot.autoAdvanceEnabled() ? 0xFF6ECF8B : ExecutionOverlayComponentRenderer.textDim
            );
        }

        String currentName = focus.current().label().getString();
        String currentStep = resolveCurrentStepLabel(focus.current());
        String branch = compactBranchText(focus);
        String next = focus.next() == null ? "Next: -"
            : "Next: " + trimLabel(font, focus.next().label().getString(), Math.max(18, rowW - 28));
        String parent = focus.parent() == null ? "Parent: -" : "Parent: " + trimLabel(font, focus.parent().label().getString(), Math.max(18, rowW - 34));

        if (telemetrySnapshot != null) {
            if (telemetrySnapshot.current() != null) {
                currentName = telemetrySnapshot.current().label();
                if (telemetrySnapshot.current().detail() != null) {
                    currentStep = telemetrySnapshot.current().detail();
                }
            }
            if (isAbstractStatusWord(currentStep)) {
                currentStep = resolveCurrentStepLabel(focus.current());
            }
            next = telemetrySnapshot.next() == null
                ? "Next: -"
                : "Next: " + trimLabel(font, telemetrySnapshot.next().label(), Math.max(18, rowW - 28));
            parent = telemetrySnapshot.parent() == null
                ? "Parent: -"
                : "Parent: " + trimLabel(font, telemetrySnapshot.parent().label(), Math.max(18, rowW - 34));
        }

        if (rowY < rowBottom) {
            canvas.fill(rowX, rowY, rowW, 1, 0x335F6570);
            rowY += 3;
            canvas.drawString("Context", rowX, rowY, ExecutionOverlayComponentRenderer.textDim, false);
            rowY += 9;
            rowY = drawWrappedText(canvas, font, "Now: " + currentName, rowX, rowY, rowW, 2, ExecutionOverlayComponentRenderer.textPrimary);
            rowY = drawWrappedText(canvas, font, "Step: " + currentStep, rowX, rowY, rowW, 2, ExecutionOverlayComponentRenderer.textSecondary);
            rowY = drawWrappedText(canvas, font, branch, rowX, rowY, rowW, 1, ExecutionOverlayComponentRenderer.textSecondary);
            rowY = drawWrappedText(canvas, font, next, rowX, rowY, rowW, 2, ExecutionOverlayComponentRenderer.textDim);
            rowY = drawWrappedText(canvas, font, parent, rowX, rowY, rowW, 2, ExecutionOverlayComponentRenderer.textDim);
            rowY += 2;
        }

        if (rowY < rowBottom) {
            canvas.fill(rowX, rowY, rowW, 1, 0x335F6570);
            rowY += 3;
            canvas.drawString("Steps", rowX, rowY, ExecutionOverlayComponentRenderer.textDim, false);
            rowY += 9;
            int stepLineLimit = Math.max(0, (rowBottom - rowY - 10) / 8);
            int rendered = 0;
            for (var segment : focus.current().laneSegments()) {
                if (rendered >= stepLineLimit || rowY > rowBottom - 8) {
                    break;
                }
                String text = segmentStatusShort(segment.status()) + " " + segment.label().getString();
                int color = segment.current() ? ExecutionOverlayComponentRenderer.textPrimary : ExecutionOverlayComponentRenderer.statusColor(segment.status());
                canvas.drawString(trimLabel(font, text, rowW), rowX, rowY, color, false);
                rowY += 8;
                rendered++;
            }
            if (rendered == 0) {
                canvas.drawString("no lane data", rowX, rowY, ExecutionOverlayComponentRenderer.textSecondary, false);
                rowY += 8;
            }
            rowY += 2;
        }

        if (rowY < rowBottom) {
            canvas.fill(rowX, rowY, rowW, 1, 0x335F6570);
            rowY += 3;
            canvas.drawString("Trace", rowX, rowY, ExecutionOverlayComponentRenderer.textDim, false);
            rowY += 9;
            var traceLines = telemetrySnapshot == null
                ? pathContextNodes().collect(node -> node.label().getString())
                : telemetrySnapshot.path().collect(ExecutionTelemetrySnapshot.NodeContext::label);
            int traceLimit = Math.max(0, (rowBottom - rowY - 9) / 8);
            int start = Math.max(0, traceLines.size() - traceLimit);
            for (int i = start; i < traceLines.size() && rowY <= rowBottom - 8; i++) {
                String marker = i == traceLines.size() - 1 ? "> " : "· ";
                int color = i == traceLines.size() - 1 ? ExecutionOverlayComponentRenderer.textPrimary : ExecutionOverlayComponentRenderer.textSecondary;
                canvas.drawString(trimLabel(font, marker + traceLines.get(i), rowW), rowX, rowY, color, false);
                rowY += 8;
            }
        }

        if (rowY < rowBottom) {
            canvas.fill(rowX, rowY, rowW, 1, 0x335F6570);
            rowY += 3;
            if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
                canvas.drawString("Error", rowX, rowY, ExecutionOverlayComponentRenderer.textWarning, false);
                rowY += 9;
                rowY = drawWrappedText(canvas, font, telemetrySnapshot.errorSummary(), rowX, rowY, rowW, 2, ExecutionOverlayComponentRenderer.textWarning);
                if (telemetrySnapshot.errorDetail() != null && rowY <= rowBottom - 8) {
                    rowY = drawWrappedText(canvas, font, telemetrySnapshot.errorDetail(), rowX, rowY, rowW, 2, ExecutionOverlayComponentRenderer.textSecondary);
                }
                return;
            }

            canvas.drawString("Warnings", rowX, rowY, ExecutionOverlayComponentRenderer.textDim, false);
            rowY += 9;
            var warnings = telemetrySnapshot == null ? Lists.immutable.<String>empty() : telemetrySnapshot.warnings();
            if (warnings.isEmpty()) {
                canvas.drawString("none", rowX, rowY, ExecutionOverlayComponentRenderer.textSecondary, false);
            } else {
                int warnLimit = Math.max(0, (rowBottom - rowY) / 8);
                for (int i = 0; i < Math.min(warnLimit, warnings.size()); i++) {
                    rowY = drawWrappedText(canvas, font, "! " + warnings.get(i), rowX, rowY, rowW, 2, ExecutionOverlayComponentRenderer.textWarning);
                }
            }
        }
    }

    private void drawProgressTrack(SceneCanvas canvas, int x, int y, int w, double progress) {
        int clampedW = Math.max(0, w);
        if (clampedW <= 0) {
            return;
        }
        ExecutionOverlayComponentRenderer.drawSegmentedProgressBar(
            canvas,
            x,
            y,
            clampedW,
            7,
            (float) Math.max(0.0d, Math.min(1.0d, progress)),
            7,
            0xFF5A98EA
        );
    }

    private int drawTagLine(SceneCanvas canvas, Font font, int x, int y, int w, String tag, String value, int valueColor) {
        String tagText = "[" + tag + "]";
        int tagW = font.width(tagText);
        canvas.drawString(tagText, x, y, ExecutionOverlayComponentRenderer.textDim, false);
        canvas.drawString(trimLabel(font, value, Math.max(16, w - tagW - 4)), x + tagW + 4, y, valueColor, false);
        return y + 9;
    }

    private int drawWrappedText(SceneCanvas canvas, Font font, String text, int x, int y, int width, int maxLines, int color) {
        if (text == null || text.isBlank() || maxLines <= 0 || width <= 4) {
            return y;
        }
        String remaining = text.trim();
        int lines = 0;
        while (!remaining.isEmpty() && lines < maxLines) {
            String line = font.plainSubstrByWidth(remaining, width);
            if (line.isEmpty()) {
                break;
            }
            remaining = remaining.substring(line.length()).trim();
            if (lines == maxLines - 1 && !remaining.isEmpty()) {
                line = trimLabel(font, line, width);
            }
            canvas.drawString(line, x, y, color, false);
            y += 8;
            lines++;
        }
        return y;
    }

    private List<String> wrapScrollableLines(Font font, String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 2 || maxLines <= 0) {
            return lines;
        }
        String[] paragraphs = text.replace('\r', ' ').trim().split("\\n");
        for (String paragraph : paragraphs) {
            String remaining = paragraph == null ? "" : paragraph.trim();
            while (!remaining.isEmpty() && lines.size() < maxLines) {
                String slice = font.plainSubstrByWidth(remaining, maxWidth);
                if (slice.isEmpty()) {
                    break;
                }
                int take = Math.min(remaining.length(), slice.length());
                int wordBreak = Math.max(slice.lastIndexOf(' '), slice.lastIndexOf('-'));
                if (take < remaining.length() && wordBreak > 0) {
                    take = wordBreak;
                }
                if (take <= 0) {
                    take = Math.min(remaining.length(), 1);
                }
                String line = remaining.substring(0, take).trim();
                if (line.isEmpty()) {
                    take = Math.min(remaining.length(), Math.max(1, slice.length()));
                    line = remaining.substring(0, take);
                }
                lines.add(trimLabel(font, line, maxWidth));
                remaining = remaining.substring(Math.min(remaining.length(), take)).trim();
            }
            if (lines.size() >= maxLines) {
                break;
            }
        }
        return lines;
    }

    private void drawWrappedScrollableLines(
        SceneCanvas canvas,
        Font font,
        String text,
        int x,
        int y,
        int w,
        int h,
        int color,
        dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState scrollState,
        int maxWrappedLines
    ) {
        if (text == null || text.isBlank() || w <= 4 || h <= 0 || maxWrappedLines <= 0) {
            configureVerticalScrollRange(scrollState, 0);
            return;
        }
        List<String> lines = wrapScrollableLines(font, text, w, maxWrappedLines);
        if (lines.isEmpty()) {
            configureVerticalScrollRange(scrollState, 0);
            return;
        }
        int lineHeight = 8;
        int visibleLines = Math.max(1, h / lineHeight);
        int maxStart = Math.max(0, lines.size() - visibleLines);
        configureVerticalScrollRange(scrollState, maxStart);
        float baseStart = Math.max(0.0f, Math.min(maxStart, scrollState.scrollY()));
        int lineStart = (int) Math.floor(baseStart);
        float lineFraction = baseStart - lineStart;
        int scrollbarWidth = maxStart > 0 ? 3 : 0;
        int textWidth = Math.max(4, w - (scrollbarWidth > 0 ? scrollbarWidth + 1 : 0));
        var abs = absolutePos();
        canvas.withClip(abs.x() + x, abs.y() + y, Math.max(1, textWidth), Math.max(1, h), () -> {
            for (int i = 0; i < visibleLines + 1; i++) {
                int idx = lineStart + i;
                if (idx >= lines.size()) {
                    break;
                }
                int lineY = y + (int) Math.round(i * lineHeight - lineFraction * lineHeight);
                canvas.drawString(trimLabel(font, lines.get(idx), textWidth), x, lineY, color, false);
            }
        });
        if (maxStart > 0) {
            int railX = x + w - scrollbarWidth;
            canvas.fill(railX, y, scrollbarWidth, h, 0x2C000000);
            int thumbH = Math.max(7, (int) Math.round((visibleLines / (double) lines.size()) * h));
            thumbH = Math.min(h, thumbH);
            int thumbY = y + (maxStart <= 0
                ? 0
                : (int) Math.round((h - thumbH) * (baseStart / (double) maxStart)));
            canvas.fill(railX, thumbY, scrollbarWidth, thumbH, 0xFF8FA8C2);
        }
    }

    private int gamifiedScore(double progress, @Nullable ExecutionTelemetrySnapshot snapshot) {
        int clampedProgress = Math.max(0, Math.min(1000, (int) Math.round(progress * 1000.0d)));
        int completed = snapshot == null ? viewModel.completedStepCount() : snapshot.completedSteps();
        int warnings = snapshot == null ? 0 : snapshot.warnings().size();
        int rateBonus = snapshot == null ? 0 : Math.min(70, (int) Math.round(snapshot.ratePerSecond() * 8.0d));
        int autoBonus = snapshot != null && snapshot.autoAdvanceEnabled() ? 24 : 0;
        int score = clampedProgress + completed * 7 + rateBonus + autoBonus - warnings * 12;
        if (snapshot != null && snapshot.errorSummary() != null) {
            score -= 180;
        }
        return Math.max(0, Math.min(9999, score));
    }

    private static String gamifiedRank(int score) {
        if (score >= 920) {
            return "S";
        }
        if (score >= 760) {
            return "A";
        }
        if (score >= 600) {
            return "B";
        }
        if (score >= 420) {
            return "C";
        }
        return "D";
    }

    private static int rankColor(String rank) {
        return switch (rank) {
            case "S" -> 0xFFF1C85D;
            case "A" -> 0xFF6ECF8B;
            case "B" -> 0xFF5C9AF2;
            case "C" -> 0xFFE3B56B;
            default -> 0xFFC98A8A;
        };
    }

    private String compactBranchText(FocusContext focus) {
        if (focus.parent() == null) {
            return "Branch root";
        }
        int total = focus.parent().children().size();
        int idx = focus.parent().children().indexOf(focus.current()) + 1;
        if (idx <= 0 || total <= 0) {
            return "Branch -";
        }
        return "Branch " + idx + "/" + total;
    }

    private String compactTraceTail(FocusContext focus) {
        if (telemetrySnapshot != null && telemetrySnapshot.path().notEmpty()) {
            var path = telemetrySnapshot.path();
            String tail = path.getLast().label();
            if (path.size() > 1) {
                return path.get(path.size() - 2).label() + " > " + tail;
            }
            return tail;
        }
        var nodes = pathContextNodes();
        if (nodes.isEmpty()) {
            return "";
        }
        String tail = nodes.getLast().label().getString();
        if (nodes.size() > 1) {
            return nodes.get(nodes.size() - 2).label().getString() + " > " + tail;
        }
        return tail;
    }

    private String compactTraceTailFromPath() {
        if (telemetrySnapshot != null && telemetrySnapshot.path().notEmpty()) {
            var path = telemetrySnapshot.path();
            if (path.size() == 1) {
                return path.getFirst().label();
            }
            return path.get(path.size() - 2).label() + " > " + path.getLast().label();
        }
        ImmutableList<ExecutionOverlayNode> nodes = pathContextNodes();
        if (nodes.isEmpty()) {
            return "";
        }
        if (nodes.size() == 1) {
            return nodes.getFirst().label().getString();
        }
        return nodes.get(nodes.size() - 2).label().getString() + " > " + nodes.getLast().label().getString();
    }

    private String compactBranchLabel() {
        ImmutableList<ExecutionOverlayNode> path = focusPathNodes();
        if (path.size() < 2) {
            return "Branch root";
        }
        ExecutionOverlayNode current = path.getLast();
        ExecutionOverlayNode parent = path.get(path.size() - 2);
        int idx = parent.children().indexOf(current) + 1;
        int total = parent.children().size();
        if (idx <= 0 || total <= 0) {
            return "Branch -";
        }
        return "Branch " + idx + "/" + total;
    }

    private void updateHoverTooltip(int mouseX, int mouseY, GraphLayoutResult layout, BodyLayout body) {
        if (mouseX < 0 || mouseY < 0 || mouseX > width() || mouseY > height()) {
            scene().setHoverTooltip(Tooltip.create());
            return;
        }
        GraphLayoutNode hovered = hoveredGraphNode(mouseX, mouseY, layout, body);
        if (hovered != null) {
            scene().setHoverTooltip(buildNodeTooltip(hovered.node()));
            return;
        }
        if (insideGraphViewport(mouseX, mouseY, body)) {
            Tooltip tip = Tooltip.create();
            tip.add(Component.literal("LMB/Middle drag pan"));
            tip.add(Component.literal("Wheel: vertical/horizontal scroll"));
            tip.add(Component.literal("Ctrl+Wheel: zoom"));
            scene().setHoverTooltip(tip);
            return;
        }
        scene().setHoverTooltip(Tooltip.create());
    }

    private @Nullable GraphLayoutNode hoveredGraphNode(int mouseX, int mouseY, GraphLayoutResult layout, BodyLayout body) {
        if (!insideGraphViewport(mouseX, mouseY, body)) {
            return null;
        }
        int collapsedStart = layoutMode == LayoutMode.timeline ? timelineCollapsedStart(layout) : -1;
        if (collapsedStart >= 0) {
            GroupBounds bounds = timelineCollapsedGroupBounds(layout, body, collapsedStart);
            if (bounds != null && bounds.contains(mouseX, mouseY)) {
                return layout.flattened().get(collapsedStart);
            }
        }
        for (int i = 0; i < layout.flattened().size(); i++) {
            if (collapsedStart >= 0 && i >= collapsedStart) {
                continue;
            }
            GraphLayoutNode node = layout.flattened().get(i);
            int x = body.graphX() + node.x() - scrollX;
            int y = body.bodyY() + node.y() - scrollY;
            if (mouseX >= x && mouseX <= x + node.width() && mouseY >= y && mouseY <= y + node.height()) {
                return node;
            }
        }
        return null;
    }

    private Tooltip buildNodeTooltip(ExecutionOverlayNode node) {
        Tooltip tip = Tooltip.create();
        int statusColor = ExecutionOverlayComponentRenderer.statusColor(node.status());
        float ratio = laneRatio(node);
        String progress = segmentProgressOf(node);
        var builder = FlowChartTooltipComponent.builder()
            .section(node.label().getString(), statusColor)
            .iconText(node.iconStack(), 0xFF2A2A2A, "Status: " + statusTextOf(node.status()), statusColor, progress, 0xFFEAEAEA)
            .bar(ratio, statusColor, 132)
            .kv("Step", 0xFF9AA8B8, resolveCurrentStepLabel(node), 0xFFE9EEF2);
        if (node.subtitle() != null && !node.subtitle().getString().isBlank()) {
            builder.kv("Detail", 0xFF9AA8B8, node.subtitle().getString(), 0xFFD4DBE3);
        }
        if (telemetrySnapshot != null) {
            builder.kv("Rate", 0xFF9AA8B8, String.format(java.util.Locale.ROOT, "%.1f /s", telemetrySnapshot.ratePerSecond()), 0xFFD4DBE3);
            builder.kv("Auto", 0xFF9AA8B8, telemetrySnapshot.autoAdvanceEnabled() ? "ON" : "OFF", telemetrySnapshot.autoAdvanceEnabled() ? 0xFF89D7A0 : 0xFFB4BBC3);
        }
        if (telemetrySnapshot != null && telemetrySnapshot.errorSummary() != null) {
            builder.divider(0xFF8A4040).text("Error: " + telemetrySnapshot.errorSummary(), 0xFFE26B6B, false);
        }
        if (useWorkbenchLayout) {
            builder.divider().text("Wheel: lane scroll | Shift+Wheel: input scroll", 0xFF9AA8B8, false);
        } else {
            builder.divider().text("LMB/Middle drag pan | Wheel scroll | Ctrl+Wheel zoom", 0xFF9AA8B8, false);
        }
        tip.add(builder.build());
        return tip;
    }

    private static String statusTextOf(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Running ignored -> "running";
            case ExecutionFlowStatus.Paused ignored -> "paused";
            case ExecutionFlowStatus.Completed ignored -> "completed";
            case ExecutionFlowStatus.Failed ignored -> "failed";
            case ExecutionFlowStatus.Pending ignored -> "pending";
        };
    }

    private static String segmentProgressOf(ExecutionOverlayNode node) {
        int done = node.laneSegments().count(segment -> segment.status() instanceof ExecutionFlowStatus.Completed);
        return done + "/" + node.laneSegments().size();
    }

    private static float laneRatio(ExecutionOverlayNode node) {
        int total = node.laneSegments().size();
        if (total <= 0) {
            return 0.0f;
        }
        int done = node.laneSegments().count(segment -> segment.status() instanceof ExecutionFlowStatus.Completed);
        return Math.max(0.0f, Math.min(1.0f, done / (float) total));
    }

    private static int statusColor(String statusText) {
        String normalized = statusText == null ? "" : statusText.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "running" -> 0xFF5C9AF2;
            case "paused" -> 0xFFE3B56B;
            case "completed" -> 0xFF6ECF8B;
            case "failed" -> 0xFFE46C6C;
            default -> ExecutionOverlayComponentRenderer.textSecondary;
        };
    }

    private static boolean isAbstractStatusWord(String value) {
        return "pending".equalsIgnoreCase(value)
            || "running".equalsIgnoreCase(value)
            || "paused".equalsIgnoreCase(value)
            || "completed".equalsIgnoreCase(value)
            || "failed".equalsIgnoreCase(value)
            || "idle".equalsIgnoreCase(value);
    }

    private static String resolveCurrentStepLabel(ExecutionOverlayNode node) {
        var currentSegment = node.laneSegments().detect(segment -> segment.current());
        if (currentSegment != null) {
            return currentSegment.label().getString();
        }
        var pendingSegment = node.laneSegments().detect(segment -> segment.status() instanceof ExecutionFlowStatus.Pending);
        if (pendingSegment != null) {
            return pendingSegment.label().getString();
        }
        return "Idle";
    }

    private static String segmentStatusShort(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Running ignored -> "[RUN]";
            case ExecutionFlowStatus.Paused ignored -> "[WAIT]";
            case ExecutionFlowStatus.Completed ignored -> "[DONE]";
            case ExecutionFlowStatus.Failed ignored -> "[ERR]";
            case ExecutionFlowStatus.Pending ignored -> "[TODO]";
        };
    }

    //endregion

    //region helpers

    private BodyLayout bodyLayout() {
        int bodyY = headerHeight;
        int bodyH = bodyHeight(height(), headerHeight, activeFooterHeight());
        int bodyX = overlayFrameInset;
        int bodyW = Math.max(0, width() - overlayFrameInset * 2);
        if (useWorkbenchLayout) {
            return new BodyLayout(bodyX, overlayFrameInset, bodyW, Math.max(0, height() - overlayFrameInset * 2), bodyX, 0);
        }
        int preferredInfo = Math.max(infoPanelMinWidth, Math.min(infoPanelMaxWidth, Math.round(width() * infoPanelWidthRatio)));
        int infoCandidate = bodyW - infoPanelMinGraphWidth;
        int infoWidth = infoCandidate < infoPanelMinWidth ? 0 : Math.min(preferredInfo, infoCandidate);
        int graphWidth = Math.max(0, bodyW - infoWidth - (infoWidth > 0 ? graphPanelGap : 0));
        int infoX = bodyX + graphWidth + (infoWidth > 0 ? graphPanelGap : 0);
        return new BodyLayout(bodyX, bodyY, graphWidth, bodyH, infoX, infoWidth);
    }

    private static boolean insideGraphViewport(double mouseX, double mouseY, BodyLayout body) {
        return mouseX >= body.graphX()
            && mouseX <= body.graphX() + body.graphWidth()
            && mouseY >= body.bodyY()
            && mouseY <= body.bodyY() + body.bodyHeight();
    }

    private void clampScroll(GraphLayoutResult layout, int viewportWidth, int viewportHeight) {
        int minX = minScrollX(viewportWidth);
        int maxX = maxScrollX(layout, viewportWidth);
        int minY = minScrollY(viewportHeight);
        int maxY = maxScrollY(layout, viewportHeight);
        scrollX = Math.max(minX, Math.min(maxX, scrollX));
        scrollY = Math.max(minY, Math.min(maxY, scrollY));
        smoothScrollX = Math.max(minX, Math.min(maxX, smoothScrollX));
        smoothScrollY = Math.max(minY, Math.min(maxY, smoothScrollY));
        targetScrollX = Math.max(minX, Math.min(maxX, targetScrollX));
        targetScrollY = Math.max(minY, Math.min(maxY, targetScrollY));
    }

    private void alignScrollToFocus(GraphLayoutResult layout, boolean centerOnFocus, int viewportWidth, int viewportHeight) {
        ScrollState aligned = focusAlignedScroll(layout, centerOnFocus, viewportWidth, viewportHeight, scrollX, scrollY);
        scrollX = aligned.x();
        scrollY = aligned.y();
    }

    private ScrollState focusAlignedScroll(
        GraphLayoutResult layout,
        boolean centerOnFocus,
        int viewportWidth,
        int viewportHeight,
        int baseScrollX,
        int baseScrollY
    ) {
        if (layout.focusNode() == null) {
            return new ScrollState(baseScrollX, baseScrollY);
        }
        GraphLayoutNode focus = layout.focusNode();
        int focusLeft = focus.x();
        int focusRight = focus.right();
        int focusTop = focus.y();
        int focusBottom = focus.bottom();
        int nextX = baseScrollX;
        int nextY = baseScrollY;

        if (centerOnFocus) {
            nextX = focusLeft - Math.max(0, (viewportWidth - focus.width()) / 2);
            nextY = focusTop - Math.max(0, (viewportHeight - focus.height()) / 2);
        } else {
            if (focusLeft < nextX + focusScrollPadding) {
                nextX = Math.max(minScrollX(viewportWidth), focusLeft - focusScrollPadding);
            } else if (focusRight > nextX + viewportWidth - focusScrollPadding) {
                nextX = Math.max(minScrollX(viewportWidth), Math.min(maxScrollX(layout, viewportWidth), focusRight - viewportWidth + focusScrollPadding));
            }
            if (focusTop < nextY + focusScrollPadding) {
                nextY = Math.max(minScrollY(viewportHeight), focusTop - focusScrollPadding);
            } else if (focusBottom > nextY + viewportHeight - focusScrollPadding) {
                nextY = Math.max(minScrollY(viewportHeight), Math.min(maxScrollY(layout, viewportHeight), focusBottom - viewportHeight + focusScrollPadding));
            }
        }
        nextX = Math.max(minScrollX(viewportWidth), Math.min(maxScrollX(layout, viewportWidth), nextX));
        nextY = Math.max(minScrollY(viewportHeight), Math.min(maxScrollY(layout, viewportHeight), nextY));
        return new ScrollState(nextX, nextY);
    }

    private boolean focusNodeFarOutsideViewport(
        GraphLayoutResult layout,
        int viewportWidth,
        int viewportHeight,
        int baseScrollX,
        int baseScrollY
    ) {
        GraphLayoutNode focus = layout.focusNode();
        if (focus == null) {
            return false;
        }
        int marginX = Math.max(16, viewportWidth / 4);
        int marginY = Math.max(14, viewportHeight / 4);
        int left = baseScrollX + marginX;
        int right = baseScrollX + Math.max(0, viewportWidth - marginX);
        int top = baseScrollY + marginY;
        int bottom = baseScrollY + Math.max(0, viewportHeight - marginY);
        return focus.right() < left || focus.x() > right || focus.bottom() < top || focus.y() > bottom;
    }

    private void smoothScrollTowardsTarget() {
        if (dragging) {
            snapSmoothingToCurrent();
            return;
        }
        double deltaX = targetScrollX - smoothScrollX;
        scrollVelocityX = scrollVelocityX * 0.72d + deltaX * 0.24d;
        if (Math.abs(deltaX) < 0.55d && Math.abs(scrollVelocityX) < 0.22d) {
            smoothScrollX = targetScrollX;
            scrollVelocityX = 0.0d;
        } else {
            smoothScrollX += scrollVelocityX;
        }

        double deltaY = targetScrollY - smoothScrollY;
        scrollVelocityY = scrollVelocityY * 0.72d + deltaY * 0.24d;
        if (Math.abs(deltaY) < 0.55d && Math.abs(scrollVelocityY) < 0.22d) {
            smoothScrollY = targetScrollY;
            scrollVelocityY = 0.0d;
        } else {
            smoothScrollY += scrollVelocityY;
        }
        scrollX = (int) Math.round(smoothScrollX);
        scrollY = (int) Math.round(smoothScrollY);
    }

    private void snapSmoothingToCurrent() {
        smoothScrollX = scrollX;
        smoothScrollY = scrollY;
        scrollVelocityX = 0.0d;
        scrollVelocityY = 0.0d;
    }

    private void animateZoom(BodyLayout body) {
        if (Math.abs(targetZoom - zoom) < zoomSnap) {
            zoom = targetZoom;
            hasZoomAnchor = false;
            return;
        }
        zoom += (targetZoom - zoom) * zoomSmoothSpeed;
        if (Math.abs(targetZoom - zoom) < zoomSnap) {
            zoom = targetZoom;
        }
        if (hasZoomAnchor) {
            scrollX = (int) Math.round(zoomAnchorUnitX * zoom - zoomAnchorScreenX);
            scrollY = (int) Math.round(zoomAnchorUnitY * zoom - zoomAnchorScreenY);
            targetScrollX = scrollX;
            targetScrollY = scrollY;
            hasTargetScroll = true;
            snapSmoothingToCurrent();
            if (Math.abs(targetZoom - zoom) < zoomSnap) {
                hasZoomAnchor = false;
            }
            if (!insideGraphViewport(zoomAnchorScreenX + body.graphX(), zoomAnchorScreenY + body.bodyY(), body)) {
                hasZoomAnchor = false;
            }
        }
    }

    private static int panOverscanX(int viewportWidth) {
        return Math.max(scrollOverscan, Math.max(20, Math.round(viewportWidth * 0.45f)));
    }

    private static int panOverscanY(int viewportHeight) {
        return Math.max(scrollOverscan, Math.max(16, Math.round(viewportHeight * 0.35f)));
    }

    private int minScrollX(int viewportWidth) {
        return -panOverscanX(viewportWidth);
    }

    private int minScrollY(int viewportHeight) {
        return -panOverscanY(viewportHeight);
    }

    private int maxScrollX(GraphLayoutResult layout, int viewportWidth) {
        int pad = panOverscanX(viewportWidth);
        return Math.max(pad, layout.contentWidth() - Math.max(0, viewportWidth) + pad);
    }

    private int maxScrollY(GraphLayoutResult layout, int viewportHeight) {
        int pad = panOverscanY(viewportHeight);
        return Math.max(pad, layout.contentHeight() - Math.max(0, viewportHeight) + pad);
    }

    private String trimLabel(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        String value = text;
        while (value.length() > 1 && font.width(value) + ellipsisWidth > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }

    private String[] wrapLabelLines(Font font, String text, int maxWidth) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return new String[]{"", ""};
        }
        if (font.width(trimmed) <= maxWidth) {
            return new String[]{trimmed, ""};
        }
        String probe = font.plainSubstrByWidth(trimmed, maxWidth).trim();
        if (probe.isEmpty()) {
            return new String[]{trimLabel(font, trimmed, maxWidth), ""};
        }
        int splitIndex = probe.length();
        int wordBreak = Math.max(probe.lastIndexOf(' '), probe.lastIndexOf('-'));
        if (wordBreak > 0) {
            splitIndex = wordBreak;
        }
        String first = trimLabel(font, trimmed.substring(0, splitIndex).trim(), maxWidth);
        String rest = trimLabel(font, trimmed.substring(Math.min(trimmed.length(), splitIndex)).trim(), maxWidth);
        return new String[]{first, rest};
    }

    private ImmutableList<ExecutionOverlayNode> focusPathNodes() {
        if (viewModel == null) {
            return Lists.immutable.empty();
        }
        String targetId = viewModel.focusNodeId();
        if (targetId == null) {
            return Lists.immutable.of(viewModel.root());
        }
        return focusPathNodes(viewModel.root(), targetId);
    }

    private static ImmutableList<ExecutionOverlayNode> focusPathNodes(ExecutionOverlayNode root, String targetId) {
        MutableList<ExecutionOverlayNode> path = Lists.mutable.empty();
        if (!appendPath(root, targetId, path)) {
            return Lists.immutable.of(root);
        }
        return path.toImmutable();
    }

    private static boolean appendPath(ExecutionOverlayNode node, String targetId, MutableList<ExecutionOverlayNode> path) {
        path.add(node);
        if (Objects.equals(node.nodeId(), targetId)) {
            return true;
        }
        for (ExecutionOverlayNode child : node.children()) {
            if (appendPath(child, targetId, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    private ImmutableList<ExecutionOverlayNode> pathContextNodes() {
        return reduceWorkbenchPathTrail(focusPathNodes(), currentSubgraphRootId());
    }

    private String currentSubgraphRootId() {
        ExecutionOverlayNode root = currentSubgraphRoot();
        return root == null ? "" : root.nodeId();
    }

    private @Nullable ExecutionOverlayNode currentSubgraphRoot() {
        ImmutableList<ExecutionOverlayNode> path = focusPathNodes();
        if (path.isEmpty()) {
            return null;
        }
        MutableList<ExecutionOverlayNode> branching = Lists.mutable.empty();
        for (ExecutionOverlayNode node : path) {
            if (node.children().size() > 1) {
                branching.add(node);
            }
        }
        if (branching.isEmpty()) {
            return path.getFirst();
        }
        if (branching.size() >= 2) {
            return branching.get(1);
        }
        return branching.getFirst();
    }

    private @Nullable FocusContext focusContext(GraphLayoutResult layout) {
        if (viewModel == null || layout.flattened().isEmpty()) {
            return null;
        }
        ExecutionOverlayNode current = layout.focusNode() != null
            ? layout.focusNode().node()
            : layout.flattened().getFirst().node();
        ImmutableList<ExecutionOverlayNode> path = focusPathNodes();
        int index = path.indexOf(current);
        ExecutionOverlayNode parent = index > 0 ? path.get(index - 1) : null;
        int visibleIndex = viewModel.focusVisibleIndex();
        ExecutionOverlayNode next = null;
        if (visibleIndex >= 0 && visibleIndex + 1 < viewModel.visibleNodes().size()) {
            next = viewModel.visibleNodes().get(visibleIndex + 1);
        }
        return new FocusContext(current, parent, next);
    }

    private @Nullable GraphLayoutResult buildLayout(int viewportWidth) {
        if (viewModel == null) {
            return null;
        }
        LayoutMetrics metrics = layoutMetrics();
        int safeViewportWidth = Math.max(metrics.nodeWidth() + metrics.paddingX() * 2, viewportWidth);
        if (viewModel.visibleNodes().notEmpty()) {
            layoutMode = LayoutMode.timeline;
            return buildTimelineLayout(viewModel.visibleNodes(), metrics, safeViewportWidth);
        }

        ExecutionOverlayNode fallbackRoot = viewModel.root();
        String rootId = fallbackRoot.nodeId();
        if (!Objects.equals(lastLayoutRootId, rootId)) {
            layoutMode = LayoutMode.balanced;
            lastLayoutRootId = rootId;
        }
        int[] nextX = new int[]{metrics.paddingX()};
        GraphLayoutNode root = layoutNodeTopToBottom(fallbackRoot, 0, nextX, metrics);
        return buildLayoutResultFromRoot(root, metrics);
    }

    private GraphLayoutResult buildTimelineLayout(
        ImmutableList<ExecutionOverlayNode> orderedNodes,
        LayoutMetrics metrics,
        int viewportWidth
    ) {
        MutableList<GraphLayoutNode> flattened = Lists.mutable.empty();
        int y = metrics.paddingY();
        int laneWidth = Math.max(metrics.nodeWidth(), viewportWidth - metrics.paddingX() * 2 - 4);
        int indentStep = Math.max(8, Math.round(metrics.nodeWidth() * 0.12f));
        int maxIndent = Math.max(18, laneWidth / 3);
        GraphLayoutNode focus = null;

        for (ExecutionOverlayNode node : orderedNodes) {
            int depthOffset = Math.max(0, node.depth() - 1);
            int indent = Math.min(maxIndent, depthOffset * indentStep);
            int x = metrics.paddingX() + indent;
            int width = Math.max(96, laneWidth - indent);
            boolean compact = node.depth() >= 2 || (!node.activeBranch() && node.children().isEmpty());
            int nodeHeightPx = compact
                ? Math.max(20, metrics.nodeHeight() - 34)
                : Math.max(28, metrics.nodeHeight() - 18);
            GraphLayoutNode layoutNode = new GraphLayoutNode(
                node,
                x,
                y,
                width,
                nodeHeightPx,
                Lists.immutable.empty()
            );
            flattened.add(layoutNode);
            if (viewModel != null
                && viewModel.focusNodeId() != null
                && viewModel.focusNodeId().equals(node.nodeId())) {
                focus = layoutNode;
            }
            int rowGap = compact
                ? Math.max(1, Math.round(zoom * 2.0f))
                : Math.max(0, Math.round(zoom));
            y += nodeHeightPx + rowGap;
        }

        int contentWidth = 0;
        for (GraphLayoutNode node : flattened) {
            contentWidth = Math.max(contentWidth, node.right() + metrics.paddingX());
        }
        int contentHeight = Math.max(metrics.paddingY() * 2 + metrics.nodeHeight(), y + metrics.paddingY());
        return new GraphLayoutResult(flattened.toImmutable(), contentWidth, contentHeight, focus);
    }

    private GraphLayoutResult buildLayoutResultFromRoot(GraphLayoutNode root, LayoutMetrics metrics) {
        var flattened = Lists.mutable.<GraphLayoutNode>empty();
        collect(root, flattened);
        int contentWidth = 0;
        int contentHeight = 0;
        GraphLayoutNode focus = null;
        for (GraphLayoutNode node : flattened) {
            contentWidth = Math.max(contentWidth, node.right() + metrics.paddingX());
            contentHeight = Math.max(contentHeight, node.bottom() + metrics.paddingY());
            if (viewModel != null && viewModel.focusNodeId() != null && viewModel.focusNodeId().equals(node.node().nodeId())) {
                focus = node;
            }
        }
        return new GraphLayoutResult(flattened.toImmutable(), contentWidth, contentHeight, focus);
    }

    private GraphLayoutNode layoutNodeTopToBottom(ExecutionOverlayNode node, int depth, int[] nextX, LayoutMetrics metrics) {
        var children = Lists.mutable.<GraphLayoutNode>empty();
        if (node.expanded()) {
            for (ExecutionOverlayNode child : node.children()) {
                children.add(layoutNodeTopToBottom(child, depth + 1, nextX, metrics));
            }
        }

        int y = metrics.paddingY() + depth * (metrics.nodeHeight() + metrics.gapY());
        int x;
        if (children.isEmpty()) {
            x = nextX[0];
            nextX[0] += metrics.nodeWidth() + metrics.gapX();
        } else {
            int first = children.getFirst().centerX();
            int last = children.getLast().centerX();
            x = (first + last) / 2 - metrics.nodeWidth() / 2;
        }
        return new GraphLayoutNode(node, x, y, metrics.nodeWidth(), metrics.nodeHeight(), children.toImmutable());
    }

    private GraphLayoutNode layoutNodeDepthFirst(ExecutionOverlayNode node, int depth, int[] nextY, LayoutMetrics metrics) {
        int compactIndent = Math.max(20, metrics.nodeWidth() / 2);
        int x = metrics.paddingX() + depth * compactIndent;
        int y = nextY[0];
        nextY[0] += metrics.nodeHeight() + metrics.gapY() + 4;

        var children = Lists.mutable.<GraphLayoutNode>empty();
        if (node.expanded()) {
            for (ExecutionOverlayNode child : node.children()) {
                children.add(layoutNodeDepthFirst(child, depth + 1, nextY, metrics));
            }
        }
        return new GraphLayoutNode(node, x, y, metrics.nodeWidth(), metrics.nodeHeight(), children.toImmutable());
    }

    private GraphLayoutNode layoutNodeDepthLanes(
        ExecutionOverlayNode node,
        int depth,
        MutableList<Integer> depthCursors,
        int laneIndent,
        LayoutMetrics metrics
    ) {
        while (depthCursors.size() <= depth) {
            depthCursors.add(metrics.paddingY());
        }
        int y = depthCursors.get(depth);
        depthCursors.set(depth, y + metrics.nodeHeight() + Math.max(12, metrics.gapY() + 2));
        int x = metrics.paddingX() + depth * laneIndent;

        var children = Lists.mutable.<GraphLayoutNode>empty();
        if (node.expanded()) {
            for (ExecutionOverlayNode child : node.children()) {
                children.add(layoutNodeDepthLanes(child, depth + 1, depthCursors, laneIndent, metrics));
            }
        }
        return new GraphLayoutNode(node, x, y, metrics.nodeWidth(), metrics.nodeHeight(), children.toImmutable());
    }

    private void collect(GraphLayoutNode node, org.eclipse.collections.api.list.MutableList<GraphLayoutNode> out) {
        out.add(node);
        for (GraphLayoutNode child : node.children()) {
            collect(child, out);
        }
    }

    private LayoutStats collectLayoutStats(ExecutionOverlayNode node) {
        MutableList<Integer> depthCounts = Lists.mutable.empty();
        collectLayoutStats(node, 0, depthCounts);
        int maxBreadth = 1;
        for (Integer count : depthCounts) {
            if (count != null) {
                maxBreadth = Math.max(maxBreadth, count);
            }
        }
        return new LayoutStats(depthCounts.size(), maxBreadth);
    }

    private void collectLayoutStats(ExecutionOverlayNode node, int depth, MutableList<Integer> depthCounts) {
        while (depthCounts.size() <= depth) {
            depthCounts.add(0);
        }
        depthCounts.set(depth, depthCounts.get(depth) + 1);
        if (!node.expanded()) {
            return;
        }
        for (ExecutionOverlayNode child : node.children()) {
            collectLayoutStats(child, depth + 1, depthCounts);
        }
    }

    private record LayoutMetrics(
        int paddingX,
        int paddingY,
        int nodeWidth,
        int nodeHeight,
        int gapX,
        int gapY
    ) {
    }

    private record GraphLayoutNode(
        ExecutionOverlayNode node,
        int x,
        int y,
        int width,
        int height,
        ImmutableList<GraphLayoutNode> children
    ) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int centerX() {
            return x + width / 2;
        }

        int centerY() {
            return y + height / 2;
        }
    }

    private record GraphLayoutResult(
        ImmutableList<GraphLayoutNode> flattened,
        int contentWidth,
        int contentHeight,
        @Nullable GraphLayoutNode focusNode
    ) {
    }

    private record FocusContext(
        ExecutionOverlayNode current,
        @Nullable ExecutionOverlayNode parent,
        @Nullable ExecutionOverlayNode next
    ) {
    }

    private enum LayoutMode {
        timeline,
        balanced,
        depthFirst,
        depthLanes
    }

    private record ScrollState(int x, int y) {
    }

    private record LayoutStats(int depth, int maxBreadth) {
    }

    private record BodyLayout(
        int graphX,
        int bodyY,
        int graphWidth,
        int bodyHeight,
        int infoX,
        int infoWidth
    ) {
    }

    private record WorkbenchDockLayout(
        Rect2i bar,
        Rect2i prevButton,
        Rect2i nextButton,
        int tabsX,
        int tabY,
        int tabWidth,
        int tabHeight,
        int visibleStart,
        int visibleCount,
        boolean paged
    ) {
        private boolean shows(WorkbenchDockWindow window) {
            int ordinal = window.ordinal();
            return ordinal >= visibleStart && ordinal < visibleStart + visibleCount;
        }

        private boolean canPrev() {
            return paged && visibleStart > 0;
        }

        private boolean canNext(int totalTabs) {
            return paged && visibleStart + visibleCount < totalTabs;
        }
    }

    private record WorkbenchInputStripLayout(
        Rect2i stripBounds,
        Rect2i prevButton,
        Rect2i nextButton,
        int visibleSlots,
        int maxScroll,
        int pageStep,
        boolean paged
    ) {
        private int clampScroll(int value) {
            return Math.max(0, Math.min(maxScroll, value));
        }

        private boolean canPrev(int scrollOffset) {
            return paged && clampScroll(scrollOffset) > 0;
        }

        private boolean canNext(int scrollOffset) {
            return paged && clampScroll(scrollOffset) < maxScroll;
        }
    }

    private record GroupBounds(int x, int y, int w, int h) {
        private boolean contains(int px, int py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    private record WorkbenchInputEntry(ItemStack stack, int count) {
    }

    private record WorkbenchOutputEntry(ItemStack stack, int count) {
    }

    //endregion
}
