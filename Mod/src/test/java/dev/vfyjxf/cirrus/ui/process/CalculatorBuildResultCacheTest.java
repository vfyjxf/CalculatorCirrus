package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.ui.process.CalculatorBuildResultCache.Key;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder.BuildResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CalculatorBuildResultCacheTest {

    @AfterEach
    void clearCache() {
        CalculatorBuildResultCache.clear();
    }

    private static Key key(int seed) {
        return new Key(null, seed, Map.of(), null, seed);
    }

    private static BuildResult result() {
        return new BuildResult(null, null, null, null);
    }

    @Test
    void storedResultsAreReturnedByEqualKey() {
        BuildResult stored = result();
        CalculatorBuildResultCache.put(key(1), stored);
        assertSame(stored, CalculatorBuildResultCache.get(key(1)));
    }

    @Test
    void distinctKeysDoNotCollide() {
        CalculatorBuildResultCache.put(key(1), result());
        assertNull(CalculatorBuildResultCache.get(key(2)));
        assertNull(CalculatorBuildResultCache.get(new Key(null, 1, Map.of("a", "b"), null, 1)));
    }

    @Test
    void lruEvictsBeyondCapacity() {
        for (int i = 0; i < 6; i++) {
            CalculatorBuildResultCache.put(key(i), result());
        }
        assertEquals(4, CalculatorBuildResultCache.size());
        assertNull(CalculatorBuildResultCache.get(key(0)));
        assertNull(CalculatorBuildResultCache.get(key(1)));
        assertNotNull(CalculatorBuildResultCache.get(key(4)));
        assertNotNull(CalculatorBuildResultCache.get(key(5)));
    }

    @Test
    void clearDropsEverything() {
        CalculatorBuildResultCache.put(key(1), result());
        CalculatorBuildResultCache.clear();
        assertEquals(0, CalculatorBuildResultCache.size());
        assertNull(CalculatorBuildResultCache.get(key(1)));
    }
}
