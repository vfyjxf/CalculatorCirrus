package dev.vfyjxf.cirrus.ui.workbench.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreUiMotionTest {

    @Test
    void cubicOutAndDampRespectReducedMotion() {
        assertEquals(1f, OreUiMotion.cubicOut(0L, 120L, true));
        assertEquals(0f, OreUiMotion.cubicOut(0L, 120L, false));
        float mid = OreUiMotion.cubicOut(60L, 120L, false);
        assertTrue(mid > 0.5f && mid < 1f);
        assertEquals(1f, OreUiMotion.cubicOut(140L, 120L, false));

        assertEquals(100f, OreUiMotion.damp(0f, 100f, 12f, 0.016f, true));
        float stepped = OreUiMotion.damp(0f, 100f, 12f, 0.016f, false);
        assertTrue(stepped > 10f && stepped < 90f);
    }

    @Test
    void smoothValueConvergesMonotonicallyAndSnapsWhenReduced() {
        OreUiMotion.SmoothValue smooth = new OreUiMotion.SmoothValue();
        long t = 1_000_000_000L;
        assertEquals(10f, smooth.update(10f, 16f, t, false));

        float first = smooth.update(50f, 16f, t + 16_000_000L, false);
        float second = smooth.update(50f, 16f, t + 32_000_000L, false);
        assertTrue(first > 10f && first < 50f);
        assertTrue(second > first && second < 50f);

        assertEquals(90f, smooth.update(90f, 16f, t + 48_000_000L, true));
    }

    @Test
    void wavesAndColorInterpolationStayBounded() {
        assertEquals(0.5f, OreUiMotion.pulse01(250L, 1000L, true));
        assertEquals(0.5f, OreUiMotion.cycle01(250L, 1000L, 0.2f, true));

        float p0 = OreUiMotion.pulse01(0L, 1000L, false);
        float pHalf = OreUiMotion.pulse01(500L, 1000L, false);
        assertEquals(0f, p0, 0.001f);
        assertEquals(1f, pHalf, 0.001f);

        int half = OreUiMotion.lerpColor(0x00000000, 0xFFFFFFFF, 0.5f);
        assertEquals(0x80808080, half);
    }
}
