package dev.vfyjxf.cirrus.ui.execution;

import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetTimesDebouncerTest {

    @Test
    void repeatedRequestsOnlyCommitLatestValueAfterDelay() {
        TargetTimesDebouncer<String> debouncer = new TargetTimesDebouncer<>(4);

        debouncer.request("recipe", 16, 1);
        assertEquals(16, debouncer.visibleValue("recipe", 1));
        assertNull(debouncer.tick("recipe", 1));
        assertNull(debouncer.tick("recipe", 1));

        debouncer.request("recipe", 24, 1);
        assertEquals(4, debouncer.ticksRemaining());
        assertNull(debouncer.tick("recipe", 1));
        assertNull(debouncer.tick("recipe", 1));
        assertNull(debouncer.tick("recipe", 1));

        TargetTimesDebouncer.Commit<String> commit = debouncer.tick("recipe", 1);
        assertEquals("recipe", Objects.requireNonNull(commit).target());
        assertEquals(24, commit.value());
        assertEquals(24, debouncer.visibleValue("recipe", 24));
    }

    @Test
    void targetChangeClearsPendingCommit() {
        TargetTimesDebouncer<String> debouncer = new TargetTimesDebouncer<>(3);

        debouncer.request("old", 8, 1);

        assertNull(debouncer.tick("new", 1));
        assertTrue(!debouncer.pending());
        assertEquals(1, debouncer.visibleValue("new", 1));
    }

    @Test
    void flushCommitsImmediatelyForExplicitPlaybackActions() {
        TargetTimesDebouncer<String> debouncer = new TargetTimesDebouncer<>(8);

        debouncer.request("recipe", 32, 1);

        TargetTimesDebouncer.Commit<String> commit = debouncer.flush("recipe", 1);
        assertEquals("recipe", Objects.requireNonNull(commit).target());
        assertEquals(32, commit.value());
        assertTrue(!debouncer.pending());
    }
}
