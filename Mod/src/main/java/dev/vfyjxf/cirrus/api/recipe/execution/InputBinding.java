package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Resolved binding of an input content to a specific amount.
 *
 * @param input  the input content
 * @param amount the required amount (must be positive)
 */
public record InputBinding(InputContent input, long amount) {

    public InputBinding {
        Checks.checkNotNull(input, "input");
        Checks.checkArgument(amount > 0, "amount must be positive");
    }
}
