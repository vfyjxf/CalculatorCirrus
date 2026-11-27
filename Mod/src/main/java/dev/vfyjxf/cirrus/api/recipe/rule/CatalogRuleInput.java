package dev.vfyjxf.cirrus.api.recipe.rule;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link RuleInput} adapter over a plain {@link RecipeClassifyView}, used at
 * the catalog (classification) stage. Registry-free by construction, so the
 * same rules run in-game and in the offline export harness.
 */
public final class CatalogRuleInput implements RuleInput {

    private final RecipeClassifyView view;
    private final List<Slot> inputSlots;
    private final List<Slot> outputSlots;
    private final Set<String> inputIds;
    private final Set<String> outputIds;

    public CatalogRuleInput(RecipeClassifyView view) {
        this.view = view;
        this.inputSlots = adapt(view.inputs());
        this.outputSlots = adapt(view.outputs());
        this.inputIds = union(inputSlots);
        this.outputIds = union(outputSlots);
    }

    private static List<Slot> adapt(List<ClassifyContent> slots) {
        List<Slot> out = new ArrayList<>(slots.size());
        for (var content : slots) {
            out.add(new Slot(content.ids(), content.tagId(), content.amount(), content.chance()));
        }
        return List.copyOf(out);
    }

    private static Set<String> union(List<Slot> slots) {
        Set<String> ids = new LinkedHashSet<>();
        for (Slot slot : slots) {
            ids.addAll(slot.ids());
        }
        return Set.copyOf(ids);
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
        return (inputSide ? inputSlots : outputSlots).size();
    }

    @Override
    public Slot slot(boolean inputSide, int index) {
        return (inputSide ? inputSlots : outputSlots).get(index);
    }

    @Override
    public Set<String> inputIds() {
        return inputIds;
    }

    @Override
    public Set<String> outputIds() {
        return outputIds;
    }

    private record Slot(Set<String> ids, @Nullable String tagId, double amount, double chance)
            implements
                RuleInput.Slot {}
}
