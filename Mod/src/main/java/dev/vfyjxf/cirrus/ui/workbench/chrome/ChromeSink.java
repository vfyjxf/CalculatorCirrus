package dev.vfyjxf.cirrus.ui.workbench.chrome;

import org.jspecify.annotations.Nullable;

/**
 * The chrome seam WP-UI-7 wires in one pass; every method is a no-op until installed.
 */
public interface ChromeSink {

    ChromeSink empty = new ChromeSink() {};

    default void focusPlanStep(String stepId) {}

    default void focusCopilotPrompt(String promptId) {}

    default void canvasCommand(ChromeShortcut.Command command) {}

    default void exportJson() {}

    static ChromeSink current() {
        return Holder.instance;
    }

    static void install(@Nullable ChromeSink sink) {
        Holder.instance = sink == null ? empty : sink;
    }

    final class Holder {

        private static volatile ChromeSink instance = empty;

        private Holder() {}
    }
}
