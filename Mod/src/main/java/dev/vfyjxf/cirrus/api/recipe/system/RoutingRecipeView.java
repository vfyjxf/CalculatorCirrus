package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Lightweight content view used by routing graph construction.
 *
 * <p>This intentionally does not carry projection clauses or schemas. Systems should return it only
 * when routing can consume their raw deterministic inputs and outputs without invoking projection
 * transformers.
 */
public record RoutingRecipeView(
    Iterable<? extends InputContent> inputs,
    Iterable<? extends OutputContent> outputs
) {

    public RoutingRecipeView {
        Checks.checkNotNull(inputs, "inputs");
        Checks.checkNotNull(outputs, "outputs");
    }
}
