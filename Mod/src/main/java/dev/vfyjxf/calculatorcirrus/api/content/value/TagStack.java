package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Unmodifiable;

/**
 * When handling recipe,only a tag with amount is useful for us.
 */
public sealed interface TagStack<VALUE, STACK>
        extends ContentStack<STACK>
        permits TagContentStack {


    //region factories

    static <STACK, VALUE> TagStack<VALUE, STACK> create(TagType<VALUE, STACK> tagType, TagKey<VALUE> tagKey) {
        return new TagContentStack<>(tagType, tagKey, 1);
    }

    static <STACK, VALUE> TagStack<VALUE, STACK> create(
            TagType<VALUE, STACK> tagType, TagKey<VALUE> tagKey,
            long amount
    ) {
        return new TagContentStack<>(tagType, tagKey, amount);
    }

    static TagStack<Item, ItemStack> createItemTag(TagKey<Item> tagKey) {
        return create(CirrusContentTypes.ITEM_TAG, tagKey);
    }

    static TagStack<Item, ItemStack> createItemTag(TagKey<Item> tagKey, long amount) {
        return create(CirrusContentTypes.ITEM_TAG, tagKey, amount);
    }

    static TagStack<Fluid, FluidStack> createFluidTag(TagKey<Fluid> tagKey) {
        return create(CirrusContentTypes.FLUID_TAG, tagKey, 1);
    }

    static TagStack<Fluid, FluidStack> createFluidTag(TagKey<Fluid> tagKey, long amount) {
        return create(CirrusContentTypes.FLUID_TAG, tagKey, amount);
    }

    //endregion

    TagType<VALUE, STACK> tagType();

    @Unmodifiable
    MutableList<? extends ContentStack<STACK>> getAsStacks();

    @Unmodifiable
    MutableList<? extends VALUE> getValues();

    TagKey<VALUE> tagKey();

    @Override
    TagStack<VALUE, STACK> copy();

    /**
     * @return make any stack with a random value and a default amount.
     */
    @Override
    STACK value();

    @Override
    default <C> boolean matches(ContentStack<C> otherContent, MatchStrategy strategy) {
        if (!(otherContent instanceof TagStack<?, C> other)) return false;
        return matchesTag(other, strategy);
    }

    default <V, S> boolean matchesTag(TagStack<V, S> other, MatchStrategy strategy) {
        boolean identity = !strategy.withIdentity() || (tagType() == other.tagType() && tagKey().equals(other.tagKey()));
        boolean amount = !strategy.withAmount() || amount() == other.amount();
        return identity && amount;
    }
}
