package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 6D Physical CostVector aggregator:
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
        Set<MachineEntry> distinctMachines = new HashSet<>();
        Set<String> seenTripEpisodes = new HashSet<>();

        for (PlanStep step : steps) {
            CostVector sc = step.stepCost();
            hCraft += sc.humanActiveSeconds();
            double tripAdd = sc.tripActiveSeconds();
            if (step instanceof AcquireStep as && !as.tripEpisodeId().isEmpty() && as.fixedTripHumanSeconds() > 0.0) {
                if (!seenTripEpisodes.add(as.tripEpisodeId())) {
                    tripAdd = Math.max(0.0, tripAdd - as.fixedTripHumanSeconds());
                }
            }
            hTrip += tripAdd;
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

    private static void collectMachines(PlanStep step, Set<MachineEntry> machines) {
        switch (step) {
            case CraftStep cs -> cs.chosenMachine().ifPresent(machines::add);
            case ProvisionMachineStep pm -> machines.add(pm.machine());
            case SetupLineStep sl -> machines.add(sl.primaryMachine());
            case CycleLoopStep loop -> {
                for (CraftStep inner : loop.singleIterationTemplate()) {
                    inner.chosenMachine().ifPresent(machines::add);
                }
            }
            default -> {}
        }
    }
}
