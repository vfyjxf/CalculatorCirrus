package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.content.renderer.FluidStackContentRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
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

public class FluidStackType extends BasicContentType<FluidStack> {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(namespace, "fluid_stack");

    public FluidStackType() {
        super(FluidStack.class, Fluid.class, new FluidStackContentRenderer());
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Object getBase(FluidStack content) {
        return content.getFluid();
    }

    @Override
    public @Nullable String getUid(ContentStack<FluidStack> content, FluidStack value) {
        return BuiltInRegistries.FLUID.getResourceKey(value.getFluid())
                .map(ResourceKey::location)
                .map(ResourceLocation::toString)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fluid " + value.getFluid()));
    }

    @Override
    public @Nullable String getModid(ContentStack<FluidStack> content, FluidStack value) {
        Fluid fluid = value.getFluid();
        return BuiltInRegistries.FLUID.getResourceKey(fluid)
                .map(ResourceKey::location)
                .map(ResourceLocation::getNamespace)
                .orElse("None");
    }

    @Override
    public boolean isEmpty(ContentStack<FluidStack> content, FluidStack value) {
        return value.isEmpty();
    }

    @Override
    public FluidStack create(ContentStack<FluidStack> content, DataComponentPatch components, long amount) {
        return new FluidStack(content.value().getFluidHolder(), (int) amount, components);
    }

    @Override
    public FluidStack copy(ContentStack<FluidStack> content, FluidStack value) {
        return value.copy();
    }

    @Override
    public FluidStack normalize(ContentStack<FluidStack> content, FluidStack value) {
        return value.copyWithAmount(1000);
    }

    @Override
    public FluidStack wildcard(ContentStack<FluidStack> entry, FluidStack value) {
        return value.copyWithAmount(1000);
    }

    @Override
    public long getAmount(ContentStack<FluidStack> content, FluidStack value) {
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
            return FluidStack.isSameFluidSameComponents(value1, value2);
        } else {
            return FluidStack.isSameFluid(value1, value2);
        }
    }

    @Override
    public long hash(ContentStack<FluidStack> content, FluidStack value, boolean fuzzy) {
        //TODO:Rewrite this
        if (fuzzy) return value.getFluid().hashCode();
        else return value.hashCode();
    }

    @Override
    public Stream<? extends TagKey<?>> getTagsFor(ContentStack<FluidStack> content, FluidStack value) {
        return value.getFluid().defaultFluidState().getTags();
    }

    @Override
    public @Nullable DataComponentMap getComponents(ContentStack<FluidStack> content, FluidStack value) {
        return value.getComponents();
    }

    @Override
    public CompoundTag save(ContentStack<FluidStack> stack, FluidStack value) {
        return (CompoundTag) FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    @Override
    public @Nullable FluidStack read(CompoundTag tag) {
        return FluidStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }

    @Override
    public @Nullable ItemStack cheatStack(ContentStack<FluidStack> content, FluidStack value) {
        if (value.isEmpty()) return ItemStack.EMPTY;
        Item bucket = value.getFluid().getBucket();
        return new ItemStack(bucket);
    }

}
