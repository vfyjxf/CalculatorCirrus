package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;

import java.util.Map;
import java.util.Optional;

public interface BatchCouplingSolver {

    Optional<Map<CompiledRecipeKey, Long>> refine(
            BatchCouplingComponent component,
            PlanningRequest request,
            long deadlineNanos
    );
}
