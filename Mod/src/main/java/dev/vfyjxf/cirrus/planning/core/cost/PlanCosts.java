package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanCosts {

    private PlanCosts() {
    }

    public static CostVector aggregate(List<PlanStep> steps) {
        double h = 0.0;
        double k = 0.0;
        double s = 0.0;
        double c = 0.0;
        double maxFinishP = 0.0;

        Map<String, Double> finishP = new HashMap<>(steps.size() * 2);
        Set<String> distinctMachines = new HashSet<>();

        for (PlanStep step : steps) {
            CostVector sc = step.stepCost();
            h += sc.humanActiveSeconds();
            k += sc.machineSetupCapital();
            s += sc.materialScarcity();
            c += sc.cognitiveFriction();

            double depMax = 0.0;
            for (String depId : step.dependsOnStepIds()) {
                Double depFinish = finishP.get(depId);
                if (depFinish != null && depFinish > depMax) {
                    depMax = depFinish;
                }
            }
            double myFinish = depMax + sc.passiveWaitSeconds();
            finishP.put(step.stepId(), myFinish);
            if (myFinish > maxFinishP) {
                maxFinishP = myFinish;
            }

            collectMachines(step, distinctMachines);
        }

        c += 0.5 * distinctMachines.size();
        return new CostVector(h, maxFinishP, k, s, c);
    }

    private static void collectMachines(PlanStep step, Set<String> machines) {
        switch (step) {
            case CraftStep cs -> cs.chosenMachineId().ifPresent(machines::add);
            case ProvisionMachineStep pm -> machines.add(pm.machineId());
            case SetupLineStep sl -> machines.add(sl.primaryMachineId());
            case CycleLoopStep loop -> {
                for (CraftStep inner : loop.singleIterationTemplate()) {
                    inner.chosenMachineId().ifPresent(machines::add);
                }
            }
            default -> {
            }
        }
    }
}
