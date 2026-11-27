package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Codecs;

/**
 * Numeric comparison for amount/slot-count/scalar/chance conditions.
 * Exact {@code eq}/{@code ne} compare with a small tolerance so parsed
 * decimals behave predictably.
 */
public enum NumOp {
    eq, ne, lt, le, gt, ge;

    public static final Codec<NumOp> codec = Codecs.lowerCaseEnum(NumOp.class);

    private static final double epsilon = 1.0e-9d;

    public boolean test(double candidate, double value) {
        return switch (this) {
            case eq -> Math.abs(candidate - value) <= epsilon;
            case ne -> Math.abs(candidate - value) > epsilon;
            case lt -> candidate < value - epsilon;
            case le -> candidate <= value + epsilon;
            case gt -> candidate > value + epsilon;
            case ge -> candidate >= value - epsilon;
        };
    }
}
