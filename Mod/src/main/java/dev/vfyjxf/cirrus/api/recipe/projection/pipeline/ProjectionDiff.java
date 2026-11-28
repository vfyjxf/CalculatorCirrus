package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.GridSpec;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributeKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
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
import java.util.Objects;
import java.util.Set;

/**
 * First-class, address-aware net diff between an original and projected {@link RecipeProjection},
 * including 2D {@link GridDiff}s, loose slot diffs, attribute diffs, and tag diffs.
 */
public final class ProjectionDiff {

    private final RecipeProjection original;
    private final RecipeProjection projected;
    private final Map<SlotAddress, SlotDiff> slotDiffsByAddress;
    private final ImmutableList<SlotDiff> slotDiffs;
    private final @Nullable GridDiff inputGridDiff;
    private final @Nullable GridDiff outputGridDiff;
    private final ImmutableList<ProjectionChange.AttributeChange> attributeDiffs;
    private final ImmutableList<ProjectionChange.TagChange> tagDiffs;

    private ProjectionDiff(
        RecipeProjection original,
        RecipeProjection projected,
        Map<SlotAddress, SlotDiff> slotDiffsByAddress,
        ImmutableList<SlotDiff> slotDiffs,
        @Nullable GridDiff inputGridDiff,
        @Nullable GridDiff outputGridDiff,
        ImmutableList<ProjectionChange.AttributeChange> attributeDiffs,
        ImmutableList<ProjectionChange.TagChange> tagDiffs
    ) {
        this.original = original;
        this.projected = projected;
        this.slotDiffsByAddress = slotDiffsByAddress;
        this.slotDiffs = slotDiffs;
        this.inputGridDiff = inputGridDiff;
        this.outputGridDiff = outputGridDiff;
        this.attributeDiffs = attributeDiffs;
        this.tagDiffs = tagDiffs;
    }

    /**
     * Computes a structural net diff directly between any two {@link RecipeProjection} states.
     */
    public static ProjectionDiff between(RecipeProjection original, RecipeProjection projected) {
        return compute(original, projected, Lists.immutable.empty());
    }

    /**
     * Computes a net diff enriched with effect/plugin provenance and move/transform semantics from step traces.
     */
    public static ProjectionDiff fromTrace(
        RecipeProjection original,
        RecipeProjection projected,
        ImmutableList<PipelineStepTrace> steps
    ) {
        return compute(original, projected, steps);
    }

