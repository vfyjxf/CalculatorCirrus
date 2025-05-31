package dev.vfyjxf.calculatorcirrus.api.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * When Content is "Stack" Content,the {@link ContentValue#value()} became the true identity of the content,
 * or exactly, the identity with {@link DataComponentMap}
 *
 * @param <T> the type of the stack, such as {@link ItemStack}, {@link FluidStack}, etc.
 * @see ContentType
 * @see TagType
 */
public interface StackContentType<T> extends ContentType<T> {

    /**
     * Its {@link TagType#baseType()} must be same as {@link StackContentType#baseType()}
     *
     * @return the tag type of this content type, or null if this content type does not support tags
     */
    @Override
    @Contract(pure = true)
    @Nullable TagType<?, T> tagType();

    @Override
    default Optional<? extends TagType<?, T>> getTgType() {
        return Optional.ofNullable(tagType());
    }

    /**
     * @return the base class of the content, not the {@link ContentType#valueType()}
     */
    Class<?> baseType();

    /**
     * <b>Must be an instance of {@link StackContentType#baseType()}</b>
     *
     * @return the item of ItemStack, the fluid of FluidStack, etc.
     * @see MatchStrategy#withIdentity()
     */
    Object getIdentity(T content);

    /**
     * @see #create(Object, long, DataComponentPatch)
     */
    default T create(ContentStack<T> content, long amount, DataComponentPatch componentPatch) {
        return create(content.value(), amount, componentPatch);
    }

    /**
     * Creates a new stack with the given content identity and amount and components.
     *
     * @param value          content identity
     * @param amount         the amount of the stack,it must be in range[0,{@link StackContentType#maxAmount()}]
     * @param componentPatch the components of the stack
     * @return the value of the stack
     * @implNote if {@code amount} greater than {@link StackContentType#maxAmount()}, set it to {@link StackContentType#maxAmount()}, if negative, set it to 0
     */
    T create(T value, long amount, DataComponentPatch componentPatch);

    /**
     * Following the behavior of  {@link ItemStack} and {@link FluidStack},
     * we also consider stacks with an amount less than or equal to 0 to be empty stack
     *
     * @param content the content stack
     * @param value   the value of the stack.
     * @return true if the value is empty, false otherwise.
     * @see ItemStack#isEmpty()
     * @see FluidStack#isEmpty()
     * @see StackContentType#isEmpty(Object)
     */
    default boolean isEmpty(ContentStack<T> content, T value) {
        return isEmpty(value);
    }

    /**
     * Checks if the stack is empty.
     *
     * @param value the value to check
     * @return true if the value is empty, false otherwise.
     * @implNote This method should be used to check if the value is empty, such as {@link ItemStack#isEmpty()} or {@link FluidStack#isEmpty()}.
     * @see ItemStack#isEmpty()
     * @see FluidStack#isEmpty()
     */
    @Override
    boolean isEmpty(T value);

    default ContentStack<T> copy(ContentStack<T> content, T value) {
        return ContentStack.create(content.contentType(), copy(value), content.amount());
    }

    @Override
    T copy(T value);

    default ContentStack<T> normalize(ContentStack<T> content, T value) {
        return ContentStack.create(content.contentType(), normalize(value), 1);
    }

    @Override
    T normalize(T value);

    default ContentStack<T> wildcard(ContentStack<T> content, T value) {
        return ContentStack.create(content.contentType(), wildcard(value), content.amount());
    }

    @Override
    T wildcard(T value);

    @Override
    @Nullable DataComponentMap getComponents(T value);

    DataComponentPatch getComponentsPatch(T value);

    /**
     * Checks if two stacks match according to the given strategy.
     *
     * @param stack1   stack instance 1
     * @param stack2   stack instance 2
     * @param strategy the strategy to match the two stacks, it can be used to ignore identity or amount or components
     * @return true if the two stacks match according to the strategy, false otherwise.
     * @see MatchStrategy
     */
    default boolean matches(ContentStack<T> stack1, ContentStack<T> stack2, MatchStrategy strategy) {
        if (stack1 == stack2) return true;
        boolean identity = !strategy.withIdentity() ||
                (stack1.contentType() == stack2.contentType() && matches(stack1.value(), stack2.value(), MatchStrategy.IDENTITY));
        boolean amount = !strategy.withAmount() || stack1.amount() == stack2.amount();
        boolean components = !strategy.withComponent() || strategy.testComponents(stack1.getComponents(), stack2.getComponents());
        return identity && amount && components;
    }

    /**
     * Checks if two values match according to the given strategy.
     *
     * @param value1   stack instance 1
     * @param value2   stack instance 2
     * @param strategy the strategy to match the two values, it can be used to ignore identity or amount or components
     * @return true if the two values match according to the strategy, false otherwise.
     * @see MatchStrategy
     */
    @Override
    boolean matches(T value1, T value2, MatchStrategy strategy);

    /**
     * @see #hash(ContentStack, T, MatchStrategy)
     */
    default int hash(ContentStack<T> content, T value, MatchStrategy strategy) {
        int hash = hash(value, strategy);
        hash = 31 * hash + Long.hashCode(content.amount());
        return hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    int hash(T value, MatchStrategy strategy);

    /**
     * @return the max amount of this stack type, 0 or negative if this content type is not stackable
     */
    long maxAmount();

    default boolean stackable() {
        return maxAmount() > 0;
    }

    /**
     * @param content the content
     * @param value   the value
     * @return the amount from the value,return -1 if this tagType is not stackable
     */
    default long getAmount(ContentStack<T> content, T value) {
        return getAmount(value);
    }

    long getAmount(T value);

    /**
     * get merge diff of two values, it should be used to calculate the difference between two values,
     *
     * @param value1   value to match against
     * @param value2   value to match with
     * @param strategy the strategy to match the two values, it can be used to ignore amount or components
     * @return 0 if the two values can't be merged, or the amount of the difference between the two values.
     * @implNote returns a positive number if value1 is greater than value2, and a negative number if value1 is less than value2.
     * <p> Also,it must check identity equality.
     * <p> return value must in range [-{@link StackContentType#maxAmount()}, {@link StackContentType#maxAmount()}]
     * @see MatchStrategy
     */
    long mergeDiff(T value1, T value2, MatchStrategy strategy);


    default @Nullable T mergeAndCopy(T value1, T value2, MatchStrategy strategy) {
        long diff = mergeDiff(value1, value2, strategy);
        if (diff == 0) return null;
        else return create(value1, getAmount(value1) + diff, getComponentsPatch(value1));
    }

    default ItemStack cheatStack(ContentStack<T> content, T value) {
        return cheatStack(create(content, content.amount(), content.getComponentsPatch()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    default Stream<? extends TagKey<?>> getTags(T stack) {
        if (tagType() != null) {
            TagType tagType = tagType();
            return tagType.getTagFor(tagType.getStackValue(stack));
        } else return Stream.empty();
    }
}
