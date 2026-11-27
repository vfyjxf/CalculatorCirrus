package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
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

/**
 * 6D Physical CostVector aggregator (M1.3):
 * aggregates H_craft, H_trip (deduplicating shared trip episodes), parallel passive wall-clock W,
 * K_mach, M_scarcity, and F_steps without leaking any soft rules into reported physical quantities.
 */
public final class PlanCosts {

    private PlanCosts() {}

    public static CostVector aggregate(List<PlanStep> steps) {
        double hCraft = 0.0;
        double hTrip = 0.0;
        double k = 0.0;
        double s = 0.0;
        double f = 0.0;
        double maxFinishP = 0.0;

        Map<String, Double> finishP = new HashMap<>(steps.size() * 2);
        Set<String> distinctMachines = new HashSet<>();

        for (PlanStep step : steps) {
            CostVector sc = step.stepCost();
            hCraft += sc.humanActiveSeconds();
            hTrip += sc.tripActiveSeconds();
            k += sc.machineSetupCapital();
            s += sc.materialScarcity();
            f += sc.cognitiveFriction();

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

        f += 0.5 * distinctMachines.size();
        return new CostVector(hCraft, hTrip, maxFinishP, k, s, f);
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
            default -> {}
        }
    }
}
