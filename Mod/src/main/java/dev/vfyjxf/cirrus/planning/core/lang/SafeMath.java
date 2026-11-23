package dev.vfyjxf.cirrus.planning.core.lang;

public final class SafeMath {

    public static final long SATURATION_LIMIT = Long.MAX_VALUE / 4L;

    private SafeMath() {
    }

    public static long addSaturating(long a, long b) {
        if (b > 0L && a > SATURATION_LIMIT - b) {
            return SATURATION_LIMIT;
        }
        if (b < 0L && a < -SATURATION_LIMIT - b) {
            return -SATURATION_LIMIT;
        }
        return a + b;
    }

    public static long mulSaturating(long a, long b) {
        if (a == 0L || b == 0L) {
            return 0L;
        }
        boolean neg = (a < 0L) ^ (b < 0L);
        long ua = a < 0L ? (a == Long.MIN_VALUE ? SATURATION_LIMIT : -a) : a;
        long ub = b < 0L ? (b == Long.MIN_VALUE ? SATURATION_LIMIT : -b) : b;
        if (ua > SATURATION_LIMIT / ub) {
            return neg ? -SATURATION_LIMIT : SATURATION_LIMIT;
        }
        long prod = ua * ub;
        return neg ? -prod : prod;
    }

    public static boolean willOverflowMul(long a, long b) {
        if (a == 0L || b == 0L) {
            return false;
        }
        long ua = Math.abs(a);
        long ub = Math.abs(b);
        return ua < 0L || ub < 0L || ua > SATURATION_LIMIT / ub;
    }

    public static boolean willOverflowAdd(long a, long b) {
        if (b > 0L) {
            return a > SATURATION_LIMIT - b;
        }
        if (b < 0L) {
            return a < -SATURATION_LIMIT - b;
        }
        return false;
    }

    public static long ceilDiv(long numerator, long denominator) {
        if (denominator <= 0L) {
            throw new IllegalArgumentException("denominator must be > 0, got " + denominator);
        }
        if (numerator <= 0L) {
            return 0L;
        }
        return (numerator - 1L) / denominator + 1L;
    }

    public static double clamp01(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, value);
    }
}