    private static ProjectionDiff compute(
        RecipeProjection original,
        RecipeProjection projected,
        ImmutableList<PipelineStepTrace> steps
    ) {
        Checks.checkNotNull(original, "original");
        Checks.checkNotNull(projected, "projected");

        // Gather per-address attribution from step traces
        Map<SlotAddress, LinkedHashSet<Namespace>> effectsBySlot = new LinkedHashMap<>();
        Map<SlotAddress, LinkedHashSet<ProjectionPlugin>> pluginsBySlot = new LinkedHashMap<>();
        Map<SlotAddress, SlotAddress> movedFromBySlot = new LinkedHashMap<>();
        Set<SlotAddress> transformedSlots = new LinkedHashSet<>();

        for (PipelineStepTrace step : steps) {
            for (ProjectionChange change : step.changes()) {
                if (change instanceof ProjectionChange.SlotChange sc) {
                    effectsBySlot.computeIfAbsent(sc.address(), ignored -> new LinkedHashSet<>()).add(step.effectId());
                    pluginsBySlot.computeIfAbsent(sc.address(), ignored -> new LinkedHashSet<>()).add(step.plugin());
                    if (sc.reason() == ChangeReason.moved && sc.movedFrom() != null) {
                        movedFromBySlot.putIfAbsent(sc.address(), sc.movedFrom());
                    }
                    if (sc.reason() == ChangeReason.transformed) {
                        transformedSlots.add(sc.address());
                    }
                }
            }
        }

        LinkedHashMap<SlotAddress, RecipeContent> beforeSlots = new LinkedHashMap<>();
        for (ProjectionSlot<?> slot : original.inputs()) {
            beforeSlots.put(slot.address(), slot.content());
        }
        for (ProjectionSlot<?> slot : original.outputs()) {
            beforeSlots.put(slot.address(), slot.content());
        }

        LinkedHashMap<SlotAddress, RecipeContent> afterSlots = new LinkedHashMap<>();
        for (ProjectionSlot<?> slot : projected.inputs()) {
            afterSlots.put(slot.address(), slot.content());
        }
        for (ProjectionSlot<?> slot : projected.outputs()) {
            afterSlots.put(slot.address(), slot.content());
        }

        LinkedHashSet<SlotAddress> allAddresses = new LinkedHashSet<>();
        allAddresses.addAll(beforeSlots.keySet());
        allAddresses.addAll(afterSlots.keySet());

        LinkedHashMap<SlotAddress, SlotDiff> byAddress = new LinkedHashMap<>();
        List<SlotDiff> orderedDiffs = new ArrayList<>(allAddresses.size());
        for (SlotAddress address : allAddresses) {
            RecipeContent before = beforeSlots.get(address);
            RecipeContent after = afterSlots.get(address);
            SlotAddress movedFrom = movedFromBySlot.get(address);
            SlotDiff.DiffStatus status = classifySlotDiff(before, after, movedFrom, transformedSlots.contains(address));
            ImmutableList<Namespace> effects = Lists.immutable
                    .withAll(effectsBySlot.getOrDefault(address, new LinkedHashSet<>()));
            ImmutableList<ProjectionPlugin> plugins = Lists.immutable
                    .withAll(pluginsBySlot.getOrDefault(address, new LinkedHashSet<>()));
            SlotDiff diff = new SlotDiff(address, status, before, after, movedFrom, effects, plugins);
            byAddress.put(address, diff);
            orderedDiffs.add(diff);
        }

        GridDiff inGrid = buildGridDiff(SlotChannel.input, original.inputGrid(), projected.inputGrid(), byAddress);
        GridDiff outGrid = buildGridDiff(SlotChannel.output, original.outputGrid(), projected.outputGrid(), byAddress);

        List<ProjectionChange.AttributeChange> attrDiffs = diffAttributes(null, original.attributes().values(), projected.attributes().values());
        List<ProjectionChange.TagChange> tagDiffs = diffTags(null, original.tags().values(), projected.tags().values());

        return new ProjectionDiff(
            original,
            projected,
            Map.copyOf(byAddress),
            Lists.immutable.withAll(orderedDiffs),
            inGrid,
            outGrid,
            Lists.immutable.withAll(attrDiffs),
            Lists.immutable.withAll(tagDiffs)
        );
    }

    private static SlotDiff.DiffStatus classifySlotDiff(
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        @Nullable SlotAddress movedFrom,
        boolean markedTransformed
    ) {
        if (before == null && after == null) {
            return SlotDiff.DiffStatus.unchanged;
        }
        if (before == null) {
            return movedFrom != null ? SlotDiff.DiffStatus.moved : SlotDiff.DiffStatus.added;
        }
        if (after == null) {
            return SlotDiff.DiffStatus.removed;
        }
        if (ProjectionContentEquality.sameContent(before, after)) {
            return SlotDiff.DiffStatus.unchanged;
        }
        if (markedTransformed || !ProjectionContentEquality.sameIdentity(before, after)) {
            return SlotDiff.DiffStatus.transformed;
        }
        return SlotDiff.DiffStatus.adjusted;
    }

    private static @Nullable GridDiff buildGridDiff(
        SlotChannel channel,
        @Nullable GridSpec beforeSpec,
        @Nullable GridSpec afterSpec,
        Map<SlotAddress, SlotDiff> byAddress
    ) {
        GridSpec spec = afterSpec != null ? afterSpec : beforeSpec;
        if (spec == null) {
            return null;
        }
        List<GridDiff.CellDiff> cells = new ArrayList<>(spec.cellCount());
        for (int row = 0; row < spec.height(); row++) {
            for (int col = 0; col < spec.width(); col++) {
                SlotAddress.GridCell cellAddr = SlotAddress.grid(channel, col, row, spec.width());
                SlotDiff slotDiff = byAddress.get(cellAddr);
                if (slotDiff != null) {
                    cells.add(GridDiff.CellDiff.fromSlotDiff(cellAddr, slotDiff));
                } else {
                    cells.add(GridDiff.CellDiff.empty(channel, col, row, spec.width()));
                }
            }
        }
        return new GridDiff(channel, spec, Lists.immutable.withAll(cells));
    }

