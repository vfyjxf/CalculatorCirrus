package dev.vfyjxf.cirrus.api.recipe.projection.display;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.projection.GridSpec;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ChangeReason;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.GridDiff;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.PipelineStepTrace;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionChange;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionDiff;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionGuard;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionStage;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * UI-toolkit-independent, on-demand visual model of a {@link RecipeProjection} or
 * {@link ProjectionResult}, unifying 2D grid cell diff overlays, loose slots, value gauges,
 * ordered pipeline stages, and per-plugin change rows.
 */
public record ProjectionDisplayModel(
    RecipeProjection original,
    RecipeProjection displayed,
    boolean runnable,
    ImmutableList<ProjectionGuard.Finding> guardFindings,
    SideView inputSide,
    SideView outputSide,
    ImmutableList<ValueView> values,
    ImmutableList<StageView> stages,
    ImmutableList<ChangeRow> changeRows
) {

    public record Options(
        boolean showDiffOverlays,
        boolean collapseBySlot,
        @Nullable Namespace filterPluginId
    ) {
        public static final Options defaults = new Options(true, true, null);
        public static final Options staticOnly = new Options(false, true, null);

        public Options withShowDiffOverlays(boolean enabled) {
            return new Options(enabled, collapseBySlot, filterPluginId);
        }

        public Options withCollapseBySlot(boolean collapse) {
            return new Options(showDiffOverlays, collapse, filterPluginId);
        }

        public Options withFilterPlugin(@Nullable Namespace pluginId) {
            return new Options(showDiffOverlays, collapseBySlot, pluginId);
        }
    }

    public static ProjectionDisplayModel fromProjection(RecipeProjection projection) {
        Checks.checkNotNull(projection, "projection");
        ProjectionResult trivial = new ProjectionResult(
            projection,
            projection,
            true,
            Lists.immutable.empty(),
            Lists.immutable.empty(),
            null
        );
        return fromResult(trivial, Options.staticOnly);
    }

    public static ProjectionDisplayModel fromResult(ProjectionResult result) {
        return fromResult(result, Options.defaults);
    }

    public static ProjectionDisplayModel fromResult(ProjectionResult result, Options options) {
        Checks.checkNotNull(result, "result");
        Checks.checkNotNull(options, "options");

        if (!options.showDiffOverlays()) {
            return buildStatic(result.projected(), result.runnable(), result.guardFindings());
        }

        ProjectionDiff diff = result.diff();
        List<ValueView> values = new ArrayList<>();
        SideView inputSide = buildDiffSide(SlotChannel.input, diff, values);
        SideView outputSide = buildDiffSide(SlotChannel.output, diff, values);
        List<StageView> stages = buildStages(result);
        List<ChangeRow> rows = buildChangeRows(result, options);

        return new ProjectionDisplayModel(
            result.original(),
            result.projected(),
            result.runnable(),
            result.guardFindings(),
            inputSide,
            outputSide,
            Lists.immutable.withAll(values),
            Lists.immutable.withAll(stages),
            Lists.immutable.withAll(rows)
        );
    }

    public boolean hasContent() {
        return inputSide.hasContent() || outputSide.hasContent() || !values.isEmpty();
    }

    public boolean hasAnyDiffs() {
        return inputSide.hasChanges() || outputSide.hasChanges()
                || values.anySatisfy(v -> v.status().isChanged())
                || !changeRows.isEmpty();
    }

    public List<ChangeRow> changeRows(SlotChannel channel) {
        List<ChangeRow> out = new ArrayList<>();
        for (ChangeRow row : changeRows) {
            if (row.channel() == channel) {
                out.add(row);
            }
        }
        return List.copyOf(out);
    }

    public List<ChangeRow> metaChangeRows() {
        List<ChangeRow> out = new ArrayList<>();
        for (ChangeRow row : changeRows) {
            if (row.channel() == null) {
                out.add(row);
            }
        }
        return List.copyOf(out);
    }

    private static ProjectionDisplayModel buildStatic(
        RecipeProjection projection,
        boolean runnable,
        ImmutableList<ProjectionGuard.Finding> guardFindings
    ) {
        List<ValueView> values = new ArrayList<>();
        SideView inputSide = buildStaticSide(SlotChannel.input, projection.inputGrid(), projection.inputSlots(), values);
        SideView outputSide = buildStaticSide(
            SlotChannel.output,
            projection.outputGrid(),
            projection.outputSlots(),
            values
        );
        return new ProjectionDisplayModel(
            projection,
            projection,
            runnable,
            guardFindings,
            inputSide,
            outputSide,
            Lists.immutable.withAll(values),
            Lists.immutable.empty(),
            Lists.immutable.empty()
        );
    }

    private static SideView buildStaticSide(
        SlotChannel channel,
        @Nullable GridSpec gridSpec,
        List<? extends ProjectionSlot<? extends RecipeContent>> slots,
        List<ValueView> valuesOut
    ) {
        Map<SlotAddress.GridCell, SlotView> gridMap = new LinkedHashMap<>();
        List<SlotView> loose = new ArrayList<>();
        for (ProjectionSlot<? extends RecipeContent> slot : slots) {
            RecipeContent content = slot.content();
            if (content.isEmpty()) {
                continue;
            }
            if (content instanceof ValueContent<?> value) {
                valuesOut.add(
                    new ValueView(
                        channel,
                        slot.address(),
                        SlotDiff.DiffStatus.unchanged,
                        value,
                        value,
                        "",
                        Lists.immutable.empty()
                    )
                );
                continue;
            }
            SlotView view = new SlotView(
                slot.address(),
                SlotDiff.DiffStatus.unchanged,
                content,
                content,
                null,
                "",
                Lists.immutable.empty()
            );
            if (gridSpec != null && slot.address() instanceof SlotAddress.GridCell cell
                    && gridSpec.contains(cell.col(), cell.row())) {
                gridMap.put(cell, view);
            } else {
                loose.add(view);
            }
        }
        GridView gridView = null;
        if (gridSpec != null) {
            List<CellView> cells = new ArrayList<>(gridSpec.cellCount());
            for (int row = 0; row < gridSpec.height(); row++) {
                for (int col = 0; col < gridSpec.width(); col++) {
                    SlotAddress.GridCell addr = SlotAddress.grid(channel, col, row, gridSpec.width());
                    SlotView sv = gridMap.get(addr);
                    cells.add(
                        sv == null
                                ? CellView.empty(addr)
                                : new CellView(
                                    col,
                                    row,
                                    addr,
                                    sv.status(),
                                    sv.before(),
                                    sv.after(),
                                    null,
                                    "",
                                    Lists.immutable.empty()
                                )
                    );
                }
            }
            gridView = new GridView(channel, gridSpec, Lists.immutable.withAll(cells));
        }
        return new SideView(channel, gridView, Lists.immutable.withAll(loose));
    }

    private static SideView buildDiffSide(SlotChannel channel, ProjectionDiff diff, List<ValueView> valuesOut) {
        GridDiff gridDiff = diff.gridDiff(channel);
        GridView gridView = null;
        if (gridDiff != null) {
            List<CellView> cells = new ArrayList<>(gridDiff.cells().size());
            for (GridDiff.CellDiff cell : gridDiff.cells()) {
                if (isValueOnly(cell.before(), cell.after())) {
                    addValueView(channel, cell.address(), cell.status(), cell.before(), cell.after(), cell.deltaBadge(), cell.touchedByEffects(), valuesOut);
                    cells.add(CellView.empty(cell.address()));
                } else {
                    cells.add(
                        new CellView(
                            cell.col(),
                            cell.row(),
                            cell.address(),
                            cell.status(),
                            cell.before(),
                            cell.after(),
                            cell.movedFrom(),
                            cell.deltaBadge(),
                            cell.touchedByEffects()
                        )
                    );
                }
            }
            gridView = new GridView(channel, gridDiff.spec(), Lists.immutable.withAll(cells));
        }

        List<SlotView> loose = new ArrayList<>();
        for (SlotDiff slotDiff : diff.looseSlotDiffs(channel)) {
            if (slotDiff.before() == null && slotDiff.after() == null) {
                continue;
            }
            if (isValueOnly(slotDiff.before(), slotDiff.after())) {
                addValueView(
                    channel,
                    slotDiff.address(),
                    slotDiff.status(),
                    slotDiff.before(),
                    slotDiff.after(),
                    slotDiff.deltaBadge(),
                    slotDiff.touchedByEffects(),
                    valuesOut
                );
                continue;
            }
            loose.add(
                new SlotView(
                    slotDiff.address(),
                    slotDiff.status(),
                    slotDiff.before(),
                    slotDiff.after(),
                    slotDiff.movedFrom(),
                    slotDiff.deltaBadge(),
                    slotDiff.touchedByEffects()
                )
            );
        }
        return new SideView(channel, gridView, Lists.immutable.withAll(loose));
    }

    private static boolean isValueOnly(@Nullable RecipeContent before, @Nullable RecipeContent after) {
        boolean beforeVal = before == null || before instanceof ValueContent<?>;
        boolean afterVal = after == null || after instanceof ValueContent<?>;
        return beforeVal && afterVal && (before != null || after != null);
    }

    private static void addValueView(
        SlotChannel channel,
        SlotAddress address,
        SlotDiff.DiffStatus status,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        String deltaBadge,
        ImmutableList<Namespace> effects,
        List<ValueView> out
    ) {
        ValueContent<?> b = before instanceof ValueContent<?> v ? v : null;
        ValueContent<?> a = after instanceof ValueContent<?> v ? v : null;
        if (b == null && a == null) {
            return;
        }
        out.add(new ValueView(channel, address, status, b, a, deltaBadge, effects));
    }

    private static List<StageView> buildStages(ProjectionResult result) {
        List<StageView> out = new ArrayList<>(ProjectionStage.values().length);
        for (ProjectionStage stage : ProjectionStage.values()) {
            List<PipelineStepTrace> stageSteps = result.stepsInStage(stage);
            int changeCount = 0;
            int guardFailures = 0;
            boolean blocked = false;
            List<SlotDiff> stageSlotDiffs = new ArrayList<>();
            LinkedHashMap<SlotAddress, StageSlotAccum> accum = new LinkedHashMap<>();
            for (PipelineStepTrace step : stageSteps) {
                if (step.blocked()) {
                    blocked = true;
                }
                for (ProjectionGuard.Finding finding : step.guardFindings()) {
                    if (!finding.passed()) {
                        guardFailures++;
                    }
                }
                changeCount += step.changes().size();
                for (ProjectionChange change : step.changes()) {
                    if (change instanceof ProjectionChange.SlotChange sc) {
                        accum.computeIfAbsent(sc.address(), StageSlotAccum::new).add(step, sc);
                    }
                }
            }
            for (StageSlotAccum acc : accum.values()) {
                stageSlotDiffs.add(acc.toSlotDiff());
            }
            out.add(
                new StageView(
                    stage,
                    stageSteps.size(),
                    changeCount,
                    guardFailures,
                    blocked,
                    Lists.immutable.withAll(stageSlotDiffs)
                )
            );
        }
        return out;
    }

    private static List<ChangeRow> buildChangeRows(ProjectionResult result, Options options) {
        List<StepChangePair> filtered = new ArrayList<>();
        for (PipelineStepTrace step : result.steps()) {
            if (options.filterPluginId() != null && !step.plugin().id().equals(options.filterPluginId())) {
                continue;
            }
            for (ProjectionChange change : step.changes()) {
                filtered.add(new StepChangePair(step, change));
            }
        }
        if (filtered.isEmpty()) {
            return List.of();
        }
        if (!options.collapseBySlot()) {
            List<ChangeRow> raw = new ArrayList<>(filtered.size());
            for (StepChangePair pair : filtered) {
                raw.add(toRawRow(pair.step, pair.change));
            }
            return raw;
        }

        LinkedHashMap<SlotAddress, StageSlotAccum> bySlot = new LinkedHashMap<>();
        List<ChangeRow> metaRows = new ArrayList<>();
        for (StepChangePair pair : filtered) {
            if (pair.change instanceof ProjectionChange.SlotChange sc) {
                bySlot.computeIfAbsent(sc.address(), StageSlotAccum::new).add(pair.step, sc);
            } else {
                metaRows.add(toRawRow(pair.step, pair.change));
            }
        }
        List<ChangeRow> merged = new ArrayList<>(bySlot.size() + metaRows.size());
        for (StageSlotAccum acc : bySlot.values()) {
            SlotDiff diff = acc.toSlotDiff();
            merged.add(
                new ChangeRow(
                    diff.channel(),
                    diff.address(),
                    diff.status(),
                    diff.before(),
                    diff.after(),
                    diff.movedFrom(),
                    diff.deltaBadge(),
                    diff.touchedByEffects(),
                    diff.touchedByPlugins(),
                    acc.stepCount,
                    null
                )
            );
        }
        merged.addAll(metaRows);
        return merged;
    }

    private static ChangeRow toRawRow(PipelineStepTrace step, ProjectionChange change) {
        if (change instanceof ProjectionChange.SlotChange sc) {
            SlotDiff.DiffStatus status = switch (sc.reason()) {
                case added -> SlotDiff.DiffStatus.added;
                case removed -> SlotDiff.DiffStatus.removed;
                case moved -> SlotDiff.DiffStatus.moved;
                case transformed -> SlotDiff.DiffStatus.transformed;
                default -> SlotDiff.DiffStatus.adjusted;
            };
            SlotDiff diff = new SlotDiff(
                sc.address(),
                status,
                sc.before(),
                sc.after(),
                sc.movedFrom(),
                Lists.immutable.of(step.effectId()),
                Lists.immutable.of(step.plugin())
            );
            return new ChangeRow(
                sc.channel(),
                sc.address(),
                status,
                sc.before(),
                sc.after(),
                sc.movedFrom(),
                diff.deltaBadge(),
                diff.touchedByEffects(),
                diff.touchedByPlugins(),
                1,
                null
            );
        }
        String metaSummary;
        @Nullable SlotAddress target;
        if (change instanceof ProjectionChange.AttributeChange ac) {
            target = ac.targetSlot();
            metaSummary = ac.key().id().path() + " = " + (ac.after() == null ? "removed" : ac.after());
        } else {
            ProjectionChange.TagChange tc = (ProjectionChange.TagChange) change;
            target = tc.targetSlot();
            metaSummary = (tc.added() ? "+" : "-") + tc.tag().path();
        }
        return new ChangeRow(
            null,
            target,
            SlotDiff.DiffStatus.adjusted,
            null,
            null,
            null,
            metaSummary,
            Lists.immutable.of(step.effectId()),
            Lists.immutable.of(step.plugin()),
            1,
            metaSummary
        );
    }

    private record StepChangePair(PipelineStepTrace step, ProjectionChange change) {}

    private static final class StageSlotAccum {
        private final SlotAddress address;
        private @Nullable RecipeContent firstBefore;
        private @Nullable RecipeContent lastAfter;
        private @Nullable SlotAddress movedFrom;
        private boolean initialized;
        private boolean transformed;
        private int stepCount;
        private final LinkedHashSet<Namespace> effects = new LinkedHashSet<>();
        private final LinkedHashSet<ProjectionPlugin> plugins = new LinkedHashSet<>();

        private StageSlotAccum(SlotAddress address) {
            this.address = address;
        }

        private void add(PipelineStepTrace step, ProjectionChange.SlotChange sc) {
            if (!initialized) {
                firstBefore = sc.before();
                initialized = true;
            }
            lastAfter = sc.after();
            if (sc.reason() == ChangeReason.moved && sc.movedFrom() != null && movedFrom == null) {
                movedFrom = sc.movedFrom();
            }
            if (sc.reason() == ChangeReason.transformed) {
                transformed = true;
            }
            effects.add(step.effectId());
            plugins.add(step.plugin());
            stepCount++;
        }

        private SlotDiff toSlotDiff() {
            SlotDiff.DiffStatus status;
            if (firstBefore == null && lastAfter == null) {
                status = SlotDiff.DiffStatus.unchanged;
            } else if (firstBefore == null) {
                status = movedFrom != null ? SlotDiff.DiffStatus.moved : SlotDiff.DiffStatus.added;
            } else if (lastAfter == null) {
                status = SlotDiff.DiffStatus.removed;
            } else if (transformed) {
                status = SlotDiff.DiffStatus.transformed;
            } else {
                status = SlotDiff.DiffStatus.adjusted;
            }
            return new SlotDiff(
                address,
                status,
                firstBefore,
                lastAfter,
                movedFrom,
                Lists.immutable.withAll(effects),
                Lists.immutable.withAll(plugins)
            );
        }
    }

    public record SideView(
        SlotChannel channel,
        @Nullable GridView grid,
        ImmutableList<SlotView> looseSlots
    ) {
        public boolean usesGrid() {
            return grid != null;
        }

        public boolean hasContent() {
            if (!looseSlots.isEmpty()) {
                return true;
            }
            return grid != null && grid.hasAnyOccupiedCell();
        }

        public boolean hasChanges() {
            if (grid != null && grid.hasChanges()) {
                return true;
            }
            return looseSlots.anySatisfy(SlotView::isChanged);
        }

        public int visibleSlotCount() {
            int count = looseSlots.size();
            if (grid != null) {
                for (CellView cell : grid.cells()) {
                    if (cell.isOccupied()) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    public record GridView(
        SlotChannel channel,
        GridSpec spec,
        ImmutableList<CellView> cells
    ) {
        public CellView cell(int col, int row) {
            return cells.get(spec.indexOf(col, row));
        }

        public boolean hasAnyOccupiedCell() {
            return cells.anySatisfy(CellView::isOccupied);
        }

        public boolean hasChanges() {
            return cells.anySatisfy(CellView::isChanged);
        }
    }

    public record CellView(
        int col,
        int row,
        SlotAddress.GridCell address,
        SlotDiff.DiffStatus status,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        @Nullable SlotAddress movedFrom,
        String deltaBadge,
        ImmutableList<Namespace> sourceEffects
    ) {
        public static CellView empty(SlotAddress.GridCell address) {
            return new CellView(
                address.col(),
                address.row(),
                address,
                SlotDiff.DiffStatus.unchanged,
                null,
                null,
                null,
                "",
                Lists.immutable.empty()
            );
        }

        public boolean isOccupied() {
            return after != null || before != null;
        }

        public boolean isChanged() {
            return status.isChanged();
        }

        public @Nullable RecipeContent displayContent() {
            return after != null ? after : before;
        }
    }

    public record SlotView(
        SlotAddress address,
        SlotDiff.DiffStatus status,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        @Nullable SlotAddress movedFrom,
        String deltaBadge,
        ImmutableList<Namespace> sourceEffects
    ) {
        public boolean isChanged() {
            return status.isChanged();
        }

        public @Nullable RecipeContent displayContent() {
            return after != null ? after : before;
        }
    }

    public record ValueView(
        SlotChannel channel,
        SlotAddress address,
        SlotDiff.DiffStatus status,
        @Nullable ValueContent<?> before,
        @Nullable ValueContent<?> after,
        String deltaBadge,
        ImmutableList<Namespace> sourceEffects
    ) {
        public @Nullable ValueContent<?> displayValue() {
            return after != null ? after : before;
        }
    }

    public record StageView(
        ProjectionStage stage,
        int executedEffectCount,
        int changeCount,
        int guardFailureCount,
        boolean blocked,
        ImmutableList<SlotDiff> slotDiffs
    ) {
        public boolean active() {
            return changeCount > 0 || blocked || guardFailureCount > 0;
        }
    }

    public record ChangeRow(
        @Nullable SlotChannel channel,
        @Nullable SlotAddress address,
        SlotDiff.DiffStatus status,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        @Nullable SlotAddress movedFrom,
        String badge,
        ImmutableList<Namespace> effectIds,
        ImmutableList<ProjectionPlugin> plugins,
        int mergedStepCount,
        @Nullable String metaSummary
    ) {
        public boolean isMeta() {
            return channel == null;
        }
    }
}
