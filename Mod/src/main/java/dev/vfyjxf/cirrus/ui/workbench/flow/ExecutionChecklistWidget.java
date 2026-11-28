package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusPlanBookmarkStore;
import dev.vfyjxf.cirrus.internal.integration.jei.JeiPlanBookmarkBridge;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.event.WidgetEvent;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.ui.widget.InfoBarWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Execution rehearsal checklist: four-stage step cards, an execution-ready badge, local check progress and the send-to-execution primary button.
 */
public class ExecutionChecklistWidget extends CompositeWidget<Widget> implements OreUiTooltipProvider {

    public static final String summaryKey = "cirrus.ui.workbench.checklist.summary";
    public static final String sendKey = "cirrus.ui.workbench.topbar.send_execution";
    public static final String closeOnSendKey = "cirrus.ui.workbench.checklist.close_on_send";
    public static final String phasePrepareKey = "cirrus.ui.workbench.checklist.phase.prepare";
    public static final String phaseBuildKey = "cirrus.ui.workbench.checklist.phase.build";
    public static final String phaseCraftKey = "cirrus.ui.workbench.checklist.phase.craft";
    public static final String phaseFinalKey = "cirrus.ui.workbench.checklist.phase.final";

    public static final int headerHeight = 30;
    public static final int cardHeight = 33;
    public static final int phaseHeaderHeight = 18;
    public static final int checkboxSize = 12;
    public static final int badgeHeight = 13;
    public static final int footerHeight = 28;
    public static final int primaryButtonWidth = 160;
    public static final int primaryButtonHeight = 18;
    public static final int contentPadding = 8;

    public enum ExecutionReadiness {
        autoReady, guidedReady, manual
    }

    @FunctionalInterface
    public interface ReadinessProbe {
        ExecutionReadiness readinessOf(PlanStep step);
    }

    @FunctionalInterface
    public interface SendHandler {
        void send(ProductionPlan plan, boolean closeOnSend);
    }

    public record ChecklistSummary(int recipeBatches, int autoSupported, int recipeSteps, int preTasks) {

        public int guidedOrManual() {
            return Math.max(0, recipeSteps - autoSupported);
        }
    }

    public record PhaseGroup(int phaseIndex, List<PlanStep> steps) {

        public PhaseGroup {
            steps = List.copyOf(steps);
        }
    }

    private record Line(int phaseIndex, @Nullable PlanStep step) {}

    private record Hotspot(int x, int y, int width, int height, Runnable action) {

        boolean contains(double localX, double localY) {
            return localX >= x && localX <= x + width && localY >= y && localY <= y + height;
        }
    }

    private final FlowViewHost host;
    private final InfoBarWidget completionBar;
    private final Set<String> checkedStepIds = new TreeSet<>();
    private final Map<String, ExecutionReadiness> readinessCache = new TreeMap<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<StepTipRegion> tipRegions = new ArrayList<>();

    private record StepTipRegion(int x, int y, int width, int height, PlanStep step, boolean checked) {
        boolean contains(double localX, double localY) {
            return localX >= x && localX <= x + width && localY >= y && localY <= y + height;
        }
    }

    public enum StepFilter {
        all, remaining, done, autoOnly, manualOnly
    }

    private ReadinessProbe probe = ProbeSources::readinessOf;
    private @Nullable SendHandler sendHandler;
    private @Nullable CompiledRecipeGraph graph;
    private @Nullable ProductionPlan cachedPlan;
    private List<PhaseGroup> groups = List.of();
    private List<Line> lines = List.of();
    private ChecklistSummary summary = new ChecklistSummary(0, 0, 0, 0);
    private boolean closeOnSend = true;
    private StepFilter filter = StepFilter.all;
    private boolean filterMenuOpen;
    private final List<Hotspot> popupHotspots = new ArrayList<>();
    private int popupX;
    private int popupY;
    private int popupW;
    private int popupH;
    private float targetScrollPx;
    private final OreUiMotion.SmoothValue smoothScrollPx = new OreUiMotion.SmoothValue();

    public ExecutionChecklistWidget(FlowViewHost host) {
        this(host, null);
    }

