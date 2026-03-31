package dev.vfyjxf.cirrus.api.recipe.execution.driver;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionInteraction;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public interface ExecutionDriver {

    ExecutionSession session();

    Namespace interactionId();

    InteractionApi interactionApi();

    default ExecutionInteraction interaction() {
        return new ExecutionInteraction(interactionId(), interactionApi());
    }

    ExecutionDriverSnapshot snapshot();

    void advanceSteps(int steps);

    void pause();

    void resume();

    void toggleAutoAdvance();

    void stopAutoAdvance();

    void setRatePerSecond(double rate);

    void reset();

    void jumpToStep(int index);

    /**
     * Requests rebuilding the current execution session from a clean driver state.
     */
    default void forceReopen() {}

    /**
     * Called once per client tick/frame so drivers can progress time-based state
     * (for example auto-advance timers) even when no explicit control action occurs.
     */
    default void tick() {}

    default void initialize(ExecutionSession session) {}
}