    private static List<ProjectionChange.AttributeChange> diffAttributes(
        @Nullable SlotAddress target,
        Map<ProjectionAttributeKey<?>, Object> before,
        Map<ProjectionAttributeKey<?>, Object> after
    ) {
        LinkedHashSet<ProjectionAttributeKey<?>> keys = new LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        List<ProjectionChange.AttributeChange> out = new ArrayList<>();
        for (ProjectionAttributeKey<?> key : keys) {
            Object b = before.get(key);
            Object a = after.get(key);
            if (!Objects.equals(b, a)) {
                out.add(new ProjectionChange.AttributeChange(target, key, b, a));
            }
        }
        return out;
    }

    private static List<ProjectionChange.TagChange> diffTags(
        @Nullable SlotAddress target,
        Set<Namespace> before,
        Set<Namespace> after
    ) {
        List<ProjectionChange.TagChange> out = new ArrayList<>();
        for (Namespace tag : before) {
            if (!after.contains(tag)) {
                out.add(new ProjectionChange.TagChange(target, tag, false));
            }
        }
        for (Namespace tag : after) {
            if (!before.contains(tag)) {
                out.add(new ProjectionChange.TagChange(target, tag, true));
            }
        }
        return out;
    }

    public RecipeProjection original() {
        return original;
    }

    public RecipeProjection projected() {
        return projected;
    }

    public ImmutableList<SlotDiff> slotDiffs() {
        return slotDiffs;
    }

    public @Nullable SlotDiff slotDiff(SlotAddress address) {
        return slotDiffsByAddress.get(address);
    }

    public List<SlotDiff> slotDiffs(SlotChannel channel) {
        List<SlotDiff> out = new ArrayList<>();
        for (SlotDiff diff : slotDiffs) {
            if (diff.channel() == channel) {
                out.add(diff);
            }
        }
        return List.copyOf(out);
    }

    public List<SlotDiff> changedSlotDiffs() {
        List<SlotDiff> out = new ArrayList<>();
        for (SlotDiff diff : slotDiffs) {
            if (diff.isChanged()) {
                out.add(diff);
            }
        }
        return List.copyOf(out);
    }

    public List<SlotDiff> changedSlotDiffs(SlotChannel channel) {
        List<SlotDiff> out = new ArrayList<>();
        for (SlotDiff diff : slotDiffs) {
            if (diff.channel() == channel && diff.isChanged()) {
                out.add(diff);
            }
        }
        return List.copyOf(out);
    }

    public @Nullable GridDiff gridDiff(SlotChannel channel) {
        return channel == SlotChannel.input ? inputGridDiff : outputGridDiff;
    }

    public @Nullable GridDiff inputGridDiff() {
        return inputGridDiff;
    }

    public @Nullable GridDiff outputGridDiff() {
        return outputGridDiff;
    }

    /**
     * Returns non-grid slots (indexed & named) on the given channel.
     */
    public List<SlotDiff> looseSlotDiffs(SlotChannel channel) {
        GridDiff grid = gridDiff(channel);
        List<SlotDiff> out = new ArrayList<>();
        for (SlotDiff diff : slotDiffs) {
            if (diff.channel() != channel) {
                continue;
            }
            if (grid != null && diff.address() instanceof SlotAddress.GridCell cell
                    && grid.spec().contains(cell.col(), cell.row())) {
                continue;
            }
            out.add(diff);
        }
        return List.copyOf(out);
    }

    public ImmutableList<ProjectionChange.AttributeChange> attributeDiffs() {
        return attributeDiffs;
    }

    public ImmutableList<ProjectionChange.TagChange> tagDiffs() {
        return tagDiffs;
    }

    public boolean hasChanges() {
        if (!attributeDiffs.isEmpty() || !tagDiffs.isEmpty()) {
            return true;
        }
        for (SlotDiff diff : slotDiffs) {
            if (diff.isChanged()) {
                return true;
            }
        }
        return false;
    }

    public int changedCount(SlotChannel channel) {
        int count = 0;
        for (SlotDiff diff : slotDiffs) {
            if (diff.channel() == channel && diff.isChanged()) {
                count++;
            }
        }
        return count;
    }
}
