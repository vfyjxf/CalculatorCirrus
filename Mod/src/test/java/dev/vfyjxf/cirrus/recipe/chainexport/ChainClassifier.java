package dev.vfyjxf.cirrus.recipe.chainexport;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds the dependency structure of a flat {@link RoutingPlan} action list
 * (plans are emitted in topological order, so inputs either match outputs of
 * earlier executes or enter the chain from outside) and derives the features
 * used to classify chains by size and shape.
 */
final class ChainClassifier {

    record ChainFeatures(
        int depth,
        int actionCount,
        int executeCount,
        int distinctRecipes,
        long totalCrafts,
        int rawKinds,
        int pendingCount,
        int catalystCount,
        int machineKinds,
        int systemKinds,
        String depthBucket,
        String category,
        String flags
    ) {

        static final ChainFeatures EMPTY = new ChainFeatures(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "trivial", "incomplete", "+failed");
    }

    record ActionNode(
        int index,
        PlanAction.ExecuteRecipe execute,
        int depth,
        List<Integer> parentIndexes
    ) {
    }

    record Analyzed(
        ChainFeatures features,
        List<ActionNode> nodes,
        Map<String, Long> rawAmounts,
        List<PlanAction.PendingInput> pendings,
        List<PlanAction.ConsumeStock> consumes,
        List<PlanAction.ReserveCatalyst> catalysts,
        List<PlanAction.PendingInput> rawLeaves
    ) {

        static final Analyzed EMPTY = new Analyzed(
            ChainFeatures.EMPTY, List.of(), Map.of(), List.of(), List.of(), List.of(), List.of());
    }

    static Analyzed analyze(@Nullable RoutingPlan plan, ChainTargetSelector.Target target) {
        if (plan == null) {
            return Analyzed.EMPTY;
        }

        List<PlanAction> actions = plan.actions().castToList();
        List<ActionNode> nodes = new ArrayList<>();
        List<PlanAction.PendingInput> pendings = new ArrayList<>();
        List<PlanAction.ConsumeStock> consumes = new ArrayList<>();
        List<PlanAction.ReserveCatalyst> catalysts = new ArrayList<>();

        // actionIndex -> node (executes only), and last producer per resource key
        Map<Integer, ActionNode> nodeByIndex = new HashMap<>();
        Map<String, Integer> lastProducer = new HashMap<>();
        Map<String, Long> externalInputs = new LinkedHashMap<>(); // key -> amount entering from outside
        Map<String, Long> producedTotals = new HashMap<>();
        Set<String> producedKeys = new HashSet<>();

        for (PlanAction action : actions) {
            switch (action) {
                case PlanAction.ExecuteRecipe execute -> {
                    Set<String> needed = new LinkedHashSet<>();
                    for (PlanAction.InputBinding binding : execute.boundInputs()) {
                        needed.addAll(resourceKeys(binding.input()));
                    }
                    List<Integer> parents = new ArrayList<>(2);
                    for (String key : needed) {
                        Integer producerIndex = lastProducer.get(key);
                        if (producerIndex != null) {
                            parents.add(producerIndex);
                        } else {
                            externalInputs.merge(key, inputAmount(execute, key), Long::sum);
                        }
                    }
                    int depth = 0;
                    for (int parent : parents) {
                        ActionNode node = nodeByIndex.get(parent);
                        if (node != null) {
                            depth = Math.max(depth, node.depth() + 1);
                        }
                    }
                    ActionNode node = new ActionNode(execute.actionIndex(), execute, depth, parents);
                    nodes.add(node);
                    nodeByIndex.put(execute.actionIndex(), node);
                    for (PlanAction.OutputBinding binding : execute.expectedOutputs()) {
                        String key = resourceKey(binding.resource());
                        if (key == null) continue;
                        producedKeys.add(key);
                        producedTotals.merge(key, binding.amount(), Long::sum);
                        lastProducer.put(key, execute.actionIndex());
                    }
                }
                case PlanAction.ConsumeStock consume -> {
                    consumes.add(consume);
                    String key = resourceKey(consume.resource());
                    if (key != null) {
                        externalInputs.merge(key, consume.amount(), Long::sum);
                    }
                }
                case PlanAction.PendingInput pending -> pendings.add(pending);
                case PlanAction.ReserveCatalyst catalyst -> catalysts.add(catalyst);
            }
        }

        int depth = 0;
        long totalCrafts = 0;
        Set<String> recipeIds = new HashSet<>();
        Set<String> machines = new HashSet<>();
        Set<String> systems = new HashSet<>();
        for (ActionNode node : nodes) {
            depth = Math.max(depth, node.depth() + 1);
            totalCrafts += node.execute().times();
            if (node.execute().recipe().recipe() instanceof ExportPackLoader.ExportRecipe export) {
                recipeIds.add(export.recipeId);
                machines.addAll(export.machines);
                systems.add(export.systemId);
            }
        }

        // pendings split: raw-material leaves are the natural inputs to gather, not
        // incompleteness. That covers inputs with no producer at all, plus inputs whose
        // only known producer loops back on itself (raw_ore <-> raw_ore_block, the
        // classic "mine it instead" case). Budget/rank truncations stay unresolved.
        List<PlanAction.PendingInput> unresolvedPendings = new ArrayList<>();
        List<PlanAction.PendingInput> rawLeaves = new ArrayList<>();
        for (PlanAction.PendingInput pending : pendings) {
            boolean rawLeaf = pending.reason() == PlanAction.PendingReason.TRUE_NO_SOURCE && pending.candidateCount() <= 0
                || pending.reason() == PlanAction.PendingReason.CASCADE_EXHAUSTED && pending.candidateCount() <= 1;
            if (rawLeaf) {
                rawLeaves.add(pending);
                for (String key : resourceKeys(pending.input())) {
                    externalInputs.merge(key, pending.amount(), Long::sum);
                }
            } else {
                unresolvedPendings.add(pending);
            }
        }
        int unresolvedCount = unresolvedPendings.size();

        // raw = resources entering from outside with no in-plan producer
        Set<String> rawKeys = new LinkedHashSet<>();
        for (String key : externalInputs.keySet()) {
            if (!producedKeys.contains(key)) {
                rawKeys.add(key);
            }
        }

        String depthBucket = depthBucket(depth, actions.size());
        boolean incomplete = unresolvedCount > 0;
        String category = incomplete ? "incomplete" : depthBucket;
        String flags = (incomplete ? "+incomplete" : "+complete")
            + (rawLeaves.size() > 0 ? "+raw" + rawLeaves.size() : "")
            + (actions.size() >= 300 ? "+mega-wide" : (depth >= 1 && actions.size() / Math.max(1, depth) >= 8 ? "+wide" : ""));

        ChainFeatures features = new ChainFeatures(
            depth,
            actions.size(),
            nodes.size(),
            recipeIds.size(),
            totalCrafts,
            rawKeys.size(),
            unresolvedCount,
            catalysts.size(),
            machines.size(),
            systems.size(),
            depthBucket,
            category,
            flags
        );
        return new Analyzed(features, nodes, externalInputs, unresolvedPendings, consumes, catalysts, rawLeaves);
    }

