package dev.vfyjxf.calculatorcirrus.recipe.facet;

import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.StackContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.checkerframework.checker.index.qual.Positive;
import org.jetbrains.annotations.Range;

//TODO:问题关键，一部分带耐久损耗配方使用的是tag进行描述，但是我们该如何处理呢，以及推断不出tag使用其他tool情景呢？？
// 我们需要在getData阶段就让system提供足够的信息，例如对input进行标记之类的，来简化推断流程
// 设计难点和其他RecipeSystem的配合，1.由其他部分拓展了一个系统，例如gt给工作台配方系统加了工具磨损配方和流体容器替代配方，
// 该如何处理修饰符转预标记的问题(注:预标记用来标记那些需要转换的content，比如推断一个用tag的磨损配方很麻烦，所以需要预标记系统)
// 如何让外部拓展已有的系统，例如添加新的燃料值，以及将燃料值转换为时间开销，同时还需要描述电力熔炉导致的燃料值转换为电力的计算公式问题
public record ItemDamageFacet(
    @Positive int damagePerRecipe
) implements RecipeFacet {

    public ItemDamageFacet(@Range(from = 1, to = Integer.MAX_VALUE) int damagePerRecipe) {
        Checks.checkRange(damagePerRecipe, 1, Integer.MAX_VALUE);
        this.damagePerRecipe = damagePerRecipe;
    }

    public static final ResourceLocation ID = CirrusLocations.ofMod("item/damagePerRecipe");
    public static final RecipeFacetType type = new RecipeFacetType(ID, (entry, content) -> checkContent(content));

    public static final FacetTransformer<StackContent<Item, ItemStack>, ItemDamageFacet> transformer = FacetTransformer.of(
        (context, view, content, asInput) -> checkContent(content),
        (context, recipe, content, facet, asInput, increment, decrement) -> {
            var cs = content.stack();
            int maxDamage = cs.getComponent(DataComponents.MAX_DAMAGE);

        });


    private static boolean checkContent(RecipeContent content) {
        if (!(content instanceof StackContent<?, ?> stack)) return false;
        if (!stack.isItem()) return false;

        var cs = stack.<Item, ItemStack>cast().stack();
        return cs.notEmpty() &&
            cs.hasComponent(DataComponents.DAMAGE) &&
            cs.hasComponent(DataComponents.MAX_DAMAGE);
    }


    @Override
    public ResourceLocation id() {
        return ID;
    }
}
