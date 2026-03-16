package dev.vfyjxf.cirrus.api.recipe.execution;

/**
 * Describes the runtime capability a step depends on before it can advance.
 */
public sealed interface ExecutionCapability permits
    ExecutionCapability.StorageAccess,
    ExecutionCapability.InventoryCapacity,
    ExecutionCapability.InventoryTransfer,
    ExecutionCapability.InteractionAccess,
    ExecutionCapability.InteractionPerform,
    ExecutionCapability.OutputStorage,
    ExecutionCapability.ManualCheckpoint {

    //region shared instances

    ExecutionCapability storageAccess = new StorageAccess();
    ExecutionCapability accessibleStorage = storageAccess;
    ExecutionCapability inventoryCapacity = new InventoryCapacity();
    ExecutionCapability inventoryTransfer = new InventoryTransfer();
    ExecutionCapability interactionAccess = new InteractionAccess();
    ExecutionCapability interactionPerform = new InteractionPerform();
    ExecutionCapability outputStorage = new OutputStorage();
    ExecutionCapability manualCheckpoint = new ManualCheckpoint();
    ExecutionCapability manualAction = manualCheckpoint;

    //endregion

    boolean requiresEnvironmentCapability();

    //region variants

    record StorageAccess() implements ExecutionCapability {
        @Override
        public boolean requiresEnvironmentCapability() {
            return true;
        }
    }

    record InventoryCapacity() implements ExecutionCapability {
        @Override
        public boolean requiresEnvironmentCapability() {
            return true;
        }
    }

    record InventoryTransfer() implements ExecutionCapability {
        @Override
        public boolean requiresEnvironmentCapability() {
            return true;
        }
    }

    record InteractionAccess() implements ExecutionCapability {
        @Override
        public boolean requiresEnvironmentCapability() {
            return true;
        }
    }

    record InteractionPerform() implements ExecutionCapability {
        @Override
        public boolean requiresEnvironmentCapability() {
            return true;
        }
    }

    record OutputStorage() implements ExecutionCapability {
        @Override
        public boolean requiresEnvironmentCapability() {
            return true;
        }
    }

    record ManualCheckpoint() implements ExecutionCapability {
        @Override
        public boolean requiresEnvironmentCapability() {
            return false;
        }
    }

    //endregion
}
