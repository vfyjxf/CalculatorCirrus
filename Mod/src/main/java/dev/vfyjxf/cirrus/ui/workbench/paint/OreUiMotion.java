package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cloudlib.api.ui.texture.Easing;

/**
 * Shared frame-rate-independent motion primitives and reduced-motion-safe state smoothers for OreUI workbench surfaces.
 */
public final class OreUiMotion {

    public static final long overlayAppearMs = 125L;
    public static final long panelSlideMs = 135L;
    public static final long tooltipAppearMs = 95L;
    public static final float overlaySlidePx = 5f;
    private static final float snapEpsilon = 0.002f;
    private static final float maxStepSeconds = 0.08f;

    private OreUiMotion() {}

    public static boolean reducedMotion() {
        try {
            return ExecutionOverlayClientConfig.workbenchReducedMotion();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    public static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    public static float cubicOut(long elapsedMs, long durationMs, boolean reduced) {
        if (reduced || durationMs <= 0L) {
            return 1f;
        }
        if (elapsedMs <= 0L) {
            return 0f;
        }
        float raw = clamp01(elapsedMs / (float) durationMs);
        return Easing.CUBIC_OUT.apply(raw);
    }

    public static float damp(float current, float target, float speed, float dtSeconds, boolean reduced) {
        if (reduced || speed <= 0f || !Float.isFinite(current)) {
            return target;
        }
        if (Math.abs(target - current) <= snapEpsilon) {
            return target;
        }
        float dt = Math.max(0f, Math.min(maxStepSeconds, dtSeconds));
        float factor = 1f - (float) Math.exp(-speed * dt);
        float next = current + (target - current) * factor;
        return Math.abs(target - next) <= snapEpsilon ? target : next;
    }

    public static float pulse01(long timeMs, long periodMs, boolean reduced) {
        if (reduced || periodMs <= 0L) {
            return 0.5f;
        }
        double phase = Math.floorMod(timeMs, periodMs) / (double) periodMs;
        return (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
    }

    public static float cycle01(long timeMs, long periodMs, float phaseOffset, boolean reduced) {
        if (reduced || periodMs <= 0L) {
            return 0.5f;
        }
        double raw = Math.floorMod(timeMs, periodMs) / (double) periodMs + phaseOffset;
        double wrapped = raw - Math.floor(raw);
        return clamp01((float) wrapped);
    }

    public static int lerpColor(int argbA, int argbB, float t) {
        float u = clamp01(t);
        int a = Math.round(((argbA >>> 24) & 0xFF) + (((argbB >>> 24) & 0xFF) - ((argbA >>> 24) & 0xFF)) * u);
        int r = Math.round(((argbA >>> 16) & 0xFF) + (((argbB >>> 16) & 0xFF) - ((argbA >>> 16) & 0xFF)) * u);
        int g = Math.round(((argbA >>> 8) & 0xFF) + (((argbB >>> 8) & 0xFF) - ((argbA >>> 8) & 0xFF)) * u);
        int b = Math.round((argbA & 0xFF) + ((argbB & 0xFF) - (argbA & 0xFF)) * u);
        return (clampByte(a) << 24) | (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
    }

    public static int modulateAlpha(int argb, float factor) {
        float clamped = clamp01(factor);
        int alpha = (argb >>> 24) & 0xFF;
        int scaled = clampByte(Math.round(alpha * clamped));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** Frame-rate-independent damped scalar for sliding indicators, hover glow, and anchor glides. */
    public static final class SmoothValue {
        private boolean initialized;
        private float value;
        private long lastNanos;

        public float update(float target, float speed, long nowNanos, boolean reduced) {
            if (!initialized || reduced) {
                value = target;
                initialized = true;
                lastNanos = nowNanos;
                return value;
            }
            float dt = Math.max(0f, Math.min(maxStepSeconds, (nowNanos - lastNanos) / 1_000_000_000f));
            lastNanos = nowNanos;
            value = damp(value, target, speed, dt, false);
            return value;
        }

        public float update(float target, float speed) {
            return update(target, speed, System.nanoTime(), reducedMotion());
        }

        public void snap(float target, long nowNanos) {
            this.value = target;
            this.initialized = true;
            this.lastNanos = nowNanos;
        }

        public void reset() {
            this.initialized = false;
        }

        public boolean initialized() {
            return initialized;
        }

        public float value() {
            return value;
        }
    }
}
