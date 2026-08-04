package dev.vfyjxf.cirrus.api.content.type;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.PotionItem;

/**
 * Items like {@link EnchantedBookItem} and {@link PotionItem}  usually have only one item instance,
 * they share the same item instance, and special data on the ItemStack,
 * such as {@link DataComponents#POTION_CONTENTS}, determines what exactly they are.
 *
 * @see dev.vfyjxf.cirrus.api.register.ContentVariantRegister
 * @see dev.vfyjxf.cirrus.api.content.ContentApi#findVariantType(StackType, Object)
 */
public interface VariantType<T, S> {

    static <T, S> VariantType<T, S> fallback(StackType<T, S> stackType) {
        Namespace stackTypeId = stackType.typeId();
        Namespace typeId = Namespace.of(stackTypeId.root(), "fallback_" + stackTypeId.path());
        return new FallbackVariantType<>(typeId, stackType);
    }

    Namespace typeId();

    default ContentType<T> contentType() {
        return stackType().contentType();
    }

    StackType<T, S> stackType();

    S copyWithIdentity(S stack);

    /**
     * Checks if two stacks are the same variant identity.
     *
     * @param stack1 the first stack
     * @param stack2 the second stack
     * @return true if the two stacks are the same variant identity, false otherwise.
     * @implNote shouldn't check identity base ({@link StackType#getContent(Object)})
     */
    boolean matchesVariantIdentity(S stack1, S stack2);

    int hashIdentity(S stack);

}

record FallbackVariantType<T, S>(
    Namespace typeId,
    StackType<T, S> stackType
) implements VariantType<T, S> {
    @Override
    public S copyWithIdentity(S stack) {
        return stackType.copy(stack);
    }

    @Override
    public boolean matchesVariantIdentity(S stack1, S stack2) {
        return true;
    }

    @Override
    public int hashIdentity(S stack) {
        return 0;
    }
}
