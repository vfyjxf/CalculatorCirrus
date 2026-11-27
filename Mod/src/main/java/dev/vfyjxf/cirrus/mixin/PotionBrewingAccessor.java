package dev.vfyjxf.cirrus.mixin;

import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PotionBrewing.class)
public interface PotionBrewingAccessor {

    @Accessor("containers")
    List<Ingredient> cirrusGetContainers();

    @Accessor("potionMixes")
    List<?> cirrusGetPotionMixes();

    @Accessor("containerMixes")
    List<?> cirrusGetContainerMixes();
}
