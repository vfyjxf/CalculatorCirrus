package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.PromptSubjectResolver;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPrompt;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.drag.DragConsumer;
import dev.vfyjxf.cloudlib.api.ui.drag.DragContext;
import dev.vfyjxf.cloudlib.api.ui.drag.DragProvider;
import dev.vfyjxf.cloudlib.api.ui.drag.DraggableElement;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingBottom;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeStretch;

/**
 * Right dock island: decision queue, step inspector and quick drop tabs.
 */
public class RightCoPilotDockWidget extends WidgetGroup<Widget> {

    public static final int tabHeight = 18;
    public static final int promptRowHeight = 18;
    public static final int dropZoneHeight = 32;
    public static final int machineStripHeight = 42;

    public enum Tab {
        prompts, inspector, quickDrop
    }

    private final CoPilotHost host;
    private final TabBar tabBar;
    private final WidgetGroup<Widget> promptPage;
    private final ScrollState promptScrollState;
    private final StepInspectorWidget inspector;
    private final WidgetGroup<Widget> dropPage;
    private final PromptCardWidget.PreviewListener previewListener;

    private @Nullable Runnable onCollapse;
    private Tab tab = Tab.prompts;
    private @Nullable CompiledRecipeGraph graph;
    private @Nullable String focusedPromptId;

