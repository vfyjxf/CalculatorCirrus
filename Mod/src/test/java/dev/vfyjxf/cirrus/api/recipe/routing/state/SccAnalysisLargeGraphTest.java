package dev.vfyjxf.cirrus.api.recipe.routing.state;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SccAnalysisLargeGraphTest {
    @Test
    void deepRecipeDependencyChainDoesNotUseJavaCallStack() {
        int nodeCount = 50_000;
        IntArrayList[] edges = new IntArrayList[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            edges[i] = new IntArrayList(1);
            if (i + 1 < nodeCount) {
                edges[i].add(i + 1);
            }
        }
        BitSet allowed = new BitSet(nodeCount);
        allowed.set(0, nodeCount);

        SccAnalysis analysis = SccAnalysis.analyzeEdgesForTesting(edges, allowed);

        boolean[] seenScc = new boolean[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            int scc = analysis.sccOf(i);
            assertTrue(scc >= 0 && scc < nodeCount);
            assertFalse(seenScc[scc]);
            seenScc[scc] = true;
            assertFalse(analysis.isCyclicScc(scc));
        }
        assertEquals(nodeCount, analysis.recipeToScc().length);
        assertEquals(nodeCount, analysis.cyclicScc().length);
    }
}
