package dev.vfyjxf.cirrus.api.content.type;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * @param <T> the type of content, see {@link ContentType}
 * @param <S> the type of stack,such as {@link ItemStack}, {@link FluidStack}, etc.
 */
public interface StackType<T, S> {

    //region content

    /**
     * @return the unique id of this type
     */
    @Contract(pure = true)
    Namespace typeId();

    ContentType<T> contentType();

    default @Nullable TagType<T> tagType() {
        return contentType().tagType();
    }

    /**
     * @param stack the stack
     * @return the content of the stack.
     */
    T getContent(S stack);

    //endregion

    //region stack properties

    /**
     * @return the max amount of this stack type, 0 or negative if this content type is not stackable
     */
    long maxAmount();

    /**
     * Gets the quantity in its standard, user-facing unit.
     * For ItemStack, this is the same as the raw amount (unit: item).
     * For FluidStack, this is the amount of a Bucket (B).
     *
     * @return The quantity as a long to handle fractional values (e.g., 1 Bucket).
     */
    long quantity();

    /**
     * @param stack the stack
     * @return the amount from the stack, return -1 if this stack is not stackable
     */
    long getAmount(S stack);

    /**
     * @param stack the stack
     * @return the max stack size of the stack, just like {@link ItemStack#getMaxStackSize()}
     */
    long getMaxStackSize(S stack);

    boolean isEmpty(S stack);

    default boolean notEmpty(S stack) {
        return !isEmpty(stack);
    }

    default boolean supportComponents() {
        return true;
    }

    /**
     * Returns the components of the stack, if the type supports components.
     * <p>
     * Use {@link DataComponentMap#EMPTY} if the stack have no components.
     *
     * @param stack the stack to get components from
     * @return the components of the stack, or null if the type does not support components.
     */
    @Nullable DataComponentMap getComponents(S stack);

    /**
     * @param stack the stack to get components patch from
     * @return the components patch of the stack, or an empty patch if the type does not support components or the stack has no components.
     */
    DataComponentPatch getComponentsPatch(S stack);

    /**
     * Returns a canonical representative of the stack's components: two stacks whose components are
     * equal (per {@link DataComponentMap#equals(Object)}) resolve to the <b>same instance</b>, so
     * reference comparison of canonical components is a sound shortcut for component equality.
     *
     * <p>Implementations may return a shared singleton instead of consulting the pool when the
     * stack carries no component patch (e.g. an item's prototype map).
     *
     * @param stack the stack whose components to canonicalize
     * @return the canonical components, or {@code null} if the type does not support components
     * @see dev.vfyjxf.cirrus.api.content.ComponentCanonicalizer
     */
    default @Nullable DataComponentMap canonicalComponents(S stack) {
        return dev.vfyjxf.cirrus.api.content.ComponentCanonicalizer.canonical(getComponents(stack));
    }

    /**
     * Checks if the given stack has the specified data component.
     *
     * @param stack         the stack to check for the component
     * @param componentType the data component type to check for
     * @return true if the stack has the specified component, false otherwise or if components are not supported.
     */
    boolean hasComponent(S stack, DataComponentType<?> componentType);

    /**
     * @param stack         the stack to get the component from
     * @param componentType the data component type to get
     * @param <C>           the component data type
     * @return the component of the specified type, or null if the stack does not have the component or components are not supported.
     * @throws IllegalArgumentException if components are not supported for the stack type.
     */
    <C> C getComponent(S stack, DataComponentType<? extends C> componentType) throws IllegalArgumentException;

    @Nullable Codec<S> stackCodec();

    //endregion

    //region match

    /**
     * Checks if the two stacks have the same identity.
     *
     * @param stack1      the stack to match
     * @param stack2      the stack to match
     * @param variantType the variant type of the stack, null if the stack does not have variant type.
     * @return true if the two stacks have the same identity, false otherwise.
     * @implNote check identity base ({@link StackType#getContent(Object)}) first!
     */
    boolean matchesIdentity(S stack1, S stack2, VariantType<T, S> variantType);

    /**
     *
     * @param stack1 the stack to match
     * @param stack2 the stack to match
     * @return true if the two stacks have the same identity, false otherwise.
     * @implNote check identity base ({@link StackType#getContent(Object)}) first!
     * @see #matchesIdentity(Object, Object, VariantType)
     */
    default boolean matchesIdentity(S stack1, S stack2) {
        //假设两个Stack的Identity相同，那么两个Stack的IdentityBase也一定相同，因此VariantType也相同。
        var identityBase = getContent(stack1);
        var variantType = ContentApi.instance().findVariantType(this, identityBase);
        return matchesIdentity(stack1, stack2, variantType);
    }

    default boolean componentsSatisfy(S stack, Predicate<DataComponentMap> componentMatcher) {
        return componentMatcher.test(getComponents(stack));
    }

