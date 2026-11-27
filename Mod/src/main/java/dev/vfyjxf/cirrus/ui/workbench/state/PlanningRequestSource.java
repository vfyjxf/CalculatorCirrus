package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;

@FunctionalInterface
public interface PlanningRequestSource {

    PlanningRequest build(PlanningRequestSpec spec);
}
