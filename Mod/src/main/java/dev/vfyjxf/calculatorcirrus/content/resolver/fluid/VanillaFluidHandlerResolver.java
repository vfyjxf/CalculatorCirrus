package dev.vfyjxf.calculatorcirrus.content.resolver.fluid;

import dev.vfyjxf.calculatorcirrus.api.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import net.minecraft.world.item.BottleItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


/**
 * TODO:考虑重新设计resolve返回值，因为对于BottleItem这类没有Capability的容器物品，
 *  它无法被{@link dev.vfyjxf.calculatorcirrus.recipe.facet.UnwrapFluidHandlerFacet}内置使用的
 *  {@link net.neoforged.neoforge.fluids.capability.IFluidHandlerItem}进行处理，因为它实际上还涉及到目前还未动工的UnitApi，不然我们无法准确表达
 *  容器物品的容量和实际存储量，因为水瓶是分数值，水桶是整数值。
 *
 */
public class VanillaFluidHandlerResolver implements StackResolver<Item, ItemStack, ItemStack> {

    public static final VanillaFluidHandlerResolver instance = new VanillaFluidHandlerResolver();

    private VanillaFluidHandlerResolver() {}

    @Override
    public ContentResolverType<ItemStack> type() {
        return CirrusTypes.ContentResolvers.FLUID_CONTAINER;
    }

    @Override
    public StackType<Item, ItemStack> stackType() {
        return CirrusTypes.Contents.ITEM_STACK;
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
