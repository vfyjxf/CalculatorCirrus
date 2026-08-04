package dev.vfyjxf.cirrus.api.content;

import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import net.minecraft.core.component.DataComponentMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * {@code Identity}:
 * <ul>
 *     <li> {@link ContentKind}: {@link ContentKind#value()} and  {@link ContentKind#type()}.
 *     <li> {@link ContentStack}: The {@link ContentStack#type()} and result of {@link StackType#getContent(Object)} and internal data handled by {@link VariantType}</li>
 * </ul>
 * <p>
 * {@code Amount}:{@link ContentStack#amount()} and {@link StackType#getAmount(Object)}
 * <p>
 * {@code Component}:{@link ContentStack#components()}  and {@link StackType#getComponents(Object)}
 *
 * @see ContentKind
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
    public static final BiPredicate<DataComponentMap, DataComponentMap> nothing = (a, b) -> true;
    public static final BiPredicate<DataComponentMap, DataComponentMap> same = Object::equals;
    //endregion

    //region Match Strategies
    public static final MatchStrategy identity = new MatchStrategy(true, false, false);
    public static final MatchStrategy amount = new MatchStrategy(false, true, false);
    public static final MatchStrategy identityAndComponent = new MatchStrategy(true, false, true, same);
    public static final MatchStrategy identityAndAmount = new MatchStrategy(true, true, false);
    public static final MatchStrategy all = new MatchStrategy(true, true, true);
    public static final MatchStrategy none = new MatchStrategy(false, false, false);//just for soundness
    //endregion

    public MatchStrategy(
            boolean withIdentity,
            boolean withAmount,
            boolean withComponent,
            BiPredicate<DataComponentMap, DataComponentMap> componentMatcher
    ) {
        this.withIdentity = withIdentity || withComponent;//ok,ok,I don't think a stack's component will be meaningful without identity.
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
        this(withIdentity, withAmount, withComponent, withComponent ? same : nothing);
    }

    public boolean testComponents(@Nullable DataComponentMap a, @Nullable DataComponentMap b) {
        return componentMatcher.test(a, b);
    }
}
