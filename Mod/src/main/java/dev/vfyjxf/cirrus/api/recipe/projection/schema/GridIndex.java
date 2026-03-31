package dev.vfyjxf.cirrus.api.recipe.projection.schema;

import dev.vfyjxf.cloudlib.util.Checks;

public record GridIndex(int index) {
    public GridIndex {
        Checks.checkArgument(index >= 0, "index must be non-negative");
    }
}
