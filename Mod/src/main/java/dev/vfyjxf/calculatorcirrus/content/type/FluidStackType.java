package dev.vfyjxf.calculatorcirrus.content.type;

import com.google.common.primitives.Ints;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.VariantType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

public class FluidStackType extends BasicStackType<Fluid, FluidStack> {

    public static final FluidStackType instance = new FluidStackType();

    private FluidStackType() {
        super(Locations.ofMc("fluid_stack"), FluidContentType.instance, Integer.MAX_VALUE, FluidStack.OPTIONAL_CODEC);
    }

    //region stack properties

    @Override
    public boolean isEmpty(FluidStack stack) {
        return stack.isEmpty();
    }

    @Override
    public Fluid getContent(FluidStack stack) {
        return stack.getFluid();
    }

    @Override
    public long quantity() {
        return FluidType.BUCKET_VOLUME;
    }

    @Override
    public long getAmount(FluidStack stack) {
        return stack.getAmount();
    }

    @Override
    public long getMaxStackSize(FluidStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public DataComponentMap getComponents(FluidStack stack) {
        return stack.getComponents();
    }

    @Override
    public DataComponentPatch getComponentsPatch(FluidStack stack) {
        return stack.getComponentsPatch();
    }

    @Override
    public boolean hasComponent(FluidStack stack, DataComponentType<?> componentType) {
        return stack.has(componentType);
    }

    @Override
    public <C> @UnknownNullability C getComponent(FluidStack stack, DataComponentType<? extends C> componentType) {
        return stack.get(componentType);
    }

    //endregion

    //region stack factories

    @Override
    public FluidStack copy(FluidStack stack) {
        return stack.copy();
    }

    @Override
    public FluidStack copyWithAmount(FluidStack stack, long amount) {
        return stack.copyWithAmount(Ints.saturatedCast(amount));
    }

    @Override
    public FluidStack normalize(FluidStack stack) {
        return stack.copyWithAmount(FluidType.BUCKET_VOLUME);
    }

    @Override
    public FluidStack wildcard(FluidStack stack) {
        var fluid = stack.getFluid();
        if (stack == FluidStack.EMPTY || fluid == Fluids.EMPTY) return FluidStack.EMPTY;
        return new FluidStack(fluid, stack.getAmount());
    }

    @Override
    public FluidStack fromStack(FluidStack stack, long amount, DataComponentPatch componentPatch) {
        if (amount > Integer.MAX_VALUE) {
            amount = Integer.MAX_VALUE;
        } else if (amount <= 0 || stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(stack.getFluidHolder(), (int) amount, componentPatch);
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidStack fromContent(Fluid content, long amount, DataComponentPatch componentPatch) {
        if (amount <= 0 || content == Fluids.EMPTY) return FluidStack.EMPTY;
        else return new FluidStack(content.builtInRegistryHolder(), Ints.saturatedCast(amount), componentPatch);
    }

    @Override
    public @Nullable FluidStack mergeAndCopy(FluidStack stack1, FluidStack stack2, MatchStrategy strategy) {
        return super.mergeAndCopy(stack1, stack2, strategy);
    }

    @Override
    public ItemStack toItemStack(ContentStack<Fluid, FluidStack> stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        Item bucket = stack.value().getFluid().getBucket();
        return new ItemStack(bucket);
    }

    //endregion

    //region match

    @Override
    public long mergeDiff(FluidStack stack1, FluidStack stack2, MatchStrategy strategy) {
        if (stack1 == stack2) return 0;
        boolean identity = stack1.getFluid() == stack2.getFluid();
        boolean components = !strategy.withComponent() || strategy.testComponents(stack1.getComponents(), stack2.getComponents());
        if (identity && components) {
            return stack1.getAmount() - stack2.getAmount();
        } else return 0;
    }

    @Override
    public boolean matches(FluidStack stack1, FluidStack stack2, MatchStrategy strategy) {
        if (stack1 == stack2) return true;
        boolean identity = !strategy.withIdentity() || stack1.getFluid() == stack2.getFluid();
        boolean amount = !strategy.withAmount() || stack1.getAmount() == stack2.getAmount();
        boolean components = !strategy.withComponent() || strategy.testComponents(stack1.getComponents(), stack2.getComponents());
        return identity && amount && components;
    }

    //endregion

    @Override
    public String toString() {
        return "fluid_stack";
    }
}
