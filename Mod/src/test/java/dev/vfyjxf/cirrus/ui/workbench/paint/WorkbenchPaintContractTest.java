package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchPaintContractTest {

    @Test
    void pulseAlphaBreathesAcrossTheTwoSecondPeriod() {
        assertEquals(0.65, WorkbenchPaint.pulseAlpha(0.0, false), 1e-9);
        assertEquals(WorkbenchPaint.pulseMax, WorkbenchPaint.pulseAlpha(500.0, false), 1e-9);
        assertEquals(WorkbenchPaint.pulseMin, WorkbenchPaint.pulseAlpha(1500.0, false), 1e-9);
        assertEquals(
            WorkbenchPaint.pulseAlpha(1234.5, false),
            WorkbenchPaint.pulseAlpha(1234.5 + WorkbenchPaint.pulsePeriodMs, false),
            1e-9
        );
        for (int step = 0; step <= 40; step++) {
            double alpha = WorkbenchPaint.pulseAlpha(step * 50.0, false);
            assertTrue(alpha >= WorkbenchPaint.pulseMin - 1e-9, "pulse alpha dropped below the floor: " + alpha);
            assertTrue(alpha <= WorkbenchPaint.pulseMax + 1e-9, "pulse alpha rose above the ceiling: " + alpha);
        }
    }

    @Test
    void reducedMotionFreezesPulseAtMaximum() {
        for (int step = 0; step <= 8; step++) {
            assertEquals(WorkbenchPaint.pulseMax, WorkbenchPaint.pulseAlpha(step * 250.0, true), 1e-9);
        }
    }

    @Test
    void flowParticlesAdvanceOneSpacingEveryFortyEightPixels() {
        assertEquals(0.0, WorkbenchPaint.flowParticleT(0.0, 480f, 0), 1e-9);
        assertEquals(0.25, WorkbenchPaint.flowParticleT(400.0, 480f, 0), 1e-9);
        assertEquals(0.5, WorkbenchPaint.flowParticleT(0.0, 480f, 5), 1e-9);
        assertEquals(0.0, WorkbenchPaint.flowParticleT(0.0, 480f, 10), 1e-9);
        assertEquals(
            WorkbenchPaint.flowParticleT(1.0, 500f, 3),
            WorkbenchPaint.flowParticleT(1.0 + WorkbenchPaint.flowPeriodMs, 500f, 3),
            1e-9
        );
        assertEquals(0.0, WorkbenchPaint.flowParticleT(100.0, 0f, 0), 1e-9);
        assertEquals(1, WorkbenchPaint.flowParticleCount(0f));
        assertEquals(1, WorkbenchPaint.flowParticleCount(48f));
        assertEquals(2, WorkbenchPaint.flowParticleCount(49f));
    }

    @Test
    void tintedWrapsTheBaseTextureWithoutRendering() {
        VisualTexture base = CirrusTextures.Icon.search;
        VisualTexture tinted = WorkbenchPaint.tinted(base, 0x8038BDF8);
        VisualTexture tintedAgain = WorkbenchPaint.tinted(base, 0x8038BDF8);

        assertNotNull(tinted);
        assertNotSame(base, tinted);
        assertNotSame(tinted, tintedAgain);
    }
}
