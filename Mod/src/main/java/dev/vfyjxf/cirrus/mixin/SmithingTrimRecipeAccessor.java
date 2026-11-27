package dev.vfyjxf.cirrus.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SmithingTrimRecipe.class)
public interface SmithingTrimRecipeAccessor {

    @Accessor("template")
    Ingredient cirrusGetTemplate();

    @Accessor("base")
    Ingredient cirrusGetBase();

    @Accessor("addition")
    Ingredient cirrusGetAddition();
}
