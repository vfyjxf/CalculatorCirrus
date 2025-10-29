package dev.vfyjxf.calculatorcirrus.content.type;

import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class FluidContentType extends BasicContentType<Fluid> {

    public static final FluidContentType instance = new FluidContentType();

    private FluidContentType() {
        super(Locations.ofMc("fluid"), Fluid.class, BuiltInRegistries.FLUID.byNameCodec());
    }

    @Override
    public @Nullable ResourceLocation getLocation(Fluid fluid) {
        return BuiltInRegistries.FLUID.getResourceKey(fluid)
            .map(ResourceKey::location)
            .orElseThrow(() -> new IllegalArgumentException("Unknown fluid: " + fluid));
    }

    @Override
    public boolean isEmpty(Fluid fluid) {
        return fluid == Fluids.EMPTY;
    }

    @Override
    public boolean matches(Fluid fluid1, Fluid fluid2) {
        return fluid1 == fluid2;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int hash(Fluid fluid) {
        return fluid.builtInRegistryHolder().hashCode();
    }

    @Override
    public ItemStack toItemStack(Fluid fluid) {
        return new ItemStack(fluid.getBucket());
    }
}
