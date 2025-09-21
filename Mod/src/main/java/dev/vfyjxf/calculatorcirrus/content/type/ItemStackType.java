package dev.vfyjxf.calculatorcirrus.content.type;

import com.google.common.primitives.Ints;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.helper.ItemHelper;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

public class ItemStackType extends BasicStackType<Item, ItemStack> {

    public static final ItemStackType instance = new ItemStackType();

    private ItemStackType() {
        super(Locations.create(ContentType.namespace, "item_stack"), ItemContentType.instance, Integer.MAX_VALUE, ItemHelper.OPTIONAL_CODEC);
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
    public boolean hasComponent(ItemStack stack, DataComponentType<?> componentType) {
        return stack.has(componentType);
    }

    @Override
    public <C> @UnknownNullability C getComponent(ItemStack stack, DataComponentType<? extends C> componentType) {
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
    public long mergeDiff(ItemStack stack1, ItemStack stack2, MatchStrategy strategy) {
        if (stack1 == stack2) return 0;
        boolean identity = stack1.getItem() == stack2.getItem();
        boolean components = !strategy.withComponent() || strategy.testComponents(stack1.getComponents(), stack2.getComponents());
        if (identity && components) {
            return stack1.getCount() - stack2.getCount();
        } else return 0;
    }

    @Override
    public boolean matches(ItemStack stack1, ItemStack stack2, MatchStrategy strategy) {
        if (stack1 == stack2) return true;
        boolean identity = !strategy.withIdentity() || stack1.getItem() == stack2.getItem();
        boolean amount = !strategy.withAmount() || stack1.getCount() == stack2.getCount();
        boolean components = !strategy.withComponent() || strategy.testComponents(stack1.getComponents(), stack2.getComponents());
        return identity && amount && components;
    }

    //endregion

    @Override
    public String toString() {
        return "item_stack";
    }
}
