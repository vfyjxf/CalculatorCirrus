package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignSelfStretch;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexBasisPx;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetZero;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Z0 flow stage: hosts the dedicated stage header strip above the spatial DAG and injectable flow views.
 */
public class FlowStageWidget extends CompositeWidget<Widget> {

    public static final int headerHeight = 24;
    public static final int bannerHeight = 18;
    public static final int bannerInset = 8;

    private static final String keyBannerPlanning = "cirrus.ui.workbench.flow.banner.planning";
    private static final String keyBannerNoTarget = "cirrus.ui.workbench.flow.banner.no_target";
    private static final String keyBannerConflict = "cirrus.ui.workbench.flow.banner.conflict";
    private static final String keyBannerUnreachable = "cirrus.ui.workbench.flow.banner.unreachable";
    private static final String keyEmptyView = "cirrus.ui.workbench.flow.empty_view";

    private final StageHeaderBar headerBar;
    private final WidgetGroup<Widget> contentHost;
    private final SpatialDagCanvasWidget dag;
    private final Map<FlowViewMode, Widget> injectedViews = new EnumMap<>(FlowViewMode.class);
    private @Nullable SpatialDagHost host;
    private FlowViewMode currentView = FlowViewMode.spatialDag;
    private Component bannerText = Component.empty();
    private int bannerColor;
    private WorkbenchThemeMode themeMode = WorkbenchThemeMode.defaultMode();

    private FlowStageWidget() {
        useStyle(sizeFull(), flexColumn(), positionAbsolute(), insetZero(), minWidth(0), minHeight(0));
        setTickable(true);

        headerBar = new StageHeaderBar();
        headerBar.useStyle(
            widthOf(TaffyDimension.percent(1f)),
            heightOf(headerHeight),
            flexShrink(0),
            alignSelfStretch(),
            minWidth(0)
        );
        addWidget(headerBar);

        contentHost = new WidgetGroup<>();
        contentHost.useStyle(
            widthOf(TaffyDimension.percent(1f)),
            flexGrow(1),
            flexShrink(1),
            flexBasisPx(0f),
            alignSelfStretch(),
            minWidth(0),
            minHeight(0)
        );
        addWidget(contentHost);

        dag = contentHost.addWidget(SpatialDagCanvasWidget.create());
        dag.embeddedInStageHeader(true);
        dag.onViewRequested(this::requestViewMode);
    }

    public static FlowStageWidget create() {
        return new FlowStageWidget();
    }

    public StageHeaderBar headerBar() {
        return headerBar;
    }

    public FlowStageWidget host(@Nullable SpatialDagHost host) {
        this.host = host;
        dag.host(host);
        dag.themeMode(themeMode);
        return this;
    }

    public FlowStageWidget feedbackSink(MultiSelectActionBarWidget.@Nullable FeedbackSink sink) {
        dag.feedbackSink(sink);
        return this;
    }

    public FlowStageWidget inlineBeacons(@Nullable Map<String, String> beacons) {
        dag.inlineBeacons(beacons);
        return this;
    }

    public FlowStageWidget themeMode(@Nullable WorkbenchThemeMode themeMode) {
        this.themeMode = themeMode == null ? WorkbenchThemeMode.defaultMode() : themeMode;
        dag.themeMode(this.themeMode);
        return this;
    }

    public FlowStageWidget resolver(dev.vfyjxf.cirrus.ui.workbench.state.@Nullable PlanVisualResolver resolver) {
        dag.resolver(resolver);
        return this;
    }

    public FlowStageWidget graph(dev.vfyjxf.cirrus.api.planning.graph.@Nullable CompiledRecipeGraph graph) {
        dag.graph(graph);
        return this;
    }

    public SpatialDagCanvasWidget dag() {
        return dag;
    }

    public FlowStageWidget preview(@Nullable Set<String> stepIds, SpatialDagCanvasWidget.@Nullable PreviewKind kind) {
        dag.previewSteps(stepIds, kind);
        return this;
    }

    public FlowStageWidget clearPreview() {
        dag.clearPreview();
        return this;
    }

    public FlowViewMode currentView() {
        return currentView;
    }

