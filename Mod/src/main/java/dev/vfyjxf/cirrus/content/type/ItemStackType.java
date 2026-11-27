package dev.vfyjxf.cirrus.content.type;

import com.google.common.primitives.Ints;
import dev.vfyjxf.cirrus.api.content.ComponentCanonicalizer;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.helper.ItemHelper;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

public final class ItemStackType extends BasicStackType<Item, ItemStack> {

    public static final ItemStackType instance = new ItemStackType();

    private ItemStackType() {
        super(Namespace.ofMc("item_stack"), ItemContentType.instance, Integer.MAX_VALUE, ItemHelper.optionalCodec);
    }

    //region stack properties

    @Override
    public boolean isEmpty(ItemStack stack) {
        return stack.isEmpty();
    }

    @Override
    public Item getContent(ItemStack content) {
        return content.getItem();
    }

    @Override
    public long quantity() {
        return 1;
    }

    @Override
    public long getAmount(ItemStack stack) {
        return stack.getCount();
    }

    @Override
    public long getMaxStackSize(ItemStack stack) {
        return stack.getMaxStackSize();
    }

    @Override
    public DataComponentMap getComponents(ItemStack stack) {
        return stack.getComponents();
    }

    @Override
    public DataComponentPatch getComponentsPatch(ItemStack stack) {
        return stack.getComponentsPatch();
    }

    @Override
    public @Nullable DataComponentMap canonicalComponents(ItemStack stack) {
        if (stack.getComponentsPatch().isEmpty()) {
            // Patch-empty stacks all share the per-Item prototype singleton: same reference for
            // free, no hashing and no pooling.
            return stack.getPrototype();
        }
        return ComponentCanonicalizer.canonical(stack.getComponents());
    }

    @Override
    public boolean hasComponent(ItemStack stack, DataComponentType<?> componentType) {
        return stack.has(componentType);
    }

    @Override
    public <C> @Nullable C getComponent(ItemStack stack, DataComponentType<? extends C> componentType) {
        return stack.get(componentType);
    }

    //endregion

    //region stack factories
    @Override
    public ItemStack copy(ItemStack stack) {
        return stack.copy();
    }

    @Override
    public ItemStack normalize(ItemStack stack) {
        var item = stack.getItem();
        if (stack == ItemStack.EMPTY || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, 1);
    }

    @Override
    public ItemStack wildcard(ItemStack value) {
        return new ItemStack(value.getItem(), 1);
    }

    @Override
    public ItemStack copyWithAmount(ItemStack stack, long amount) {
        return stack.copyWithCount(Ints.checkedCast(amount));
    }

    @Override
    public ItemStack fromStack(ItemStack stack, long amount, DataComponentPatch componentPatch) {
        return new ItemStack(stack.getItemHolder(), Ints.checkedCast(amount), componentPatch);
    }

    @Override
    public ItemStack fromStack(ItemStack stack, long amount) {
        return new ItemStack(stack.getItemHolder(), Ints.checkedCast(amount));
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack fromContent(Item content, long amount, DataComponentPatch componentPatch) {
        if (amount <= 0 || content == Items.AIR) return ItemStack.EMPTY;
        else return new ItemStack(content.builtInRegistryHolder(), Ints.checkedCast(amount), componentPatch);
    }

    @Override
    public ItemStack fromContent(Item content, long amount) {
        if (amount <= 0 || content == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(content, Ints.checkedCast(amount));
    }

    @Override
    public ItemStack toItemStack(ContentStack<Item, ItemStack> stack) {
        int amount = Ints.saturatedCast(stack.amount());
        if (amount <= 0) return ItemStack.EMPTY;
        var is = stack.value();
        return new ItemStack(is.getItemHolder(), amount, is.getComponentsPatch());
    }

    //endregion

    //region match

    @Override
    public boolean matchesIdentity(ItemStack stack1, ItemStack stack2, VariantType<Item, ItemStack> variantType) {
        if (stack1.getItem() != stack2.getItem()) return false;

        Item identityBase = stack1.getItem();
        if (identityBase == Items.AIR) return true;

        return variantType.matchesVariantIdentity(stack1, stack2);
    }

    @Override
    public long mergeDiff(ItemStack stack1, ItemStack stack2, MatchStrategy strategy) {
        if (stack1 == stack2) return 0;
        if (!matchesIdentity(stack1, stack2)) return 0;
        if (strategy.withComponent() && !strategy.testComponents(stack1.getComponents(), stack2.getComponents()))
            return 0;
        return stack1.getCount() - stack2.getCount();
    }

    @Override
    public boolean matches(
        ItemStack stack1,
        ItemStack stack2,
        VariantType<Item, ItemStack> variantType,
        MatchStrategy strategy
    ) {
        if (stack1 == stack2) return true;
        if (strategy.withIdentity() && !matchesIdentity(stack1, stack2, variantType)) return false;
        if (strategy.withAmount() && stack1.getCount() != stack2.getCount()) return false;
        return !strategy.withComponent() || strategy.testComponents(stack1.getComponents(), stack2.getComponents());
    }

    //endregion

    @Override
    public String toString() {
        return "item_stack";
    }
}
