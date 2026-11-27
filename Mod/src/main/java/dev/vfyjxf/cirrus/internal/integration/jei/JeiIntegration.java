package dev.vfyjxf.cirrus.internal.integration.jei;

import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lifecycle bridge for callers outside the jei integration that must react to
 * the JEI system catalog becoming final. {@code onRuntimeAvailable} is the
 * point where JEI's category set (and therefore the recipe system set built
 * from it) stops growing; before that, any system-derived index only covers a
 * prefix of the real set.
 *
 * <p>Holds no JEI API types in its signature so classes in other packages can
 * reference it safely even when JEI is absent.
 */
public final class JeiIntegration {

    private static volatile boolean systemsReady;
    @Nullable
    private static volatile List<Runnable> pendingCallbacks = List.of();

    private JeiIntegration() {
        throw new AssertionError("This class should not be instantiated!");
    }

    /**
     * True once JEI's runtime is available and the system catalog has been
     * rebuilt from it. False while JEI is present but its runtime has not
     * arrived yet; use {@link #jeiAbsent()} to tell that case apart from a
     * JEI-less environment.
     */
    public static boolean systemsReady() {
        return systemsReady;
    }

    /**
     * True when the JEI mod is not loaded at all — the plugin-loaded system
     * set (vanilla fallback categories) is then already final at plugin
     * dispatch time, with nothing to wait for.
     */
    public static boolean jeiAbsent() {
        return !ModList.get().isLoaded("jei");
    }

    /**
     * Registers {@code callback} to run once the system set is final. When the
     * set is already final the callback runs synchronously on the caller's
     * thread; otherwise it runs on the thread that flips readiness (JEI's
     * runtime-available signal, typically the mod-loading or render thread).
     * Single-shot: the callback is consumed on the first readiness flip.
     */
    public static void onSystemsReady(Runnable callback) {
        if (systemsReady) {
            callback.run();
            return;
        }
        synchronized (JeiIntegration.class) {
            if (systemsReady) {
                callback.run();
                return;
            }
            ArrayList<Runnable> pending = new ArrayList<>(pendingCallbacks);
            pending.add(callback);
            pendingCallbacks = pending;
        }
    }

    /** Called by {@code CirrusJeiPlugin.onRuntimeAvailable} after the catalog rebuild. */
    public static void markSystemsReady() {
        List<Runnable> toRun;
        synchronized (JeiIntegration.class) {
            if (systemsReady) {
                return;
            }
            systemsReady = true;
            toRun = pendingCallbacks;
            pendingCallbacks = List.of();
        }
        for (Runnable callback : toRun == null ? List.<Runnable>of() : toRun) {
            callback.run();
        }
    }

    /**
     * Test-only: clears the readiness flag and pending callbacks. The bridge is
     * a process-wide singleton, so tests need a way back to the pristine state.
     */
    static void resetForTests() {
        synchronized (JeiIntegration.class) {
            systemsReady = false;
            pendingCallbacks = List.of();
        }
    }
}
