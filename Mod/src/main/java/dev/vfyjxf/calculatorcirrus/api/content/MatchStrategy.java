package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.VariantType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import net.minecraft.core.component.DataComponentMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * {@code Identity}:
 * <ul>
 *     <li> {@link ContentValue}: {@link ContentValue#value()} and  {@link ContentValue#type()}.
 *     <li> {@link ContentStack}: The {@link ContentStack#type()} and result of {@link StackType#getContent(Object)} and internal data handled by {@link VariantType}</li>
 * </ul>
 * <p>
 * {@code Amount}:{@link ContentStack#amount()} and {@link StackType#getAmount(Object)}
 * <p>
 * {@code Component}:{@link ContentStack#components()}  and {@link StackType#getComponents(Object)}
 *
 * @see ContentValue
 * @see ContentStack
 * @see VariantType
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
    public static final MatchStrategy IDENTITY_COMPONENT = new MatchStrategy(true, false, true, EQUALS);
    public static final MatchStrategy IDENTITY_AMOUNT = new MatchStrategy(true, true, false);
    public static final MatchStrategy ALL = new MatchStrategy(true, true, true);
    public static final MatchStrategy NONE = new MatchStrategy(false, false, false);//just for soundness
    //endregion

    public MatchStrategy(
        boolean withIdentity,
        boolean withAmount,
        boolean withComponent,
        BiPredicate<DataComponentMap, DataComponentMap> componentMatcher
    ) {
        this.withIdentity = withIdentity || withComponent;//ok,ok,I don't think a stack's component will be meaningful without identity.
        this.withAmount = withAmount;
        this.withComponent = withIdentity;
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

    public boolean testComponents(@Nullable DataComponentMap a, @Nullable DataComponentMap b) {
        return componentMatcher.test(a, b);
    }
}
