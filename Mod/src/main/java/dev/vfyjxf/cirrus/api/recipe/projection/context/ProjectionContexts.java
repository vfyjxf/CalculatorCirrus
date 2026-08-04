package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cloudlib.util.Checks;

public final class ProjectionContexts {

    private ProjectionContexts() {
    }

    public static ExecutionContext global() {
        return new ExecutionContext()
            .set(ProjectionContextKeys.scope, ProjectionContextScope.global)
            .set(ProjectionContextKeys.global, GlobalProjectionContext.defaults());
    }

    public static ExecutionContext database(ExecutionContext parent, DatabaseProjectionContext database) {
        return Checks.checkNotNull(parent, "parent").child()
            .set(ProjectionContextKeys.scope, ProjectionContextScope.database)
            .set(ProjectionContextKeys.database, Checks.checkNotNull(database, "database"));
    }

    public static ExecutionContext calculator(ExecutionContext parent, CalculatorProjectionContext calculator) {
        return Checks.checkNotNull(parent, "parent").child()
            .set(ProjectionContextKeys.scope, ProjectionContextScope.calculator)
            .set(ProjectionContextKeys.calculator, Checks.checkNotNull(calculator, "calculator"));
    }
}
