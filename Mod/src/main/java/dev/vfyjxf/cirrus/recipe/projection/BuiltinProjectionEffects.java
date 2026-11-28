package dev.vfyjxf.cirrus.recipe.projection;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.FluidContainerContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionKeys;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.TagQuery;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ChangeReason;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionStage;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Built-in GAS {@link ProjectionEffect} implementations replacing the legacy clause transformers.
 */
public final class BuiltinProjectionEffects {

    public static final Namespace fluidContainerUnpackId = CirrusNamespaces.ofMod("input/fluid_container_unpack");
    public static final Namespace durabilityCostId = CirrusNamespaces.ofMod("input/durability_cost");
    public static final Namespace guaranteedOutputId = CirrusNamespaces.ofMod("output/guaranteed");

    /**
     * Unpacks tagged fluid-container input slots in place (preserving grid cell address) into
     * fluid content plus a named non-consumed {@link FluidContainerContent} requirement.
     */
    public static final ProjectionEffect fluidContainerUnpack = ProjectionEffect.builder(fluidContainerUnpackId)
            .plugin(ProjectionPlugin.builtin)
            .stage(ProjectionStage.prepare)
            .priority(0)
            .forEachSlot(
                SlotChannel.input,
                TagQuery.requireAll(ProjectionKeys.Tags.fluidContainer),
                (tx, slot) -> {
                    if (!(slot.content() instanceof StackContent<?, ?> stack) || !stack.isItem()) {
                        return;
                    }
                    ItemStack containerStack = stack.<Item, ItemStack>cast().content().value();
                    var fluidHandler = containerStack.getCapability(FluidHandler.ITEM);
                    if (fluidHandler == null) {
                        return;
                    }
                    var fluids = MutableLists.<ContentStack<Fluid, FluidStack>>empty();
                    for (int i = 0; i < fluidHandler.getTanks(); i++) {
                        FluidStack fluidStack = fluidHandler.getFluidInTank(i);
                        if (!fluidStack.isEmpty()) {
                            fluids.add(ContentStack.fromStack(fluidStack));
                        }
                    }
                    if (fluids.isEmpty()) {
                        return;
                    }
                    InputContent fluidContent;
                    if (fluids.size() == 1) {
                        fluidContent = InputContent.fromStack(fluids.getFirst());
                    } else {
                        long avgAmount = fluids.collectLong(ContentStack::amount).sum() / fluids.size();
                        fluidContent = InputContent.fromStackList(fluids, Math.max(1L, avgAmount));
                    }
                    ItemStack emptyContainer = resolveEmptyContainer(containerStack);
                    tx.setSlot(slot.address(), fluidContent, ChangeReason.transformed);
                    tx.addNamedInput(
                        "fluid_container/" + slot.address().shortLabel(),
                        FluidContainerContent.from(emptyContainer, fluids, 1, RecipeContent.MIN_CHANCE)
                    );
                }
            )
            .build();

    private static ItemStack resolveEmptyContainer(ItemStack filled) {
        ItemStack working = filled.copyWithCount(1);
        var handler = working.getCapability(FluidHandler.ITEM);
        if (handler != null) {
            int maxDrains = Math.max(1, handler.getTanks());
            for (int i = 0; i < maxDrains; i++) {
                FluidStack drained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                if (drained.isEmpty()) {
                    break;
                }
            }
            ItemStack drainedContainer = handler.getContainer();
            if (!drainedContainer.isEmpty()) {
                return drainedContainer.copyWithCount(1);
            }
        }
        ItemStack remainder = filled.getCraftingRemainingItem();
        if (!remainder.isEmpty()) {
            return remainder.copyWithCount(1);
        }
        return working;
    }

    /**
     * Transforms damageable tool input slots carrying {@link ProjectionKeys#durabilityCost}
     * or tagged {@link ProjectionKeys.Tags#durabilityTool} (defaulting omitted cost to {@code 1})
     * into a dedicated {@link DurabilityContent} requirement ({@code tool with -N durability cost}).
     *
     * <p>The slot keeps normal certainty ({@link RecipeContent#MAX_CHANCE}) and an undamaged tool
     * representation instead of mutating stack damage or using a {@code 0%} catalyst chance hack.
     */
    public static final ProjectionEffect durabilityCost = ProjectionEffect.builder(durabilityCostId)
            .plugin(ProjectionPlugin.builtin)
            .stage(ProjectionStage.transform)
            .priority(0)
            .when(
                (ctx, view) -> view.inputSlots().stream().anyMatch(
                    slot -> slot.attributes().has(ProjectionKeys.durabilityCost)
                            || slot.tags().has(ProjectionKeys.Tags.durabilityTool)
                )
            )
            .apply(tx -> {
                for (ProjectionSlot<InputContent> slot : tx.inputSlots()) {
                    Integer rawCost = slot.attribute(ProjectionKeys.durabilityCost);
                    boolean taggedTool = slot.tags().has(ProjectionKeys.Tags.durabilityTool);
                    if (rawCost == null && !taggedTool) {
                        continue;
                    }
                    int cost = rawCost == null ? 1 : rawCost;
                    if (cost <= 0) {
                        continue;
                    }
                    InputContent current = slot.content();
                    if (!DurabilityContent.supportsDurability(current)) {
                        continue;
                    }
                    if (rawCost == null) {
                        tx.putSlotAttribute(slot.address(), ProjectionKeys.durabilityCost, cost);
                    }
                    if (!taggedTool) {
                        tx.addSlotTag(slot.address(), ProjectionKeys.Tags.durabilityTool);
                    }
                    if (current instanceof DurabilityContent existing) {
                        if (existing.durabilityCost() == cost
                                && Float.compare(existing.chance(), RecipeContent.MAX_CHANCE) == 0) {
                            continue;
                        }
                        InputContent updated = DurabilityContent.from(
                            existing.ingredient(),
                            cost,
                            existing.amount(),
                            RecipeContent.MAX_CHANCE
                        );
                        if (updated.notEmpty()) {
                            tx.setSlot(slot.address(), updated, ChangeReason.adjusted);
                        }
                        continue;
                    }
                    InputContent requirement = DurabilityContent.from(
                        current,
                        cost,
                        current.amount(),
                        RecipeContent.MAX_CHANCE
                    );
                    if (requirement.notEmpty()) {
                        tx.setSlot(slot.address(), requirement, ChangeReason.transformed);
                    }
                }
            })
            .build();

    /**
     * Strips non-guaranteed (chance &lt; 1) output slots from recipes tagged
     * {@link ProjectionKeys.Tags#guaranteedOutput}.
     */
    public static final ProjectionEffect guaranteedOutput = ProjectionEffect.builder(guaranteedOutputId)
            .plugin(ProjectionPlugin.builtin)
            .stage(ProjectionStage.finalize)
            .priority(0)
            .requireTags(ProjectionKeys.Tags.guaranteedOutput)
            .when((ctx, view) -> view.outputSlots().stream().anyMatch(slot -> !slot.constantly()))
            .apply(tx -> {
                for (ProjectionSlot<OutputContent> output : tx.outputSlots()) {
                    if (!output.constantly()) {
                        tx.removeSlot(output.address());
                    }
                }
            })
            .build();

    private BuiltinProjectionEffects() {}
}
