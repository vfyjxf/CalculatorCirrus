//package dev.vfyjxf.calculatorcirrus.api.content.value;
//
//import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
//import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
//import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
//import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
//import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
//import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
//import net.minecraft.tags.TagKey;
//import org.eclipse.collections.api.list.MutableList;
//import org.jetbrains.annotations.Contract;
//import org.jetbrains.annotations.Nullable;
//import org.jetbrains.annotations.UnmodifiableView;
//
//import java.util.function.BiConsumer;
//
//public sealed interface TagStack<T> extends ContentVariant permits TypedTagStack, EmptyTagStack {
//
//    @SuppressWarnings("unchecked")
//    static <T> TagStack<T> empty() {
//        return EmptyTagStack.instance;
//    }
//
//    //region factory
//
//    static <T> TagStack<T> from(TagType<T> type, TagKey<T> tagKey, long amount) {
//        return new TypedTagStack<>(type, tagKey, amount);
//    }
//
//    //endregion
//
//    //region builtin type factory
//
//    //endregion
//
//    //region type info
//
//    TagType<T> type();
//
//    default ContentType<T> contentType() {
//        return type().contentType();
//    }
//
//    //endregion
//
//    //region stack info
//
//    TagKey<T> tagKey();
//
//    boolean isEmpty();
//
//    @UnmodifiableView
//    MutableList<T> values();
//
//    /**
//     * @see ContentStack#amount()
//     */
//    long amount();
//
//    /**
//     * @see ContentStack#setAmount(long)
//     */
//    TagStack<T> setAmount(long amount);
//
//    default boolean matches(TagStack<?> other, MatchStrategy strategy) {
//        boolean identity = !strategy.withIdentity() || (type() == other.type() && tagKey().equals(other.tagKey()));
//        boolean amount = !strategy.withAmount() || other.amount() == amount();
//        return identity && amount;
//    }
//
//    //endregion
//
//    //region copy
//
//    default TagStack<T> copy() {
//        if (this == empty() || isEmpty()) return empty();
//        return from(type(), tagKey(), amount());
//    }
//
//    //endregion
//
//    //region tooltip
//
//    /**
//     * Append tooltips to the tag stack.
//     *
//     * @param configurator the builder to append tooltip to this tag stack
//     * @return this tag stack
//     */
//    @Contract("_ -> this")
//    TagStack<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator);
//
//    /**
//     * Same as {@link #appendTooltip(BiConsumer)} .
//     *
//     * @see #appendTooltip(BiConsumer)
//     */
//    @Contract("_ -> this")
//    default TagStack<T> withTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
//        return appendTooltip(configurator);
//    }
//
//    /**
//     * Configure the tooltip for this tag stack.
//     *
//     * @param tooltip the rich tooltip to configure.
//     * @see #getTooltip(TooltipContext)
//     */
//    void configureTooltip(RichTooltip tooltip, TooltipContext context);
//
//    /**
//     * @param context the tooltip context.
//     * @return the rich tooltip for this tag stack, or null if tooltip is not available or called from server side.
//     */
//    @Nullable
//    RichTooltip getTooltip(TooltipContext context);
//
//    //endregion
//
//    //region util
//
//    <S> MutableList<S> toStacks(StackType<T, S> stackType);
//
//    @SuppressWarnings("unchecked")
//    default <C> TagStack<C> cast() {
//        return (TagStack<C>) this;
//    }
//
//    //endregion
//}
//
