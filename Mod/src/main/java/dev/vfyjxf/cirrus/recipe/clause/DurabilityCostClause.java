package dev.vfyjxf.cirrus.recipe.clause;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer.ActionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.checkerframework.checker.index.qual.Positive;
import org.jetbrains.annotations.Range;

public record DurabilityCostClause(@Positive int durabilityCost) implements RecipeClause {

    public static final Namespace id = CirrusNamespaces.ofMod("input/durability_cost");
    public static final ClauseType type = new ClauseType(
        id,
        ProjectionPlugin.builtin,
        (entry, content) -> checkContent(content)
    );
    public static final ContentTransformer<StackContent<Item, ItemStack>, DurabilityCostClause> transformer = ContentTransformer
            .of(
                ProjectionPlugin.builtin,
                (context, projection, content, asInput) -> checkContent(content),
                (context, recipe, content, clause, asInput, increment, decrement) -> {
                    var stack = content.content();
                    Integer currentDamage = stack.getComponent(DataComponents.DAMAGE);
                    Integer maxDamage = stack.getComponent(DataComponents.MAX_DAMAGE);
                    if (currentDamage == null || maxDamage == null) {
                        return ActionResult.retain;
                    }
                    long nextDamage = currentDamage + (long) clause.durabilityCost();
                    if (nextDamage >= maxDamage) {
                        return ActionResult.remove;
                    }
                    return ActionResult.retain;
                }
            );

    public DurabilityCostClause(@Range(from = 1, to = Integer.MAX_VALUE) int durabilityCost) {
        Checks.checkRange(durabilityCost, 1, Integer.MAX_VALUE);
        this.durabilityCost = durabilityCost;
    }

    //region internal

    private static boolean checkContent(RecipeContent content) {
        if (!(content instanceof StackContent<?, ?> stack)) return false;
        if (!stack.isItem()) return false;

        var cs = stack.<Item, ItemStack>cast().content();
        return cs.notEmpty() && cs.hasComponent(DataComponents.DAMAGE) && cs.hasComponent(DataComponents.MAX_DAMAGE);
    }

    //endregion

    @Override
    public Namespace id() {
        return id;
    }

    @Override
    public ProjectionPlugin plugin() {
        return ProjectionPlugin.builtin;
    }
}
