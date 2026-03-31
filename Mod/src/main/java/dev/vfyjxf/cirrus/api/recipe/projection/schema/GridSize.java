package dev.vfyjxf.cirrus.api.recipe.projection.schema;

import dev.vfyjxf.cloudlib.util.Checks;

public record GridSize(int width, int height) {
    public GridSize {
        Checks.checkArgument(width > 0, "width must be positive");
        Checks.checkArgument(height > 0, "height must be positive");
    }
}
