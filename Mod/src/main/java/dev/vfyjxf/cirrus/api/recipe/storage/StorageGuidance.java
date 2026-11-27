package dev.vfyjxf.cirrus.api.recipe.storage;

import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Structured operator guidance emitted by runtime storage checks and transfers.
 */
public record StorageGuidance(Component summary, @Nullable Component detail) {

    public StorageGuidance {
        Checks.checkNotNull(summary, "summary");
    }

    //region factories

    public static StorageGuidance simple(Component summary) {
        return new StorageGuidance(summary, null);
    }

    //endregion
}
