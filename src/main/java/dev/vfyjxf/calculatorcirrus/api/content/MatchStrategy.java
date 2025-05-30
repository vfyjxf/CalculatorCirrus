package dev.vfyjxf.calculatorcirrus.api.content;

import net.minecraft.core.component.DataComponentMap;

import java.util.function.BiPredicate;

/**
 * //TODO:define concept of following
 * Identity:
 * <p>
 * Amount:
 * <p>
 * component:
 */
public record MatchStrategy(
        boolean withIdentity,
        boolean withAmount,
        boolean withComponent,
        BiPredicate<DataComponentMap, DataComponentMap> componentMatcher
) {
    //region Extra Component Match Strategies
    public static final BiPredicate<DataComponentMap, DataComponentMap> ALWAYS_TRUE = (a, b) -> true;
    public static final BiPredicate<DataComponentMap, DataComponentMap> EQUALS = Object::equals;
    //endregion

    //region Match Strategies
    public static final MatchStrategy IDENTITY = new MatchStrategy(true, false, false);
    public static final MatchStrategy AMOUNT = new MatchStrategy(false, true, false);
    public static final MatchStrategy COMPONENT = new MatchStrategy(false, false, true, EQUALS);
    public static final MatchStrategy IDENTITY_AMOUNT = new MatchStrategy(true, true, false);
    public static final MatchStrategy IDENTITY_COMPONENT = new MatchStrategy(true, false, true, EQUALS);
    public static final MatchStrategy AMOUNT_COMPONENT = new MatchStrategy(false, true, true, EQUALS);
    public static final MatchStrategy ALL = new MatchStrategy(true, true, true);
    public static final MatchStrategy NONE = new MatchStrategy(false, false, false);
    //endregion

    public MatchStrategy(
            boolean withIdentity,
            boolean withAmount,
            boolean withComponent,
            BiPredicate<DataComponentMap, DataComponentMap> componentMatcher
    ) {
        this.withIdentity = withIdentity;
        this.withAmount = withAmount;
        this.withComponent = withComponent;
        this.componentMatcher = (a, b) -> {
            if (!withComponent || a == b) return true;
            if (a == null || b == null) return false;
            return componentMatcher.test(a, b);
        };
    }

    public MatchStrategy(
            boolean withIdentity,
            boolean withAmount,
            boolean withComponent
    ) {
        this(withIdentity, withAmount, withComponent, ALWAYS_TRUE);
    }

    public boolean testComponents(DataComponentMap a, DataComponentMap b) {
        return componentMatcher.test(a, b);
    }
}
