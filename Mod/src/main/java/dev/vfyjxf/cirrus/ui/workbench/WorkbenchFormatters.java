package dev.vfyjxf.cirrus.ui.workbench;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure workbench formatting: counts, stacks, durations, percentages and cost deltas.
 */
public final class WorkbenchFormatters {

    public static final int itemsPerStack = 64;

    private static final long thousand = 1_000L;
    private static final double[] scaleSteps = {1e3, 1e6, 1e9, 1e12, 1e15, 1e18};
    private static final String[] scaleSuffixes = {"k", "M", "B", "T", "P", "E"};

    private WorkbenchFormatters() {}

    public static String formatAmount(long amount) {
        BigDecimal magnitude = BigDecimal.valueOf(amount).abs();
        if (magnitude.compareTo(BigDecimal.valueOf(thousand)) < 0) {
            return Long.toString(amount);
        }
        String sign = amount < 0L ? "-" : "";
        for (int index = 0; index < scaleSteps.length; index++) {
            BigDecimal scaled = magnitude.divide(BigDecimal.valueOf(scaleSteps[index]), 1, RoundingMode.HALF_UP);
            if (index == scaleSteps.length - 1 || scaled.doubleValue() < thousand) {
                return sign + stripRound(scaled) + scaleSuffixes[index];
            }
        }
        return sign + Long.toString(amount);
    }

    public static String formatPercent(double fraction) {
        if (!Double.isFinite(fraction)) {
            return "0%";
        }
        return Math.round(fraction * 100.0) + "%";
    }

    public static String formatSeconds(double seconds) {
        if (!(seconds > 0.0) || !Double.isFinite(seconds)) {
            return "0s";
        }
        if (seconds >= 60.0) {
            long wholeSeconds = Math.round(seconds);
            return wholeSeconds / 60L + "m " + wholeSeconds % 60L + "s";
        }
        return stripRound(BigDecimal.valueOf(seconds).setScale(1, RoundingMode.HALF_UP)) + "s";
    }

    public static String formatSigned(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            return "0";
        }
        return stripRound(rounded);
    }

    public static long[] stackParts(long amount) {
        long safeAmount = Math.max(0L, amount);
        return new long[]{safeAmount / itemsPerStack, safeAmount % itemsPerStack};
    }

    public static String formatCompactCount(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (Math.abs(value) < thousand) {
            return formatSigned(value, 1);
        }
        return formatAmount(Math.round(value));
    }

    private static String stripRound(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
