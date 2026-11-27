package dev.vfyjxf.cirrus.api.recipe.storage;

import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

/**
 * Result of a simulated or real storage transfer operation.
 */
public record StorageTransferResult(
    @Nullable StorageSource source,
    StorageTransferRequest request,
    long transferredAmount,
    @Nullable StorageGuidance guidance
) {

    public StorageTransferResult {
        Checks.checkNotNull(request, "request");
        Checks.checkArgument(transferredAmount >= 0, "transferredAmount must be non-negative");
    }

    //region factories

    public static StorageTransferResult none(
        @Nullable StorageSource source,
        StorageTransferRequest request,
        @Nullable StorageGuidance guidance
    ) {
        return new StorageTransferResult(source, request, 0L, guidance);
    }

    public static StorageTransferResult transferred(
        @Nullable StorageSource source,
        StorageTransferRequest request,
        long transferredAmount
    ) {
        return new StorageTransferResult(source, request, transferredAmount, null);
    }

    //endregion

    //region derived state

    public boolean completed() {
        return transferredAmount >= request.amount();
    }

    public long remainingAmount() {
        return Math.max(0L, request.amount() - transferredAmount);
    }

    //endregion
}