    public RightCoPilotDockWidget(CoPilotHost host, PromptCardWidget.PreviewListener previewListener) {
        this.host = Objects.requireNonNull(host, "host");
        this.previewListener = Objects.requireNonNull(previewListener, "previewListener");
        useStyle(flexColumn(), gap(2), padding(3), sizeStretch(), minWidth(0), minHeight(0));

        this.tabBar = new TabBar(host, this::setTab, () -> {
            if (onCollapse != null) {
                onCollapse.run();
            }
        });
        tabBar.useStyle(heightOf(tabHeight), flexShrink(0));
        addWidget(tabBar);

        this.promptPage = new WidgetGroup<>();
        CirrusThemeTextures initialTextures = CirrusUiTheme.textures(this);
        this.promptScrollState = CirrusScroll.longVerticalState(14).smooth(true).smoothSpeed(0.35f)
                .trackTexture(initialTextures.scrollTrack()).thumbTexture(initialTextures.scrollThumb())
                .scrollbarWidth(5).minThumbSize(14).viewportInset(0, 0, 0, 0);
        promptPage.useEffect(UIEffects.scrollable(promptScrollState));
        promptPage.useStyle(flexColumn(), flexGrow(1), minWidth(0), minHeight(0), gap(4), paddingRight(6), paddingBottom(4));
        promptPage.onMount((scene, context, handle) -> CirrusUiTheme.bindThemeListener(promptPage, handle, () -> {
            CirrusThemeTextures textures = CirrusUiTheme.textures(promptPage);
            promptScrollState.trackTexture(textures.scrollTrack()).thumbTexture(textures.scrollThumb());
        }));
        addWidget(promptPage);

        this.inspector = new StepInspectorWidget(host);
        inspector.useStyle(flexGrow(1), minWidth(0), minHeight(0));
        addWidget(inspector);

        this.dropPage = new WidgetGroup<>();
        dropPage.useStyle(flexColumn(), flexGrow(1), minWidth(0), minHeight(0), gap(4));
        this.freeDropZone = new QuickDropZone(CoPilotDnd.DropTarget.freeSourceSlot, true);
        this.banDropZone = new QuickDropZone(CoPilotDnd.DropTarget.banSlot, false);
        this.planResourceStrip = new PlanResourceStrip();
        this.machineStrip = new MachineStrip();
        dropPage.addWidget(freeDropZone);
        dropPage.addWidget(banDropZone);
        dropPage.addWidget(planResourceStrip);
        dropPage.addWidget(machineStrip);
        addWidget(dropPage);

        onMouseReleased((input, ctx) -> {
            if (finishActiveDrag(input.mouseX(), input.mouseY())) {
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });

        setTab(Tab.prompts);
        rebuildPrompts();
    }

    private final QuickDropZone freeDropZone;
    private final QuickDropZone banDropZone;
    private final PlanResourceStrip planResourceStrip;
    private final MachineStrip machineStrip;
    private @Nullable ComponentStackKey externalDragResource;
    private @Nullable MachineEntry externalDragMachine;
    private @Nullable QuickDropZone externalHoveredZone;
    private @Nullable ComponentStackKey stripPressedResource;
    private @Nullable MachineEntry stripPressedMachine;
    private boolean stripDragging;
    private double stripPressSceneX;
    private double stripPressSceneY;
    private double stripDragSceneX;
    private double stripDragSceneY;
    private @Nullable QuickDropZone lastConsumedZone;
    private long lastConsumedAtMs;

    public boolean hoverExternalResourceDrag(double sceneX, double sceneY, @Nullable ComponentStackKey resource) {
        externalDragResource = resource;
        externalDragMachine = null;
        externalHoveredZone = null;
        if (resource == null || !visible() || tab != Tab.quickDrop) {
            return false;
        }
        if (freeDropZone.containsScenePoint(sceneX, sceneY)) {
            externalHoveredZone = freeDropZone;
            return true;
        }
        if (banDropZone.containsScenePoint(sceneX, sceneY)) {
            externalHoveredZone = banDropZone;
            return true;
        }
        return false;
    }

    public boolean hoverExternalMachineDrag(double sceneX, double sceneY, @Nullable MachineEntry machine) {
        externalDragResource = null;
        externalDragMachine = machine;
        externalHoveredZone = null;
        if (machine == null || machine.isEmpty() || !visible() || tab != Tab.quickDrop) {
            return false;
        }
        if (freeDropZone.containsScenePoint(sceneX, sceneY)) {
            externalHoveredZone = freeDropZone;
            return true;
        }
        if (banDropZone.containsScenePoint(sceneX, sceneY)) {
            externalHoveredZone = banDropZone;
            return true;
        }
        return false;
    }

    public boolean dropExternalResource(double sceneX, double sceneY, @Nullable ComponentStackKey resource) {
        boolean hit = hoverExternalResourceDrag(sceneX, sceneY, resource);
        QuickDropZone targetZone = externalHoveredZone;
        clearExternalResourceDrag();
        if (!hit || targetZone == null || resource == null) {
            return false;
        }
        boolean handled = targetZone.accepts(new DragPayload.ResourceDrag(resource, 1L, "quick-drop"));
        if (handled) {
            recordDropConsumed(targetZone);
            CirrusUiSound.playClickSound();
        }
        return handled;
    }

    public boolean dropExternalMachine(double sceneX, double sceneY, @Nullable MachineEntry machine) {
        boolean hit = hoverExternalMachineDrag(sceneX, sceneY, machine);
        QuickDropZone targetZone = externalHoveredZone;
        clearExternalResourceDrag();
        if (!hit || targetZone == null || machine == null || machine.isEmpty()) {
            return false;
        }
        boolean handled = targetZone.accepts(new DragPayload.MachineDrag(machine, 1));
        if (handled) {
            recordDropConsumed(targetZone);
            CirrusUiSound.playClickSound();
        }
        return handled;
    }

    public boolean finishActiveDrag(double sceneX, double sceneY) {
        if (stripPressedResource != null) {
            ComponentStackKey carried = stripPressedResource;
            boolean wasDragging = stripDragging;
            stripPressedResource = null;
            stripDragging = false;
            if (wasDragging) {
                return dropExternalResource(sceneX, sceneY, carried);
            }
            clearExternalResourceDrag();
            return false;
        }
        if (stripPressedMachine != null) {
            MachineEntry carried = stripPressedMachine;
            boolean wasDragging = stripDragging;
            stripPressedMachine = null;
            stripDragging = false;
            if (wasDragging) {
                return dropExternalMachine(sceneX, sceneY, carried);
            }
            clearExternalResourceDrag();
            return false;
        }
        return false;
    }

    public boolean isStripDragging() {
        return stripDragging;
    }

    public QuickDropZone freeDropZone() {
        return freeDropZone;
    }

    public QuickDropZone banDropZone() {
        return banDropZone;
    }

    public PlanResourceStrip planResourceStrip() {
        return planResourceStrip;
    }

    public MachineStrip machineStrip() {
        return machineStrip;
    }

    public void clearExternalResourceDrag() {
        externalDragResource = null;
        externalDragMachine = null;
        externalHoveredZone = null;
    }

    private void recordDropConsumed(QuickDropZone zone) {
        lastConsumedZone = zone;
        lastConsumedAtMs = OreUiMotion.nowMs();
    }

    @Override
    public void render(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.render(canvas, mouseX, mouseY, partialTicks);
        if (!stripDragging || (stripPressedResource == null && stripPressedMachine == null)) {
            return;
        }
        CirrusPalette palette = host.palette();
        FloatPos local = sceneToLocal(stripDragSceneX, stripDragSceneY);
        int gx = (int) Math.round(local.x()) - 11;
        int gy = (int) Math.round(local.y()) - 11;
        ComponentStackKey iconResource = stripPressedResource != null
                ? stripPressedResource
                : Objects.requireNonNull(stripPressedMachine).contentKey().orElse(null);
        Component label = stripPressedResource != null
                ? CoPilotPaint.resourceName(stripPressedResource)
                : CoPilotPaint.machineName(stripPressedMachine);
        int labelW = Math.min(110, ChromeText.width(canvas, label) + 8);
        int pillW = 24 + labelW;
        int pillH = 22;
        int px = Math.max(2, Math.min(Math.max(2, width() - pillW - 2), gx));
        int py = Math.max(2, Math.min(Math.max(2, height() - pillH - 2), gy));
        OreUiPaint.raisedCard(canvas, px, py, pillW, pillH, palette, true, true);
        if (iconResource != null) {
            CoPilotPaint.icon(canvas, CoPilotPaint.stackOf(iconResource, 1L), px + 3, py + 3, 16, palette);
        }
        ChromeText.drawFitted(
            canvas,
            label,
            px + 22,
            ChromeText.centeredY(py, pillH, canvas),
            Math.max(12, pillW - 26),
            palette.textPrimary()
        );
    }

    private @Nullable ComponentStackKey selectedStepResource() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        if (snapshot == null || snapshot.selectedStepIds().isEmpty()) {
            return null;
        }
        ProductionPlan plan = snapshot.selectedPlan().orElse(null);
        if (plan == null) {
            return null;
        }
        String firstId = snapshot.selectedStepIds().iterator().next();
        for (PlanStep step : plan.steps()) {
            if (firstId.equals(step.stepId())) {
                return primaryResourceOf(step);
            }
        }
        return null;
    }

    private @Nullable ComponentStackKey primaryResourceOf(PlanStep step) {
        return inspector.outputResource(step);
    }

