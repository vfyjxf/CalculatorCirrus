package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;

/**
 * Default interaction adapter for crafting-grid style recipe execution.
 */
public final class CraftingTableLikeInteraction implements InteractionApi {

    private final String interactionId;
    private final boolean automaticPerformerAvailable;
    private final ExecutionGuidance guidance;

    private CraftingTableLikeInteraction(String interactionId, boolean automaticPerformerAvailable, ExecutionGuidance guidance) {
        this.interactionId = Checks.checkNotNull(interactionId, "interactionId");
        this.automaticPerformerAvailable = automaticPerformerAvailable;
        this.guidance = Checks.checkNotNull(guidance, "guidance");
    }

    //region factories

    public static CraftingTableLikeInteraction automatic(String interactionId) {
        return new CraftingTableLikeInteraction(
            interactionId,
            true,
            ExecutionGuidance.simple(Component.literal("Automatic performer available"))
        );
    }

    public static CraftingTableLikeInteraction guided(String interactionId, ExecutionGuidance guidance) {
        return new CraftingTableLikeInteraction(interactionId, false, guidance);
    }

    //endregion

    //region interaction

    @Override
    public InteractionProbeResult probe(RecipeEntry<?> recipe) {
        Checks.checkNotNull(recipe, "recipe");
        return new InteractionProbeResult.Supported(interactionId, automaticPerformerAvailable, false);
    }

    @Override
    public InteractionGuideResult guide(RecipeEntry<?> recipe) {
        Checks.checkNotNull(recipe, "recipe");
        return new InteractionGuideResult(guidance);
    }

    @Override
    public InteractionPerformResult perform(InteractionContext context) {
        Checks.checkNotNull(context, "context");
        if (automaticPerformerAvailable) {
            return new InteractionPerformResult.Performed();
        }
        return new InteractionPerformResult.ManualRequired(guidance);
    }

    //endregion
}
