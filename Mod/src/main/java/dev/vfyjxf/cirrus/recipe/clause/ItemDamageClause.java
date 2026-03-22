package dev.vfyjxf.cirrus.recipe.clause;

import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * @deprecated Renamed to {@link DurabilityCostClause}.
 */
@Deprecated(forRemoval = false)
public record ItemDamageClause(
    int damagePerRecipe
) implements RecipeClause {

    public static final Namespace id = DurabilityCostClause.id;
    public static final ClauseType type = DurabilityCostClause.type;
    @SuppressWarnings("unchecked")
    public static final ContentTransformer<StackContent<Item, ItemStack>, ItemDamageClause> transformer =
        (ContentTransformer<StackContent<Item, ItemStack>, ItemDamageClause>) (ContentTransformer<?, ?>) DurabilityCostClause.transformer;

    @Override
    public Namespace id() {
        return id;
    }
}
