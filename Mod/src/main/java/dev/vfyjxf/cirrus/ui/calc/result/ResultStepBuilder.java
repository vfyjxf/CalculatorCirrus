package dev.vfyjxf.cirrus.ui.calc.result;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ResultStepBuilder {
    public StepTree fromEdges(Map<String, List<String>> edges, String rootId) {
        Map<String, StepNode> nodes = new HashMap<>();
        nodes.computeIfAbsent(rootId, key -> new StepNode(key, null));
        for (Map.Entry<String, List<String>> entry : edges.entrySet()) {
            StepNode parent = nodes.computeIfAbsent(entry.getKey(), key -> new StepNode(key, null));
            for (String childId : entry.getValue()) {
                StepNode child = nodes.computeIfAbsent(childId, key -> new StepNode(key, null));
                parent.addChild(child);
            }
        }
        return new StepTree(nodes.get(rootId));
    }

    public StepTree fromPlan(RoutingPlan plan, RecipeGraph graph) {
        Map<String, StepNode> nodes = new HashMap<>();
        List<PlanAction.ExecuteRecipeAction> actions = new ArrayList<>();
        for (PlanAction action : plan.actions()) {
            if (action instanceof PlanAction.ExecuteRecipeAction exec) {
                actions.add(exec);
            }
        }
        for (PlanAction.ExecuteRecipeAction exec : actions) {
            RecipeGraph.Node node = graph.findNode(exec.recipe());
            ResultStep step = new ResultStep(
                exec.actionId(),
                exec.actionIndex(),
                exec.recipe(),
                node,
                exec.times(),
                exec.boundInputs(),
                exec.expectedOutputs()
            );
            nodes.put(exec.recipe().toString(), new StepNode(exec.recipe().toString(), step));
        }
        StepNode root = nodes.get(plan.targetRecipe().toString());
        // Partial routing: target recipe may not have an ExecuteRecipeAction
        // (resolveRecipe failed midway). Create a synthetic root so the UI can display resolved sub-steps.
        if (root == null && !nodes.isEmpty()) {
            root = new StepNode(plan.targetRecipe().toString(), null);
            RecipeGraph.Node targetGraphNode = graph.findNode(plan.targetRecipe());
            if (targetGraphNode != null) {
                for (var input : targetGraphNode.mergedInputs()) {
                    for (var producer : graph.producersFor(input)) {
                        StepNode child = nodes.get(producer.node().recipe().toString());
                        if (child != null && child != root) {
                            root.addChild(child);
                        }
                    }
                }
            }
        }
        for (PlanAction.ExecuteRecipeAction exec : actions) {
            RecipeGraph.Node consumerNode = graph.findNode(exec.recipe());
            if (consumerNode == null) {
                continue;
            }
            StepNode parent = nodes.get(exec.recipe().toString());
            if (parent == null) {
                continue;
            }
            for (var input : consumerNode.mergedInputs()) {
                for (var producer : graph.producersFor(input)) {
                    StepNode child = nodes.get(producer.node().recipe().toString());
                    if (child != null && child != parent) {
                        parent.addChild(child);
                    }
                }
            }
        }
        return new StepTree(root);
    }
}
