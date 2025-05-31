package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
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

public class FluidStackType extends BasicStackContentType<FluidStack> {

    public static final FluidStackType INSTANCE = new FluidStackType();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ContentType.namespace, "fluid_stack");

    private FluidStackType() {
        super(FluidStack.class, Fluid.class);
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    public @Nullable ResourceLocation getLocation(FluidStack value) {
        return BuiltInRegistries.FLUID.getResourceKey(value.getFluid())
                .map(ResourceKey::location)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fluid " + value.getFluid()));
    }

    @Override
    public @Nullable String getNamespace(FluidStack value) {
        Fluid fluid = value.getFluid();
        return BuiltInRegistries.FLUID.getResourceKey(fluid)
                .map(ResourceKey::location)
                .map(ResourceLocation::getNamespace)
                .orElse(null);
    }

    @Override
    public boolean isEmpty(FluidStack value) {
        return value.isEmpty();
    }

    @Override
    public Object getIdentity(FluidStack content) {
        return content.getFluid();
    }

    @Override
    public boolean isEmpty(ContentStack<FluidStack> content, FluidStack value) {
        return content.amount() <= 0 || value.isEmpty();
    }

    @Override
    public FluidStack create(ContentStack<FluidStack> content, long amount, DataComponentPatch componentPatch) {
        if (amount > Integer.MAX_VALUE) {
            amount = Integer.MAX_VALUE;
        } else if (amount <= 0 || content.value().isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(content.value().getFluidHolder(), (int) amount, componentPatch);
    }

    @Override
    public FluidStack create(FluidStack value, long amount, DataComponentPatch componentPatch) {
        if (amount > Integer.MAX_VALUE) {
            amount = Integer.MAX_VALUE;
        } else if (amount <= 0 || value.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(value.getFluidHolder(), (int) amount, componentPatch);
    }


    @Override
    public FluidStack copy(FluidStack value) {
        return value.copy();
    }

    @Override
    public FluidStack normalize(FluidStack value) {
        return value.copyWithAmount(1000);
    }

    @Override
    public FluidStack wildcard(FluidStack value) {
        return new FluidStack(value.getFluid(), 1000);
    }

    @Override
    public DataComponentMap getComponents(FluidStack value) {
        return value.getComponents();
    }

    @Override
    public DataComponentPatch getComponentsPatch(FluidStack value) {
        return value.getComponentsPatch();
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
    public long mergeDiff(FluidStack value1, FluidStack value2, MatchStrategy strategy) {
        return 0;
    }

    @Override
    public boolean matches(FluidStack value1, FluidStack value2, MatchStrategy strategy) {
        if (value1 == value2) return true;
        boolean identity = !strategy.withIdentity() || value1.getFluid() == value2.getFluid();
        boolean amount = !strategy.withAmount() || value1.getAmount() == value2.getAmount();
        boolean components = !strategy.withComponent() || strategy.testComponents(value1.getComponents(), value2.getComponents());
        return identity && amount && components;
    }

    @Override
    public int hash(FluidStack value, MatchStrategy strategy) {
        int hashcode = value.getFluid().hashCode();
        hashcode = strategy.withAmount() ? 31 * hashcode + value.getAmount() : hashcode;
        hashcode = strategy.withComponent() ? 31 * hashcode + value.getComponents().hashCode() : hashcode;
        return hashcode;
    }

    @Override
    public long maxAmount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Stream<? extends TagKey<?>> getTags(FluidStack value) {
        return Stream.empty();
    }

    @Override
    public ItemStack cheatStack(FluidStack value) {
        if (value.isEmpty()) return ItemStack.EMPTY;
        Item bucket = value.getFluid().getBucket();
        return new ItemStack(bucket);
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
    public String toString() {
        return "fluid_stack";
    }
}
