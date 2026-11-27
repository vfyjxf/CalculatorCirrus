package dev.vfyjxf.cirrus.api.recipe.execution.util;

import dev.vfyjxf.cirrus.api.recipe.execution.core.ExecutionModes;
import dev.vfyjxf.cirrus.api.recipe.execution.core.ExecutionStatuses;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionKeysContractTest {

    //region null rejection

    @Test
    @SuppressWarnings("NullAway")
    void executionStatusKeyRejectsNull() {
        assertThrows(NullPointerException.class, () -> new ExecutionStatusKey(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void executionModeKeyRejectsNull() {
        assertThrows(NullPointerException.class, () -> new ExecutionModeKey(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void pauseReasonKeyRejectsNull() {
        assertThrows(NullPointerException.class, () -> new PauseReasonKey(null));
    }

    //endregion

    //region ExecutionStatuses constants

    @Test
    void runningStatusHasCorrectPath() {
        assertEquals("running", ExecutionStatuses.running.id().path());
    }

    @Test
    void completedStatusHasCorrectPath() {
        assertEquals("completed", ExecutionStatuses.completed.id().path());
    }

    @Test
    void pausedStatusHasCorrectPath() {
        assertEquals("paused", ExecutionStatuses.paused.id().path());
    }

    @Test
    void failedStatusHasCorrectPath() {
        assertEquals("failed", ExecutionStatuses.failed.id().path());
    }

    //endregion

    //region ExecutionModes constants

    @Test
    void automaticModeHasCorrectPath() {
        assertEquals("automatic", ExecutionModes.automatic.id().path());
    }

    @Test
    void guidedModeHasCorrectPath() {
        assertEquals("guided", ExecutionModes.guided.id().path());
    }

    //endregion

    //region PauseReasons constants

    @Test
    void awaitingPlayerActionHasCorrectPath() {
        assertEquals("awaiting_player_action", PauseReasons.awaitingPlayerAction.id().path());
    }

    @Test
    void noAccessibleProviderHasCorrectPath() {
        assertEquals("no_accessible_provider", PauseReasons.noAccessibleProvider.id().path());
    }

    @Test
    void noAccessibleReceiverHasCorrectPath() {
        assertEquals("no_accessible_receiver", PauseReasons.noAccessibleReceiver.id().path());
    }

    @Test
    void noAccessibleProcessorHasCorrectPath() {
        assertEquals("no_accessible_processor", PauseReasons.noAccessibleProcessor.id().path());
    }

    @Test
    void inventoryCapacityBlockedHasCorrectPath() {
        assertEquals("inventory_capacity_blocked", PauseReasons.inventoryCapacityBlocked.id().path());
    }

    //endregion

    //region equality and hashCode

    @Test
    void keysWithSameNamespaceAreEqual() {
        var ns = CirrusNamespaces.ofMod("running");
        var a = new ExecutionStatusKey(ns);
        var b = new ExecutionStatusKey(ns);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void keysWithDifferentNamespaceAreNotEqual() {
        var a = new ExecutionStatusKey(CirrusNamespaces.ofMod("running"));
        var b = new ExecutionStatusKey(CirrusNamespaces.ofMod("paused"));
        assertNotEquals(a, b);
    }

    //endregion
}
