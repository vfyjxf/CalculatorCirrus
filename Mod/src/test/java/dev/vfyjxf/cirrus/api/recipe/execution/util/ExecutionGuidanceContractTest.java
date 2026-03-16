package dev.vfyjxf.cirrus.api.recipe.execution.util;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionGuidanceContractTest {

    //region null rejection

    @Test
    void summaryRejectsNull() {
        assertThrows(NullPointerException.class, () ->
                new ExecutionGuidance(null, null, Lists.immutable.empty(), Sets.immutable.empty()));
    }

    @Test
    void stepsRejectsNull() {
        assertThrows(NullPointerException.class, () ->
                new ExecutionGuidance(Component.literal("ok"), null, null, Sets.immutable.empty()));
    }

    @Test
    void hintTagsRejectsNull() {
        assertThrows(NullPointerException.class, () ->
                new ExecutionGuidance(Component.literal("ok"), null, Lists.immutable.empty(), null));
    }

    //endregion

    //region nullable detail

    @Test
    void detailMayBeNull() {
        var guidance = new ExecutionGuidance(
                Component.literal("summary"), null, Lists.immutable.empty(), Sets.immutable.empty());
        assertNull(guidance.detail());
    }

    //endregion

    //region steps preservation

    @Test
    void emptyStepsPreserved() {
        var guidance = new ExecutionGuidance(
                Component.literal("summary"), null, Lists.immutable.empty(), Sets.immutable.empty());
        assertTrue(guidance.steps().isEmpty());
    }

    @Test
    void nonEmptyStepsPreserved() {
        var step1 = Component.literal("step one");
        var step2 = Component.literal("step two");
        var guidance = new ExecutionGuidance(
                Component.literal("summary"), null, Lists.immutable.of(step1, step2), Sets.immutable.empty());
        assertEquals(2, guidance.steps().size());
        assertEquals(step1, guidance.steps().get(0));
        assertEquals(step2, guidance.steps().get(1));
    }

    //endregion

    //region simple factory

    @Test
    void simpleFactoryCreatesWithDefaults() {
        var summary = Component.literal("test summary");
        var guidance = ExecutionGuidance.simple(summary);
        assertEquals(summary, guidance.summary());
        assertNull(guidance.detail());
        assertTrue(guidance.steps().isEmpty());
        assertTrue(guidance.hintTags().isEmpty());
    }

    //endregion

    //region content preservation

    @Test
    void summaryComponentContentPreserved() {
        var summary = Component.literal("expected text");
        var guidance = ExecutionGuidance.simple(summary);
        assertEquals(summary, guidance.summary());
        assertEquals("expected text", summary.getString());
    }

    @Test
    void hintTagsPreserved() {
        var tag = Namespace.of("cirrus", "important");
        var guidance = new ExecutionGuidance(
                Component.literal("summary"), null, Lists.immutable.empty(), Sets.immutable.of(tag));
        assertTrue(guidance.hintTags().contains(tag));
        assertEquals(1, guidance.hintTags().size());
    }

    //endregion
}
