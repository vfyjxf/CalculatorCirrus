package dev.vfyjxf.cirrus.api.recipe.storage;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Immutable transfer request used for runtime storage simulation and execution.
 */
public record StorageTransferRequest(ContentResource resource, long amount) {

    public StorageTransferRequest {
        Checks.checkNotNull(resource, "resource");
        Checks.checkArgument(amount > 0, "amount must be positive");
    }
}
