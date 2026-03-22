package dev.vfyjxf.cirrus.recipe.clause;

import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * @deprecated Renamed to {@link FluidContainerUnpackClause}.
 */
@Deprecated(forRemoval = false)
public final class UnwrapFluidHandlerClause implements RecipeClause {

    public static final Namespace id = FluidContainerUnpackClause.id;
    public static final ClauseType type = FluidContainerUnpackClause.type;
    public static final UnwrapFluidHandlerClause instance = new UnwrapFluidHandlerClause();
    @SuppressWarnings("unchecked")
    public static final ContentTransformer<StackContent<Item, ItemStack>, UnwrapFluidHandlerClause> transformer =
        (ContentTransformer<StackContent<Item, ItemStack>, UnwrapFluidHandlerClause>) (ContentTransformer<?, ?>) FluidContainerUnpackClause.transformer;

    private UnwrapFluidHandlerClause() {}

    @Override
    public Namespace id() {
        return id;
    }
}
