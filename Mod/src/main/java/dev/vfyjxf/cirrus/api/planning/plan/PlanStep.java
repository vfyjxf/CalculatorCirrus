package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;

import java.util.List;

public sealed interface PlanStep
        permits AcquireStep, ProvisionMachineStep, SetupLineStep, CraftStep, LinePullStep, ConvertStep, CycleLoopStep {

    String stepId();

    List<String> dependsOnStepIds();

    CostVector stepCost();
}