    /**
     *
     * @param stack1      the stack to match
     * @param stack2      the stack to match
     * @param variantType the variant type of the stack.
     * @param strategy    the match strategy
     * @return true if the two stacks satisfy the match strategy, false otherwise.
     * @see MatchStrategy
     * @see VariantType
     * @see #matchesIdentity(Object, Object, VariantType)
     * @see ContentType#matches(Object, Object)
     */
    boolean matches(S stack1, S stack2, VariantType<T, S> variantType, MatchStrategy strategy);

    /**
     * @see #matches(Object, Object, VariantType, MatchStrategy)
     */
    default boolean matches(S stack1, S stack2, MatchStrategy strategy) {
        var identityBase = getContent(stack1);
        var variantType = ContentApi.instance().findVariantType(this, identityBase);
        return matches(stack1, stack2, variantType, strategy);
    }

    /**
     * @see #matches(Object, Object, VariantType, MatchStrategy)
     */
    default boolean matches(S stack1, S stack2) {
        var identityBase = getContent(stack1);
        var variantType = ContentApi.instance().findVariantType(this, identityBase);
        return matches(stack1, stack2, variantType, MatchStrategy.all);
    }

    /**
     * Get amount of merge difference of two stacks, it should be used to calculate the difference between two stacks,
     *
     * @param stack1   stack to match against
     * @param stack2   stack to match with
     * @param strategy the strategy to match the two stacks, it can be used to ignore amount or components
     * @return 0 if the two stacks can't be merged, or the amount of the difference between the two stacks.
     * @implNote returns a positive number if stack1 is greater than stack2, and a negative number if stack1 is less than stack2.
     * <p> Also,it must check identity equality.
     * <p> return value must in range [-{@link StackType#maxAmount()}, {@link StackType#maxAmount()}]
     * <p> if stack1 == stack2, the return value will be 0.
     * @see MatchStrategy
     */
    long mergeDiff(S stack1, S stack2, MatchStrategy strategy);

    default @Nullable S mergeAndCopy(S stack1, S stack2, MatchStrategy strategy) {
        long diff = mergeDiff(stack1, stack2, strategy);
        if (diff == 0) return null;
        else return fromStack(stack1, getAmount(stack1) + diff, getComponentsPatch(stack1));
    }

    //endregion

    //region stack factories

    /**
     * <b>Note:it maybe not create new instance</b>
     *
     * @param stack the stack to copy
     * @return a copy of the stack, it should be a new instance with the same properties as the original stack.
     * @implNote if stack is empty, it should return the empty instance of the stack type.
     */
    S copy(S stack);

    /**
     * @return the copy of stack with amount
     * @see StackType#copy(Object)
     */
    S copyWithAmount(S stack, long amount);

    /**
     * Normalize the given stack,it should return a stack which amount/count set to standard unit.
     * <p>
     * Such as,ItemStack should be 1,FluidStack should be {@link net.neoforged.neoforge.fluids.FluidType#BUCKET_VOLUME}
     * </p>
     *
     * @param stack the stack to normalize
     * @return the normalized stack,normally it just set amount to 1.
     */
    S normalize(S stack);

    /**
     * Wildcard the given stack,normally it should return a stack with the most generic components,
     * <p>
     * Such as,tool item should ignore {@link net.minecraft.core.component.DataComponents#DAMAGE}
     * </p>
     *
     * @param stack the stack to wildcard
     * @return the wildcard stack
     */
    S wildcard(S stack);

    /**
     * @see #fromStack(Object, long, DataComponentPatch)
     */
    default S fromStack(ContentStack<T, S> contentStack, long amount, DataComponentPatch componentPatch) {
        return fromStack(contentStack.value(), amount, componentPatch);
    }

    default S fromStack(ContentStack<T, S> contentStack, long amount) {
        return fromStack(contentStack, amount, DataComponentPatch.EMPTY);
    }

    /**
     * Creates a new stack with the given content identity and amount and components.
     *
     * @param stack          content identity
     * @param amount         the amount of the stack,it must be in range[0,{@link StackType#maxAmount()}]
     * @param componentPatch the components of the stack
     * @return the created stack
     * @implNote if {@code amount} greater than {@link StackType#maxAmount()}, set it to {@link StackType#maxAmount()}, if negative, set it to 0
     */
    S fromStack(S stack, long amount, DataComponentPatch componentPatch);

    default S fromStack(S stack, long amount) {
        return fromStack(stack, amount, DataComponentPatch.EMPTY);
    }

    /**
     * @param content        the content of stack,such as {@link Item} or {@link Fluid}.
     * @param amount         the amount of the stack,it must be in range[0,{@link StackType#maxAmount()}]
     * @param componentPatch the components of the stack
     * @return the created stack.
     */
    S fromContent(T content, long amount, DataComponentPatch componentPatch);

    default S fromContent(T content, long amount) {
        return fromContent(content, amount, DataComponentPatch.EMPTY);
    }

    default S fromContent(T content) {
        return fromContent(content, quantity(), DataComponentPatch.EMPTY);
    }

    default ItemStack toItemStack(ContentStack<T, S> stack) {
        return ItemStack.EMPTY;
    }

    //endregion

    //region util

    default String toString(S stack) {
        return Objects.toString(stack);
    }

    //endregion

}