    public Map<FlowViewMode, Widget> injectedViews() {
        return Map.copyOf(injectedViews);
    }

    /**
     * Registers a view implementation; passing {@code null} removes the previously injected widget.
     */
    public void setViewWidget(FlowViewMode mode, @Nullable Widget widget) {
        Objects.requireNonNull(mode, "mode");
        Widget previous = injectedViews.remove(mode);
        if (previous != null && previous != widget) {
            contentHost.remove(previous);
        }
        if (widget == null) {
            return;
        }
        widget.useStyle(sizeFull(), positionAbsolute(), insetZero());
        widget.setVisible(mode == currentView);
        injectedViews.put(mode, contentHost.addWidget(widget));
    }

    public void requestViewMode(FlowViewMode mode) {
        if (mode == null) {
            return;
        }
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        PlannerWorkbenchController controller = shell == null ? null : shell.activeController();
        if (controller != null) {
            controller.setFlowViewMode(mode);
        }
        showView(mode);
    }

    public void showView(FlowViewMode mode) {
        if (mode == null) {
            return;
        }
        if (mode != FlowViewMode.spatialDag) {
            dag.closeViewSettingsPopover();
        }
        currentView = mode;
        dag.setVisible(mode == FlowViewMode.spatialDag);
        for (Map.Entry<FlowViewMode, Widget> entry : injectedViews.entrySet()) {
            boolean active = entry.getKey() == mode;
            Widget view = entry.getValue();
            view.setVisible(active);
            if (active) {
                if (view instanceof PreparationLedgerWidget ledger) {
                    ledger.refresh();
                } else if (view instanceof ExecutionChecklistWidget checklist) {
                    checklist.refresh();
                }
            }
        }
        if (mode == FlowViewMode.spatialDag && lifecycle().mounted()) {
            scene().requestFocus(dag);
        }
    }

    @Override
    public void tick() {
        super.tick();
        SpatialDagHost dagHost = host;
        if (dagHost == null) {
            bannerText = Component.empty();
            return;
        }
        WorkbenchUiSnapshot snapshot = dagHost.snapshot();
        flowViewHint(snapshot.flowViewMode());
        bannerText = bannerFor(snapshot.phase());
        bannerColor = bannerColorOf(snapshot.phase(), dagHost.palette());
    }

    private void flowViewHint(FlowViewMode mode) {
        if (mode != null && mode != currentView) {
            showView(mode);
        }
    }

    private static Component bannerFor(WorkbenchPhase phase) {
        if (phase == null) {
            return Component.empty();
        }
        return switch (phase) {
            case planning -> Component.translatable(keyBannerPlanning);
            case idleNoTarget, noGraph, closed -> Component.translatable(keyBannerNoTarget);
            case conflictInfeasible -> Component.translatable(keyBannerConflict);
            case unreachable -> Component.translatable(keyBannerUnreachable);
            case readyPareto, readyBudgetHit -> Component.empty();
        };
    }

    private static int bannerColorOf(WorkbenchPhase phase, CirrusPalette palette) {
        if (phase == null) {
            return palette.textMuted();
        }
        return switch (phase) {
            case planning -> palette.focusRing();
            case conflictInfeasible -> palette.ruleHardBan();
            case unreachable -> palette.advisoryLastResort();
            case idleNoTarget, noGraph, closed -> palette.textMuted();
            case readyPareto, readyBudgetHit -> palette.textMuted();
        };
    }

    public int bannerLeft() {
        WorkbenchShellContext shell = CirrusUiTheme.shellContext(this);
        return shell != null && shell.zenMode() ? Math.max(48, bannerInset) : bannerInset;
    }

