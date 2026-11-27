package dev.vfyjxf.cirrus.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.alchemy.PotionBrewing$Mix")
public interface PotionBrewingMixAccessor {

    @Accessor("from")
    Holder<?> cirrusGetFrom();

    @Accessor("ingredient")
    Ingredient cirrusGetIngredient();

    @Accessor("to")
    Holder<?> cirrusGetTo();
}
