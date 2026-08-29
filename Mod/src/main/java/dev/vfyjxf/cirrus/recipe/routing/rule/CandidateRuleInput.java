package dev.vfyjxf.cirrus.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.CandidateContext;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleFlag;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleInput;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleScalar;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * {@link RuleInput} adapter over a planner {@link CandidateContext}, used at
 * the candidate (routing) stage. Recipe content is materialized through the
 * same registry-free {@link ClassifyViewFactory} the classifier uses, so a
 * {@code material} condition matches identical ids/tags at both stages.
 *
 * <p>Built only on the planner's slow path (when user candidate rules are
 * attached); allocation is acceptable there.
 */
public final class CandidateRuleInput implements RuleInput {

    private final CandidateContext context;
    private final RecipeClassifyView view;

    public CandidateRuleInput(CandidateContext context) {
        this.context = context;
        RecipeGraph.Node node = context.recipe();
        this.view = ClassifyViewFactory.fromNode(node, -1);
    }

    @Override
    public String systemId() {
        return view.systemId();
    }

    @Override
    public String recipeId() {
        return view.recipeId();
    }

    @Override
    public List<String> machines() {
        return view.machines();
    }

    @Override
    public int slotCount(boolean inputSide) {
        return inputSide ? view.inputs().size() : view.outputs().size();
    }

    @Override
    public RuleInput.Slot slot(boolean inputSide, int index) {
        var content = inputSide ? view.inputs().get(index) : view.outputs().get(index);
        return new Slot(content.ids(), content.tagId(), content.amount(), content.chance());
    }

    @Override
    public Set<String> inputIds() {
        return view.allInputIds();
    }

    @Override
    public Set<String> outputIds() {
        return view.allOutputIds();
    }

    @Override
    public boolean hasCandidateFields() {
        return true;
    }

    @Override
    public boolean flag(RuleFlag flag) {
        return switch (flag) {
            case cycleCandidate -> context.cycleCandidate();
            case activeDemandCycle -> context.indirectActiveDemandCycle();
            case intraLoopConversion -> context.intraLoopConversion();
            case roundTrip -> context.roundTripWithActive();
        };
    }

    @Override
    public double scalar(RuleScalar scalar) {
        return switch (scalar) {
            case depth -> context.depth();
            case craftTimes -> context.craftTimes();
            case subtreeCost -> context.estimatedSubtreeCost();
            case amplificationRisk -> context.amplificationRisk();
            case contextualPenalty -> context.contextualPenalty();
        };
    }

    @Override
    public double coverage() {
        return context.availableCoverage();
    }

    @Override
    public @Nullable RecipeUsePolicy hintPolicy() {
        return context.routingHints().usePolicy();
    }

    private record Slot(Set<String> ids, @Nullable String tagId, double amount, double chance) implements RuleInput.Slot {
    }
}
