package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.CirrusConstants;

/**
 * Standard meta keys for builtin projection effects.
 */
public final class ProjectionEffectMetaKeys {

    private static final String keyPrefix = CirrusConstants.MOD_ID + ".projection.";

    //region key id

    public static final String beforeAmountId = keyPrefix + "before.amount";
    public static final String afterAmountId = keyPrefix + "after.amount";
    public static final String amountDeltaId = keyPrefix + "amount.delta";
    public static final String amountFactorId = keyPrefix + "amount.factor";
    public static final String beforeTypeId = keyPrefix + "before.type";
    public static final String afterTypeId = keyPrefix + "after.type";
    public static final String resolvePolicyClauseId = keyPrefix + "resolve.policy.id";
    public static final String resolveEffectId = keyPrefix + "resolve.effect.id";
    public static final String resolveEffectOrderId = keyPrefix + "resolve.effect.order";
    public static final String resolveEffectDeclaredIndexId = keyPrefix + "resolve.effect.declared_index";
    public static final String resolveGuardId = keyPrefix + "resolve.guard.id";
    public static final String resolveGuardReasonCode = keyPrefix + "resolve.guard.reason_code";

    //endregion

    //region amount

    public static final EffectMetaKey<Long> beforeAmount = EffectMetaKey.of(beforeAmountId, Long.class);
    public static final EffectMetaKey<Long> afterAmount = EffectMetaKey.of(afterAmountId, Long.class);
    public static final EffectMetaKey<Long> amountDelta = EffectMetaKey.of(amountDeltaId, Long.class);
    public static final EffectMetaKey<Double> amountFactor = EffectMetaKey.of(amountFactorId, Double.class);

    //endregion

    //region type

    public static final EffectMetaKey<String> beforeType = EffectMetaKey.of(beforeTypeId, String.class);
    public static final EffectMetaKey<String> afterType = EffectMetaKey.of(afterTypeId, String.class);

    //endregion

    //region resolve

    public static final EffectMetaKey<String> resolvePolicy = EffectMetaKey.of(resolvePolicyClauseId, String.class);
    public static final EffectMetaKey<String> resolveEffect = EffectMetaKey.of(resolveEffectId, String.class);
    public static final EffectMetaKey<Integer> resolveEffectOrder = EffectMetaKey.of(resolveEffectOrderId, Integer.class);
    public static final EffectMetaKey<Integer> resolveEffectDeclaredIndex = EffectMetaKey.of(resolveEffectDeclaredIndexId, Integer.class);
    public static final EffectMetaKey<String> guardId = EffectMetaKey.of(resolveGuardId, String.class);
    public static final EffectMetaKey<String> guardReasonCode = EffectMetaKey.of(resolveGuardReasonCode, String.class);

    //endregion

    private ProjectionEffectMetaKeys() {throw new AssertionError("No " + ProjectionEffectMetaKeys.class.getSimpleName() + " instances for you!");}
}
