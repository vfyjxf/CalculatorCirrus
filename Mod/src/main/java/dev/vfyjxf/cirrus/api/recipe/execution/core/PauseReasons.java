package dev.vfyjxf.cirrus.api.recipe.execution.core;

import dev.vfyjxf.cirrus.api.recipe.execution.util.PauseReasonKey;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;

/**
 * Builtin pause reasons used when interaction execution cannot continue automatically.
 */
public final class PauseReasons {

    //region reasons

    public static final PauseReasonKey awaitingPlayerAction = reason("awaiting_player_action");
    public static final PauseReasonKey noAccessibleProvider = reason("no_accessible_provider");
    public static final PauseReasonKey noAccessibleReceiver = reason("no_accessible_receiver");
    public static final PauseReasonKey noAccessibleProcessor = reason("no_accessible_processor");
    public static final PauseReasonKey inventoryCapacityBlocked = reason("inventory_capacity_blocked");

    //endregion

    //region helpers

    private static PauseReasonKey reason(String path) {
        return new PauseReasonKey(CirrusNamespaces.ofMod(path));
    }

    //endregion

    private PauseReasons() {throw new AssertionError("No " + PauseReasons.class.getSimpleName() + " instances for you!");}
}
