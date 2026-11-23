package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Expected output binding indicating the resource and amount produced by executing a recipe.
 *
 * @param resource the produced resource
 * @param amount   the expected amount (must be positive)
 */
public record OutputBinding(ContentResource resource, long amount) {

    public OutputBinding {
        Checks.checkNotNull(resource, "resource");
        Checks.checkArgument(amount > 0, "amount must be positive");
    }
}
