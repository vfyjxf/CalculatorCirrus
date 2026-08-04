package dev.vfyjxf.cirrus.api.recipe.execution;

/**
 * Describes the runtime capability a step depends on before it can advance.
 */
public enum ExecutionCapability {
	storageAccess(true),
	inventoryCapacity(true),
	inventoryTransfer(true),
	interactionAccess(true),
	interactionPerform(true),
	outputStorage(true),
	manualCheckpoint(false);

	//region aliases

	public static final ExecutionCapability accessibleStorage = storageAccess;
	public static final ExecutionCapability manualAction = manualCheckpoint;

	//endregion

	private final boolean requiresEnvironmentCapability;

	ExecutionCapability(boolean requiresEnvironmentCapability) {
		this.requiresEnvironmentCapability = requiresEnvironmentCapability;
	}

	public boolean requiresEnvironmentCapability() {
		return requiresEnvironmentCapability;
	}
}
