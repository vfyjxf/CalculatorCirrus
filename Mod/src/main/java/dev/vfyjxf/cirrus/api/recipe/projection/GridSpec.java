package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cloudlib.util.Checks;

/**
 * 2D grid dimensions for a recipe channel (e.g., 3x3 crafting table or 3x1 smithing table).
 */
public record GridSpec(int width, int height) {

    public GridSpec {
        Checks.checkArgument(width > 0, "grid width must be positive");
        Checks.checkArgument(height > 0, "grid height must be positive");
    }

    public int cellCount() {
        return Math.multiplyExact(width, height);
    }

    public int colOf(int index) {
        Checks.checkArgument(index >= 0 && index < cellCount(), "grid index out of bounds: " + index);
        return index % width;
    }

    public int rowOf(int index) {
        Checks.checkArgument(index >= 0 && index < cellCount(), "grid index out of bounds: " + index);
        return index / width;
    }

    public int indexOf(int col, int row) {
        Checks.checkArgument(contains(col, row), "grid cell out of bounds: (" + col + ", " + row + ")");
        return row * width + col;
    }

    public boolean contains(int col, int row) {
        return col >= 0 && col < width && row >= 0 && row < height;
    }
}
