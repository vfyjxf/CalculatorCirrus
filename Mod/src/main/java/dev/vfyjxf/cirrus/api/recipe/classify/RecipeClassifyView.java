package dev.vfyjxf.cirrus.api.recipe.classify;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A plain-data view of one recipe for classification purposes.
 *
 * @param index    stable index of the recipe inside the classified catalog
 * @param systemId id of the owning recipe system ({@code namespace:path},
 *                 e.g. {@code minecraft:crafting} or {@code jei:category/...})
 * @param recipeId the recipe id, or an empty string when unresolved
 * @param machines ids of machine items able to execute the recipe
 * @param inputs   input slots (flattened, order not significant)
 * @param outputs  output slots (flattened, order not significant)
 */
public record RecipeClassifyView(
    int index,
    String systemId,
    String recipeId,
    List<String> machines,
    List<ClassifyContent> inputs,
    List<ClassifyContent> outputs
) {

    public RecipeClassifyView {
        systemId = systemId == null ? "" : systemId;
        recipeId = recipeId == null ? "" : recipeId;
        machines = machines == null ? List.of() : List.copyOf(machines);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }

    /** Union of concrete ids over all input slots. */
    public Set<String> allInputIds() {
        Set<String> out = new LinkedHashSet<>();
        for (ClassifyContent content : inputs) {
            out.addAll(content.ids());
        }
        return out;
    }

    /** Union of concrete ids over all output slots. */
    public Set<String> allOutputIds() {
        Set<String> out = new LinkedHashSet<>();
        for (ClassifyContent content : outputs) {
            out.addAll(content.ids());
        }
        return out;
    }

    /**
     * Primary output id: the single output id when there is exactly one
     * concrete output id, otherwise the first output's first id, or null.
     */
    public @Nullable String primaryOutputId() {
        Set<String> all = allOutputIds();
        if (all.size() == 1) {
            return all.iterator().next();
        }
        for (ClassifyContent content : outputs) {
            if (!content.ids().isEmpty()) {
                return content.ids().iterator().next();
            }
        }
        return null;
    }

    /** The mod namespace of the recipe id ("" when unknown). */
    public String recipeMod() {
        int colon = recipeId.indexOf(':');
        return colon > 0 ? recipeId.substring(0, colon) : "";
    }

    /** The path part of the recipe id. */
    public String recipePath() {
        int colon = recipeId.indexOf(':');
        return colon >= 0 ? recipeId.substring(colon + 1) : recipeId;
    }

    /** Whether at least one input slot carries a concrete item/fluid id. */
    public boolean hasConcreteInputs() {
        for (ClassifyContent content : inputs) {
            if (content.hasIds()) {
                return true;
            }
        }
        return false;
    }
}
