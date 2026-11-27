package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeGraphCompiler;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.service.PlanningService;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultRecipeGraphCompiler;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import org.junit.jupiter.api.BeforeAll;

class OpenPlannerComplianceTestImpl extends OpenPlannerComplianceTest {

    @BeforeAll
    static void warmUp() {
        PlanningTestSupport.warmUpJvm();
    }

    @Override
    protected RecipeGraphCompiler compiler() {
        return new DefaultRecipeGraphCompiler();
    }

    @Override
    protected SystemAdmissionPolicy admissionPolicy() {
        return DefaultSystemAdmissionPolicy.defaults();
    }

    @Override
    protected PlanningService createService(CompiledRecipeGraph graph) {
        return new DefaultPlanningService(graph);
    }

    @Override
    protected HumanGuidanceView emptyGuidance() {
        return DefaultHumanGuidanceView.empty();
    }

    @Override
    protected AcquisitionOracle defaultOracle(CompiledRecipeGraph graph) {
        return new DefaultAcquisitionOracle(graph);
    }
}
