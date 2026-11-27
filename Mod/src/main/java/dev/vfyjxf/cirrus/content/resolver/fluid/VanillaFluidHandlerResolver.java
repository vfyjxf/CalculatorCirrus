package dev.vfyjxf.cirrus.content.resolver.fluid;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.cirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import net.minecraft.world.item.BottleItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * TODO: redesign the resolve return value: a capability-less container item such as a bottle
 *  cannot go through the {@link net.neoforged.neoforge.fluids.capability.IFluidHandlerItem} that
 *  {@link dev.vfyjxf.cirrus.recipe.facet.UnwrapFluidHandlerFacet} uses internally, because it also needs the
 *  not-yet-started UnitApi to express fractional contents (a water bottle) against whole units (a bucket).
 *
 */
public class VanillaFluidHandlerResolver implements StackResolver<Item, ItemStack, ItemStack> {

    public static final VanillaFluidHandlerResolver instance = new VanillaFluidHandlerResolver();

    private VanillaFluidHandlerResolver() {}

    @Override
    public ContentResolverType<ItemStack> type() {
        return CirrusTypes.ContentResolvers.fluidContainer;
    }

    @Override
    public StackType<Item, ItemStack> stackType() {
        return CirrusTypes.Contents.itemStack;
    }

    @Override
    public boolean resolvable(Item content, ItemStack stack) {
        return content instanceof BucketItem;
    }

    @Override
    public ItemStack resolve(Item content, ItemStack stack) {
        return switch (content) {
            case BucketItem ignored -> new ItemStack(Items.BUCKET);
            case BottleItem ignored -> throw new UnsupportedOperationException("BottleItem is not supported yet");
            default -> throw new UnsupportedOperationException("Unreachable!");
        };
    }
}
