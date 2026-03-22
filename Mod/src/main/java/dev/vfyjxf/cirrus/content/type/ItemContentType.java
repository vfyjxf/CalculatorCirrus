package dev.vfyjxf.cirrus.content.type;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemContentType extends BasicContentType<Item> {

    public static final ItemContentType instance = new ItemContentType();

    private ItemContentType() {
        super(Namespace.ofMc("item"), BuiltInRegistries.ITEM.byNameCodec());
    }

    @Override
    public ResourceLocation getLocation(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    @Override
    public boolean supportComponents() {
        return true;
    }

    @Override
    public DataComponentMap getComponents(Item item) {
        return item.components();
    }

    @Override
    public ResourceKey<Registry<Item>> getRegistryKey() {
        return Registries.ITEM;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Holder<Item> getHolder(Item value) throws UnsupportedOperationException {
        return value.builtInRegistryHolder();
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

    @Override
    public String toString() {
        return "item";
    }
}
