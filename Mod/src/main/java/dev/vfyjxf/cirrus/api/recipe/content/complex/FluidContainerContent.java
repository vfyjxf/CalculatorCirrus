package dev.vfyjxf.cirrus.api.recipe.content.complex;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.jetbrains.annotations.Range;

import java.util.Collection;

public final class FluidContainerContent implements ComplexContent {

    public static InputContent from(
        ItemStack sampleContainer,
        Collection<? extends ContentStack<Fluid, FluidStack>> requiredFluids,
        long amount,
        float chance
    ) {
        if (amount <= 0 || sampleContainer.isEmpty() || requiredFluids.isEmpty()) {
            return empty;
        }
        ImmutableList<ContentStack<Fluid, FluidStack>> fluids = Lists.immutable
            .ofAll(requiredFluids)
            .reject(ContentStack::isEmpty)
            .collect(ContentStack::copy);
        if (fluids.isEmpty()) {
            return empty;
        }
        return new FluidContainerContent(ContentStack.fromStack(sampleContainer), fluids, amount, chance);
    }

    private final ContentStack<Item, ItemStack> sampleContainer;
    private final ImmutableList<ContentStack<Fluid, FluidStack>> requiredFluids;
    private final long amount;
    private final float chance;

    private FluidContainerContent(
        ContentStack<Item, ItemStack> sampleContainer,
        ImmutableList<ContentStack<Fluid, FluidStack>> requiredFluids,
        long amount,
        float chance
    ) {
        Checks.checkRangeClosed(chance, 0, 1);
        this.sampleContainer = sampleContainer.copyWithAmount(1);
        this.requiredFluids = requiredFluids.collect(ContentStack::copy);
        this.amount = amount;
        this.chance = chance;
    }

    public ContentStack<Item, ItemStack> sampleContainer() {
        return sampleContainer;
    }

    public ImmutableList<ContentStack<Fluid, FluidStack>> requiredFluids() {
        return requiredFluids;
    }

    @Override
    public boolean isEmpty() {
        return sampleContainer.isEmpty() || requiredFluids.isEmpty() || amount <= 0;
    }

    @Override
    public @Range(from = 0, to = Long.MAX_VALUE) long amount() {
        return amount;
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return chance;
    }

    @Override
    public FluidContainerContent copy() {
        return new FluidContainerContent(sampleContainer, requiredFluids, amount, chance);
    }

    @Override
    public FluidContainerContent copyWithAmount(long amount) {
        return new FluidContainerContent(sampleContainer, requiredFluids, amount, chance);
    }

    @Override
    public FluidContainerContent copyWithChance(float chance) {
        return new FluidContainerContent(sampleContainer, requiredFluids, amount, chance);
    }

    @Override
    public FluidContainerContent copyWithAmountAndChance(long amount, float chance) {
        return new FluidContainerContent(sampleContainer, requiredFluids, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        if (!(other instanceof FluidContainerContent content)) return false;
        if (requiredFluids.size() != content.requiredFluids.size()) return false;
        for (int i = 0; i < requiredFluids.size(); i++) {
            if (!requiredFluids.get(i).matches(content.requiredFluids.get(i), MatchStrategy.identityAndComponent)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        if (!(actual instanceof ContentStack<?, ?> stack) || !stack.isItem()) {
            return false;
        }
        ItemStack itemStack = stack.<Item, ItemStack>cast().value().copy();
        if (itemStack.isEmpty()) {
            return false;
        }
        var handler = itemStack.getCapability(FluidHandler.ITEM);
        if (handler == null) {
            return false;
        }
        for (ContentStack<Fluid, FluidStack> required : requiredFluids) {
            long found = 0L;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack fluid = handler.getFluidInTank(tank);
                if (fluid.isEmpty()) {
                    continue;
                }
                ContentStack<Fluid, FluidStack> candidate = ContentStack.fromStack(CirrusTypes.Contents.FLUID_STACK, fluid, fluid.getAmount());
                if (candidate.matches(required, MatchStrategy.identityAndComponent)) {
                    found += candidate.amount();
                }
            }
            if (found < required.amount()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isEnumerable() {
        return false;
    }

    @Override
    public String toString() {
        return "FluidContainerContent{" +
               "sampleContainer=" + sampleContainer +
               ", requiredFluids=" + requiredFluids +
               ", amount=" + amount +
               ", chance=" + chance +
               '}';
    }

    @Override
    public RecipeContentType<? extends RecipeContent> recipeType() {
        return RecipeContentType.COMPLEX;
    }
}