package dev.vfyjxf.cirrus.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Default interaction adapter for crafting-grid style recipe execution.
 */
public final class CraftingTableLikeInteraction implements InteractionApi {

    private final Namespace interactionId;
    private final boolean automaticPerformerAvailable;
    private final ExecutionGuidance guidance;

    private CraftingTableLikeInteraction(
        Namespace interactionId,
        boolean automaticPerformerAvailable,
        ExecutionGuidance guidance
    ) {
        this.interactionId = interactionId;
        this.automaticPerformerAvailable = automaticPerformerAvailable;
        this.guidance = guidance;
    }

    //region factories

    public static CraftingTableLikeInteraction automatic(Namespace interactionId) {
        return new CraftingTableLikeInteraction(
            interactionId,
            true,
            ExecutionGuidance.simple(Component.translatable("cirrus.ui.execution.slot.automatic.available"))
        );
    }

    public static CraftingTableLikeInteraction guided(Namespace interactionId, ExecutionGuidance guidance) {
        return new CraftingTableLikeInteraction(interactionId, false, guidance);
    }

    //endregion

    //region interaction

    @Override
    public InteractionProbeResult probe(RecipeEntry<?> recipe) {
        return new InteractionProbeResult.Supported(interactionId, automaticPerformerAvailable, false);
    }

    @Override
    public InteractionGuideResult guide(RecipeEntry<?> recipe) {
        return new InteractionGuideResult(guidance);
    }

    @Override
    public InteractionPerformResult perform(InteractionContext context) {
        if (automaticPerformerAvailable) {
            return new InteractionPerformResult.Performed();
        }
        return new InteractionPerformResult.ManualRequired(guidance);
    }

    @Override
    public int maxExecuteBatchSize(InteractionContext context) {
        int maxBatch = Math.max(1, context.times());
        for (var input : context.inputs()) {
            long totalAmount = Math.max(0L, input.amount());
            long perCraftAmount = totalAmount / Math.max(1, context.times());
            if (perCraftAmount <= 0L) {
                continue;
            }
            long stackLimit = maxStackLimit(input.input());
            long inputBatchLimit = stackLimit / perCraftAmount;
            if (inputBatchLimit <= 0L) {
                return 1;
            }
            maxBatch = Math.min(maxBatch, (int) Math.max(1L, inputBatchLimit));
        }
        return Math.max(1, maxBatch);
    }

    private static long maxStackLimit(InputContent input) {
        if (input instanceof StackContent<?, ?> stackContent && !stackContent.isEmpty()) {
            ContentStack<?, ?> content = stackContent.content();
            if (content.notEmpty() && content.isItem()) {
                ItemStack stack = content.<Item, ItemStack>cast().value();
                if (!stack.isEmpty()) {
                    return Math.max(1L, stack.getMaxStackSize());
                }
            }
        }
        return 64L;
    }

    //endregion
}
