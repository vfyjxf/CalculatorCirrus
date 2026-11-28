package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.function.BiFunction;

/**
 * GAS-inspired application requirement evaluated before a {@link ProjectionEffect} mutates state.
 *
 * <p>A {@link #blocking()} guard failure prevents the effect from executing and marks the whole
 * projection non-runnable; a non-blocking guard failure skips only its owning effect while keeping
 * the projection runnable.
 */
public final class ProjectionGuard {

    private final Namespace id;
    private final boolean blocking;
    private final BiFunction<ExecutionContext, ProjectionView, Result> evaluator;

    private ProjectionGuard(
        Namespace id,
        boolean blocking,
        BiFunction<ExecutionContext, ProjectionView, Result> evaluator
    ) {
        this.id = Checks.checkNotNull(id, "id");
        this.blocking = blocking;
        this.evaluator = Checks.checkNotNull(evaluator, "evaluator");
    }

    public static ProjectionGuard of(Namespace id, BiFunction<ExecutionContext, ProjectionView, Result> evaluator) {
        return new ProjectionGuard(id, true, evaluator);
    }

    public static ProjectionGuard nonBlocking(
        Namespace id,
        BiFunction<ExecutionContext, ProjectionView, Result> evaluator
    ) {
        return new ProjectionGuard(id, false, evaluator);
    }

    public Namespace id() {
        return id;
    }

    public boolean blocking() {
        return blocking;
    }

    public Result evaluate(ExecutionContext context, ProjectionView view) {
        Checks.checkNotNull(context, "context");
        Checks.checkNotNull(view, "view");
        return Checks.checkNotNull(evaluator.apply(context, view), "guard result");
    }

    public record Result(boolean passed, String reasonCode) {
        private static final Result passResult = new Result(true, "");

        public Result {
            Checks.checkNotNull(reasonCode, "reasonCode");
        }

        public static Result pass() {
            return passResult;
        }

        public static Result fail(String reasonCode) {
            return new Result(false, Checks.checkNotNull(reasonCode, "reasonCode"));
        }
    }

    public record Finding(
        Namespace effectId,
        Namespace guardId,
        boolean passed,
        boolean blocking,
        String reasonCode
    ) {
        public Finding {
            Checks.checkNotNull(effectId, "effectId");
            Checks.checkNotNull(guardId, "guardId");
            Checks.checkNotNull(reasonCode, "reasonCode");
        }
    }
}
