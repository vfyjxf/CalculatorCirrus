package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.GridSpec;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributeKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributes;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionKeys;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionTags;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, composable GAS-inspired recipe projection pipeline.
 *
 * <p>Effects execute in deterministic {@code (stage, priority, declarationOrder)} order.
 * Use {@link #project} / {@link #evaluate} on hot paths ({@link TraceMode#none}) and
 * {@link #trace} when visualizing diffs, grid cell changes, or per-plugin breakdowns.
 */
public final class ProjectionPipeline {

    private static final ProjectionPipeline emptyInstance = new ProjectionPipeline(List.of());
    private static volatile long cachedEpoch = -1L;
    private static volatile @Nullable ProjectionPipeline cachedRegistryPipeline;

    private final List<ProjectionEffect> effects;

    private ProjectionPipeline(List<ProjectionEffect> effects) {
        this.effects = sortEffects(effects);
    }

    public static ProjectionPipeline empty() {
        return emptyInstance;
    }

    public static ProjectionPipeline of(ProjectionEffect... effects) {
        Checks.checkNotNull(effects, "effects");
        return effects.length == 0 ? emptyInstance : new ProjectionPipeline(List.of(effects));
    }

    public static ProjectionPipeline ofAll(Iterable<ProjectionEffect> effects) {
        Checks.checkNotNull(effects, "effects");
        List<ProjectionEffect> list = new ArrayList<>();
        for (ProjectionEffect effect : effects) {
            list.add(Checks.checkNotNull(effect, "effect"));
        }
        return list.isEmpty() ? emptyInstance : new ProjectionPipeline(list);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the shared pipeline containing all {@link ProjectionEffect}s registered on
     * {@link RecipeApi}, cached per {@link RecipeApiImpl#attachEpoch()}.
     */
    public static ProjectionPipeline fromRegistry() {
        long epoch = RecipeApiImpl.attachEpoch();
        ProjectionPipeline cached = cachedRegistryPipeline;
        if (cached != null && cachedEpoch == epoch) {
            return cached;
        }
        RecipeApi api = RecipeApiImpl.instance();
        if (api == null) {
            return emptyInstance;
        }
        List<ProjectionEffect> registered = new ArrayList<>();
        for (ProjectionEffect effect : api.projectionEffects()) {
            registered.add(effect);
        }
        ProjectionPipeline built = registered.isEmpty() ? emptyInstance : new ProjectionPipeline(registered);
        cachedRegistryPipeline = built;
        cachedEpoch = epoch;
        return built;
    }

    public List<ProjectionEffect> effects() {
        return effects;
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    //region composition

    public ProjectionPipeline with(ProjectionEffect effect) {
        Checks.checkNotNull(effect, "effect");
        List<ProjectionEffect> combined = new ArrayList<>(effects.size() + 1);
        combined.addAll(effects);
        combined.add(effect);
        return new ProjectionPipeline(combined);
    }

    public ProjectionPipeline without(Namespace effectId) {
        Checks.checkNotNull(effectId, "effectId");
        List<ProjectionEffect> filtered = new ArrayList<>(effects.size());
        for (ProjectionEffect effect : effects) {
            if (!effect.id().equals(effectId)) {
                filtered.add(effect);
            }
        }
        return filtered.isEmpty() ? emptyInstance : new ProjectionPipeline(filtered);
    }

    public ProjectionPipeline andThen(ProjectionPipeline other) {
        Checks.checkNotNull(other, "other");
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        List<ProjectionEffect> combined = new ArrayList<>(effects.size() + other.effects.size());
        combined.addAll(effects);
        combined.addAll(other.effects);
        return new ProjectionPipeline(combined);
    }

    public ProjectionPipeline forPlugin(ProjectionPlugin plugin) {
        Checks.checkNotNull(plugin, "plugin");
        List<ProjectionEffect> filtered = new ArrayList<>();
        for (ProjectionEffect effect : effects) {
            if (effect.plugin().id().equals(plugin.id())) {
                filtered.add(effect);
            }
        }
        return filtered.isEmpty() ? emptyInstance : new ProjectionPipeline(filtered);
    }

    //endregion

    //region introspection

    /**
     * Returns all effects (registered + recipe-local) that structurally match {@code projection}
     * under {@code context}. When {@code ignorePluginEnabled} is {@code true}, disabled plugins'
     * matching effects are still included so UI can surface their toggles and watched options.
     */
    public List<ProjectionEffect> applicableEffects(
        ExecutionContext context,
        RecipeProjection projection,
        boolean ignorePluginEnabled
    ) {
        Checks.checkNotNull(context, "context");
        Checks.checkNotNull(projection, "projection");
        List<ProjectionEffect> combined = effectiveEffects(projection);
        List<ProjectionEffect> matched = new ArrayList<>();
        for (ProjectionEffect effect : combined) {
            if (!ignorePluginEnabled && !effect.plugin().enabled(context)) {
                continue;
            }
            if (effect.matches(context, projection)) {
                matched.add(effect);
            }
        }
        return List.copyOf(matched);
    }

    //endregion

    //region execution

    public ProjectedRecipe project(ExecutionContext context, RecipeProjection projection) {
        return run(context, projection, TraceMode.none).projectedRecipe();
    }

    public ProjectionResult evaluate(ExecutionContext context, RecipeProjection projection) {
        return run(context, projection, TraceMode.none);
    }

    public ProjectionResult trace(ExecutionContext context, RecipeProjection projection) {
        return run(context, projection, TraceMode.full);
    }

    public ProjectionResult run(ExecutionContext context, RecipeProjection projection, TraceMode traceMode) {
        Checks.checkNotNull(context, "context");
        Checks.checkNotNull(projection, "projection");
        Checks.checkNotNull(traceMode, "traceMode");

        List<ProjectionEffect> ordered = effectiveEffects(projection);
        if (ordered.isEmpty()) {
            ProjectionDiff diff = traceMode == TraceMode.none
                    ? null
                    : ProjectionDiff.between(projection, projection);
            return new ProjectionResult(
                projection,
                projection,
                true,
                Lists.immutable.empty(),
                Lists.immutable.empty(),
                diff
            );
        }

        boolean recordSteps = traceMode == TraceMode.full;
        WorkingState state = new WorkingState(projection);
        List<ProjectionGuard.Finding> allFindings = new ArrayList<>();
        List<PipelineStepTrace> stepTraces = recordSteps ? new ArrayList<>(ordered.size()) : List.of();
        boolean runnable = true;

        for (ProjectionEffect effect : ordered) {
            if (!effect.plugin().enabled(context)) {
                continue;
            }
            if (!effect.matches(context, state)) {
                continue;
            }

            List<ProjectionGuard.Finding> stepFindings = effect.guards().isEmpty()
                    ? List.of()
                    : new ArrayList<>(effect.guards().size());
            boolean blockedByGuard = false;
            boolean skippedByNonBlockingGuard = false;
            for (ProjectionGuard guard : effect.guards()) {
                ProjectionGuard.Result res = guard.evaluate(context, state);
                if (!res.passed()) {
                    ProjectionGuard.Finding finding = new ProjectionGuard.Finding(
                        effect.id(),
                        guard.id(),
                        false,
                        guard.blocking(),
                        res.reasonCode()
                    );
                    stepFindings.add(finding);
                    allFindings.add(finding);
                    if (guard.blocking()) {
                        blockedByGuard = true;
                        runnable = false;
                    } else {
                        skippedByNonBlockingGuard = true;
                    }
                }
            }

            if (blockedByGuard || skippedByNonBlockingGuard) {
                if (recordSteps) {
                    stepTraces.add(
                        new PipelineStepTrace(
                            effect.id(),
                            effect.plugin(),
                            effect.stage(),
                            effect.priority(),
                            false,
                            blockedByGuard,
                            Lists.immutable.withAll(stepFindings),
                            Lists.immutable.empty()
                        )
                    );
                }
                continue;
            }

            TransactionImpl tx = new TransactionImpl(context, state, recordSteps);
            effect.apply(tx);
            if (tx.blockedReason != null) {
                runnable = false;
                ProjectionGuard.Finding txBlock = new ProjectionGuard.Finding(
                    effect.id(),
                    effect.id(),
                    false,
                    true,
                    tx.blockedReason
                );
                if (stepFindings.isEmpty()) {
                    stepFindings = List.of(txBlock);
                } else {
                    stepFindings.add(txBlock);
                }
                allFindings.add(txBlock);
            }

            if (recordSteps) {
                stepTraces.add(
                    new PipelineStepTrace(
                        effect.id(),
                        effect.plugin(),
                        effect.stage(),
                        effect.priority(),
                        true,
                        tx.blockedReason != null,
                        Lists.immutable.withAll(stepFindings),
                        Lists.immutable.withAll(tx.changes)
                    )
                );
            }
        }

        RecipeProjection finalProjection = state.toProjection();
        var immutableSteps = Lists.immutable.withAll(stepTraces);
        ProjectionDiff diff = switch (traceMode) {
            case none -> null;
            case netDiff -> ProjectionDiff.between(projection, finalProjection);
            case full -> ProjectionDiff.fromTrace(projection, finalProjection, immutableSteps);
        };
        return new ProjectionResult(
            projection,
            finalProjection,
            runnable,
            Lists.immutable.withAll(allFindings),
            immutableSteps,
            diff
        );
    }

    //endregion

    private List<ProjectionEffect> effectiveEffects(RecipeProjection projection) {
        List<ProjectionEffect> local = projection.localEffects();
        if (local.isEmpty()) {
            return effects;
        }
        if (effects.isEmpty()) {
            return sortEffects(local);
        }
        List<ProjectionEffect> combined = new ArrayList<>(effects.size() + local.size());
        combined.addAll(effects);
        combined.addAll(local);
        return sortEffects(combined);
    }

    private static List<ProjectionEffect> sortEffects(List<ProjectionEffect> input) {
        if (input.size() <= 1) {
            return List.copyOf(input);
        }
        List<IndexedEffect> indexed = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            indexed.add(new IndexedEffect(i, input.get(i)));
        }
        indexed.sort(
            Comparator.comparingInt((IndexedEffect e) -> e.effect.stage().ordinal())
                    .thenComparingInt(e -> e.effect.priority())
                    .thenComparingInt(e -> e.index)
        );
        List<ProjectionEffect> out = new ArrayList<>(indexed.size());
        for (IndexedEffect item : indexed) {
            out.add(item.effect);
        }
        return List.copyOf(out);
    }

    private record IndexedEffect(int index, ProjectionEffect effect) {}

    public static final class Builder {
        private final List<ProjectionEffect> effects = new ArrayList<>();

        private Builder() {}

        public Builder add(ProjectionEffect effect) {
            effects.add(Checks.checkNotNull(effect, "effect"));
            return this;
        }

        public Builder addAll(Iterable<ProjectionEffect> extra) {
            for (ProjectionEffect effect : extra) {
                effects.add(Checks.checkNotNull(effect, "effect"));
            }
            return this;
        }

        public ProjectionPipeline build() {
            return effects.isEmpty() ? emptyInstance : new ProjectionPipeline(effects);
        }
    }

    //region mutable working state & transaction

    private static final class WorkingState implements ProjectionView {
        private final RecipeEntry<?> recipe;
        private final LinkedHashMap<SlotAddress, ProjectionSlot<InputContent>> inputs = new LinkedHashMap<>();
        private final LinkedHashMap<SlotAddress, ProjectionSlot<OutputContent>> outputs = new LinkedHashMap<>();
        private ProjectionAttributes attributes;
        private ProjectionTags tags;
        private int nextInputIndex;
        private int nextOutputIndex;

        private WorkingState(RecipeProjection source) {
            this.recipe = source.recipe();
            this.attributes = source.attributes();
            this.tags = source.tags();
            int maxIn = -1;
            for (ProjectionSlot<InputContent> slot : source.inputs()) {
                inputs.put(slot.address(), slot.snapshot());
                if (slot.address() instanceof SlotAddress.Indexed idx) {
                    maxIn = Math.max(maxIn, idx.index());
                }
            }
            int maxOut = -1;
            for (ProjectionSlot<OutputContent> slot : source.outputs()) {
                outputs.put(slot.address(), slot.snapshot());
                if (slot.address() instanceof SlotAddress.Indexed idx) {
                    maxOut = Math.max(maxOut, idx.index());
                }
            }
            this.nextInputIndex = Math.max(source.inputs().size(), maxIn + 1);
            this.nextOutputIndex = Math.max(source.outputs().size(), maxOut + 1);
        }

        @Override
        public RecipeEntry<?> recipe() {
            return recipe;
        }

        @Override
        public List<ProjectionSlot<InputContent>> inputSlots() {
            return List.copyOf(inputs.values());
        }

        @Override
        public List<ProjectionSlot<OutputContent>> outputSlots() {
            return List.copyOf(outputs.values());
        }

        @Override
        public @Nullable ProjectionSlot<? extends RecipeContent> slot(SlotAddress address) {
            return address.channel() == SlotChannel.input ? inputs.get(address) : outputs.get(address);
        }

        @Override
        public @Nullable GridSpec grid(SlotChannel channel) {
            return channel == SlotChannel.input
                    ? attributes.find(ProjectionKeys.inputGrid)
                    : attributes.find(ProjectionKeys.outputGrid);
        }

        @Override
        public ProjectionAttributes attributes() {
            return attributes;
        }

        @Override
        public ProjectionTags tags() {
            return tags;
        }

        private SlotAddress allocateIndexed(SlotChannel channel) {
            if (channel == SlotChannel.input) {
                SlotAddress addr = SlotAddress.input(nextInputIndex++);
                while (inputs.containsKey(addr)) {
                    addr = SlotAddress.input(nextInputIndex++);
                }
                return addr;
            }
            SlotAddress addr = SlotAddress.output(nextOutputIndex++);
            while (outputs.containsKey(addr)) {
                addr = SlotAddress.output(nextOutputIndex++);
            }
            return addr;
        }

        private RecipeProjection toProjection() {
            return new RecipeProjection(
                recipe,
                Lists.immutable.withAll(inputs.values()),
                Lists.immutable.withAll(outputs.values()),
                attributes,
                tags
            );
        }
    }

    private static final class TransactionImpl implements ProjectionTransaction {
        private final ExecutionContext context;
        private final WorkingState state;
        private final boolean recordChanges;
        private final List<ProjectionChange> changes;
        private @Nullable String blockedReason;

        private TransactionImpl(ExecutionContext context, WorkingState state, boolean recordChanges) {
            this.context = context;
            this.state = state;
            this.recordChanges = recordChanges;
            this.changes = recordChanges ? new ArrayList<>(4) : List.of();
        }

        @Override
        public ExecutionContext context() {
            return context;
        }

        @Override
        public ProjectionView view() {
            return state;
        }

        @Override
        public SlotAddress addInput(InputContent content) {
            Checks.checkNotNull(content, "content");
            SlotAddress addr = state.allocateIndexed(SlotChannel.input);
            setSlot(addr, content, ChangeReason.added);
            return addr;
        }

        @Override
        public SlotAddress addOutput(OutputContent content) {
            Checks.checkNotNull(content, "content");
            SlotAddress addr = state.allocateIndexed(SlotChannel.output);
            setSlot(addr, content, ChangeReason.added);
            return addr;
        }

        @Override
        public SlotAddress addNamedInput(String name, InputContent content) {
            Checks.checkNotNull(content, "content");
            SlotAddress addr = SlotAddress.inputNamed(name);
            setSlot(addr, content, ChangeReason.added);
            return addr;
        }

        @Override
        public SlotAddress addNamedOutput(String name, OutputContent content) {
            Checks.checkNotNull(content, "content");
            SlotAddress addr = SlotAddress.outputNamed(name);
            setSlot(addr, content, ChangeReason.added);
            return addr;
        }

        @Override
        public void setSlot(SlotAddress address, RecipeContent content, ChangeReason reason) {
            Checks.checkNotNull(address, "address");
            Checks.checkNotNull(content, "content");
            Checks.checkNotNull(reason, "reason");
            RecipeContent snapped = ProjectionContentEquality.snapshot(content);

            if (address.channel() == SlotChannel.input) {
                Checks.checkArgument(snapped instanceof InputContent, "input slot requires InputContent");
                InputContent inContent = (InputContent) snapped;
                ProjectionSlot<InputContent> existing = state.inputs.get(address);
                if (existing == null) {
                    state.inputs.put(address, ProjectionSlot.of(address, inContent));
                    record(new ProjectionChange.SlotChange(address, null, null, inContent, ChangeReason.added));
                } else {
                    if (ProjectionContentEquality.sameContent(existing.content(), inContent)) {
                        return;
                    }
                    state.inputs.put(address, existing.withContent(inContent));
                    ChangeReason effectiveReason = reason == ChangeReason.added ? ChangeReason.adjusted : reason;
                    record(new ProjectionChange.SlotChange(address, null, existing.content(), inContent, effectiveReason));
                }
            } else {
                Checks.checkArgument(snapped instanceof OutputContent, "output slot requires OutputContent");
                OutputContent outContent = (OutputContent) snapped;
                ProjectionSlot<OutputContent> existing = state.outputs.get(address);
                if (existing == null) {
                    state.outputs.put(address, ProjectionSlot.of(address, outContent));
                    record(new ProjectionChange.SlotChange(address, null, null, outContent, ChangeReason.added));
                } else {
                    if (ProjectionContentEquality.sameContent(existing.content(), outContent)) {
                        return;
                    }
                    state.outputs.put(address, existing.withContent(outContent));
                    ChangeReason effectiveReason = reason == ChangeReason.added ? ChangeReason.adjusted : reason;
                    record(new ProjectionChange.SlotChange(address, null, existing.content(), outContent, effectiveReason));
                }
            }
        }

        @Override
        public void setSlotAmount(SlotAddress address, long newAmount) {
            ProjectionSlot<? extends RecipeContent> existing = state.slot(address);
            if (existing == null) {
                return;
            }
            if (newAmount <= 0L) {
                removeSlot(address);
                return;
            }
            if (existing.amount() == newAmount) {
                return;
            }
            setSlot(address, existing.content().copyWithAmount(newAmount), ChangeReason.adjusted);
        }

        @Override
        public void setSlotChance(SlotAddress address, float newChance) {
            ProjectionSlot<? extends RecipeContent> existing = state.slot(address);
            if (existing == null || Float.compare(existing.chance(), newChance) == 0) {
                return;
            }
            setSlot(address, existing.content().copyWithChance(newChance), ChangeReason.adjusted);
        }

        @Override
        public void removeSlot(SlotAddress address) {
            Checks.checkNotNull(address, "address");
            if (address.channel() == SlotChannel.input) {
                ProjectionSlot<InputContent> removed = state.inputs.remove(address);
                if (removed != null) {
                    record(new ProjectionChange.SlotChange(address, null, removed.content(), null, ChangeReason.removed));
                }
            } else {
                ProjectionSlot<OutputContent> removed = state.outputs.remove(address);
                if (removed != null) {
                    record(new ProjectionChange.SlotChange(address, null, removed.content(), null, ChangeReason.removed));
                }
            }
        }

        @Override
        public void moveSlot(SlotAddress from, SlotAddress to) {
            Checks.checkNotNull(from, "from");
            Checks.checkNotNull(to, "to");
            Checks.checkArgument(from.channel() == to.channel(), "moveSlot requires matching channels");
            if (from.equals(to)) {
                return;
            }
            if (from.channel() == SlotChannel.input) {
                ProjectionSlot<InputContent> moved = state.inputs.remove(from);
                if (moved == null) {
                    return;
                }
                ProjectionSlot<InputContent> overwritten = state.inputs.put(to, moved.withAddress(to));
                record(new ProjectionChange.SlotChange(from, null, moved.content(), null, ChangeReason.removed));
                record(
                    new ProjectionChange.SlotChange(
                        to,
                        from,
                        overwritten == null ? null : overwritten.content(),
                        moved.content(),
                        ChangeReason.moved
                    )
                );
            } else {
                ProjectionSlot<OutputContent> moved = state.outputs.remove(from);
                if (moved == null) {
                    return;
                }
                ProjectionSlot<OutputContent> overwritten = state.outputs.put(to, moved.withAddress(to));
                record(new ProjectionChange.SlotChange(from, null, moved.content(), null, ChangeReason.removed));
                record(
                    new ProjectionChange.SlotChange(
                        to,
                        from,
                        overwritten == null ? null : overwritten.content(),
                        moved.content(),
                        ChangeReason.moved
                    )
                );
            }
        }

        @Override
        public void setGrid(SlotChannel channel, GridSpec spec) {
            Checks.checkNotNull(channel, "channel");
            Checks.checkNotNull(spec, "spec");
            putAttribute(channel == SlotChannel.input ? ProjectionKeys.inputGrid : ProjectionKeys.outputGrid, spec);
        }

        @Override
        public SlotAddress.GridCell setGridCell(
            SlotChannel channel,
            int col,
            int row,
            RecipeContent content,
            ChangeReason reason
        ) {
            GridSpec spec = Checks.checkNotNull(state.grid(channel), "grid spec not defined for channel: " + channel);
            Checks.checkArgument(spec.contains(col, row), "grid cell out of bounds: (" + col + ", " + row + ")");
            SlotAddress.GridCell cell = SlotAddress.grid(channel, col, row, spec.width());
            setSlot(cell, content, reason);
            return cell;
        }

        @Override
        public void clearGridCell(SlotChannel channel, int col, int row) {
            GridSpec spec = state.grid(channel);
            if (spec == null || !spec.contains(col, row)) {
                return;
            }
            removeSlot(SlotAddress.grid(channel, col, row, spec.width()));
        }

        @Override
        public void moveGridCell(SlotChannel channel, int fromCol, int fromRow, int toCol, int toRow) {
            GridSpec spec = Checks.checkNotNull(state.grid(channel), "grid spec not defined for channel: " + channel);
            Checks.checkArgument(spec.contains(fromCol, fromRow), "source grid cell out of bounds");
            Checks.checkArgument(spec.contains(toCol, toRow), "target grid cell out of bounds");
            moveSlot(
                SlotAddress.grid(channel, fromCol, fromRow, spec.width()),
                SlotAddress.grid(channel, toCol, toRow, spec.width())
            );
        }

        @Override
        public <T> void putAttribute(ProjectionAttributeKey<T> key, T value) {
            Object before = state.attributes.find(key);
            if (Objects.equals(before, value)) {
                return;
            }
            state.attributes = state.attributes.with(key, value);
            record(new ProjectionChange.AttributeChange(null, key, before, value));
        }

        @Override
        public void removeAttribute(ProjectionAttributeKey<?> key) {
            Object before = state.attributes.find(key);
            if (before == null) {
                return;
            }
            state.attributes = state.attributes.without(key);
            record(new ProjectionChange.AttributeChange(null, key, before, null));
        }

        @Override
        public <T> void putSlotAttribute(SlotAddress address, ProjectionAttributeKey<T> key, T value) {
            if (address.channel() == SlotChannel.input) {
                ProjectionSlot<InputContent> slot = state.inputs.get(address);
                if (slot == null) {
                    return;
                }
                Object before = slot.attribute(key);
                if (Objects.equals(before, value)) {
                    return;
                }
                state.inputs.put(address, slot.withAttribute(key, value));
                record(new ProjectionChange.AttributeChange(address, key, before, value));
            } else {
                ProjectionSlot<OutputContent> slot = state.outputs.get(address);
                if (slot == null) {
                    return;
                }
                Object before = slot.attribute(key);
                if (Objects.equals(before, value)) {
                    return;
                }
                state.outputs.put(address, slot.withAttribute(key, value));
                record(new ProjectionChange.AttributeChange(address, key, before, value));
            }
        }

        @Override
        public void removeSlotAttribute(SlotAddress address, ProjectionAttributeKey<?> key) {
            if (address.channel() == SlotChannel.input) {
                ProjectionSlot<InputContent> slot = state.inputs.get(address);
                if (slot == null) {
                    return;
                }
                Object before = slot.attribute(key);
                if (before == null) {
                    return;
                }
                state.inputs.put(address, slot.withoutAttribute(key));
                record(new ProjectionChange.AttributeChange(address, key, before, null));
            } else {
                ProjectionSlot<OutputContent> slot = state.outputs.get(address);
                if (slot == null) {
                    return;
                }
                Object before = slot.attribute(key);
                if (before == null) {
                    return;
                }
                state.outputs.put(address, slot.withoutAttribute(key));
                record(new ProjectionChange.AttributeChange(address, key, before, null));
            }
        }

        @Override
        public void addTag(Namespace tag) {
            if (state.tags.has(tag)) {
                return;
            }
            state.tags = state.tags.with(tag);
            record(new ProjectionChange.TagChange(null, tag, true));
        }

        @Override
        public void removeTag(Namespace tag) {
            if (!state.tags.has(tag)) {
                return;
            }
            state.tags = state.tags.without(tag);
            record(new ProjectionChange.TagChange(null, tag, false));
        }

        @Override
        public void addSlotTag(SlotAddress address, Namespace tag) {
            if (address.channel() == SlotChannel.input) {
                ProjectionSlot<InputContent> slot = state.inputs.get(address);
                if (slot == null || slot.hasTag(tag)) {
                    return;
                }
                state.inputs.put(address, slot.withTag(tag));
                record(new ProjectionChange.TagChange(address, tag, true));
            } else {
                ProjectionSlot<OutputContent> slot = state.outputs.get(address);
                if (slot == null || slot.hasTag(tag)) {
                    return;
                }
                state.outputs.put(address, slot.withTag(tag));
                record(new ProjectionChange.TagChange(address, tag, true));
            }
        }

        @Override
        public void removeSlotTag(SlotAddress address, Namespace tag) {
            if (address.channel() == SlotChannel.input) {
                ProjectionSlot<InputContent> slot = state.inputs.get(address);
                if (slot == null || !slot.hasTag(tag)) {
                    return;
                }
                state.inputs.put(address, slot.withoutTag(tag));
                record(new ProjectionChange.TagChange(address, tag, false));
            } else {
                ProjectionSlot<OutputContent> slot = state.outputs.get(address);
                if (slot == null || !slot.hasTag(tag)) {
                    return;
                }
                state.outputs.put(address, slot.withoutTag(tag));
                record(new ProjectionChange.TagChange(address, tag, false));
            }
        }

        @Override
        public void block(String reasonCode) {
            this.blockedReason = Checks.checkNotNull(reasonCode, "reasonCode");
        }

        private void record(ProjectionChange change) {
            if (recordChanges) {
                changes.add(change);
            }
        }
    }

    //endregion
}
