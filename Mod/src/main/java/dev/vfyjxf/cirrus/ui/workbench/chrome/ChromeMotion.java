package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cloudlib.api.ui.texture.Easing;

/**
 * Shared reduced-motion checks and overlay entrance easing for workbench chrome.
 */
final class ChromeMotion {

    static final long overlayAppearMillis = 120L;
    static final float overlaySlidePx = 4f;

    private ChromeMotion() {}

    static boolean reducedMotion() {
        try {
            return ExecutionOverlayClientConfig.workbenchReducedMotion();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static float cubicOutProgress(long elapsedMillis, long durationMillis, boolean reducedMotion) {
        if (reducedMotion || durationMillis <= 0L) {
            return 1f;
        }
        float raw = Math.max(0f, Math.min(1f, elapsedMillis / (float) durationMillis));
        return Easing.CUBIC_OUT.apply(raw);
    }

    static int modulateAlpha(int argb, float factor) {
        float clamped = Float.isFinite(factor) ? Math.max(0f, Math.min(1f, factor)) : 1f;
        int alpha = (argb >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(alpha * clamped)));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }
}
