package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;

public enum UnresolvedInputMode {
    strict,
    directSupplyOnNoSource,
    directSupplyOnNoSourceOrSafeExhausted;

    public static final Codec<UnresolvedInputMode> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(UnresolvedInputMode.class);

    public UnresolvedInputRule toRule() {
        return switch (this) {
            case strict -> context -> UnresolvedInputRule.Resolution.fail;
            case directSupplyOnNoSource -> context -> context.phase() == UnresolvedInputContext.Phase.noSource
                ? UnresolvedInputRule.Resolution.pendingInput
                : UnresolvedInputRule.Resolution.fail;
            case directSupplyOnNoSourceOrSafeExhausted -> context -> {
                if (context.phase() == UnresolvedInputContext.Phase.noSource) {
                    return UnresolvedInputRule.Resolution.pendingInput;
                }
                return preservesOriginalInputSemantics(context.input()) || !context.activeCycleCandidate()
                    ? UnresolvedInputRule.Resolution.pendingInput
                    : UnresolvedInputRule.Resolution.fail;
            };
        };
    }

    private static boolean preservesOriginalInputSemantics(InputContent input) {
        if (input.preserveOriginalShortageSemantics()) {
            return true;
        }
        if (!input.isEnumerable()) {
            return false;
        }
        int count = 0;
        for (var ignored : input.enumerate()) {
            count++;
            if (count > 1) {
                return true;
            }
        }
        return false;
    }
}
