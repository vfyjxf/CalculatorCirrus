package dev.vfyjxf.calculatorcirrus.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public class ItemContentType extends BasicContentType<Item> {

    public static final ItemContentType instance = new ItemContentType();

    private ItemContentType() {
        super(Locations.create(ContentType.namespace, "item"), Item.class);
    }

    @Override
    public @Nullable ResourceLocation getLocation(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    @Override
    public boolean isEmpty(Item item) {
        return item == Items.AIR;
    }

    @Override
    public boolean matches(Item item1, Item item2) {
        return item1 == item2;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int hash(Item item) {
        return item.builtInRegistryHolder().hashCode();
    }

    @Override
    public ItemStack toItemStack(Item item) {
        return new ItemStack(item, 1);
    }

}
