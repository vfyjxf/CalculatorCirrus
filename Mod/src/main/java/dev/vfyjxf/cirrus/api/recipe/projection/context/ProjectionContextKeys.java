package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import net.minecraft.resources.ResourceLocation;

public final class ProjectionContextKeys {

    public static final ExecutionContextKey<ProjectionContextScope> scope = key(
        "scope",
        ProjectionContextScope.class,
        ProjectionContextScope.global
    );
    public static final ExecutionContextKey<GlobalProjectionContext> global = key(
        "global",
        GlobalProjectionContext.class,
        GlobalProjectionContext.defaults()
    );
    public static final ExecutionContextKey<DatabaseProjectionContext> database = key(
        "database",
        DatabaseProjectionContext.class,
        DatabaseProjectionContext.none()
    );
    public static final ExecutionContextKey<CalculatorProjectionContext> calculator = key(
        "calculator",
        CalculatorProjectionContext.class,
        CalculatorProjectionContext.none()
    );

    private ProjectionContextKeys() {}

    private static <T> ExecutionContextKey<T> key(String path, Class<T> type, T defaultValue) {
        return ExecutionContextKey.create(
            ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, "projection/context/" + path),
            type,
            defaultValue
        );
    }
}