    public RightCoPilotDockWidget onCollapse(@Nullable Runnable onCollapse) {
        this.onCollapse = onCollapse;
        this.tabBar.showCollapse = onCollapse != null;
        return this;
    }

    public CoPilotHost host() {
        return host;
    }

    public RightCoPilotDockWidget graph(@Nullable CompiledRecipeGraph graph) {
        this.graph = graph;
        this.inspector.graph(graph);
        return this;
    }

    public @Nullable CompiledRecipeGraph resolvedGraph() {
        if (graph != null) {
            return graph;
        }
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        return runtime == null ? null : runtime.currentGraph().orElse(null);
    }

    public Tab tab() {
        return tab;
    }

    public StepInspectorWidget inspector() {
        return inspector;
    }

    public WidgetGroup<Widget> promptPage() {
        return promptPage;
    }

    public RightCoPilotDockWidget setTab(Tab tab) {
        this.tab = Objects.requireNonNull(tab, "tab");
        promptPage.useStyle(tab == Tab.prompts ? displayFlex() : displayNone());
        inspector.useStyle(tab == Tab.inspector ? displayFlex() : displayNone());
        dropPage.useStyle(tab == Tab.quickDrop ? displayFlex() : displayNone());
        tabBar.tab = tab;
        if (tab == Tab.prompts) {
            rebuildPrompts();
        }
        return this;
    }

