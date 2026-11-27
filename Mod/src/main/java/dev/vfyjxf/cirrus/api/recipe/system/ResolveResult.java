package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

public record ResolveResult(@Nullable RecipeProjection projection, ResolveError error) {

    public ResolveResult {
        Checks.checkNotNull(error, "error");
        Checks.checkArgument(
            (projection == null) != (error == ResolveError.none),
            "Resolved result requires a projection and unresolved result requires an error"
        );
    }

    public static ResolveResult resolved(RecipeProjection projection) {
        Checks.checkNotNull(projection, "projection");
        return new ResolveResult(projection, ResolveError.none);
    }

    public static ResolveResult failed(ResolveError error) {
        Checks.checkNotNull(error, "error");
        Checks.checkArgument(error != ResolveError.none, "failed result requires a real error");
        return new ResolveResult(null, error);
    }

    public static ResolveResult fromNullable(@Nullable RecipeProjection projection) {
        return projection == null ? unsupported() : resolved(projection);
    }

    public static ResolveResult unsupported() {
        return failed(ResolveError.unsupported);
    }

    public static ResolveResult missingContext() {
        return failed(ResolveError.missingContext);
    }

    public static ResolveResult ambiguous() {
        return failed(ResolveError.ambiguous);
    }

    public static ResolveResult invalidRecipe() {
        return failed(ResolveError.invalidRecipe);
    }

    public boolean resolved() {
        return projection != null;
    }
}
