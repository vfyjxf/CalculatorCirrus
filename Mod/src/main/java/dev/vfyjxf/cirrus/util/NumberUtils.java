package dev.vfyjxf.cirrus.util;

import java.util.Locale;

public class NumberUtils {

    private static final char[] ENCODED_POSTFIXES = "KMGTPE".toCharArray();


    public static String toSuffix(long number) {
        boolean negative = number < 0;
        double absolute = negative ? -(double) number : (double) number;
        if (absolute < 1_000D) {
            return Long.toString(number);
        }

        double scaled = absolute;
        int exponent = -1;
        while (scaled >= 1_000D && exponent + 1 < ENCODED_POSTFIXES.length) {
            scaled /= 1_000D;
            exponent++;
        }

        double rounded = scaled >= 100D ? Math.round(scaled) : Math.round(scaled * 10D) / 10D;
        if (rounded >= 1_000D && exponent + 1 < ENCODED_POSTFIXES.length) {
            rounded /= 1_000D;
            exponent++;
        }

        String prefix = negative ? "-" : "";
        String value = rounded == Math.rint(rounded)
            ? Long.toString((long) rounded)
            : String.format(Locale.ROOT, "%.1f", rounded);
        return prefix + value + ENCODED_POSTFIXES[Math.max(0, exponent)];
    }

}
