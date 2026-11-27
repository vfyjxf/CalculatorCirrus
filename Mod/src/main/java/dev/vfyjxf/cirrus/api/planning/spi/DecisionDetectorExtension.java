package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;

import java.util.List;

/** SPI 6: Custom mod-specific high-leverage decision prompt detector. */
public interface DecisionDetectorExtension {
    String detectorId();

    List<DecisionPrompt> detect(PlanningRequest request, List<ProductionPlan> plans, CompiledRecipeGraph graph);
}
