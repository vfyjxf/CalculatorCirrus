package dev.vfyjxf.calculatorcirrus.utils;


import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.event.EventListeners;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * From JEI
 */
public class StartEventObserver implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Class<? extends Event>> requiredEvents = Set.of(TagsUpdatedEvent.class, RecipesUpdatedEvent.class);

    private enum State {
        LISTENING, MOD_STARTED
    }

    private final Set<Class<? extends Event>> observedEvents = new HashSet<>();
    private final Runnable startRunnable;
    private final Runnable stopRunnable;
    private WeakReference<Connection> currentConnection = new WeakReference<>(null);
    private State state = State.LISTENING;

    public StartEventObserver(Runnable startRunnable, Runnable stopRunnable) {
        this.startRunnable = startRunnable;
        this.stopRunnable = stopRunnable;
    }

    public void register() {
        requiredEvents.forEach(eventClass -> EventListeners.register(eventClass, this::onEvent));

        EventListeners.register(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            if (event.getPlayer() != null) {
                LOGGER.info("Calculator Cirrus StartEventObserver received {}", event.getClass());
                transitionState(State.LISTENING);
            }
        });

        EventListeners.register(ScreenEvent.Init.Pre.class, event -> {
            if (this.state != State.MOD_STARTED) {
                Screen screen = event.getScreen();
                Minecraft minecraft = screen.getMinecraft();
                if (screen instanceof AbstractContainerScreen && minecraft != null && minecraft.player != null) {
                    var missingEvents = requiredEvents.stream()
                            .filter(e -> !observedEvents.contains(e))
                            .sorted()
                            .toList();

                    LOGGER.error("""
                                         A Screen is opening but Calculator Cirrus hasn't started yet.
                                         Normally, Calculator Cirrus is started after ClientPlayerNetworkEvent.LoggedInEvent, TagsUpdatedEvent, and RecipesUpdatedEvent.
                                         Something has caused one or more of these events to fail, so Calculator Cirrus is starting very late.
                                         Missing events: {}""", missingEvents);
                    transitionState(State.LISTENING);
                    transitionState(State.MOD_STARTED);
                }
            }
        });
    }

    /**
     * Observe an event and start JEI if we have observed all the required events.
     */
    private <T extends Event> void onEvent(T event) {
        Connection observingConnection = this.currentConnection.get();
        Connection currentConnection = getCurrentConnection();
        if (currentConnection != observingConnection) {
            // Connection changed => any information we previously got is useless now
            observedEvents.clear();
            this.currentConnection = new WeakReference<>(currentConnection);
        }
        if (currentConnection == null) {
            // No connection => Disregard, this probably an event being fired on the integrated server thread
            LOGGER.info("Calculator Cirrus StartEventObserver received {} too early, ignoring", event.getClass());
            return;
        }
        LOGGER.info("Calculator Cirrus StartEventObserver received {}", event.getClass());
        Class<? extends Event> eventClass = event.getClass();
        if (requiredEvents.contains(eventClass) &&
                observedEvents.add(eventClass) &&
                observedEvents.containsAll(requiredEvents)
        ) {
            if (this.state == State.MOD_STARTED) {
                restart();
            } else {
                transitionState(State.MOD_STARTED);
            }
        }
    }

    @Nullable
    private static Connection getCurrentConnection() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener packetListener = minecraft.getConnection();
        if (packetListener != null) {
            return packetListener.getConnection();
        } else if (minecraft.pendingConnection != null) {
            // TagsUpdatedEvent is fired very early in the connection process,
            // so packetListener is not yet initialized.
            // Instead, we grab it from pendingConnection (singleplayer) or...
            return minecraft.pendingConnection;
        } else if (minecraft.screen instanceof ConnectScreen connectScreen) {
            //...the connect screen (multiplayer)
            return connectScreen.connection;
        } else {
            return null;
        }
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        restart();
    }

    private void restart() {
        if (this.state != State.MOD_STARTED) {
            return;
        }
        transitionState(State.LISTENING);
        transitionState(State.MOD_STARTED);
    }

    private void transitionState(State newState) {
        LOGGER.info("Calculator Cirrus StartEventObserver transitioning state from {} to {}", this.state, newState);

        switch (newState) {
            case LISTENING -> {
                if (this.state == State.MOD_STARTED) {
                    this.stopRunnable.run();
                }
            }
            case MOD_STARTED -> {
                if (this.state != State.LISTENING) {
                    throw new IllegalStateException("Attempted Illegal state transition from " + this.state + " to " + newState);
                }
                this.startRunnable.run();
            }
        }

        this.state = newState;
        this.observedEvents.clear();
    }
}