    @Override
    public void render(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.render(canvas, mouseX, mouseY, partialTicks);
        CirrusPalette palette = host == null ? CirrusPalette.obsidianBlueprint : host.palette();
        if (currentView != FlowViewMode.spatialDag && injectedViews.get(currentView) == null) {
            canvas.fill(0, headerHeight, width(), Math.max(0, height() - headerHeight), palette.canvasVoid());
            canvas.drawCenteredString(
                Component.translatable(keyEmptyView),
                width() / 2,
                ChromeText.centeredY(headerHeight, Math.max(1, height() - headerHeight), canvas),
                palette.textMuted()
            );
        }
        WorkbenchPhase phase = host == null ? null : host.snapshot().phase();
        boolean suppressedEmptyBanner = phase == null
                || phase == WorkbenchPhase.idleNoTarget
                || phase == WorkbenchPhase.noGraph
                || phase == WorkbenchPhase.closed;
        if (!suppressedEmptyBanner && bannerText != null && !bannerText.getString().isEmpty()) {
            int textWidth = ChromeText.width(canvas, bannerText);
            int stripWidth = textWidth + 16;
            int left = Math.max(bannerLeft(), width() - stripWidth - bannerInset);
            int top = headerHeight + bannerInset;
            canvas.fill(left, top, stripWidth, bannerHeight, palette.popoverSurface());
            canvas.strokeRect(left, top, stripWidth, bannerHeight, bannerColor, 1);
            ChromeText.draw(canvas, bannerText, left + 8, ChromeText.centeredY(top, bannerHeight, canvas), bannerColor);
        }
    }

    /**
     * Dedicated 24px OreUI stage strip hosting docked left/right panel expand buttons, flow mode switcher, and DAG view menu.
     */
    public final class StageHeaderBar extends Widget implements OreUiTooltipProvider {

        private static final int padX = 6;
        private static final int btnH = 18;
        private static final int gap = 3;
        private static final FlowViewMode[] viewModes = {FlowViewMode.spatialDag, FlowViewMode.preparationLedger,
                FlowViewMode.executionChecklist};

        private @Nullable Rect leftExpandRect;
        private @Nullable Rect rightExpandRect;
        private @Nullable Rect minimapToggleRect;
        private @Nullable Rect viewMenuRect;
        private final Rect[] modeRects = new Rect[viewModes.length];