    public ExecutionChecklistWidget(FlowViewHost host, @Nullable SendHandler sendHandler) {
        this.host = Objects.requireNonNull(host, "host");
        this.sendHandler = sendHandler;
        useStyle(UIStyles.sizeFull());
        setTickable(true);
        completionBar = addWidget(
            InfoBarWidget.segmented(InfoBarWidget.Segment.of(this::completionFraction, InfoBarWidget.styleOk))
        );
        completionBar.addStyleClass("cirrus-meter");
        onMount((scene, context, handle) -> refresh());
        onEvent(WidgetEvent.onSizeChanged, (size, context) -> relayout());
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
            if (scrollY == 0.0) {
                return EventDispatch.pass;
            }
            float maxScroll = maxScrollPx();
            if (maxScroll <= 0.5f) {
                return EventDispatch.pass;
            }
            float next = Math.max(0f, Math.min(maxScroll, targetScrollPx - (float) (scrollY * 38.0)));
            if (Math.abs(next - targetScrollPx) < 0.01f) {
                return EventDispatch.pass;
            }
            targetScrollPx = next;
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            if (filterMenuOpen) {
                for (Hotspot hotspot : popupHotspots) {
                    if (hotspot.contains(local.x(), local.y())) {
                        hotspot.action().run();
                        CirrusUiSound.playClickSound();
                        context.consume();
                        return EventDispatch.consumed;
                    }
                }
                if (!ChromeText.hit(local.x(), local.y(), popupX, popupY, popupW, popupH)) {
                    filterMenuOpen = false;
                }
                context.consume();
                return EventDispatch.consumed;
            }
            for (Hotspot hotspot : hotspots) {
                if (hotspot.contains(local.x(), local.y())) {
                    hotspot.action().run();
                    context.consume();
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        for (int i = tipRegions.size() - 1; i >= 0; i--) {
            StepTipRegion region = tipRegions.get(i);
            if (region.contains(localX, localY)) {
                PlanStep step = region.step();
                ComponentStackKey icon = iconResourceOf(step);
                ExecutionReadiness readiness = readinessOf(step);
                OreUiRichTooltip.Builder builder = OreUiRichTooltip
                        .builder(
                            icon != null
                                    ? CoPilotPaint.resourceName(icon)
                                    : Component.literal(CoPilotPaint.humanizeId(step.stepId()))
                        ).icon(icon)
                        .badge(
                            region.checked() ? "DONE" : DagRichTooltips.kindShort(step),
                            region.checked() ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
                        )
                        .pills(
                            OreUiRichTooltip.Pill.of(
                                "Work " + WorkbenchFormatters.formatSeconds(step.stepCost().humanActiveSeconds()),
                                OreUiRichTooltip.Tone.costH
                            ),
                            OreUiRichTooltip.Pill.of(
                                "Wait " + WorkbenchFormatters.formatSeconds(step.stepCost().passiveWaitSeconds()),
                                OreUiRichTooltip.Tone.costP
                            )
                        )
                        .keyValue(
                            Component.literal("Readiness"),
                            Component.literal(CoPilotPaint.humanizeId(readiness.name())),
                            readiness == ExecutionReadiness.autoReady
                                    ? OreUiRichTooltip.Tone.positive
                                    : OreUiRichTooltip.Tone.warning
                        ).footer("Click checkbox to mark done \u00b7 Click card to focus step");
                return builder.build();
            }
        }
        return null;
    }

    //region data

    public ExecutionChecklistWidget setProbe(@Nullable ReadinessProbe probe) {
        this.probe = probe == null ? ProbeSources::readinessOf : probe;
        rebuild();
        return this;
    }

    public ExecutionChecklistWidget setSendHandler(@Nullable SendHandler handler) {
        this.sendHandler = handler;
        return this;
    }

    public ExecutionChecklistWidget setGraph(@Nullable CompiledRecipeGraph graph) {
        this.graph = graph;
        return this;
    }

    private @Nullable CompiledRecipeGraph resolvedGraph() {
        if (graph != null) {
            return graph;
        }
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        return runtime == null ? null : runtime.currentGraph().orElse(null);
    }

    public void refresh() {
        ProductionPlan plan = host.snapshot().selectedPlan().orElse(null);
        cachedPlan = plan;
        rebuild();
        relayout();
    }

    @Override
    public void tick() {
        super.tick();
        syncPlanIfChanged();
    }

    private void syncPlanIfChanged() {
        ProductionPlan latest = host.snapshot().selectedPlan().orElse(null);
        if (!Objects.equals(latest, cachedPlan)) {
            cachedPlan = latest;
            rebuild();
            relayout();
        }
    }

    public @Nullable ProductionPlan plan() {
        return cachedPlan;
    }

    public List<PhaseGroup> phaseGroups() {
        return groups;
    }

    public ChecklistSummary summary() {
        return summary;
    }

    public Component summaryText() {
        return Component.translatable(
            summaryKey,
            String.valueOf(summary.recipeBatches()),
            String.valueOf(summary.autoSupported()),
            String.valueOf(summary.recipeSteps()),
            String.valueOf(summary.preTasks())
        );
    }

    public boolean closeOnSend() {
        return closeOnSend;
    }

    public void setCloseOnSend(boolean closeOnSend) {
        this.closeOnSend = closeOnSend;
    }

    public boolean canSend() {
        return cachedPlan != null && sendHandler != null;
    }

    public boolean isChecked(@Nullable String stepId) {
        return stepId != null && checkedStepIds.contains(stepId);
    }

    public void toggleChecked(@Nullable String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return;
        }
        if (!checkedStepIds.remove(stepId)) {
            checkedStepIds.add(stepId);
        }
        if (cachedPlan != null) {
            CirrusPlanBookmarkStore.saveCheckedSteps(CirrusPlanBookmarkStore.planKeyOf(cachedPlan), checkedStepIds);
        }
    }

    public StepFilter filter() {
        return filter;
    }

    public void setFilter(StepFilter next) {
        if (next != null && next != filter) {
            filter = next;
            targetScrollPx = 0f;
            smoothScrollPx.snap(0f, System.nanoTime());
        }
    }

    public int checkedCount() {
        int count = 0;
        for (Line line : lines) {
            if (line.step() != null && checkedStepIds.contains(line.step().stepId())) {
                count++;
            }
        }
        return count;
    }

    public int cardCount() {
        int count = 0;
        for (Line line : lines) {
            if (line.step() != null) {
                count++;
            }
        }
        return count;
    }

    public double completionFraction() {
        int total = cardCount();
        return total <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, checkedCount() / (double) total));
    }

    public boolean requestSend() {
        if (!canSend() || sendHandler == null || cachedPlan == null) {
            return false;
        }
        sendHandler.send(cachedPlan, closeOnSend);
        host.toast(CirrusLangs.Workbench.Topbar.sentToast);
        return true;
    }

    public ExecutionReadiness readinessOf(PlanStep step) {
        if (step == null) {
            return ExecutionReadiness.manual;
        }
        ExecutionReadiness cached = readinessCache.get(step.stepId());
        return cached == null ? computeReadiness(step) : cached;
    }

    private ExecutionReadiness computeReadiness(PlanStep step) {
        if (step == null) {
            return ExecutionReadiness.manual;
        }
        return switch (step) {
            case CraftStep craft -> probe.readinessOf(craft);
            case ConvertStep convert -> probe.readinessOf(convert);
            default -> ExecutionReadiness.manual;
        };
    }

    public static LangEntry badgeKey(ExecutionReadiness readiness) {
        return switch (readiness) {
            case autoReady -> CirrusLangs.Workbench.Checklist.probeAuto;
            case guidedReady -> CirrusLangs.Workbench.Checklist.probeGuide;
            case manual -> CirrusLangs.Workbench.Checklist.probeManual;
        };
    }

    public static int badgeColor(CirrusPalette palette, ExecutionReadiness readiness) {
        Objects.requireNonNull(palette, "palette");
        return switch (readiness == null ? ExecutionReadiness.manual : readiness) {
            case autoReady -> palette.costS();
            case guidedReady -> palette.convertStep().accent();
            case manual -> palette.costH();
        };
    }

    public static LangEntry phaseKey(int phaseIndex) {
        return switch (phaseIndex) {
            case 0 -> LangEntry.of(phasePrepareKey);
            case 1 -> LangEntry.of(phaseBuildKey);
            case 2 -> LangEntry.of(phaseCraftKey);
            default -> LangEntry.of(phaseFinalKey);
        };
    }

    public static List<PhaseGroup> phaseGroupsOf(@Nullable ProductionPlan plan) {
        if (plan == null) {
            return List.of();
        }
        Set<String> terminal = terminalStepIds(plan);
        List<PlanStep> prepare = new ArrayList<>();
        List<PlanStep> build = new ArrayList<>();
        List<PlanStep> craft = new ArrayList<>();
        List<PlanStep> targets = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            if (step == null) {
                continue;
            }
            if (terminal.contains(step.stepId())) {
                targets.add(step);
                continue;
            }
            switch (step) {
                case AcquireStep ignored -> prepare.add(step);
                case LinePullStep ignored -> prepare.add(step);
                case ProvisionMachineStep ignored -> build.add(step);
                case SetupLineStep ignored -> build.add(step);
                default -> craft.add(step);
            }
        }
        List<PhaseGroup> groups = new ArrayList<>(4);
        groups.add(new PhaseGroup(0, prepare));
        groups.add(new PhaseGroup(1, build));
        groups.add(new PhaseGroup(2, craft));
        groups.add(new PhaseGroup(3, targets));
        return List.copyOf(groups);
    }

    public static ChecklistSummary summarize(@Nullable ProductionPlan plan, @Nullable ReadinessProbe probe) {
        if (plan == null) {
            return new ChecklistSummary(0, 0, 0, 0);
        }
        ReadinessProbe effectiveProbe = probe == null ? ProbeSources::readinessOf : probe;
        int batches = 0;
        int auto = 0;
        int recipeSteps = 0;
        int preTasks = 0;
        for (PlanStep step : plan.steps()) {
            if (step == null) {
                continue;
            }
            switch (step) {
                case CraftStep craft -> {
                    batches++;
                    recipeSteps++;
                    if (effectiveProbe.readinessOf(craft) == ExecutionReadiness.autoReady) {
                        auto++;
                    }
                }
                case ConvertStep convert -> {
                    batches++;
                    recipeSteps++;
                    if (effectiveProbe.readinessOf(convert) == ExecutionReadiness.autoReady) {
                        auto++;
                    }
                }
                case CycleLoopStep loop -> {
                    int iterations = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, loop.iterations()));
                    int members = Math.max(1, loop.singleIterationTemplate().size());
                    batches += iterations * members;
                    recipeSteps += iterations * members;
                    for (CraftStep template : loop.singleIterationTemplate()) {
                        if (effectiveProbe.readinessOf(template) == ExecutionReadiness.autoReady) {
                            auto += iterations;
                        }
                    }
                }
                case AcquireStep ignored -> preTasks++;
                case ProvisionMachineStep ignored -> preTasks++;
                case SetupLineStep ignored -> preTasks++;
                case LinePullStep ignored -> preTasks++;
            }
        }
        return new ChecklistSummary(batches, Math.min(auto, recipeSteps), recipeSteps, preTasks);
    }

    private static Set<String> terminalStepIds(ProductionPlan plan) {
        Set<String> referenced = new LinkedHashSet<>();
        Set<String> all = new LinkedHashSet<>();
        for (PlanStep step : plan.steps()) {
            if (step == null) {
                continue;
            }
            all.add(step.stepId());
            for (String dependency : step.dependsOnStepIds()) {
                referenced.add(dependency);
            }
        }
        Set<String> terminal = new LinkedHashSet<>();
        for (String stepId : all) {
            if (!referenced.contains(stepId)) {
                terminal.add(stepId);
            }
        }
        return terminal;
    }

    private void rebuild() {
        groups = phaseGroupsOf(cachedPlan);
        summary = summarize(cachedPlan, probe);
        if (cachedPlan != null) {
            Set<String> persisted = CirrusPlanBookmarkStore.checkedSteps(CirrusPlanBookmarkStore.planKeyOf(cachedPlan));
            if (!persisted.isEmpty()) {
                checkedStepIds.addAll(persisted);
            }
        }
        List<Line> next = new ArrayList<>();
        for (PhaseGroup group : groups) {
            if (group.steps().isEmpty()) {
                continue;
            }
            next.add(new Line(group.phaseIndex(), null));
            for (PlanStep step : group.steps()) {
                next.add(new Line(group.phaseIndex(), step));
            }
        }
        lines = List.copyOf(next);
        targetScrollPx = Math.max(0f, Math.min(targetScrollPx, maxScrollPx()));
        readinessCache.clear();
        for (Line line : lines) {
            if (line.step() != null) {
                readinessCache.put(line.step().stepId(), computeReadiness(line.step()));
            }
        }
    }

    //endregion

    //region layout

    private int contentTop() {
        return headerHeight + 6;
    }

    private int contentHeight() {
        return Math.max(0, height() - contentTop() - footerHeight);
    }

    private List<Line> filteredLines() {
        if (filter == StepFilter.all) {
            return lines;
        }
        List<Line> out = new ArrayList<>();
        for (PhaseGroup group : groups) {
            List<PlanStep> matched = new ArrayList<>();
            for (PlanStep step : group.steps()) {
                boolean checked = checkedStepIds.contains(step.stepId());
                ExecutionReadiness readiness = readinessOf(step);
                boolean keep = switch (filter) {
                    case all -> true;
                    case remaining -> !checked;
                    case done -> checked;
                    case autoOnly -> readiness == ExecutionReadiness.autoReady;
                    case manualOnly -> readiness != ExecutionReadiness.autoReady;
                };
                if (keep) {
                    matched.add(step);
                }
            }
            if (!matched.isEmpty()) {
                out.add(new Line(group.phaseIndex(), null));
                for (PlanStep s : matched) {
                    out.add(new Line(group.phaseIndex(), s));
                }
            }
        }
        return out;
    }

    private float maxScrollPx() {
        List<Line> active = filteredLines();
        int totalH = 0;
        for (Line line : active) {
            totalH += line.step() == null ? phaseHeaderHeight : cardHeight;
        }
        return Math.max(0f, totalH + 6 - contentHeight());
    }

    private float currentScrollPx() {
        float max = maxScrollPx();
        if (targetScrollPx > max) {
            targetScrollPx = max;
        }
        if (!smoothScrollPx.initialized()) {
            smoothScrollPx.snap(targetScrollPx, System.nanoTime());
        }
        return smoothScrollPx.update(targetScrollPx, 18.0f);
    }

    private int visibleLineCount() {
        int available = contentHeight();
        int count = 0;
        int used = 0;
        for (Line line : filteredLines()) {
            int lineHeight = line.step() == null ? phaseHeaderHeight : cardHeight;
            if (used + lineHeight > available) {
                break;
            }
            used += lineHeight;
            count++;
        }
        return count;
    }

    private void relayout() {
        if (width() <= 0 || height() <= 0) {
            return;
        }
        place(completionBar, contentPadding, headerHeight - 8, Math.max(1, width() - contentPadding * 2), 5);
    }

    private static void place(Widget widget, int x, int y, int width, int height) {
        if (widget == null) {
            return;
        }
        widget.useStyle(UIStyle.of(positionAbsolute(), insetLeft(x), insetTop(y), sizeOf(width, height)));
    }

    //endregion

    //region render

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        syncPlanIfChanged();
        hotspots.clear();
        tipRegions.clear();
        CirrusPalette palette = host.palette();
        canvas.fill(0, 0, width, height, palette.canvasVoid());
        Font font = fontOrNull(canvas);
        renderHeader(canvas, palette, font, width, mouseX, mouseY);
        renderLines(canvas, palette, font, width, mouseX, mouseY);
        renderFooter(canvas, palette, font, width, height, mouseX, mouseY);
        if (filterMenuOpen) {
            renderFilterPopup(canvas, palette, font, width, height, mouseX, mouseY);
        }
    }

    private String filterLabel(StepFilter f) {
        return switch (f) {
            case all -> "All";
            case remaining -> "Remaining";
            case done -> "Done";
            case autoOnly -> "Auto";
            case manualOnly -> "Manual";
        };
    }

    private void renderHeader(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int width,
        int mouseX,
        int mouseY
    ) {
        canvas.fill(0, 0, width, headerHeight, palette.dockSurface());
        canvas.fill(0, headerHeight - 1, width, 1, OreUiPaint.cardDivider(palette));
        int btnH = 16;
        int btnY = 3;
        int rx = width - contentPadding;

        if (cachedPlan != null) {
            boolean bookmarked = JeiPlanBookmarkBridge.isPlanBookmarked(cachedPlan);
            int bmW = width >= 360 ? 68 : 20;
            int bmX = rx - bmW;
            boolean bmHov = ChromeText.hit(mouseX, mouseY, bmX, btnY, bmW, btnH);
            if (bookmarked) {
                OreUiPaint.selectedToggle(canvas, bmX, btnY, bmW, btnH, palette, bmHov);
            } else {
                OreUiPaint.raisedButton(canvas, bmX, btnY, bmW, btnH, palette, false, bmHov, false, false);
            }
            int bmInk = bookmarked ? palette.focusRing() : (bmHov ? palette.textPrimary() : palette.textSecondary());
            if (font != null) {
                String txt = bmW >= 54
                        ? (bookmarked ? "\u2605 Saved" : "\u2606 Bookmark")
                        : (bookmarked ? "\u2605" : "\u2606");
                String fitted = ChromeText.fitText(txt, Math.max(8, bmW - 6), font::width, "...");
                canvas.text(
                    fitted,
                    bmX + Math.max(3, (bmW - font.width(fitted)) / 2),
                    ChromeText.centeredButtonY(btnY, btnH, canvas),
                    bmInk,
                    false
                );
            }
            hotspots.add(new Hotspot(bmX, btnY, bmW, btnH, () -> {
                if (cachedPlan != null) {
                    JeiPlanBookmarkBridge.togglePlanBookmark(cachedPlan, null, checkedStepIds);
                    CirrusUiSound.playClickSound();
                }
            }));
            rx = bmX - 4;
        }

        String filterTxt = "Filter: " + filterLabel(filter);
        int filterW = font == null ? 68 : Math.min(104, Math.max(48, font.width(filterTxt) + 18));
        int filterX = Math.max(contentPadding + 40, rx - filterW);
        boolean filterHov = ChromeText.hit(mouseX, mouseY, filterX, btnY, filterW, btnH);
        if (filterMenuOpen) {
            OreUiPaint.selectedToggle(canvas, filterX, btnY, filterW, btnH, palette, filterHov);
        } else {
            OreUiPaint.raisedButton(canvas, filterX, btnY, filterW, btnH, palette, false, filterHov, false, false);
        }
        int fInk = filterMenuOpen ? palette.focusRing() : (filterHov ? palette.textPrimary() : palette.textSecondary());
        if (font != null) {
            String fitted = ChromeText.fitText(filterTxt, Math.max(8, filterW - 14), font::width, "...");
            canvas.text(fitted, filterX + 5, ChromeText.centeredButtonY(btnY, btnH, canvas), fInk, false);
        }
        ChromeButtons.caretDown(canvas, filterX + filterW - 9, btnY + Math.max(3, (btnH - 6) / 2), fInk);
        hotspots.add(new Hotspot(filterX, btnY, filterW, btnH, () -> {
            filterMenuOpen = !filterMenuOpen;
            CirrusUiSound.playClickSound();
        }));

        if (font != null) {
            int maxSummaryW = Math.max(12, filterX - contentPadding - 6);
            Component summaryComponent = summaryText();
            String raw = summaryComponent.getString();
            String fitted = ChromeText.fitText(raw, maxSummaryW, font::width, "...");
            if (fitted.equals(raw)) {
                canvas.text(summaryComponent, contentPadding, 6, palette.textPrimary(), false);
            } else if (!fitted.isEmpty()) {
                canvas.text(fitted, contentPadding, 6, palette.textPrimary(), false);
            }
        }
    }

    private void renderFilterPopup(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        popupHotspots.clear();
        popupW = Math.min(170, Math.max(124, width - 16));
        popupH = 96;
        popupX = Math.max(6, width - popupW - 8);
        popupY = Math.min(Math.max(4, height - popupH - 4), headerHeight + 2);

        canvas.shadow(popupX, popupY, popupW, popupH, 2f, 4f, 8f, palette.dropShadow());
        OreUiPaint.popover(canvas, popupX, popupY, popupW, popupH, palette);

        int pad = 7;
        int innerW = popupW - pad * 2;
        int y = popupY + pad;
        if (font != null) {
            canvas.text("Show Steps", popupX + pad, y, palette.textMuted(), false);
        }
        y += 10;
        StepFilter[] allFilters = StepFilter.values();
        int colW = (innerW - 6) / 3;
        for (int i = 0; i < allFilters.length; i++) {
            StepFilter f = allFilters[i];
            int row = i / 3;
            int col = i % 3;
            int bx = popupX + pad + col * (colW + 3);
            int by = y + row * 19;
            int bw = col == 2 ? (innerW - (colW + 3) * 2) : colW;
            drawPopupBtn(
                canvas,
                palette,
                font,
                bx,
                by,
                bw,
                16,
                filterLabel(f),
                filter == f,
                mouseX,
                mouseY,
                () -> setFilter(f)
            );
        }

        y += 42;
        int halfW = (innerW - 4) / 2;
        drawPopupBtn(canvas, palette, font, popupX + pad, y, halfW, 16, "Check All", false, mouseX, mouseY, () -> {
            for (Line line : lines) {
                if (line.step() != null) {
                    checkedStepIds.add(line.step().stepId());
                }
            }
            if (cachedPlan != null) {
                CirrusPlanBookmarkStore.saveCheckedSteps(CirrusPlanBookmarkStore.planKeyOf(cachedPlan), checkedStepIds);
            }
            filterMenuOpen = false;
        });
        drawPopupBtn(
            canvas,
            palette,
            font,
            popupX + pad + halfW + 4,
            y,
            innerW - halfW - 4,
            16,
            "Reset",
            false,
            mouseX,
            mouseY,
            () -> {
                checkedStepIds.clear();
                if (cachedPlan != null) {
                    CirrusPlanBookmarkStore
                            .saveCheckedSteps(CirrusPlanBookmarkStore.planKeyOf(cachedPlan), checkedStepIds);
                }
                filterMenuOpen = false;
            }
        );
    }

    private void drawPopupBtn(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int x,
        int y,
        int w,
        int h,
        String label,
        boolean selected,
        int mouseX,
        int mouseY,
        Runnable action
    ) {
        boolean hov = ChromeText.hit(mouseX, mouseY, x, y, w, h);
        if (selected) {
            OreUiPaint.selectedToggle(canvas, x, y, w, h, palette, hov);
        } else {
            OreUiPaint.raisedButton(canvas, x, y, w, h, palette, false, hov, false, false);
        }
        if (font != null) {
            String fitted = ChromeText.fitText(label, Math.max(8, w - 6), font::width, "...");
            int tx = x + Math.max(3, (w - font.width(fitted)) / 2);
            int ty = selected ? ChromeText.centeredY(y, h, canvas) : ChromeText.centeredButtonY(y, h, canvas);
            canvas.text(
                fitted,
                tx,
                ty,
                selected ? palette.focusRing() : (hov ? palette.textPrimary() : palette.textSecondary()),
                false
            );
        }
        popupHotspots.add(new Hotspot(x, y, w, h, action));
    }

    private void renderLines(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int width,
        int mouseX,
        int mouseY
    ) {
        int top = contentTop();
        int available = contentHeight();
        if (available <= 0) {
            return;
        }
        List<Line> active = filteredLines();
        if (cachedPlan == null || active.isEmpty()) {
            Component empty = cachedPlan == null
                    ? CirrusLangs.Workbench.Flow.bannerNoTarget.get()
                    : CirrusLangs.Workbench.Ledger.emptySection.get();
            canvas.drawCenteredString(
                empty,
                width / 2,
                ChromeText.centeredY(top, available, canvas),
                palette.textMuted()
            );
            return;
        }
        float maxScroll = maxScrollPx();
        float scrollPx = currentScrollPx();
        int scrollOff = Math.round(scrollPx);
        int barReserve = maxScroll > 0.5f ? 6 : 0;
        int clipX = contentPadding / 2;
        canvas.withClip(clipX, top, Math.max(0, width - clipX * 2), available, () -> {
            int cursor = top - scrollOff;
            int stepNumber = 1;
            for (Line line : active) {
                int lineHeight = line.step() == null ? phaseHeaderHeight : cardHeight;
                if (cursor + lineHeight >= top && cursor <= top + available) {
                    if (line.step() == null) {
                        renderPhaseHeader(canvas, palette, font, line.phaseIndex(), cursor, width - barReserve);
                    } else {
                        renderCard(
                            canvas,
                            palette,
                            font,
                            line.step(),
                            stepNumber,
                            cursor,
                            width - barReserve,
                            mouseX,
                            mouseY
                        );
                    }
                }
                if (line.step() != null) {
                    stepNumber++;
                }
                cursor += lineHeight;
            }
        });
        if (maxScroll > 0.5f && available >= 16) {
            float total = available + maxScroll;
            int thumbH = Math.max(14, Math.round(available * (available / total)));
            int thumbY = top + Math.round((available - thumbH) * (scrollPx / maxScroll));
            int sbX = width - 5;
            canvas.fill(sbX, top + 2, 3, available - 4, OreUiPaint.withAlpha(palette.recessedSurface(), 0.7f));
            canvas.fill(sbX, thumbY, 3, thumbH, palette.hairlineHighlight());
        }
    }

    private void renderPhaseHeader(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int phaseIndex,
        int y,
        int width
    ) {
        int phaseColor = switch (phaseIndex) {
            case 0 -> palette.acquireStep().accent();
            case 1 -> palette.provisionMachineStep().accent();
            case 2 -> palette.craftStep().accent();
            default -> palette.costS();
        };
        canvas.fill(contentPadding, y + 6, 4, 6, phaseColor);
        canvas.fill(
            contentPadding,
            y + phaseHeaderHeight - 1,
            Math.max(0, width - contentPadding * 2),
            1,
            OreUiPaint.cardDivider(palette)
        );
        if (font == null) {
            return;
        }
        String label = phaseKey(phaseIndex).get().getString();
        canvas.text(
            label,
            contentPadding + 8,
            ChromeText.centeredY(y, phaseHeaderHeight - 1, canvas),
            palette.textPrimary(),
            false
        );
    }

    private void renderCard(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        PlanStep step,
        int stepNumber,
        int y,
        int width,
        int mouseX,
        int mouseY
    ) {
        String stepId = step.stepId();
        boolean checked = checkedStepIds.contains(stepId);
        int cx = contentPadding - 2;
        int cy = y + 2;
        int cw = Math.max(0, width - contentPadding * 2 + 4);
        int ch = cardHeight - 3;
        boolean hovered = ChromeText.hit(mouseX, mouseY, cx, cy, cw, ch);
        OreUiPaint.raisedCard(canvas, cx, cy, cw, ch, palette, hovered, false);
        tipRegions.add(new StepTipRegion(cx, cy, cw, ch, step, checked));

        int checkboxX = cx + 7;
        int checkboxY = cy + (ch - 2 - checkboxSize) / 2;
        boolean cbHover = ChromeText.hit(mouseX, mouseY, checkboxX - 2, cy, checkboxSize + 6, ch);
        OreUiPaint.checkbox(canvas, checkboxX, checkboxY, checkboxSize, checked, cbHover, palette);
        hotspots.add(new Hotspot(checkboxX - 2, cy, checkboxSize + 6, ch, () -> toggleChecked(stepId)));

        int slotX = checkboxX + checkboxSize + 7;
        int slotSize = 20;
        int slotY = cy + Math.max(2, (ch - 2 - slotSize) / 2);
        ComponentStackKey iconRes = iconResourceOf(step);
        CoPilotPaint.renderSlotItem(
            canvas,
            iconRes == null ? null : CoPilotPaint.stackOf(iconRes, 1L),
            slotX,
            slotY,
            slotSize,
            palette
        );
        if (iconRes != null) {
            hotspots.add(
                new Hotspot(slotX, slotY, slotSize, slotSize, () -> JeiPlanBookmarkBridge.showRecipesFor(iconRes))
            );
        }
        int bodyX = slotX + slotSize + 2;
        hotspots.add(new Hotspot(bodyX, cy, Math.max(0, cx + cw - bodyX), ch, () -> host.onFocusStep(stepId)));

        int textX = slotX + slotSize + 7;
        if (font != null) {
            ExecutionReadiness readiness = readinessOf(step);
            Component badge = badgeKey(readiness).get();
            int badgeWidth = font.width(badge) + 10;
            int badgeX = cx + cw - badgeWidth - 7;
            int badgeY = cy + (ch - 2 - badgeHeight) / 2;
            int color = badgeColor(palette, readiness);
            OreUiPaint.tagPill(canvas, badgeX, badgeY, badgeHeight, badge, color, palette);

            int textMaxW = Math.max(12, badgeX - textX - 8);
            String title = "#" + stepNumber + "  " + humanTitleOf(step);
            String sub = humanSubtitleOf(step);
            if (sub.isEmpty()) {
                canvas.text(
                    ChromeText.fitText(title, textMaxW, font::width, "..."),
                    textX,
                    ChromeText.centeredButtonY(cy, ch, canvas),
                    checked ? palette.textMuted() : palette.textPrimary(),
                    false
                );
            } else {
                canvas.text(
                    ChromeText.fitText(title, textMaxW, font::width, "..."),
                    textX,
                    cy + 5,
                    checked ? palette.textMuted() : palette.textPrimary(),
                    false
                );
                canvas.text(
                    ChromeText.fitText(sub, textMaxW, font::width, "..."),
                    textX,
                    cy + 16,
                    palette.textSecondary(),
                    false
                );
            }
        }
    }

    private @Nullable ComponentStackKey iconResourceOf(PlanStep step) {
        return switch (step) {
            case AcquireStep acquire -> acquire.resource();
            case LinePullStep pull -> pull.resource();
            case SetupLineStep setup -> setup.targetOutput();
            case ProvisionMachineStep provision -> provision.machine().contentKey().orElse(null);
            case CraftStep craft -> {
                StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer
                        .resolve(craft, resolvedGraph(), null, false);
                if (visuals.primaryOutput() != null) {
                    yield visuals.primaryOutput();
                }
                yield craft.chosenMachine().flatMap(m -> m.contentKey()).orElse(null);
            }
            case ConvertStep convert -> {
                StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer
                        .resolve(convert, resolvedGraph(), null, false);
                if (visuals.primaryOutput() != null) {
                    yield visuals.primaryOutput();
                }
                yield convert.slotBindings().values().stream().findFirst().map(b -> b.chosenResource()).orElse(null);
            }
            case CycleLoopStep loop -> loop.netDeltaPerIteration().keySet().stream().findFirst().orElse(null);
        };
    }

    private String humanTitleOf(PlanStep step) {
        return switch (step) {
            case AcquireStep acquire -> CirrusLangs.Workbench.Checklist.stepAcquire.get(
                CoPilotPaint.resourceName(acquire.resource()).getString() + " ×"
                        + WorkbenchFormatters.formatAmount(acquire.amount())
            ).getString();
            case CraftStep craft -> {
                StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer
                        .resolve(craft, resolvedGraph(), null, false);
                String name = visuals.primaryOutput() != null
                        ? CoPilotPaint.resourceName(visuals.primaryOutput()).getString()
                        : CoPilotPaint.humanizeId(craft.recipeKey().shortString());
                long outQty = visuals.outputAmount() > 0L ? visuals.outputAmount() : craft.batches();
                yield CirrusLangs.Workbench.Checklist.stepCraft
                        .get(name + " ×" + WorkbenchFormatters.formatAmount(outQty)).getString();
            }
            case ConvertStep convert -> {
                StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer
                        .resolve(convert, resolvedGraph(), null, false);
                String name = visuals.primaryOutput() != null
                        ? CoPilotPaint.resourceName(visuals.primaryOutput()).getString()
                        : CoPilotPaint.humanizeId(convert.conversionRecipeKey().shortString());
                long outQty = visuals.outputAmount() > 0L ? visuals.outputAmount() : convert.batches();
                yield CirrusLangs.Workbench.Checklist.stepConvert
                        .get(name + " ×" + WorkbenchFormatters.formatAmount(outQty)).getString();
            }
            case ProvisionMachineStep provision -> CirrusLangs.Workbench.Checklist.stepProvision.get(
                StepNodeCardRenderer.machineNameOf(provision.machine(), null).getString() + " ×"
                        + provision.countToBuild()
            ).getString();
            case SetupLineStep setup -> CirrusLangs.Workbench.Checklist.stepSetupLine
                    .get(CoPilotPaint.resourceName(setup.targetOutput()).getString()).getString();
            case LinePullStep pull -> CirrusLangs.Workbench.Checklist.stepLinePull.get(
                CoPilotPaint.resourceName(pull.resource()).getString() + " ×"
                        + WorkbenchFormatters.formatAmount(pull.amount())
            ).getString();
            case CycleLoopStep loop -> CirrusLangs.Workbench.Checklist.stepCycle.get(
                CoPilotPaint.humanizeId(loop.macroKey().macroId()) + " ×"
                        + WorkbenchFormatters.formatAmount(loop.iterations())
            ).getString();
        };
    }

    private String humanSubtitleOf(PlanStep step) {
        if (step instanceof CraftStep craft) {
            String machine = craft.chosenMachine().map(m -> StepNodeCardRenderer.machineNameOf(m, null).getString())
                    .orElseGet(() -> CirrusLangs.Workbench.Flow.nodeHandCraft.get().getString());
            double human = craft.stepCost().humanActiveSeconds();
            double wait = craft.stepCost().passiveWaitSeconds();
            if (human > 0.05 && wait > 0.05) {
                return machine + " · "
                        + CirrusLangs.Workbench.Flow.timeBoth
                                .get(WorkbenchFormatters.formatSeconds(human), WorkbenchFormatters.formatSeconds(wait))
                                .getString();
            }
            if (wait > 0.05) {
                return machine + " · "
                        + CirrusLangs.Workbench.Flow.timeWait.get(WorkbenchFormatters.formatSeconds(wait)).getString();
            }
            return machine;
        }
        return "";
    }

    private void renderFooter(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable Font font,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        int footerY = height - footerHeight;
        canvas.fill(0, footerY, width, footerHeight, palette.dockSurface());
        canvas.fill(0, footerY, width, 1, OreUiPaint.cardDivider(palette));
        int buttonX = width - contentPadding - primaryButtonWidth;
        int buttonY = footerY + (footerHeight - primaryButtonHeight) / 2;
        boolean enabled = canSend();
        boolean btnHover = enabled
                && ChromeText.hit(mouseX, mouseY, buttonX, buttonY, primaryButtonWidth, primaryButtonHeight);
        if (enabled) {
            OreUiPaint.raisedButton(
                canvas,
                buttonX,
                buttonY,
                primaryButtonWidth,
                primaryButtonHeight,
                palette,
                false,
                btnHover,
                true,
                false
            );
        } else {
            OreUiPaint.searchField(canvas, buttonX, buttonY, primaryButtonWidth, primaryButtonHeight, palette, false);
        }
        if (font != null) {
            String label = LangEntry.of(sendKey).get().getString();
            String fitted = ChromeText.fitText(label, primaryButtonWidth - 10, font::width, "...");
            int textW = font.width(fitted);
            canvas.text(
                fitted,
                buttonX + Math.max(4, (primaryButtonWidth - textW) / 2),
                ChromeText.centeredY(buttonY, primaryButtonHeight - 1, canvas),
                enabled ? palette.textOnAccent() : palette.textDisabled(),
                false
            );
            String closeLabel = LangEntry.of(closeOnSendKey).get().getString();
            int closeCbY = footerY + (footerHeight - checkboxSize) / 2;
            int closeX = contentPadding + checkboxSize + 6;
            boolean cbHover = ChromeText.hit(
                mouseX,
                mouseY,
                contentPadding,
                footerY,
                checkboxSize + 6 + font.width(closeLabel),
                footerHeight
            );
            OreUiPaint.checkbox(canvas, contentPadding, closeCbY, checkboxSize, closeOnSend, cbHover, palette);
            canvas.text(
                closeLabel,
                closeX,
                ChromeText.centeredY(footerY, footerHeight, canvas),
                palette.textSecondary(),
                false
            );
            hotspots.add(
                new Hotspot(
                    contentPadding,
                    footerY,
                    checkboxSize + 6 + font.width(closeLabel),
                    footerHeight,
                    () -> closeOnSend = !closeOnSend
                )
            );
        }
        if (enabled) {
            hotspots.add(new Hotspot(buttonX, buttonY, primaryButtonWidth, primaryButtonHeight, this::requestSend));
        }
    }

    private static @Nullable Font fontOrNull(SceneCanvas canvas) {
        try {
            return canvas.font();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    //endregion

    //region probe sources

    /**
     * Real source of the execution-ready probe: entries resolve through the planning runtime, the interaction adapter is injected by the registrar.
     */
    public static final class ProbeSources {

        private static final Map<CompiledRecipeKey, ExecutionReadiness> cache = new ConcurrentHashMap<>();
        private static volatile @Nullable Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> entryResolver;
        private static volatile @Nullable Function<RecipeEntry<?>, Optional<InteractionApi>> interactionResolver;

        private ProbeSources() {}

        public static void installEntryResolver(
            @Nullable Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> resolver
        ) {
            entryResolver = resolver;
            cache.clear();
        }

        public static void installInteractionResolver(
            @Nullable Function<RecipeEntry<?>, Optional<InteractionApi>> resolver
        ) {
            interactionResolver = resolver;
            cache.clear();
        }

        public static void clear() {
            entryResolver = null;
            interactionResolver = null;
            cache.clear();
        }

        public static Optional<RecipeEntry<?>> resolveEntry(CompiledRecipeKey key) {
            if (key == null) {
                return Optional.empty();
            }
            Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> injected = entryResolver;
            if (injected != null) {
                Optional<RecipeEntry<?>> resolved = injected.apply(key);
                return resolved == null ? Optional.empty() : resolved;
            }
            return CirrusPlanningRuntime.shared().flatMap(runtime -> runtime.resolveRecipeEntry(key));
        }

        public static Optional<InteractionApi> resolveInteraction(@Nullable RecipeEntry<?> entry) {
            if (entry == null) {
                return Optional.empty();
            }
            Function<RecipeEntry<?>, Optional<InteractionApi>> injected = interactionResolver;
            if (injected == null) {
                return Optional.empty();
            }
            Optional<InteractionApi> resolved = injected.apply(entry);
            return resolved == null ? Optional.empty() : resolved;
        }

        public static ExecutionReadiness mapProbe(@Nullable InteractionProbeResult result) {
            if (result instanceof InteractionProbeResult.Supported supported) {
                return supported.performerAvailable() ? ExecutionReadiness.autoReady : ExecutionReadiness.guidedReady;
            }
            return ExecutionReadiness.manual;
        }

        public static ExecutionReadiness readinessOf(@Nullable PlanStep step) {
            return readinessOf(keyOf(step).orElse(null));
        }

        public static ExecutionReadiness readinessOf(@Nullable CompiledRecipeKey key) {
            if (key == null) {
                return ExecutionReadiness.manual;
            }
            ExecutionReadiness cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            Optional<RecipeEntry<?>> entry = resolveEntry(key);
            if (entry.isEmpty()) {
                return ExecutionReadiness.manual;
            }
            Optional<InteractionApi> api = resolveInteraction(entry.get());
            if (api.isEmpty()) {
                return ExecutionReadiness.manual;
            }
            ExecutionReadiness readiness;
            try {
                readiness = mapProbe(api.get().probe(entry.get()));
            } catch (RuntimeException | LinkageError failure) {
                return ExecutionReadiness.manual;
            }
            cache.put(key, readiness);
            return readiness;
        }

        public static Optional<CompiledRecipeKey> keyOf(@Nullable PlanStep step) {
            if (step instanceof CraftStep craft) {
                return Optional.of(craft.recipeKey());
            }
            if (step instanceof ConvertStep convert) {
                return Optional.of(convert.conversionRecipeKey());
            }
            return Optional.empty();
        }
    }

    //endregion
}
