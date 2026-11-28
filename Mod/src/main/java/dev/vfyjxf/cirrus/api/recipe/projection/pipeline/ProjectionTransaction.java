package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.GridSpec;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributeKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Structured, address-aware mutation surface passed to a {@link ProjectionEffect}.
 *
 * <p>All state edits flow through typed operations so that:
 * <ul>
 *     <li>Fast evaluation ({@link TraceMode#none}) applies edits directly with zero diff overhead.</li>
 *     <li>Traced evaluation ({@link TraceMode#full}) captures exact per-slot, per-grid-cell,
 *         attribute, and tag deltas automatically.</li>
 * </ul>
 */
public interface ProjectionTransaction {

    ExecutionContext context();

    ProjectionView view();

    default RecipeEntry<?> recipe() {
        return view().recipe();
    }

    default List<ProjectionSlot<InputContent>> inputSlots() {
        return view().inputSlots();
    }

    default List<ProjectionSlot<OutputContent>> outputSlots() {
        return view().outputSlots();
    }

    default @Nullable ProjectionSlot<? extends RecipeContent> slot(SlotAddress address) {
        return view().slot(address);
    }

    //region slot operations

    /**
     * Appends a new indexed input slot and returns its assigned {@link SlotAddress}.
     */
    SlotAddress addInput(InputContent content);

    /**
     * Appends a new indexed output slot and returns its assigned {@link SlotAddress}.
     */
    SlotAddress addOutput(OutputContent content);

    /**
     * Adds a named input slot (e.g. an unpacked container catalyst) and returns its {@link SlotAddress}.
     */
    SlotAddress addNamedInput(String name, InputContent content);

    /**
     * Adds a named output slot (e.g. a secondary byproduct) and returns its {@link SlotAddress}.
     */
    SlotAddress addNamedOutput(String name, OutputContent content);

    /**
     * Places or updates a slot at the given {@link SlotAddress}. If the address already exists,
     * its content is replaced with the given {@link ChangeReason}; otherwise a new slot is added.
     */
    void setSlot(SlotAddress address, RecipeContent content, ChangeReason reason);

    /**
     * Convenience helper that adjusts the amount of an existing slot (recording {@link ChangeReason#adjusted}).
     */
    void setSlotAmount(SlotAddress address, long newAmount);

    /**
     * Convenience helper that adjusts the chance of an existing slot (recording {@link ChangeReason#adjusted}).
     */
    void setSlotChance(SlotAddress address, float newChance);

    /**
     * Removes the slot at {@code address}, if present.
     */
    void removeSlot(SlotAddress address);

    /**
     * Moves an existing slot (including its attributes and tags) from {@code from} to {@code to}.
     * Both addresses must belong to the same {@link SlotChannel}.
     */
    void moveSlot(SlotAddress from, SlotAddress to);

    //endregion

    //region grid cell operations

    void setGrid(SlotChannel channel, GridSpec spec);

    default void setInputGrid(int width, int height) {
        setGrid(SlotChannel.input, new GridSpec(width, height));
    }

    default void setOutputGrid(int width, int height) {
        setGrid(SlotChannel.output, new GridSpec(width, height));
    }

    /**
     * Sets or replaces the content in a 2D grid cell {@code (col, row)}, preserving its grid address.
     */
    SlotAddress.GridCell setGridCell(
        SlotChannel channel,
        int col,
        int row,
        RecipeContent content,
        ChangeReason reason
    );

    /**
     * Clears (removes) the slot at grid cell {@code (col, row)}, if occupied.
     */
    void clearGridCell(SlotChannel channel, int col, int row);

    /**
     * Moves the slot in grid cell {@code (fromCol, fromRow)} to {@code (toCol, toRow)}.
     */
    void moveGridCell(SlotChannel channel, int fromCol, int fromRow, int toCol, int toRow);

    //endregion

    //region attributes and tags

    <T> void putAttribute(ProjectionAttributeKey<T> key, T value);

    void removeAttribute(ProjectionAttributeKey<?> key);

    <T> void putSlotAttribute(SlotAddress address, ProjectionAttributeKey<T> key, T value);

    void removeSlotAttribute(SlotAddress address, ProjectionAttributeKey<?> key);

    void addTag(Namespace tag);

    void removeTag(Namespace tag);

    void addSlotTag(SlotAddress address, Namespace tag);

    void removeSlotTag(SlotAddress address, Namespace tag);

    //endregion

    //region blocking

    /**
     * Marks this projection as non-runnable (blocked) with the given machine-readable reason code.
     */
    void block(String reasonCode);

    //endregion
}
