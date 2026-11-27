package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;

public final class WorkbenchPaint {
    public static final int pulsePeriodMs = 2000;
    public static final double pulseMin = 0.35;
    public static final double pulseMax = 0.95;
    public static final int flowPeriodMs = 1600;
    public static final int flowParticleSpacingPx = 48;

    private WorkbenchPaint() {}

    public static VisualTexture tinted(VisualTexture texture, int argb) {
        return VisualTexture.tinted(texture, argb);
    }

    public static void card(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        boolean hovered,
        boolean selected
    ) {
        OreUiPaint.raisedCard(canvas, x, y, width, height, palette, hovered, selected);
    }

    public static void shadowed(SceneCanvas canvas, int x, int y, int width, int height, CirrusPalette palette) {
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.shadow(x, y, width, height, 3f, 6f, 8f, palette.dropShadow());
    }

    public static void dotGrid(SceneCanvas canvas, int width, int height, float zoom, CirrusPalette palette) {
        if (width <= 0 || height <= 0 || !Float.isFinite(zoom)) {
            return;
        }
        if (zoom >= 0.55f) {
            for (int y = 0; y < height; y += 16) {
                for (int x = 0; x < width; x += 16) {
                    canvas.fill(x, y, 1, 1, palette.canvasGridMinor());
                }
            }
        }
        if (zoom >= 0.35f) {
            for (int y = 0; y < height; y += 80) {
                for (int x = 0; x < width; x += 80) {
                    canvas.fill(x - 2, y, 5, 1, palette.canvasGridMajor());
                    canvas.fill(x, y - 2, 1, 5, palette.canvasGridMajor());
                }
            }
        }
    }

    public static double pulseAlpha(double timeMs, boolean reducedMotion) {
        if (reducedMotion) {
            return pulseMax;
        }
        double phase = (timeMs % pulsePeriodMs) / (double) pulsePeriodMs;
        double mid = (pulseMin + pulseMax) / 2.0;
        double amplitude = (pulseMax - pulseMin) / 2.0;
        return mid + amplitude * Math.sin(phase * Math.PI * 2.0);
    }

    public static void bezierGlow(
        SceneCanvas canvas,
        float x0,
        float y0,
        float cx0,
        float cy0,
        float cx1,
        float cy1,
        float x1,
        float y1,
        int argb
    ) {
        int glow = (argb & 0x00FFFFFF) | 0x33000000;
        canvas.bezierCubic(x0, y0, cx0, cy0, cx1, cy1, x1, y1, 6f, glow);
        canvas.bezierCubic(x0, y0, cx0, cy0, cx1, cy1, x1, y1, 2f, argb);
    }

    public static double flowParticleT(double timeMs, float pathLengthPx, int index) {
        if (!Double.isFinite(timeMs) || pathLengthPx <= 0 || index < 0) {
            return 0.0;
        }
        double spacing = flowParticleSpacingPx / (double) pathLengthPx;
        double base = (timeMs % flowPeriodMs) / (double) flowPeriodMs;
        double t = (base + index * spacing) % 1.0;
        return t < 0 ? t + 1.0 : t;
    }

    public static int flowParticleCount(float pathLengthPx) {
        if (pathLengthPx <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(pathLengthPx / (double) flowParticleSpacingPx));
    }
}
