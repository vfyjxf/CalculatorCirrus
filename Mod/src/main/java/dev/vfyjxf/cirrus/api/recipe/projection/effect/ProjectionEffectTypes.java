package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public final class ProjectionEffectTypes {

    public static final EffectType contentInjected = EffectType.of(Namespace.of(CirrusConstants.MOD_ID, "projection/content_injected"));
    public static final EffectType contentRejected = EffectType.of(Namespace.of(CirrusConstants.MOD_ID, "projection/content_rejected"));
    public static final EffectType contentChanged = EffectType.of(Namespace.of(CirrusConstants.MOD_ID, "projection/content_changed"));

    private ProjectionEffectTypes() {throw new AssertionError("No " + ProjectionEffectTypes.class.getSimpleName() + " instances for you!");}
}
