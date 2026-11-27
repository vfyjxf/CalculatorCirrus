package dev.vfyjxf.cirrus.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConcretePowderBlock.class)
public interface ConcretePowderBlockAccessor {

    @Accessor("concrete")
    Block cirrus$concrete();
}
