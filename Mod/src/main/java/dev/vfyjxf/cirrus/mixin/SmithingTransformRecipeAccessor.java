package dev.vfyjxf.cirrus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {

    @Accessor("template")
    Ingredient cirrusGetTemplate();

    @Accessor("base")
    Ingredient cirrusGetBase();

    @Accessor("addition")
    Ingredient cirrusGetAddition();

    @Accessor("result")
    ItemStack cirrusGetResult();
}
