package dev.vfyjxf.cirrus.content.type.variant;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;

public class PaintingVariantType extends BasicVariantType<Item, ItemStack> {

    public static final PaintingVariantType instance = new PaintingVariantType();

    private PaintingVariantType() {
        super(Namespace.ofMc("painting"), CirrusTypes.Contents.itemStack);
    }

    @Override
    public ItemStack copyWithIdentity(ItemStack stack) {
        if (stack.getItem() != Items.PAINTING) return ItemStack.EMPTY;
        CustomData entityData = stack.get(DataComponents.ENTITY_DATA);
        if (entityData == null) return ItemStack.EMPTY;
        CompoundTag compoundTag = entityData.copyTag();
        ItemStack ret = new ItemStack(Items.PAINTING, stack.getCount());
        ret.set(DataComponents.ENTITY_DATA, CustomData.of(compoundTag));
        return ret;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean matchesVariantIdentity(ItemStack stack1, ItemStack stack2) {
        var properties1 = stack1.get(DataComponents.ENTITY_DATA);
        var properties2 = stack2.get(DataComponents.ENTITY_DATA);
        if (properties1 == null || properties2 == null) return false;
        return Objects.equals(properties1.getUnsafe().get("variant"), properties2.getUnsafe().get("variant"));

    }

    @Override
    public int hashIdentity(ItemStack stack) {
        return Objects.hashCode(stack.get(DataComponents.ENTITY_DATA));
    }
}
