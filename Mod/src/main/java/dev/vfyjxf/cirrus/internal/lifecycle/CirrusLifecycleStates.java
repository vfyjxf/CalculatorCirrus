package dev.vfyjxf.cirrus.internal.lifecycle;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.api.lifecycle.LifecycleState;
import dev.vfyjxf.cloudlib.api.lifecycle.LifecycleStates;
import net.minecraft.network.Connection;

public final class CirrusLifecycleStates {

    private static final LifecycleStates states = LifecycleStates.create(CirrusConstants.modId);

    public static final LifecycleState<Connection> clientConnection = states.context("client_connection", Connection.class);
    public static final LifecycleState<Void> tagsReady = states.event("tags_ready");
    public static final LifecycleState<Void> recipesReady = states.event("recipes_ready");
    public static final LifecycleState<Void> dataMapsReady = states.event("data_maps_ready");
    public static final LifecycleState<Void> serverStarted = states.context("server_started");
    public static final LifecycleState<Void> serverDataReady = states.event("server_data_ready");

    private CirrusLifecycleStates() {
        throw new AssertionError("This class should not be instantiated!");
    }
}
