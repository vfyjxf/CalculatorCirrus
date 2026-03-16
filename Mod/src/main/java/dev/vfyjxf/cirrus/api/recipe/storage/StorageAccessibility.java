package dev.vfyjxf.cirrus.api.recipe.storage;

import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

/**
 * Describes whether a storage source is currently reachable for execution.
 */
public sealed interface StorageAccessibility permits
    StorageAccessibility.Accessible,
    StorageAccessibility.Inaccessible {

    //region shared instances

    StorageAccessibility accessible = new Accessible();

    //endregion

    boolean accessible();

    @Nullable StorageGuidance guidance();

    //region factories

    static StorageAccessibility inaccessible(StorageGuidance guidance) {
        return new Inaccessible(guidance);
    }

    //endregion

    //region variants

    record Accessible() implements StorageAccessibility {
        @Override
        public boolean accessible() {
            return true;
        }

        @Override
        public StorageGuidance guidance() {
            return null;
        }
    }

    record Inaccessible(StorageGuidance guidance) implements StorageAccessibility {
        public Inaccessible {
            Checks.checkNotNull(guidance, "guidance");
        }

        @Override
        public boolean accessible() {
            return false;
        }
    }

    //endregion
}
