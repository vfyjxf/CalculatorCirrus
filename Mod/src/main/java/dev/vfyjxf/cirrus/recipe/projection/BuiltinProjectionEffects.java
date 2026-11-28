package dev.vfyjxf.cirrus.recipe.projection;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;

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
                    tx.setSlot(slot.address(), fluidContent, ChangeReason.transformed);
                    tx.addNamedInput(
                        "fluid_container/" + slot.address().shortLabel(),
                        FluidContainerContent.from(containerStack.copy(), fluids, 1, RecipeContent.MIN_CHANCE)
                    );
                }
            )
            .build();

    /**
     * Evaluates {@link ProjectionKeys#durabilityCost} on damageable input slots: removes the slot
     * (at its exact address/grid cell) if applying the durability cost would break the tool.
     */
    public static final ProjectionEffect durabilityCost = ProjectionEffect.builder(durabilityCostId)
            .plugin(ProjectionPlugin.builtin)
            .stage(ProjectionStage.transform)
            .priority(0)
            .forEachSlotAttribute(
                SlotChannel.input,
                ProjectionKeys.durabilityCost,
                (tx, slot, cost) -> {
                    if (cost <= 0 || !(slot.content() instanceof StackContent<?, ?> stack) || !stack.isItem()) {
                        return;
                    }
                    ContentStack<Item, ItemStack> cs = stack.<Item, ItemStack>cast().content();
                    if (cs.isEmpty() || !cs.hasComponent(DataComponents.DAMAGE) || !cs.hasComponent(DataComponents.MAX_DAMAGE)) {
                        return;
                    }
                    Integer currentDamage = cs.getComponent(DataComponents.DAMAGE);
                    Integer maxDamage = cs.getComponent(DataComponents.MAX_DAMAGE);
                    if (currentDamage == null || maxDamage == null) {
                        return;
                    }
                    if (currentDamage + (long) cost >= maxDamage) {
                        tx.removeSlot(slot.address());
                    }
                }
            )
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
