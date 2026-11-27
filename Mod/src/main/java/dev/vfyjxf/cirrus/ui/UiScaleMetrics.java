package dev.vfyjxf.cirrus.ui;

import org.jspecify.annotations.Nullable;

/**
 * Shared scale-adaptive geometry helpers so Calculator and Executioner panels never overflow at high GUI scale.
 */
public final class UiScaleMetrics {

    public enum Density {
        comfortable, compact, tight
    }

    private UiScaleMetrics() {}

    public static Density classifyWidth(int width, int compactBelow, int tightBelow) {
        if (width < tightBelow) {
            return Density.tight;
        }
        if (width < compactBelow) {
            return Density.compact;
        }
        return Density.comfortable;
    }

    public static Density classifyHeight(int height, int compactBelow, int tightBelow) {
        if (height < tightBelow) {
            return Density.tight;
        }
        if (height < compactBelow) {
            return Density.compact;
        }
        return Density.comfortable;
    }

    /**
     * Fits a preferred span inside {@code available - margin * 2} without ever overflowing the viewport.
     */
    public static float fitWithin(float preferred, float minPreferred, float available, float margin) {
        float safeAvailable = Math.max(0f, available - Math.max(0f, margin) * 2f);
        if (safeAvailable <= 0f) {
            return 0f;
        }
        float target = Math.max(minPreferred, preferred);
        return Math.min(safeAvailable, target);
    }

    public static int fitWithinInt(int preferred, int minPreferred, int available, int margin) {
        return Math.round(fitWithin(preferred, minPreferred, available, margin));
    }

    /**
     * Distributes {@code availableWidth} across {@code n} items with {@code gap} between them so total width never exceeds {@code availableWidth}.
     */
    public static int[] distributeRow(
        int availableWidth,
        int gap,
        int[] weights,
        int @Nullable [] minWidths,
        int @Nullable [] maxWidths
    ) {
        int n = weights.length;
        int[] out = new int[n];
        if (n == 0 || availableWidth <= 0) {
            return out;
        }
        int totalGap = Math.max(0, n - 1) * Math.max(0, gap);
        int usable = Math.max(0, availableWidth - totalGap);
        if (usable <= 0) {
            return out;
        }
        int totalWeight = 0;
        for (int w : weights) {
            totalWeight += Math.max(1, w);
        }
        int remaining = usable;
        for (int i = 0; i < n; i++) {
            int share = i == n - 1 ? remaining : Math.round((float) usable * Math.max(1, weights[i]) / totalWeight);
            int maxW = maxWidths != null && i < maxWidths.length && maxWidths[i] > 0 ? maxWidths[i] : usable;
            int minW = minWidths != null && i < minWidths.length && minWidths[i] > 0 ? minWidths[i] : 0;
            int clamped = Math.max(Math.min(minW, usable / n), Math.min(maxW, share));
            out[i] = Math.max(0, clamped);
            remaining -= out[i];
        }
        // Normalize if rounding or min/max clamping exceeded usable width.
        int sum = 0;
        for (int v : out) {
            sum += v;
        }
        if (sum > usable && sum > 0) {
            int excess = sum - usable;
            for (int i = n - 1; i >= 0 && excess > 0; i--) {
                int cut = Math.min(excess, Math.max(0, out[i] - 1));
                out[i] -= cut;
                excess -= cut;
            }
        } else if (sum < usable) {
            int spare = usable - sum;
            for (int i = 0; i < n && spare > 0; i++) {
                int maxW = maxWidths != null && i < maxWidths.length && maxWidths[i] > 0 ? maxWidths[i] : usable;
                int add = Math.min(spare, Math.max(0, maxW - out[i]));
                out[i] += add;
                spare -= add;
            }
        }
        return out;
    }
}
