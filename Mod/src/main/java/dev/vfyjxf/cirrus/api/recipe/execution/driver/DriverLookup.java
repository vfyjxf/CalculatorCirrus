package dev.vfyjxf.cirrus.api.recipe.execution.driver;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.DriverContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.SideContext;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite lookup carrying all known runtime context elements.
 */
public interface DriverLookup {

    List<DriverContext> contexts();

    default <T extends DriverContext> @Nullable T first(Class<T> type) {
        for (DriverContext context : contexts()) {
            if (type.isInstance(context)) {
                return type.cast(context);
            }
        }
        return null;
    }

    default DriverLookup with(DriverContext... contexts) {
        ArrayList<DriverContext> merged = new ArrayList<>(contexts());
        outer:
        for (DriverContext context : contexts) {
            for (int index = 0; index < merged.size(); index++) {
                if (merged.get(index).getClass() == context.getClass()) {
                    merged.set(index, context);
                    continue outer;
                }
            }
            merged.add(context);
        }
        List<DriverContext> list = List.copyOf(merged);
        return () -> list;
    }

    default Namespace interactionId() {
        InteractionContext context = first(InteractionContext.class);
        if (context == null) {
            throw new IllegalStateException("DriverLookup is missing InteractionContext");
        }
        return context.interactionId();
    }

    default ExecutionSideRequirement side() {
        SideContext context = first(SideContext.class);
        return context == null ? ExecutionSideRequirement.environmentAgnostic : context.side();
    }

    static DriverLookup of(DriverContext... contexts) {
        var list = List.of(contexts);
        return () -> list;
    }
}
