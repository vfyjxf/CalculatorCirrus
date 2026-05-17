package dev.vfyjxf.cirrus.internal.lifecycle;

import dev.vfyjxf.cloudlib.api.lifecycle.LifecycleReceiver;
import dev.vfyjxf.cloudlib.api.lifecycle.LifecycleSource;
import dev.vfyjxf.cloudlib.api.lifecycle.LifecycleState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import org.jetbrains.annotations.Nullable;

public final class CirrusLifecycleSources {

    private CirrusLifecycleSources() {
        throw new AssertionError("This class should not be instantiated!");
    }

    @OnlyIn(Dist.CLIENT)
    public static LifecycleSource client(IEventBus modBus) {
        return receiver -> {
            NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) ->
                    eventIfConnected(receiver, CirrusLifecycleStates.tagsReady, event));
            NeoForge.EVENT_BUS.addListener((RecipesUpdatedEvent event) ->
                    eventIfConnected(receiver, CirrusLifecycleStates.recipesReady, event));
            NeoForge.EVENT_BUS.addListener((DataMapsUpdatedEvent event) ->
                    eventIfConnected(receiver, CirrusLifecycleStates.dataMapsReady, event));
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
                if (event.getPlayer() != null) {
                    receiver.clear(CirrusLifecycleStates.clientConnection, "clientLoggingOut");
                }
            });
            modBus.addListener((RegisterClientReloadListenersEvent event) ->
                    event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
                        if (!receiver.reload("clientResourceReload")) {
                            receiver.resetEvents("clientResourceReload");
                        }
                    }));
        };
    }

    public static LifecycleSource server() {
        return receiver -> {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
                receiver.context(CirrusLifecycleStates.serverStarted, "serverStarted");
                if (!receiver.isLoaded()) {
                    receiver.event(CirrusLifecycleStates.serverDataReady, "serverStartedDataAvailable");
                }
            });
            NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                    receiver.clear(CirrusLifecycleStates.serverStarted, "serverStopping"));
            NeoForge.EVENT_BUS.addListener(
                    EventPriority.LOWEST,
                    (TagsUpdatedEvent event) -> {
                        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
                            receiver.event(CirrusLifecycleStates.serverDataReady, "serverDataLoad");
                        }
                    }
            );
        };
    }

    @OnlyIn(Dist.CLIENT)
    private static <T extends Event> void eventIfConnected(
            LifecycleReceiver receiver,
            LifecycleState<Void> state,
            T event
    ) {
        if (!contextCurrentConnection(receiver, event.getClass().getSimpleName())) {
            return;
        }
        receiver.event(state, event.getClass().getSimpleName());
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean contextCurrentConnection(LifecycleReceiver receiver, String reason) {
        Connection currentConnection = currentConnection();
        if (currentConnection == null) {
            return false;
        }
        receiver.context(CirrusLifecycleStates.clientConnection, currentConnection, reason);
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static @Nullable Connection currentConnection() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener packetListener = minecraft.getConnection();
        if (packetListener != null) {
            return packetListener.getConnection();
        } else if (minecraft.pendingConnection != null) {
            return minecraft.pendingConnection;
        } else if (minecraft.screen instanceof ConnectScreen connectScreen) {
            return connectScreen.connection;
        } else {
            return null;
        }
    }
}
