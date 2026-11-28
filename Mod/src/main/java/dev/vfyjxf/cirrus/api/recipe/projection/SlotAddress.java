package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Stable identity address of a slot in a {@link RecipeProjection}.
 *
 * <p>Unlike bare list indices, a {@link SlotAddress} remains stable across slot insertions,
 * removals, and multi-stage pipeline transformations — enabling exact tracking of crafting grid
 * cells, named machine slots, and dynamically injected slots.
 */
public sealed interface SlotAddress permits SlotAddress.GridCell, SlotAddress.Indexed, SlotAddress.Named {

    SlotChannel channel();

    /**
     * Canonical stable key suitable for map keys, serialization, and deterministic ordering.
     */
    String key();

    /**
     * Compact human-readable location label for UI display (e.g., {@code (1,1)}, {@code #1}, {@code base}).
     */
    String shortLabel();

    default boolean isGridCell() {
        return this instanceof GridCell;
    }

    //region factories

    static GridCell grid(SlotChannel channel, int col, int row, int gridWidth) {
        Checks.checkNotNull(channel, "channel");
        Checks.checkArgument(gridWidth > 0, "gridWidth must be positive");
        Checks.checkArgument(col >= 0 && col < gridWidth, "col out of bounds: " + col);
        Checks.checkArgument(row >= 0, "row must be non-negative: " + row);
        return new GridCell(channel, col, row, row * gridWidth + col);
    }

    static GridCell gridIndex(SlotChannel channel, int index, int gridWidth) {
        Checks.checkNotNull(channel, "channel");
        Checks.checkArgument(gridWidth > 0, "gridWidth must be positive");
        Checks.checkArgument(index >= 0, "index must be non-negative: " + index);
        return new GridCell(channel, index % gridWidth, index / gridWidth, index);
    }

    static GridCell inputGrid(int col, int row, int gridWidth) {
        return grid(SlotChannel.input, col, row, gridWidth);
    }

    static GridCell inputGridIndex(int index, int gridWidth) {
        return gridIndex(SlotChannel.input, index, gridWidth);
    }

    static GridCell outputGrid(int col, int row, int gridWidth) {
        return grid(SlotChannel.output, col, row, gridWidth);
    }

    static GridCell outputGridIndex(int index, int gridWidth) {
        return gridIndex(SlotChannel.output, index, gridWidth);
    }

    static Indexed indexed(SlotChannel channel, int index) {
        return new Indexed(channel, index);
    }

    static Indexed input(int index) {
        return new Indexed(SlotChannel.input, index);
    }

    static Indexed output(int index) {
        return new Indexed(SlotChannel.output, index);
    }

    static Named named(SlotChannel channel, String name) {
        return new Named(channel, name);
    }

    static Named inputNamed(String name) {
        return new Named(SlotChannel.input, name);
    }

    static Named outputNamed(String name) {
        return new Named(SlotChannel.output, name);
    }

    //endregion

    record GridCell(SlotChannel channel, int col, int row, int index) implements SlotAddress {
        public GridCell {
            Checks.checkNotNull(channel, "channel");
            Checks.checkArgument(col >= 0, "col must be non-negative");
            Checks.checkArgument(row >= 0, "row must be non-negative");
            Checks.checkArgument(index >= 0, "index must be non-negative");
        }

        @Override
        public String key() {
            return (channel == SlotChannel.input ? "in" : "out") + ":grid(" + col + "," + row + ")";
        }

        @Override
        public String shortLabel() {
            return "(" + col + "," + row + ")";
        }
    }

    record Indexed(SlotChannel channel, int index) implements SlotAddress {
        public Indexed {
            Checks.checkNotNull(channel, "channel");
            Checks.checkArgument(index >= 0, "index must be non-negative");
        }

        @Override
        public String key() {
            return (channel == SlotChannel.input ? "in" : "out") + ":#" + index;
        }

        @Override
        public String shortLabel() {
            return "#" + (index + 1);
        }
    }

    record Named(SlotChannel channel, String name) implements SlotAddress {
        public Named {
            Checks.checkNotNull(channel, "channel");
            Checks.checkNotNull(name, "name");
            Checks.checkArgument(!name.isBlank(), "name cannot be blank");
        }

        @Override
        public String key() {
            return (channel == SlotChannel.input ? "in" : "out") + ":named(" + name + ")";
        }

        @Override
        public String shortLabel() {
            return name;
        }
    }
}
