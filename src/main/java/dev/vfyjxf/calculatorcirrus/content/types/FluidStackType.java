package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.IContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.content.renderer.FluidStackContentRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class FluidStackType extends AbstractContentType<FluidStack> {

    public FluidStackType() {
        super(FluidStack.class, new FluidStackContentRenderer());
    }

    @Override
    public ResourceLocation getId() {
        return null;
    }

    @Override
    public boolean supportTag() {
        return true;
    }

    @Override
    public @Nullable String getUid(IContentStack<FluidStack> content, FluidStack value) {
        return BuiltInRegistries.FLUID.getResourceKey(value.getFluid())
                .map(ResourceKey::location)
                .map(ResourceLocation::toString)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fluid " + value.getFluid()));
    }

    @Override
    public @Nullable String getModid(IContentStack<FluidStack> content, FluidStack value) {
        Fluid fluid = value.getFluid();
        return BuiltInRegistries.FLUID.getResourceKey(fluid)
                .map(ResourceKey::location)
                .map(ResourceLocation::getNamespace)
                .orElse("None");
    }

    @Override
    public boolean isEmpty(IContentStack<FluidStack> content, FluidStack value) {
        return value.isEmpty();
    }

    @Override
    public FluidStack copy(IContentStack<FluidStack> content, FluidStack value) {
        return value.copy();
    }

    @Override
    public FluidStack normalize(IContentStack<FluidStack> content, FluidStack value) {
        return value.copyWithAmount(1000);
    }

    @Override
    public FluidStack wildcard(IContentStack<FluidStack> entry, FluidStack value) {
        return value.copyWithAmount(1000);
    }

    @Override
    public long getAmount(IContentStack<FluidStack> content, FluidStack value) {
        return value.getAmount();
    }

    @Override
    public long getAmount(FluidStack value) {
        return value.getAmount();
    }

    @Override
    public @Nullable FluidStack merge(FluidStack value1, FluidStack value2, MatchContext matchContext) {
        if (matches(value1, value2, matchContext)) {
            FluidStack result = value1.copy();
            result.grow(value2.getAmount());
        }
        return null;
    }

    @Override
    public boolean matches(FluidStack value1, FluidStack value2, MatchContext matchContext) {
        if (value1 == value2) return true;
        if (matchContext.exact()) {
            return matchContext.withCount() ? FluidStack.matches(value1, value2) : FluidStack.isSameFluidSameComponents(value1, value2);
        } else {
            boolean sameFluid = FluidStack.isSameFluid(value1, value2);
            return matchContext.withCount() ? sameFluid && value1.getAmount() == value2.getAmount() : sameFluid;
        }
    }

    @Override
    public long hash(IContentStack<FluidStack> content, FluidStack value, boolean fuzzy) {
        if (fuzzy) return value.getFluid().hashCode();
        else return value.hashCode();
    }

    @Override
    public Stream<? extends TagKey<?>> getTagsFor(IContentStack<FluidStack> content, FluidStack value) {
        return value.getFluid().defaultFluidState().getTags();
    }

    @Override
    public @Nullable DataComponentMap getComponents(IContentStack<FluidStack> content, FluidStack value) {
        return value.getComponents();
    }

    @Override
    public CompoundTag save(IContentStack<FluidStack> stack, FluidStack value) {
        return (CompoundTag) FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    @Override
    public @Nullable FluidStack read(CompoundTag tag) {
        return FluidStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }

    @Override
    public @Nullable ItemStack cheatStack(IContentStack<FluidStack> content, FluidStack value) {
        if (value.isEmpty()) return ItemStack.EMPTY;
        Item bucket = value.getFluid().getBucket();
        return new ItemStack(bucket);
    }

    public static class FluidTagType extends AbstractTagType<FluidStack, Fluid> {

        private final static ResourceLocation IDENTIFIER = ResourceLocation.parse("tag:fluid");

        public FluidTagType(IContentDefinition<FluidStack> definition) {
            super(definition, Fluid.class, BuiltInRegistries.FLUID);
        }

        @Override
        public ResourceLocation getId() {
            return IDENTIFIER;
        }

        @Override
        public Fluid getValue(FluidStack fluidStack) {
            return fluidStack.getFluid();
        }

        @Override
        public FluidStack makeStack(Fluid fluid) {
            return new FluidStack(fluid, 1000);
        }

        @Override
        public FluidStack makeStackWithAmount(Fluid fluid, int amount) {
            return new FluidStack(fluid, amount);
        }
    }

}
