package dev.vfyjxf.cirrus.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverLookup;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverSelector;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverSelector.MatchResult;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.MenuContext;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayViewModel;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionRuntime;
import dev.vfyjxf.cirrus.recipe.execution.interaction.CraftingTableLikeInteraction;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * Server-side execution driver that runs the full plan via {@link ExecutionRuntime}.
 * <p>
 * All step advancement is delegated to the runtime; no slot-level access is needed.
 */
public class CraftingTableServerExecutionDriver extends SlotExecutionDriver {

    public static final DriverSelector selector = context -> switch (context) {
        case InteractionContext(var id) when CraftingTableExecutionDriver.interactionId.equals(id) -> MatchResult.pass;
        case InteractionContext ignored -> MatchResult.reject;
        case MenuContext(var type) when CraftingMenu.class.isAssignableFrom(type) -> MatchResult.accept;
        case MenuContext ignored -> MatchResult.reject;
        default -> MatchResult.pass;
    };

    /**
     * @param runtime runtime that executes translated steps completely on the server side
     */
    public CraftingTableServerExecutionDriver(ExecutionRuntime runtime) {
        this(runtime, CraftingTableLikeInteraction.automatic(CraftingTableExecutionDriver.interactionId));
    }

    public CraftingTableServerExecutionDriver(ExecutionRuntime runtime, CraftingTableLikeInteraction interactionApi) {
        super(CraftingTableExecutionDriver.interactionId, interactionApi, runtime);
    }

    public static CraftingTableServerExecutionDriver create(DriverLookup lookup) {
        var interactionApi = CraftingTableLikeInteraction.automatic(CraftingTableExecutionDriver.interactionId);
        var runtime = new ExecutionRuntime(StorageApi.instance(), interactionApi);
        return new CraftingTableServerExecutionDriver(runtime, interactionApi);
    }

    /**
     * Reprojects overlay view-model from the runtime session so UI always receives latest flow state.
     */
    @Override
    public ExecutionDriverSnapshot snapshot() {
        ExecutionDriverSnapshot base = super.snapshot();
        ExecutionSession session = base.session();
        return new ExecutionDriverSnapshot(
            session,
            session == null ? null : ExecutionOverlayViewModel.derive(session),
            base.telemetry(),
            base.materials(),
            base.targetProgress(),
            base.trace(),
            base.ratePerSecond(),
            base.autoAdvanceEnabled(),
            base.reTranslationHint()
        );
    }
}