        private StageHeaderBar() {
            setInteractive(true);
            onMouseClick((input, clickCount, ctx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                int lx = (int) Math.round(local.x());
                int ly = (int) Math.round(local.y());
                computeGeometry(width(), height(), null);
                WorkbenchShellContext shell = CirrusUiTheme.shellContext(FlowStageWidget.this);
                if (leftExpandRect != null && leftExpandRect.contains(lx, ly)) {
                    if (shell != null) {
                        shell.setLeftDockCollapsed(false);
                    }
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (rightExpandRect != null && rightExpandRect.contains(lx, ly)) {
                    if (shell != null) {
                        shell.setRightDockCollapsed(false);
                    }
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (minimapToggleRect != null && minimapToggleRect.contains(lx, ly)) {
                    dag.setMinimapUserVisible(!dag.minimapUserVisible());
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (viewMenuRect != null && viewMenuRect.contains(lx, ly)) {
                    FloatPos sceneAnchor = localToScene(viewMenuRect.right(), height() + 4);
                    dag.toggleViewSettingsPopoverAt(
                        (int) Math.round(sceneAnchor.x()),
                        (int) Math.round(sceneAnchor.y())
                    );
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                for (int i = 0; i < viewModes.length; i++) {
                    if (modeRects[i] != null && modeRects[i].contains(lx, ly)) {
                        requestViewMode(viewModes[i]);
                        CirrusUiSound.playClickSound();
                        ctx.consume();
                        return EventDispatch.consumed;
                    }
                }
                return EventDispatch.pass;
            });
        }

        private Component modeLabel(FlowViewMode mode, boolean compact) {
            return switch (mode) {
                case spatialDag -> compact ? Component.literal("DAG") : CirrusLangs.Workbench.Flow.dag.get();
                case preparationLedger ->
                    compact ? Component.literal("Ledger") : CirrusLangs.Workbench.Flow.ledger.get();
                case executionChecklist ->
                    compact ? Component.literal("List") : CirrusLangs.Workbench.Flow.checklist.get();
            };
        }

        private Component viewMenuLabel(boolean compact) {
            DagLayoutMode activeLayout = host == null ? DagLayoutMode.horizontalLr : host.snapshot().dagLayoutMode();
            String dirShort = switch (activeLayout) {
                case horizontalLr -> "LR";
                case verticalTb -> "TB";
                case horizontalRl -> "RL";
                case verticalBt -> "BT";
                case systemSwimlane -> "Lane";
            };
            int zoomPct = Math.round(dag.camera().zoom() * 100.0f);
            return compact ? Component.literal(dirShort) : Component.literal(dirShort + " \u00b7 " + zoomPct + "%");
        }

        private int measureText(@Nullable SceneCanvas canvas, Component text) {
            if (canvas != null) {
                return ChromeText.width(canvas, text);
            }
            Font font = resolveFont();
            if (font != null) {
                return font.width(text);
            }
            String s = text.getString();
            int est = 0;
            for (int i = 0; i < s.length(); i++) {
                est += s.charAt(i) > 0x7F ? 10 : 6;
            }
            return Math.max(12, est);
        }

        private @Nullable Font resolveFont() {
            if (lifecycle().mounted() && context() != null && context().font() != null) {
                return context().font();
            }
            try {
                Minecraft mc = Minecraft.getInstance();
                return mc == null ? null : mc.font;
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private boolean computeGeometry(int w, int h, @Nullable SceneCanvas canvas) {
            leftExpandRect = null;
            rightExpandRect = null;
            minimapToggleRect = null;
            viewMenuRect = null;
            for (int i = 0; i < modeRects.length; i++) {
                modeRects[i] = null;
            }
            if (w <= 20 || h <= 6) {
                return false;
            }
            WorkbenchShellContext shell = CirrusUiTheme.shellContext(FlowStageWidget.this);
            boolean zen = shell != null && shell.zenMode();
            boolean showLeftExpand = shell != null
                    && !zen
                    && shell.leftDockCollapsed()
                    && shell.breakpoint().showsLeftDock();
            boolean showRightExpand = shell != null
                    && !zen
                    && shell.rightDockCollapsed()
                    && shell.breakpoint().showsRightDock();
            boolean showViewMenu = currentView == FlowViewMode.spatialDag;

            int btnY = Math.max(2, (h - btnH) / 2);
            int leftX = zen ? Math.round(WorkbenchShellLayout.zenRailSize) + 6 : padX;
            int rightX = w - padX;

            if (showLeftExpand) {
                int lw = w >= 420 ? 58 : 22;
                leftExpandRect = new Rect(leftX, btnY, lw, btnH);
                leftX = leftExpandRect.right() + 6;
            }
            if (showRightExpand) {
                int rw = w >= 420 ? 72 : 22;
                rightExpandRect = new Rect(rightX - rw, btnY, rw, btnH);
                rightX = rightExpandRect.x() - 6;
            }
            boolean compact = w < 520;
            if (showViewMenu) {
                Component menuLbl = viewMenuLabel(compact);
                int mw = Math.max(36, measureText(canvas, menuLbl) + 20);
                if (rightX - mw > leftX + 42) {
                    viewMenuRect = new Rect(rightX - mw, btnY, mw, btnH);
                    rightX = viewMenuRect.x() - gap;
                }
                int mapBtnW = 18;
                if (rightX - mapBtnW > leftX + 42) {
                    minimapToggleRect = new Rect(rightX - mapBtnW, btnY, mapBtnW, btnH);
                    rightX = minimapToggleRect.x() - 5;
                }
            }

            int availModesW = Math.max(0, rightX - leftX);
            int fullSum = 0;
            for (int i = 0; i < viewModes.length; i++) {
                fullSum += measureText(canvas, modeLabel(viewModes[i], false)) + 14 + (i > 0 ? gap : 0);
            }
            boolean useCompactModes = fullSum > availModesW;
            int cx = leftX;
            int modePad = useCompactModes ? 5 : 7;
            for (int i = 0; i < viewModes.length; i++) {
                Component lbl = modeLabel(viewModes[i], useCompactModes);
                int mw = Math.max(24, measureText(canvas, lbl) + modePad * 2);
                int remaining = rightX - cx;
                if (remaining < 18) {
                    break;
                }
                int clamped = Math.min(mw, remaining);
                modeRects[i] = new Rect(cx, btnY, clamped, btnH);
                cx += clamped + gap;
            }
            return useCompactModes;
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            computeGeometry(width(), height(), null);
            if (leftExpandRect != null && leftExpandRect.contains(localX, localY)) {
                return OreUiRichTooltip.builder("Expand Plans Dock").badge("OPEN", OreUiRichTooltip.Tone.accent)
                        .text("Restores the left Pareto plans comparison panel.", OreUiRichTooltip.Tone.secondary)
                        .build();
            }
            if (rightExpandRect != null && rightExpandRect.contains(localX, localY)) {
                return OreUiRichTooltip.builder("Expand Co-Pilot Dock").badge("OPEN", OreUiRichTooltip.Tone.accent)
                        .text(
                            "Restores the right Decisions, Inspector, and Quick Drop panel.",
                            OreUiRichTooltip.Tone.secondary
                        ).build();
            }
            if (minimapToggleRect != null && minimapToggleRect.contains(localX, localY)) {
                boolean shown = dag.minimapUserVisible();
                return OreUiRichTooltip.builder(CirrusLangs.Workbench.Flow.minimap.get())
                        .badge(
                            shown ? "ON" : "OFF",
                            shown ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.muted
                        ).build();
            }
            if (viewMenuRect != null && viewMenuRect.contains(localX, localY)) {
                return DagRichTooltips.capsuleAction(SpatialDagCanvasWidget.CapsuleAction.viewMenu);
            }
            SpatialDagCanvasWidget.CapsuleAction[] actions = {SpatialDagCanvasWidget.CapsuleAction.viewDag,
                    SpatialDagCanvasWidget.CapsuleAction.viewLedger,
                    SpatialDagCanvasWidget.CapsuleAction.viewChecklist};
            for (int i = 0; i < modeRects.length; i++) {
                if (modeRects[i] != null && modeRects[i].contains(localX, localY)) {
                    return DagRichTooltips.capsuleAction(actions[i]);
                }
            }
            return null;
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            int w = width();
            int h = height();
            if (w <= 0 || h <= 0) {
                return;
            }
            CirrusPalette p = host == null ? CirrusPalette.obsidianBlueprint : host.palette();
            boolean compactModes = computeGeometry(w, h, canvas);

            // Clean dedicated OreUI header strip surface with bottom divider.
            OreUiPaint.headerBand(canvas, 0, 0, w, h, p, false);

            if (leftExpandRect != null) {
                boolean hov = leftExpandRect.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    leftExpandRect.x(),
                    leftExpandRect.y(),
                    leftExpandRect.width(),
                    leftExpandRect.height(),
                    p,
                    false,
                    hov,
                    false,
                    false
                );
                int ink = hov ? p.focusRing() : p.textSecondary();
                ChromeButtons.doubleChevronRight(
                    canvas,
                    leftExpandRect.x() + 6,
                    leftExpandRect.y() + Math.max(2, (leftExpandRect.height() - 2 - 7) / 2),
                    ink
                );
                if (leftExpandRect.width() >= 44) {
                    ChromeText.drawFitted(
                        canvas,
                        Component.literal("Plans"),
                        leftExpandRect.x() + 17,
                        ChromeText.centeredButtonY(leftExpandRect.y(), leftExpandRect.height(), canvas),
                        leftExpandRect.width() - 21,
                        hov ? p.textPrimary() : p.textSecondary()
                    );
                }
            }

            if (rightExpandRect != null) {
                boolean hov = rightExpandRect.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    rightExpandRect.x(),
                    rightExpandRect.y(),
                    rightExpandRect.width(),
                    rightExpandRect.height(),
                    p,
                    false,
                    hov,
                    false,
                    false
                );
                int ink = hov ? p.focusRing() : p.textSecondary();
                if (rightExpandRect.width() >= 48) {
                    ChromeText.drawFitted(
                        canvas,
                        Component.literal("Co-Pilot"),
                        rightExpandRect.x() + 6,
                        ChromeText.centeredButtonY(rightExpandRect.y(), rightExpandRect.height(), canvas),
                        rightExpandRect.width() - 22,
                        hov ? p.textPrimary() : p.textSecondary()
                    );
                }
                ChromeButtons.doubleChevronLeft(
                    canvas,
                    rightExpandRect.right() - 13,
                    rightExpandRect.y() + Math.max(2, (rightExpandRect.height() - 2 - 7) / 2),
                    ink
                );
            }

            for (int i = 0; i < viewModes.length; i++) {
                Rect r = modeRects[i];
                if (r == null) {
                    continue;
                }
                boolean active = currentView == viewModes[i];
                boolean hov = r.contains(mouseX, mouseY);
                if (active) {
                    OreUiPaint.selectedToggle(canvas, r.x(), r.y(), r.width(), r.height(), p, hov);
                } else {
                    OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, false, hov, false, false);
                }
                Component lbl = ChromeText
                        .fit(canvas, modeLabel(viewModes[i], compactModes), Math.max(8, r.width() - 8));
                int tx = r.x() + Math.max(4, (r.width() - ChromeText.width(canvas, lbl)) / 2);
                int ty = active
                        ? ChromeText.centeredY(r.y(), r.height(), canvas)
                        : ChromeText.centeredButtonY(r.y(), r.height(), canvas);
                ChromeText
                        .draw(canvas, lbl, tx, ty, active ? p.focusRing() : hov ? p.textPrimary() : p.textSecondary());
            }

            if (minimapToggleRect != null) {
                boolean mapOn = dag.minimapUserVisible();
                boolean hov = minimapToggleRect.contains(mouseX, mouseY);
                if (mapOn) {
                    OreUiPaint.selectedToggle(
                        canvas,
                        minimapToggleRect.x(),
                        minimapToggleRect.y(),
                        minimapToggleRect.width(),
                        minimapToggleRect.height(),
                        p,
                        hov
                    );
                } else {
                    OreUiPaint.raisedButton(
                        canvas,
                        minimapToggleRect.x(),
                        minimapToggleRect.y(),
                        minimapToggleRect.width(),
                        minimapToggleRect.height(),
                        p,
                        false,
                        hov,
                        false,
                        false
                    );
                }
                int ink = mapOn ? p.focusRing() : (hov ? p.textPrimary() : p.textMuted());
                int ix = minimapToggleRect.x() + (minimapToggleRect.width() - 10) / 2;
                int iy = minimapToggleRect.y() + (minimapToggleRect.height() - 7) / 2;
                canvas.strokeRect(ix, iy, 10, 7, ink, 1);
                canvas.fill(ix + 2, iy + 2, 3, 2, ink);
                canvas.fill(ix + 6, iy + 3, 2, 2, mapOn ? p.textPrimary() : ink);
            }

            if (viewMenuRect != null) {
                boolean open = dag.isViewSettingsPopoverOpen();
                boolean hov = viewMenuRect.contains(mouseX, mouseY);
                if (open) {
                    OreUiPaint.selectedToggle(
                        canvas,
                        viewMenuRect.x(),
                        viewMenuRect.y(),
                        viewMenuRect.width(),
                        viewMenuRect.height(),
                        p,
                        hov
                    );
                } else {
                    OreUiPaint.raisedButton(
                        canvas,
                        viewMenuRect.x(),
                        viewMenuRect.y(),
                        viewMenuRect.width(),
                        viewMenuRect.height(),
                        p,
                        false,
                        hov,
                        false,
                        false
                    );
                }
                Component lbl = ChromeText.fit(canvas, viewMenuLabel(w < 520), Math.max(8, viewMenuRect.width() - 18));
                int contentW = ChromeText.width(canvas, lbl) + 10;
                int tx = viewMenuRect.x() + Math.max(5, (viewMenuRect.width() - contentW) / 2);
                int ty = open
                        ? ChromeText.centeredY(viewMenuRect.y(), viewMenuRect.height(), canvas)
                        : ChromeText.centeredButtonY(viewMenuRect.y(), viewMenuRect.height(), canvas);
                int ink = open ? p.focusRing() : hov ? p.textPrimary() : p.textSecondary();
                ChromeText.draw(canvas, lbl, tx, ty, ink);
                ChromeButtons.caretDown(
                    canvas,
                    tx + ChromeText.width(canvas, lbl) + 3,
                    viewMenuRect.y() + Math.max(3, (viewMenuRect.height() - (open ? 4 : 6)) / 2),
                    ink
                );
            }
        }
    }
}
