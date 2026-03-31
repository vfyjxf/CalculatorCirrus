package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cloudlib.util.Checks;

public record GlobalProjectionContext(String profileId) {

    private static final GlobalProjectionContext defaults = new GlobalProjectionContext("default");

    public GlobalProjectionContext {
        Checks.checkNotNull(profileId, "profileId");
    }

    public static GlobalProjectionContext defaults() {
        return defaults;
    }
}
