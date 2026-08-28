package dev.vfyjxf.cirrus.recipe.chainexport;

import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Renders one solved chain as plain text for LLM audit: header with target and
 * features, indented dependency tree of executes, raw-material totals, pending
 * (unresolved) demands and catalyst reservations.
 */
final class ChainTextRenderer {

    private ChainTextRenderer() {
    }

    static String render(int chainId, ChainTargetSelector.Target target, ChainClassifier.Analyzed analyzed,
                         RoutingOutcome outcome, String ruleLabel) {
        ChainClassifier.ChainFeatures f = analyzed.features();
        StringBuilder sb = new StringBuilder(2048);

        sb.append("### CHAIN ").append(String.format("%04d", chainId))
            .append(" | category=").append(f.category())
            .append(" | depthBucket=").append(f.depthBucket())
            .append(" | flags=").append(f.flags().replaceFirst("^\\+", ""))
            .append('\n');
        sb.append("target: ").append(target.outputId()).append(" x1")
            .append(" | recipe: ").append(target.recipe().recipeId)
            .append(" | system: ").append(target.recipe().systemId)
            .append(" | tier: ").append(target.tier())
            .append(" | coneScore: ").append(target.coneScore()).append('\n');
        sb.append("status: ").append(outcome.status())
            .append(" | depth=").append(f.depth())
            .append(" actions=").append(f.actionCount())
            .append(" recipes=").append(f.distinctRecipes())
            .append(" crafts=").append(f.totalCrafts())
            .append(" raw_kinds=").append(f.rawKinds())
            .append(" pending=").append(f.pendingCount())
            .append(" catalysts=").append(f.catalystCount())
            .append(" machines=").append(f.machineKinds())
            .append(" systems=").append(f.systemKinds())
            .append(" elapsed_ms=").append(outcome.metrics().elapsedMs()).append('\n');
        RoutingMetrics m = outcome.metrics();
        sb.append("search: expanded=").append(m.expandedNodes())
            .append(" candidates=").append(m.generatedCandidates())
            .append(" budgetStop=").append(m.budgetStopReason() == null ? "none" : m.budgetStopReason())
            .append(m.budgetExhausted() ? " [BUDGET_EXHAUSTED]" : "")
            .append('\n');
        sb.append("rule: ").append(ruleLabel).append('\n');
        // builtin classification tags hit by this chain — these patterns are ALREADY
        // known to the production rule system; reviewers should not re-report them
        java.util.Map<String, Integer> tagCounts = new java.util.TreeMap<>();
        for (ChainClassifier.ActionNode node : analyzed.nodes()) {
            if (node.execute().recipe().recipe() instanceof ExportPackLoader.ExportRecipe export) {
                for (String tag : export.classificationTags) {
                    tagCounts.merge(shortTag(tag), 1, Integer::sum);
                }
            }
        }
        if (!tagCounts.isEmpty()) {
            StringJoiner tags = new StringJoiner(", ");
            tagCounts.forEach((tag, count) -> tags.add(tag + " x" + count));
            sb.append("builtin-rule-tags (already handled, do not re-report): ").append(tags).append('\n');
        }

        if (!target.recipe().machines.isEmpty()) {
            sb.append("machines: ").append(String.join(", ", target.recipe().machines)).append('\n');
        }

        if (analyzed.nodes().isEmpty()) {
            sb.append("[NO EXECUTABLE ACTIONS]").append('\n');
        } else {
            for (ChainClassifier.ActionNode node : analyzed.nodes()) {
                PlanAction.ExecuteRecipe execute = node.execute();
                String indent = "  ".repeat(Math.min(node.depth(), 24));
                sb.append(indent).append("[A").append(node.index()).append("] EXECUTE ").append(recipeLabel(execute)).append('\n');
                StringJoiner in = new StringJoiner(", ");
                for (PlanAction.InputBinding binding : execute.boundInputs()) {
                    in.add(ChainClassifier.describeInput(binding.input()) + " x" + binding.amount());
                }
                StringJoiner out = new StringJoiner(", ");
                for (PlanAction.OutputBinding binding : execute.expectedOutputs()) {
                    String key = ChainClassifier.resourceKey(binding.resource());
                    out.add((key == null ? "unknown" : key) + " x" + binding.amount());
                }
                sb.append(indent).append("       in : ").append(in).append('\n');
                sb.append(indent).append("       out: ").append(out).append('\n');
                if (!node.parentIndexes().isEmpty()) {
                    sb.append(indent).append("       <= from ");
                    StringJoiner parents = new StringJoiner(",");
                    for (int parent : node.parentIndexes()) {
                        parents.add("A" + parent);
                    }
                    sb.append(parents).append('\n');
                }
            }
        }

        // raw materials
        Map<String, Long> raws = analyzed.rawAmounts();
        List<String> rawKeys = raws.keySet().stream()
            .filter(key -> !key.startsWith("value:"))
            .sorted()
            .toList();
        if (!rawKeys.isEmpty()) {
            StringJoiner raw = new StringJoiner(", ");
            for (String key : rawKeys) {
                raw.add(key + " x" + raws.get(key));
            }
            sb.append("[RAW] ").append(raw).append('\n');
        }

        // unresolved demands
        if (!analyzed.pendings().isEmpty()) {
            for (PlanAction.PendingInput pending : analyzed.pendings()) {
                sb.append("[UNRESOLVED] ").append(ChainClassifier.describeInput(pending.input()))
                    .append(" x").append(pending.amount())
                    .append(" (candidates=").append(pending.candidateCount())
                    .append(", reason=").append(pending.reason())
                    .append(pending.truncated() ? ", truncated" : "")
                    .append(")\n");
            }
        } else if (analyzed.nodes().isEmpty()) {
            sb.append("[UNRESOLVED] no plan produced\n");
        }

        // catalysts
        for (PlanAction.ReserveCatalyst catalyst : analyzed.catalysts()) {
            sb.append("[CATALYST] ").append(ChainClassifier.describeInput(catalyst.input()))
                .append(" x").append(catalyst.amount())
                .append(" for ").append(catalyst.fromRecipe().id()).append('\n');
        }

        return sb.toString();
    }

    private static String recipeLabel(PlanAction.ExecuteRecipe execute) {
        String id = execute.recipe().id() != null ? execute.recipe().id().toString() : execute.recipe().systemId().toString();
        StringBuilder sb = new StringBuilder(id).append(" x").append(execute.times());
        if (execute.recipe().recipe() instanceof ExportPackLoader.ExportRecipe export) {
            if (!export.machines.isEmpty()) {
                sb.append(" @ ").append(export.machines.get(0));
            }
            if (!export.classificationTags.isEmpty()) {
                StringJoiner tags = new StringJoiner(",");
                for (String tag : export.classificationTags) {
                    tags.add(shortTag(tag));
                }
                sb.append(" [rule-tag: ").append(tags).append("]");
            }
        }
        return sb.toString();
    }

    /** "cirrus:pattern/exchange" -> "pattern/exchange". */
    private static String shortTag(String tag) {
        return tag.startsWith("cirrus:") ? tag.substring("cirrus:".length()) : tag;
    }

    static @Nullable String outcomeIssueSummary(RoutingOutcome outcome) {
        if (outcome.issues().isEmpty()) return null;
        StringJoiner joiner = new StringJoiner("; ");
        outcome.issues().forEach(issue -> joiner.add(String.valueOf(issue)));
        return joiner.toString();
    }
}
