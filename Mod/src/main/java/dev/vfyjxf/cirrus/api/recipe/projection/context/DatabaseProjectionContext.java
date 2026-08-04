package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cloudlib.util.Checks;

public record DatabaseProjectionContext(String databaseId, String displayName, boolean builtin) {

    private static final DatabaseProjectionContext none = new DatabaseProjectionContext("", "", false);

    public DatabaseProjectionContext {
        Checks.checkNotNull(databaseId, "databaseId");
        Checks.checkNotNull(displayName, "displayName");
    }

    public static DatabaseProjectionContext none() {
        return none;
    }
}
