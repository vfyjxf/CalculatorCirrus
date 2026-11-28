package dev.vfyjxf.cirrus.mixin;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(AxeItem.class)
public interface AxeItemAccessor {

    @SuppressWarnings({"PMD.AvoidDollarSigns", "PMD.MethodNamingConventions"})
    @Accessor("STRIPPABLES")
    static Map<Block, Block> cirrus$strippables() {
        throw new AssertionError();
    }
}
