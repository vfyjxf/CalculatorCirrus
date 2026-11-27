package dev.vfyjxf.cirrus.ui.execution.overlay;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ExecutionOverlayOpenState {
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static volatile boolean open = true;

    private ExecutionOverlayOpenState() {}

    public static boolean open() {
        return open;
    }

    public static boolean setOpen(boolean nextOpen) {
        if (open == nextOpen) {
            return open;
        }
        open = nextOpen;
        notifyListeners();
        return open;
    }

    public static boolean toggle() {
        return setOpen(!open);
    }

    public static void addListener(Runnable listener) {
        if (listener == null || listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
