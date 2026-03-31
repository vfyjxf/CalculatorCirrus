package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import org.jetbrains.annotations.Nullable;

public record RecipeDatabaseAddResult(
    Status status,
    String databaseId,
    String databaseName,
    @Nullable RecipeEntry<?> recipe
) {
    public enum Status {
        added,
        alreadyPresent,
        readOnly,
        unsupported,
        failed
    }

    public boolean success() {
        return status == Status.added || status == Status.alreadyPresent;
    }

    public boolean added() {
        return status == Status.added;
    }
}