    public List<ResolvedPrompt> sortedPrompts() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        if (snapshot == null || snapshot.result() == null) {
            return List.of();
        }
        ProductionPlan plan = snapshot.selectedPlan().orElse(null);
        CompiledRecipeGraph resolved = resolvedGraph();
        if (resolved == null) {
            return List.of();
        }
        List<ResolvedPrompt> prompts = new ArrayList<>(
            PromptSubjectResolver.resolveAll(snapshot.result().decisionPrompts(), plan, resolved)
        );
        prompts.sort(Comparator.comparingDouble((ResolvedPrompt prompt) -> prompt.raw().leverageScore()).reversed());
        if (focusedPromptId != null) {
            prompts.sort(Comparator.comparingInt(prompt -> prompt.raw().promptId().equals(focusedPromptId) ? 0 : 1));
        }
        return List.copyOf(prompts);
    }

    public int promptCount() {
        return sortedPrompts().size();
    }

    public void rebuildPrompts() {
        List<Widget> existing = new ArrayList<>(promptPage.children());
        for (Widget widget : existing) {
            promptPage.remove(widget);
        }
        CompiledRecipeGraph resolved = resolvedGraph();
        inspector.graph(resolved);
        List<ResolvedPrompt> resolvedPrompts = sortedPrompts();
        for (ResolvedPrompt prompt : resolvedPrompts) {
            PromptCardWidget card = new PromptCardWidget(host, prompt, false, false);
            card.graph(resolved);
            card.previewListener(previewListener);
            promptPage.addWidget(card);
        }
        inspector.prompts(resolvedPrompts);
    }

    public boolean isEmptyQueue() {
        return promptCount() == 0;
    }

    /**
     * Switches to the Co-Pilot tab and pins the given decision prompt to the front of the queue; {@code null} clears the pin.
     */
    public void focusPrompt(@Nullable String promptId) {
        this.focusedPromptId = promptId;
        setTab(Tab.prompts);
    }

    /** Three-tab bar; the Co-Pilot tab carries an unhandled-count badge. */
    static final class TabBar extends Widget implements OreUiTooltipProvider {

        private static final int collapseButtonWidth = 18;
        private final @Nullable CoPilotHost host;
        private final Consumer<Tab> onPick;
        private final Runnable onCollapse;
        private final OreUiMotion.SmoothValue indicatorX = new OreUiMotion.SmoothValue();
        private final OreUiMotion.SmoothValue indicatorW = new OreUiMotion.SmoothValue();
        private Tab tab = Tab.prompts;
        private boolean showCollapse;

        TabBar(Consumer<Tab> onPick) {
            this(null, onPick, () -> {});
        }

        TabBar(@Nullable CoPilotHost host, Consumer<Tab> onPick, Runnable onCollapse) {
            this.host = host;
            this.onPick = onPick;
            this.onCollapse = onCollapse;
            onMouseClick((input, clickCount, context) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                Rect collapse = collapseRect();
                if (collapse != null && collapse.contains(local.x(), local.y())) {
                    onCollapse.run();
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
                Tab picked = null;
                for (Tab candidate : Tab.values()) {
                    if (tabRect(candidate).contains(local.x(), local.y())) {
                        picked = candidate;
                    }
                }
                if (picked == null) {
                    return EventDispatch.pass;
                }
                onPick.accept(picked);
                CirrusUiSound.playClickSound();
                context.consume();
                return EventDispatch.consumed;
            });
        }

        @Nullable
        Rect collapseRect() {
            if (!showCollapse || width() < 96) {
                return null;
            }
            return new Rect(width() - collapseButtonWidth, 0, collapseButtonWidth, Math.max(10, height()));
        }

        Rect tabRect(Tab candidate) {
            int reserve = collapseRect() != null ? collapseButtonWidth + 2 : 0;
            int avail = Math.max(72, width() - reserve);
            int third = Math.max(24, avail / 3);
            int w = candidate.ordinal() == 2 ? Math.max(24, avail - third * 2) : third - 1;
            return new Rect(candidate.ordinal() * third, 0, w, Math.max(10, height()));
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            Rect collapse = collapseRect();
            if (collapse != null && collapse.contains(localX, localY)) {
                return OreUiRichTooltip.builder("Collapse Co-Pilot Panel").badge("FOLD", OreUiRichTooltip.Tone.accent)
                        .text(
                            "Hides the right decisions & inspector dock to widen the canvas.",
                            OreUiRichTooltip.Tone.secondary
                        ).build();
            }
            return null;
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette palette = host != null ? host.palette() : CirrusUiTheme.palette(this);
            for (Tab candidate : Tab.values()) {
                Rect rect = tabRect(candidate);
                boolean selected = candidate == tab;
                boolean hover = rect.contains(mouseX, mouseY);
                if (selected) {
                    OreUiPaint
                            .raisedCard(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, hover, true);
                } else {
                    OreUiPaint.raisedButton(
                        canvas,
                        rect.x(),
                        rect.y(),
                        rect.width(),
                        rect.height(),
                        palette,
                        false,
                        hover,
                        false,
                        false
                    );
                }
                Component label = label(candidate);
                Component fitted = CoPilotPaint.fit(canvas, label, Math.max(0, rect.width() - 6));
                int textX = rect.x() + Math.max(3, (rect.width() - ChromeText.width(canvas, fitted)) / 2);
                CoPilotPaint.text(
                    canvas,
                    fitted,
                    textX,
                    ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
                    selected ? palette.textPrimary() : palette.textSecondary()
                );
            }
            Rect collapse = collapseRect();
            if (collapse != null) {
                boolean hover = collapse.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    collapse.x(),
                    collapse.y(),
                    collapse.width(),
                    collapse.height(),
                    palette,
                    false,
                    hover,
                    false,
                    false
                );
                ChromeButtons.doubleChevronRight(
                    canvas,
                    collapse.x() + (collapse.width() - 7) / 2,
                    collapse.y() + Math.max(2, (collapse.height() - 2 - 7) / 2),
                    hover ? palette.textPrimary() : palette.textSecondary()
                );
            }
            Rect active = tabRect(tab);
            int gx = Math.round(indicatorX.update(active.x() + 2, 22f));
            int gw = Math.max(8, Math.round(indicatorW.update(active.width() - 4, 22f)));
            canvas.fill(gx, active.bottom() - 2, gw, 2, palette.focusRing());
        }

        static Component label(Tab tab) {
            return switch (tab) {
                case prompts -> CirrusLangs.Workbench.Copilot.tabPrompts.get();
                case inspector -> CirrusLangs.Workbench.Copilot.tabInspector.get();
                case quickDrop -> CirrusLangs.Workbench.Copilot.tabDrop.get();
            };
        }
    }

    /** Live drop zone: free source / hard ban — displays dropped items & active effects inside the zone with 1-click removal. */
    public final class QuickDropZone extends Widget implements OreUiTooltipProvider {

        private static final int zoneHeaderH = 18;
        private static final int zoneRowH = 20;
        private static final int zoneFooterH = 15;
        private static final int maxVisibleZoneRows = 4;

        private record DroppedEntry(
            GuidanceRule rule,
            @Nullable ComponentStackKey iconKey,
            Component label,
            Component effectBadge,
            Rect rowRect,
            Rect removeRect
        ) {}

        private final CoPilotDnd.DropTarget target;
        private final boolean free;
        private final List<DroppedEntry> renderedEntries = new ArrayList<>();
        private int lastAppliedHeight = -1;

        QuickDropZone(CoPilotDnd.DropTarget target, boolean free) {
            this.target = target;
            this.free = free;
            setInteractive(true);
            syncHeight(0);
            onMouseReleased((input, ctx) -> {
                if (finishActiveDrag(input.mouseX(), input.mouseY())) {
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (externalDragResource != null) {
                    WorkbenchShellContext shell = CirrusUiTheme.shellContext(RightCoPilotDockWidget.this);
                    if (shell instanceof WorkbenchShellWidget shellWidget
                            && shellWidget.finishExternalDrags(input.mouseX(), input.mouseY())) {
                        ctx.consume();
                        return EventDispatch.consumed;
                    }
                }
                return EventDispatch.pass;
            });
            onMouseClick((input, clickCount, ctx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                int lx = (int) local.x();
                int ly = (int) local.y();
                for (DroppedEntry entry : renderedEntries) {
                    if (entry.removeRect().contains(lx, ly)) {
                        host.relaxConflictRules(List.of(entry.rule().ruleId()));
                        CirrusUiSound.playClickSound();
                        ctx.consume();
                        return EventDispatch.consumed;
                    }
                }
                if (stripPressedResource != null
                        || stripPressedMachine != null
                        || (lastConsumedAtMs > 0L && OreUiMotion.nowMs() - lastConsumedAtMs < 150L)) {
                    return EventDispatch.pass;
                }
                ComponentStackKey selected = selectedStepResource();
                if (selected != null && accepts(new DragPayload.ResourceDrag(selected, 1L, "quick-drop-click"))) {
                    recordDropConsumed(QuickDropZone.this);
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
            onMount((scene, context, handle) -> {
                DragConsumer consumer = new DragConsumer() {
                    @Override
                    public boolean consume(DraggableElement<?> element, DragContext context) {
                        if (!(element instanceof CoPilotDnd.Carried carried)) {
                            return false;
                        }
                        if (carried.cancelled()) {
                            return true;
                        }
                        if (!CoPilotDnd.consumable(carried.payload(), false, context.getCurrent(), absoluteBounds())) {
                            return false;
                        }
                        boolean handled = CoPilotDnd
                                .accept(host, target, carried.payload(), CoPilotDnd.DropContext.of(false)).handled();
                        if (handled) {
                            recordDropConsumed(QuickDropZone.this);
                        }
                        return handled;
                    }
                };
                CoPilotDnd.attachConsumer(scene, QuickDropZone.this, handle, consumer);
            });
        }

        private void syncHeight(int entryCount) {
            int visible = Math.min(maxVisibleZoneRows, entryCount);
            int desired = visible == 0
                    ? Math.max(dropZoneHeight, 44)
                    : zoneHeaderH + visible * zoneRowH + zoneFooterH + 6;
            if (lastAppliedHeight != desired) {
                lastAppliedHeight = desired;
                useStyle(heightOf(desired), flexShrink(0));
            }
        }

        private List<GuidanceRule> matchingRules() {
            List<GuidanceRule> out = new ArrayList<>();
            for (GuidanceRule rule : host.snapshot().activeSessionRules()) {
                if (free) {
                    if (rule instanceof ResourceRule rr
                            && (rr.treatAsFreeBaseSource() || rr.strength() != RuleStrength.hardBan)) {
                        out.add(rule);
                    } else
                        if (rule instanceof MachineRule mr
                                && mr.strength() != RuleStrength.hardBan
                                && mr.willingToBuildNew()) {
                                    out.add(rule);
                                }
                } else {
                    if (rule instanceof ResourceRule rr && rr.strength() == RuleStrength.hardBan) {
                        out.add(rule);
                    } else if (rule instanceof MachineRule mr && mr.strength() == RuleStrength.hardBan) {
                        out.add(rule);
                    } else if (rule instanceof RecipeRule rec && rec.strength() == RuleStrength.hardBan) {
                        out.add(rule);
                    }
                }
            }
            return out;
        }

        private @Nullable ComponentStackKey iconForRule(GuidanceRule rule) {
            if (rule instanceof ResourceRule rr) {
                return rr.resource();
            }
            if (rule instanceof MachineRule mr) {
                return mr.machine().contentKey().orElse(null);
            }
            if (rule instanceof RecipeRule rec) {
                CompiledRecipeGraph g = CirrusPlanningRuntime.shared().flatMap(CirrusPlanningRuntime::currentGraph)
                        .orElse(null);
                if (g != null) {
                    CompiledRecipe compiled = g.recipeByKey(rec.recipeKey()).orElse(null);
                    if (compiled != null && !compiled.outputs().isEmpty()) {
                        return compiled.outputs().get(0).resource();
                    }
                }
            }
            return null;
        }

        private Component labelForRule(GuidanceRule rule) {
            if (rule instanceof ResourceRule rr) {
                return CoPilotPaint.resourceName(rr.resource());
            }
            if (rule instanceof MachineRule mr) {
                return CoPilotPaint.machineName(mr.machine());
            }
            if (rule instanceof RecipeRule rec) {
                ComponentStackKey out = iconForRule(rule);
                if (out != null) {
                    return CoPilotPaint.resourceName(out);
                }
                return Component.literal(CoPilotPaint.humanizeId(rec.recipeKey().shortString()));
            }
            return Component.literal(CoPilotPaint.humanizeId(rule.ruleId()));
        }

        private Component effectBadgeForRule(GuidanceRule rule) {
            if (rule instanceof ResourceRule rr) {
                return rr.strength() == RuleStrength.hardBan
                        ? CirrusLangs.Workbench.Copilot.dropEffectBan.get()
                        : CirrusLangs.Workbench.Copilot.dropEffectFree.get();
            }
            if (rule instanceof MachineRule mr) {
                return mr.strength() == RuleStrength.hardBan
                        ? CirrusLangs.Workbench.Copilot.dropEffectBan.get()
                        : CirrusLangs.Workbench.Copilot.dropEffectOwned.get();
            }
            return CirrusLangs.Workbench.Copilot.dropEffectBan.get();
        }

        boolean containsScenePoint(double sceneX, double sceneY) {
            Rect abs = absoluteBounds();
            return abs.width() > 0 && abs.height() > 0 && abs.contains(sceneX, sceneY);
        }

        public CoPilotDnd.DropTarget dropTarget() {
            return target;
        }

        public boolean accepts(DragPayload payload) {
            return CoPilotDnd.accept(host, target, payload, CoPilotDnd.DropContext.of(false)).handled();
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            for (DroppedEntry entry : renderedEntries) {
                if (entry.rowRect().contains((int) localX, (int) localY)) {
                    OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(entry.label())
                            .badge(
                                entry.effectBadge().getString(),
                                free ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.warning
                            )
                            .keyValue(
                                Component.literal("Scope"),
                                Component.literal(
                                    entry.rule().scope() == GuidanceScope.worldSave ? "World Save" : "Session"
                                ),
                                OreUiRichTooltip.Tone.accent
                            ).footer("Click \u00d7 to remove this rule and replan");
                    if (entry.iconKey() != null) {
                        builder.icon(entry.iconKey());
                    }
                    return builder.build();
                }
            }
            ComponentStackKey selected = selectedStepResource();
            OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(
                free ? "Free Resource Zone" : "Hard Ban Resource Zone"
            ).badge(free ? "FREE" : "BAN", free ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.warning).text(
                free
                        ? "Drag any DAG step card, plan resource chip, or machine chip here to treat as free/owned."
                        : "Drag any DAG step card, plan resource chip, or machine chip here to hard-ban it.",
                OreUiRichTooltip.Tone.secondary
            );
            if (selected != null) {
                builder.keyValue(
                    Component.literal("Selected Step"),
                    CoPilotPaint.resourceName(selected),
                    OreUiRichTooltip.Tone.primary
                ).footer("Click to apply immediately to selected step");
            } else {
                builder.footer("Drag from DAG or Plan Items below");
            }
            return builder.build();
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            List<GuidanceRule> rules = matchingRules();
            syncHeight(rules.size());
            CirrusPalette palette = host.palette();
            int width = width();
            int height = height();
            renderedEntries.clear();
            if (width <= 0 || height <= 0) {
                return;
            }
            boolean dragHover = externalHoveredZone == this
                    && (externalDragResource != null || externalDragMachine != null);
            boolean mouseHover = mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height;
            long flashElapsed = lastConsumedZone == this ? Math.max(0L, OreUiMotion.nowMs() - lastConsumedAtMs) : 9999L;
            float flashStrength = flashElapsed < 420L ? 1.0f - (flashElapsed / 420.0f) : 0.0f;
            ComponentStackKey selected = selectedStepResource();
            int ink = free ? palette.costS() : palette.ruleHardBan();
            int fill;
            if (dragHover) {
                fill = OreUiPaint.withAlpha(ink, OreUiPaint.isDark(palette) ? 0.24f : 0.16f);
            } else if (flashStrength > 0.01f) {
                fill = OreUiPaint.withAlpha(ink, (OreUiPaint.isDark(palette) ? 0.28f : 0.20f) * flashStrength);
            } else {
                fill = mouseHover && selected != null && rules.isEmpty()
                        ? palette.cardSurfaceHover()
                        : palette.recessedSurface();
            }
            canvas.fill(0, 0, width, height, fill);
            if (dragHover || flashStrength > 0.05f) {
                canvas.strokeRect(
                    0,
                    0,
                    width,
                    height,
                    dragHover ? ink : OreUiPaint.withAlpha(ink, 0.55f + 0.45f * flashStrength),
                    2
                );
            } else {
                CoPilotPaint
                        .dashedRect(canvas, 0, 0, width, height, mouseHover ? ink : OreUiPaint.withAlpha(ink, 0.78f));
            }

            // Header row: title + live active count pill
            Component hint = free
                    ? CirrusLangs.Workbench.Copilot.dropFreeHint.get()
                    : CirrusLangs.Workbench.Copilot.dropBanHint.get();
            int headerRight = width - 6;
            if (!rules.isEmpty()) {
                Component countComp = Component.literal(Integer.toString(rules.size()));
                int cw = ChromeText.width(canvas, countComp) + 8;
                OreUiPaint.tagPill(canvas, headerRight - cw, 3, 12, countComp, ink, palette);
                headerRight -= cw + 4;
            }
            CoPilotPaint.text(canvas, CoPilotPaint.fit(canvas, hint, Math.max(12, headerRight - 6)), 6, 5, ink);

            // Live dropped items inside the zone
            int visible = Math.min(maxVisibleZoneRows, rules.size());
            int cursorY = zoneHeaderH + 2;
            for (int index = 0; index < visible; index++) {
                GuidanceRule rule = rules.get(index);
                Rect rowRect = new Rect(5, cursorY, Math.max(24, width - 10), zoneRowH - 2);
                boolean rowHov = rowRect.contains(mouseX, mouseY);
                OreUiPaint.raisedCard(
                    canvas,
                    rowRect.x(),
                    rowRect.y(),
                    rowRect.width(),
                    rowRect.height(),
                    palette,
                    false,
                    rowHov
                );
                ComponentStackKey iconKey = iconForRule(rule);
                if (iconKey != null) {
                    CoPilotPaint.icon(
                        canvas,
                        CoPilotPaint.stackOf(iconKey, 1L),
                        rowRect.x() + 3,
                        rowRect.y() + 2,
                        14,
                        palette
                    );
                }
                Rect removeRect = new Rect(rowRect.right() - 15, rowRect.y() + 2, 13, rowRect.height() - 4);
                boolean remHov = removeRect.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    removeRect.x(),
                    removeRect.y(),
                    removeRect.width(),
                    removeRect.height(),
                    palette,
                    false,
                    remHov,
                    false,
                    false
                );
                ChromeText.draw(
                    canvas,
                    Component.literal("\u00d7"),
                    removeRect.x() + 4,
                    ChromeText.centeredY(removeRect.y(), removeRect.height(), canvas),
                    remHov ? palette.ruleHardBan() : palette.textMuted()
                );

                Component effectBadge = effectBadgeForRule(rule);
                int badgeW = ChromeText.width(canvas, effectBadge) + 8;
                int badgeX = removeRect.x() - badgeW - 3;
                int nameLeft = rowRect.x() + (iconKey != null ? 20 : 5);
                if (badgeX > nameLeft + 26) {
                    OreUiPaint.tagPill(canvas, badgeX, rowRect.y() + 3, 12, effectBadge, ink, palette);
                } else {
                    badgeX = removeRect.x() - 3;
                }
                Component label = labelForRule(rule);
                CoPilotPaint.text(
                    canvas,
                    CoPilotPaint.fit(canvas, label, Math.max(12, badgeX - nameLeft - 3)),
                    nameLeft,
                    ChromeText.centeredY(rowRect.y(), rowRect.height(), canvas),
                    palette.textPrimary()
                );
                renderedEntries.add(new DroppedEntry(rule, iconKey, label, effectBadge, rowRect, removeRect));
                cursorY += zoneRowH;
            }

            // Bottom drop cue
            Component sub;
            if (dragHover && externalDragResource != null) {
                sub = Component.literal("Release: ").append(CoPilotPaint.resourceName(externalDragResource));
            } else if (dragHover && externalDragMachine != null) {
                sub = Component.literal("Release: ").append(CoPilotPaint.machineName(externalDragMachine));
            } else if (rules.size() > visible) {
                sub = Component.literal("+" + (rules.size() - visible) + " more active rules");
            } else if (selected != null) {
                sub = Component.literal("Click: ").append(CoPilotPaint.resourceName(selected));
            } else {
                sub = Component.literal("Drag DAG card or item below");
            }
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, sub, Math.max(12, width - 12)),
                6,
                Math.max(zoneHeaderH + 2, height - 13),
                dragHover ? palette.textPrimary() : palette.textMuted()
            );
        }
    }

    /** Draggable and clickable resource chips extracted from the active plan so Quick Drop always has immediate drag sources. */
    public final class PlanResourceStrip extends Widget implements OreUiTooltipProvider {

        public static final int chipSize = 22;
        private static final int headerH = 12;

        PlanResourceStrip() {
            setInteractive(true);
            useStyle(heightOf(headerH + chipSize * 2 + 12), flexShrink(0));
            onMouseClicked((input, ctx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                ComponentStackKey hit = resourceAt(local.x(), local.y());
                if (hit == null) {
                    return EventDispatch.pass;
                }
                stripPressedResource = hit;
                stripDragging = false;
                stripPressSceneX = input.mouseX();
                stripPressSceneY = input.mouseY();
                stripDragSceneX = input.mouseX();
                stripDragSceneY = input.mouseY();
                ctx.consume();
                return EventDispatch.consumed;
            });
            onMouseDragged((input, deltaX, deltaY, ctx) -> {
                if (stripPressedResource == null) {
                    return EventDispatch.pass;
                }
                stripDragSceneX = input.mouseX();
                stripDragSceneY = input.mouseY();
                double dist = Math.hypot(stripDragSceneX - stripPressSceneX, stripDragSceneY - stripPressSceneY);
                if (!stripDragging && dist >= 3.0) {
                    stripDragging = true;
                }
                if (stripDragging) {
                    hoverExternalResourceDrag(stripDragSceneX, stripDragSceneY, stripPressedResource);
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
            onMouseReleased((input, ctx) -> {
                if (stripPressedResource == null) {
                    return EventDispatch.pass;
                }
                boolean consumed = finishActiveDrag(input.mouseX(), input.mouseY());
                if (consumed) {
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
        }

        public List<ComponentStackKey> resources() {
            WorkbenchUiSnapshot snapshot = host.snapshot();
            if (snapshot == null) {
                return List.of();
            }
            Set<ComponentStackKey> keys = new LinkedHashSet<>();
            for (PlanStep step : snapshot.selectedPlan().map(ProductionPlan::steps).orElse(List.of())) {
                ComponentStackKey res = primaryResourceOf(step);
                if (res != null) {
                    keys.add(res);
                }
            }
            return List.copyOf(keys);
        }

        public @Nullable ComponentStackKey resourceAt(double localX, double localY) {
            List<ComponentStackKey> list = resources();
            for (int index = 0; index < list.size(); index++) {
                Rect r = chipRect(index);
                if (r != null && r.contains(localX, localY)) {
                    return list.get(index);
                }
            }
            return null;
        }

        private @Nullable Rect chipRect(int index) {
            int cols = Math.max(1, (Math.max(chipSize + 8, width()) - 8 + 3) / (chipSize + 3));
            int row = index / cols;
            if (row >= 2) {
                return null;
            }
            int col = index % cols;
            return new Rect(4 + col * (chipSize + 3), headerH + 3 + row * (chipSize + 3), chipSize, chipSize);
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            if (stripDragging) {
                return null;
            }
            ComponentStackKey res = resourceAt(localX, localY);
            if (res == null) {
                return null;
            }
            return OreUiRichTooltip.builder(CoPilotPaint.resourceName(res)).icon(res)
                    .badge("DRAG", OreUiRichTooltip.Tone.accent)
                    .text(
                        "Drag this single item onto Free or Ban zone above to create a rule.",
                        OreUiRichTooltip.Tone.secondary
                    ).build();
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette palette = host.palette();
            int w = width();
            int h = height();
            if (w <= 0 || h <= 0) {
                return;
            }
            OreUiPaint.searchField(canvas, 0, 0, w, h, palette, false);
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, Component.literal("Plan Items (Drag Above)"), Math.max(12, w - 8)),
                4,
                2,
                palette.textMuted()
            );
            List<ComponentStackKey> list = resources();
            if (list.isEmpty()) {
                CoPilotPaint.text(
                    canvas,
                    CoPilotPaint.fit(canvas, Component.literal("No active plan items"), Math.max(12, w - 8)),
                    4,
                    headerH + 8,
                    palette.textDisabled()
                );
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                Rect r = chipRect(i);
                if (r == null || r.right() > w - 2) {
                    continue;
                }
                ComponentStackKey item = list.get(i);
                boolean isDraggedSource = stripDragging && item.equals(stripPressedResource);
                boolean hov = !stripDragging && r.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    r.x(),
                    r.y(),
                    r.width(),
                    r.height(),
                    palette,
                    isDraggedSource,
                    hov,
                    false,
                    false
                );
                if (!isDraggedSource) {
                    CoPilotPaint.icon(canvas, CoPilotPaint.stackOf(item, 1L), r.x() + 3, r.y() + 2, 16, palette);
                }
            }
        }
    }

    /** Quick-drag strip of the machines the plan touches (MachineDrag). */
    public final class MachineStrip extends Widget {

        public static final int chipWidth = 26;

        MachineStrip() {
            setInteractive(true);
            useStyle(heightOf(machineStripHeight), flexShrink(0));
            onMouseClicked((input, ctx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                MachineEntry hit = machineAt(local.x(), local.y());
                if (hit == null) {
                    return EventDispatch.pass;
                }
                stripPressedMachine = hit;
                stripPressedResource = null;
                stripDragging = false;
                stripPressSceneX = input.mouseX();
                stripPressSceneY = input.mouseY();
                stripDragSceneX = input.mouseX();
                stripDragSceneY = input.mouseY();
                ctx.consume();
                return EventDispatch.consumed;
            });
            onMouseDragged((input, deltaX, deltaY, ctx) -> {
                if (stripPressedMachine == null) {
                    return EventDispatch.pass;
                }
                stripDragSceneX = input.mouseX();
                stripDragSceneY = input.mouseY();
                double dist = Math.hypot(stripDragSceneX - stripPressSceneX, stripDragSceneY - stripPressSceneY);
                if (!stripDragging && dist >= 3.0) {
                    stripDragging = true;
                }
                if (stripDragging) {
                    hoverExternalMachineDrag(stripDragSceneX, stripDragSceneY, stripPressedMachine);
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
            onMouseReleased((input, ctx) -> {
                if (stripPressedMachine == null) {
                    return EventDispatch.pass;
                }
                boolean consumed = finishActiveDrag(input.mouseX(), input.mouseY());
                if (consumed) {
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
            onMount(
                (scene, context, handle) -> CoPilotDnd
                        .attachProvider(scene, MachineStrip.this, handle, new MachineDragProvider())
            );
        }

        public List<MachineEntry> machines() {
            WorkbenchUiSnapshot snapshot = host.snapshot();
            if (snapshot == null) {
                return List.of();
            }
            Set<MachineEntry> machines = new LinkedHashSet<>();
            for (PlanStep step : snapshot.selectedPlan().map(ProductionPlan::steps).orElse(List.of())) {
                if (step instanceof ProvisionMachineStep provision) {
                    machines.add(provision.machine());
                } else if (step instanceof CraftStep craft) {
                    craft.chosenMachine().ifPresent(machines::add);
                }
            }
            return List.copyOf(machines);
        }

        public @Nullable MachineEntry machineAt(double localX, double localY) {
            List<MachineEntry> machines = machines();
            for (int index = 0; index < machines.size(); index++) {
                if (chipRect(index).contains(localX, localY)) {
                    return machines.get(index);
                }
            }
            return null;
        }

        public Rect chipRect(int index) {
            return new Rect(4 + index * (chipWidth + 2), 14, chipWidth, Math.max(12, height() - 18));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette palette = host.palette();
            int width = width();
            int height = height();
            if (width <= 0 || height <= 0) {
                return;
            }
            OreUiPaint.searchField(canvas, 0, 0, width, height, palette, false);
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, Component.literal("Plan Machines (Drag)"), Math.max(12, width - 8)),
                4,
                2,
                palette.textMuted()
            );
            List<MachineEntry> machines = machines();
            for (int index = 0; index < machines.size(); index++) {
                Rect rect = chipRect(index);
                if (rect.right() > width) {
                    return;
                }
                MachineEntry machine = machines.get(index);
                boolean isDraggedSource = stripDragging && machine.equals(stripPressedMachine);
                boolean hover = !stripDragging && rect.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    palette,
                    isDraggedSource,
                    hover,
                    false,
                    false
                );
                if (!isDraggedSource) {
                    OreUiPaint
                            .slot(canvas, rect.x() + 3, rect.y() + Math.max(1, (rect.height() - 18) / 2), 18, palette);
                    ComponentStackKey iconKey = machine.contentKey().orElse(null);
                    if (iconKey != null) {
                        CoPilotPaint.icon(
                            canvas,
                            CoPilotPaint.stackOf(iconKey, 1L),
                            rect.x() + 4,
                            rect.y() + Math.max(2, (rect.height() - 16) / 2),
                            16,
                            palette
                        );
                    }
                }
            }
        }

        final class MachineDragProvider implements DragProvider {

            @Override
            public boolean draggable(@Nullable Scene dragScene, InputContext input, DragContext dragContext) {
                return parent() != null && machineAt(localX(input), localY(input)) != null;
            }

            @Override
            public @Nullable DraggableElement<?> getDraggableElement(
                @Nullable Scene dragScene,
                InputContext input,
                DragContext dragContext
            ) {
                if (parent() == null) {
                    return null;
                }
                MachineEntry machine = machineAt(localX(input), localY(input));
                if (machine == null) {
                    return null;
                }
                return CoPilotDnd.Carried.of(MachineStrip.this, new DragPayload.MachineDrag(machine, 1));
            }

            private double localX(InputContext input) {
                return input.mousePos().x() - absolutePos().x();
            }

            private double localY(InputContext input) {
                return input.mousePos().y() - absolutePos().y();
            }
        }
    }
}
