package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.GridSpec;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributeKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributes;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionTags;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.TagQuery;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Read-only view of a recipe projection state, shared by immutable {@code RecipeProjection}
 * snapshots and live in-flight {@link ProjectionTransaction} states.
 */
public interface ProjectionView {

    RecipeEntry<?> recipe();

    List<ProjectionSlot<InputContent>> inputSlots();

    List<ProjectionSlot<OutputContent>> outputSlots();

    @Nullable
    ProjectionSlot<? extends RecipeContent> slot(SlotAddress address);

    default List<? extends ProjectionSlot<? extends RecipeContent>> slots(SlotChannel channel) {
        return channel == SlotChannel.input ? inputSlots() : outputSlots();
    }

    @Nullable
    GridSpec grid(SlotChannel channel);

    default @Nullable GridSpec inputGrid() {
        return grid(SlotChannel.input);
    }

    default @Nullable GridSpec outputGrid() {
        return grid(SlotChannel.output);
    }

    @SuppressWarnings("unchecked")
    default @Nullable ProjectionSlot<InputContent> inputCell(int col, int row) {
        GridSpec spec = inputGrid();
        if (spec == null || !spec.contains(col, row)) {
            return null;
        }
        return (ProjectionSlot<InputContent>) slot(SlotAddress.grid(SlotChannel.input, col, row, spec.width()));
    }

    @SuppressWarnings("unchecked")
    default @Nullable ProjectionSlot<OutputContent> outputCell(int col, int row) {
        GridSpec spec = outputGrid();
        if (spec == null || !spec.contains(col, row)) {
            return null;
        }
        return (ProjectionSlot<OutputContent>) slot(SlotAddress.grid(SlotChannel.output, col, row, spec.width()));
    }

    ProjectionAttributes attributes();

    ProjectionTags tags();

    default <T> @Nullable T attribute(ProjectionAttributeKey<T> key) {
        return attributes().find(key);
    }

    default boolean hasTag(Namespace tag) {
        return tags().has(tag);
    }

    default boolean hasTagHierarchical(Namespace tag) {
        return tags().hasHierarchical(tag);
    }

    default boolean matchesTags(TagQuery query) {
        return tags().matches(query);
    }
}
