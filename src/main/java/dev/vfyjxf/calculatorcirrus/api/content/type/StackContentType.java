package dev.vfyjxf.calculatorcirrus.api.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * When Content is "Stack" Content,the {@link ContentValue#value()} became the true identity of the content,
 * or exactly, the identity with {@link DataComponentMap}
 *
 * @param <T> the type of the content, such as {@link ItemStack}, {@link FluidStack}, etc.
 */
public interface StackContentType<T> extends ContentType<T> {

    /**
     * Its {@link TagType#baseType()} must be same as {@link StackContentType#baseType()}
     *
     * @return the tag type of this content type, or null if this content type does not support tags
     */
    @Override
    @Nullable TagType<?, T> tagType();

    /**
     * @return the base class of the content, not the {@link ContentType#valueType()}
     */
    Class<?> baseType();

    /**
     * <b>Must be an instance of {@link StackContentType#baseType()}</b>
     *
     * @return the item of ItemStack, the fluid of FluidStack, etc.
     */
    Object getIdentity(T content);

    /**
     * @param content        content stack
     * @param amount         the amount of the stack,it must be less than or equal to {@link StackContentType#maxAmount()},if over, it will be {@link StackContentType#maxAmount()}
     * @param componentPatch the components of the stack
     * @return the value of the stack
     */
    T create(ContentStack<T> content, long amount, DataComponentPatch componentPatch);

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
     * {@inheritDoc}
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

    default boolean matches(ContentStack<T> stack1, ContentStack<T> stack2, MatchStrategy strategy) {
        if (stack1 == stack2) return true;
        if (stack1.isEmpty() || stack2.isEmpty()) return false;
        if (stack1.contentType() != stack2.contentType()) return false;
        if (stack1.amount() != stack2.amount()) return false;
        return matches(stack1.value(), stack2.value(), strategy);
    }

    /**
     * @param value1   stack instance 1
     * @param value2   stack instance 2
     * @param strategy the strategy to match the two values, it can be used to ignore amount or components
     * @return true if the two values match according to the strategy, false otherwise.
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
     * @return the max amount of this stack type, -1 if this content type is not stackable
     */
    long maxAmount();

    /**
     * @param content the content
     * @param value   the value
     * @return the amount from the value,return -1 if this tagType is not stackable
     */
    default long getAmount(ContentStack<T> content, T value) {
        return getAmount(value);
    }

    long getAmount(T value);

    @Nullable
    T merge(T value1, T value2, MatchStrategy matchStrategy);

    default ItemStack cheatStack(ContentStack<T> content, T value) {
        var components = getComponents(value);
        return cheatStack(create(content, content.amount(), content.getComponentsPatch()));
    }

}