    private static long inputAmount(PlanAction.ExecuteRecipe execute, String key) {
        long total = 0;
        for (PlanAction.InputBinding binding : execute.boundInputs()) {
            if (resourceKeys(binding.input()).contains(key)) {
                total += binding.amount();
            }
        }
        return Math.max(1, total);
    }

    static String depthBucket(int depth, int actionCount) {
        if (actionCount >= 300) return "mega-wide";
        if (depth <= 1) return "trivial";
        if (depth <= 3) return "short";
        if (depth <= 6) return "medium";
        if (depth <= 11) return "long";
        if (depth <= 19) return "giant";
        return "colossal";
    }

    //region resource keys

    static @Nullable String resourceKey(ContentResource resource) {
        if (resource instanceof ContentStack<?, ?> stack) {
            Object value = stack.value();
            return value == null ? null : value.toString();
        }
        if (resource instanceof ContentValue<?> value) {
            return "value:" + value.type().typeId() + "=" + value.value();
        }
        return null;
    }

    static Set<String> resourceKeys(InputContent input) {
        Set<String> keys = new LinkedHashSet<>(2);
        switch (input) {
            case StackContent<?, ?> stack -> {
                Object value = stack.content().value();
                if (value != null) keys.add(value.toString());
            }
            case TypedStackListContent<?, ?> typed -> {
                for (ContentStack<?, ?> content : typed.stacks()) {
                    Object value = content.value();
                    if (value != null) keys.add(value.toString());
                }
            }
            case StackListContent list -> {
                for (ContentStack<?, ?> content : list.stacks()) {
                    Object value = content.value();
                    if (value != null) keys.add(value.toString());
                }
            }
            case TagStackContent<?> tag -> keys.add("#" + tag.tagKey().location());
            case ValueContent<?> value -> keys.add("value:" + value.type().typeId() + "=" + value.content().value());
            default -> {
            }
        }
        return keys;
    }

    /** Human-readable single label for an input (first variant / tag marker). */
    static String describeInput(InputContent input) {
        Set<String> keys = resourceKeys(input);
        if (keys.isEmpty()) return "unknown";
        if (keys.size() == 1) return keys.iterator().next();
        List<String> sample = new ArrayList<>(keys);
        return sample.get(0) + "|+" + (sample.size() - 1);
    }

    //endregion
}
