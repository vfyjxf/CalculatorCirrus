package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionView;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Immutable GAS-inspired recipe state snapshot consisting of addressed {@link ProjectionSlot}s,
 * typed {@link ProjectionAttributes}, and hierarchical {@link ProjectionTags}.
 */
public record RecipeProjection(
    RecipeEntry<?> recipe,
    ImmutableList<ProjectionSlot<InputContent>> inputs,
    ImmutableList<ProjectionSlot<OutputContent>> outputs,
    ProjectionAttributes attributes,
    ProjectionTags tags
) implements ProjectionView {

    public RecipeProjection {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(inputs, "inputs");
        Checks.checkNotNull(outputs, "outputs");
        Checks.checkNotNull(attributes, "attributes");
        Checks.checkNotNull(tags, "tags");
    }

    //region factories

    public static <R> RecipeProjection create(
        RecipeSystem<R> system,
        R recipe,
        Collection<? extends InputContent> inputs,
        Collection<? extends OutputContent> outputs
    ) {
        return create(RecipeEntry.of(system, recipe), inputs, outputs);
    }

    public static <R> RecipeProjection create(
        RecipeEntry<R> entry,
        Collection<? extends InputContent> inputs,
        Collection<? extends OutputContent> outputs
    ) {
        Checks.checkNotNull(entry, "entry");
        Checks.checkNotNull(inputs, "inputs");
        Checks.checkNotNull(outputs, "outputs");
        Checks.checkArgument(!inputs.isEmpty(), "inputs cannot be empty");
        Checks.checkArgument(!outputs.isEmpty(), "outputs cannot be empty");

        var inSlots = Lists.mutable.<ProjectionSlot<InputContent>>ofInitialCapacity(inputs.size());
        int idx = 0;
        for (InputContent input : inputs) {
            inSlots.add(ProjectionSlot.of(SlotAddress.input(idx++), input));
        }
        var outSlots = Lists.mutable.<ProjectionSlot<OutputContent>>ofInitialCapacity(outputs.size());
        idx = 0;
        for (OutputContent output : outputs) {
            outSlots.add(ProjectionSlot.of(SlotAddress.output(idx++), output));
        }
        return new RecipeProjection(
            entry,
            inSlots.toImmutable(),
            outSlots.toImmutable(),
            ProjectionAttributes.empty(),
            ProjectionTags.empty()
        );
    }

    public static Builder builder(RecipeEntry<?> entry) {
        return new Builder(entry);
    }

    public static <R> Builder builder(RecipeSystem<R> system, R recipe) {
        return new Builder(RecipeEntry.of(system, recipe));
    }

    //endregion

    //region ProjectionView

    @Override
    public List<ProjectionSlot<InputContent>> inputSlots() {
        return inputs.castToList();
    }

    @Override
    public List<ProjectionSlot<OutputContent>> outputSlots() {
        return outputs.castToList();
    }

    @Override
    public @Nullable ProjectionSlot<? extends RecipeContent> slot(SlotAddress address) {
        Checks.checkNotNull(address, "address");
        if (address.channel() == SlotChannel.input) {
            for (ProjectionSlot<InputContent> slot : inputs) {
                if (slot.address().equals(address)) {
                    return slot;
                }
            }
        } else {
            for (ProjectionSlot<OutputContent> slot : outputs) {
                if (slot.address().equals(address)) {
                    return slot;
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable GridSpec grid(SlotChannel channel) {
        return channel == SlotChannel.input
                ? attributes.find(ProjectionKeys.inputGrid)
                : attributes.find(ProjectionKeys.outputGrid);
    }

    //endregion

    //region immutable transforms & views

    public RecipeProjection withTag(Namespace tag) {
        return new RecipeProjection(recipe, inputs, outputs, attributes, tags.with(tag));
    }

    public RecipeProjection withoutTag(Namespace tag) {
        return new RecipeProjection(recipe, inputs, outputs, attributes, tags.without(tag));
    }

    public <T> RecipeProjection withAttribute(ProjectionAttributeKey<T> key, T value) {
        return new RecipeProjection(recipe, inputs, outputs, attributes.with(key, value), tags);
    }

    public RecipeProjection withLocalEffect(ProjectionEffect effect) {
        Checks.checkNotNull(effect, "effect");
        List<ProjectionEffect> existing = attributes.find(ProjectionKeys.localEffects);
        ArrayList<ProjectionEffect> next = existing == null ? new ArrayList<>(1) : new ArrayList<>(existing);
        next.add(effect);
        return withAttribute(ProjectionKeys.localEffects, List.copyOf(next));
    }

    public List<ProjectionEffect> localEffects() {
        List<ProjectionEffect> found = attributes.find(ProjectionKeys.localEffects);
        return found == null ? List.of() : found;
    }

    public ProjectedRecipe toProjectedRecipe() {
        return new ProjectedRecipe(
            recipe,
            inputs.collect(ProjectionSlot::content),
            outputs.collect(ProjectionSlot::content)
        );
    }

    /**
     * Deep-copies mutable underlying stack contents while sharing immutable slot/tag/attribute metadata.
     */
    public RecipeProjection snapshot() {
        var copiedInputs = Lists.mutable.<ProjectionSlot<InputContent>>ofInitialCapacity(inputs.size());
        for (ProjectionSlot<InputContent> slot : inputs) {
            copiedInputs.add(slot.snapshot());
        }
        var copiedOutputs = Lists.mutable.<ProjectionSlot<OutputContent>>ofInitialCapacity(outputs.size());
        for (ProjectionSlot<OutputContent> slot : outputs) {
            copiedOutputs.add(slot.snapshot());
        }
        return new RecipeProjection(recipe, copiedInputs.toImmutable(), copiedOutputs.toImmutable(), attributes, tags);
    }

    //endregion

    public static final class Builder {
        private final RecipeEntry<?> recipe;
        private final List<ProjectionSlot<InputContent>> inputs = new ArrayList<>();
        private final List<ProjectionSlot<OutputContent>> outputs = new ArrayList<>();
        private ProjectionAttributes attributes = ProjectionAttributes.empty();
        private ProjectionTags tags = ProjectionTags.empty();
        private int nextInputIndex = 0;
        private int nextOutputIndex = 0;

        private Builder(RecipeEntry<?> recipe) {
            this.recipe = Checks.checkNotNull(recipe, "recipe");
        }

        public Builder inputGrid(int width, int height) {
            this.attributes = this.attributes.with(ProjectionKeys.inputGrid, new GridSpec(width, height));
            this.tags = this.tags.with(ProjectionKeys.Tags.gridShaped);
            return this;
        }

        public Builder outputGrid(int width, int height) {
            this.attributes = this.attributes.with(ProjectionKeys.outputGrid, new GridSpec(width, height));
            return this;
        }

        public Builder input(InputContent content) {
            return input(content, ProjectionAttributes.empty(), ProjectionTags.empty());
        }

        public Builder input(InputContent content, ProjectionAttributes attrs, ProjectionTags slotTags) {
            inputs.add(ProjectionSlot.of(SlotAddress.input(nextInputIndex++), content, attrs, slotTags));
            return this;
        }

        public Builder inputCell(int col, int row, InputContent content) {
            return inputCell(col, row, content, ProjectionAttributes.empty(), ProjectionTags.empty());
        }

        public Builder inputCell(
            int col,
            int row,
            InputContent content,
            ProjectionAttributes attrs,
            ProjectionTags slotTags
        ) {
            GridSpec spec = Checks.checkNotNull(attributes.find(ProjectionKeys.inputGrid), "inputGrid must be set first");
            SlotAddress.GridCell cell = SlotAddress.inputGrid(col, row, spec.width());
            inputs.add(ProjectionSlot.of(cell, content, attrs, slotTags));
            return this;
        }

        public Builder inputCellIndex(int index, InputContent content) {
            return inputCellIndex(index, content, ProjectionAttributes.empty(), ProjectionTags.empty());
        }

        public Builder inputCellIndex(
            int index,
            InputContent content,
            ProjectionAttributes attrs,
            ProjectionTags slotTags
        ) {
            GridSpec spec = Checks.checkNotNull(attributes.find(ProjectionKeys.inputGrid), "inputGrid must be set first");
            SlotAddress.GridCell cell = SlotAddress.inputGridIndex(index, spec.width());
            inputs.add(ProjectionSlot.of(cell, content, attrs, slotTags));
            return this;
        }

        public Builder namedInput(String name, InputContent content) {
            return namedInput(name, content, ProjectionAttributes.empty(), ProjectionTags.empty());
        }

        public Builder namedInput(
            String name,
            InputContent content,
            ProjectionAttributes attrs,
            ProjectionTags slotTags
        ) {
            inputs.add(ProjectionSlot.of(SlotAddress.inputNamed(name), content, attrs, slotTags));
            return this;
        }

        public Builder inputSlot(ProjectionSlot<InputContent> slot) {
            inputs.add(Checks.checkNotNull(slot, "slot"));
            return this;
        }

        public Builder output(OutputContent content) {
            return output(content, ProjectionAttributes.empty(), ProjectionTags.empty());
        }

        public Builder output(OutputContent content, ProjectionAttributes attrs, ProjectionTags slotTags) {
            outputs.add(ProjectionSlot.of(SlotAddress.output(nextOutputIndex++), content, attrs, slotTags));
            return this;
        }

        public Builder outputCell(int col, int row, OutputContent content) {
            return outputCell(col, row, content, ProjectionAttributes.empty(), ProjectionTags.empty());
        }

        public Builder outputCell(
            int col,
            int row,
            OutputContent content,
            ProjectionAttributes attrs,
            ProjectionTags slotTags
        ) {
            GridSpec spec = Checks.checkNotNull(
                attributes.find(ProjectionKeys.outputGrid),
                "outputGrid must be set first"
            );
            SlotAddress.GridCell cell = SlotAddress.outputGrid(col, row, spec.width());
            outputs.add(ProjectionSlot.of(cell, content, attrs, slotTags));
            return this;
        }

        public Builder namedOutput(String name, OutputContent content) {
            return namedOutput(name, content, ProjectionAttributes.empty(), ProjectionTags.empty());
        }

        public Builder namedOutput(
            String name,
            OutputContent content,
            ProjectionAttributes attrs,
            ProjectionTags slotTags
        ) {
            outputs.add(ProjectionSlot.of(SlotAddress.outputNamed(name), content, attrs, slotTags));
            return this;
        }

        public Builder outputSlot(ProjectionSlot<OutputContent> slot) {
            outputs.add(Checks.checkNotNull(slot, "slot"));
            return this;
        }

        public <T> Builder attribute(ProjectionAttributeKey<T> key, T value) {
            this.attributes = this.attributes.with(key, value);
            return this;
        }

        public Builder tag(Namespace tag) {
            this.tags = this.tags.with(tag);
            return this;
        }

        public Builder tags(Namespace... extraTags) {
            for (Namespace tag : extraTags) {
                this.tags = this.tags.with(tag);
            }
            return this;
        }

        public Builder localEffect(ProjectionEffect effect) {
            Checks.checkNotNull(effect, "effect");
            List<ProjectionEffect> existing = attributes.find(ProjectionKeys.localEffects);
            ArrayList<ProjectionEffect> next = existing == null ? new ArrayList<>(1) : new ArrayList<>(existing);
            next.add(effect);
            this.attributes = this.attributes.with(ProjectionKeys.localEffects, List.copyOf(next));
            return this;
        }

        public RecipeProjection build() {
            return new RecipeProjection(
                recipe,
                Lists.immutable.withAll(inputs),
                Lists.immutable.withAll(outputs),
                attributes,
                tags
            );
        }
    }
}
