package dev.vfyjxf.cirrus.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.content.stock.Stock;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverLookup;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverSelector;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverSelector.MatchResult;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.MenuContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.MenuTypeContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.ScreenContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotAccess;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayViewModel;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.helper.RecipeHelper;
import dev.vfyjxf.cirrus.recipe.execution.interaction.CraftingTableLikeInteraction;
import dev.vfyjxf.cirrus.ui.CirrusUiAsync;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Built-in client driver for vanilla crafting-table style interactions.
 * <p>
 * The driver is intentionally implementation-side (non-api package). Public contracts remain on
 * {@link SlotExecutionDriver} and related slot abstractions in the api package.
 */
public class CraftingTableExecutionDriver extends SlotExecutionDriver {

    /**
     * Interaction identity used for driver registration and plan side routing.
     */
    public static final Namespace interactionId = Namespace.ofMc("crafting_table");
    public static final DriverSelector selector = context -> switch (context) {
        case InteractionContext(var id) when interactionId.equals(id) -> MatchResult.pass;
        case InteractionContext ignored -> MatchResult.reject;
        case ScreenContext(var type) when CraftingScreen.class.isAssignableFrom(type) -> MatchResult.accept;
        case MenuContext(var type) when CraftingMenu.class.isAssignableFrom(type) -> MatchResult.accept;
        case MenuTypeContext(var type) when type == MenuType.CRAFTING -> MatchResult.accept;
        case ScreenContext ignored -> MatchResult.pass;
        case MenuContext ignored -> MatchResult.reject;
        default -> MatchResult.pass;
    };

    private static final int outputSlot = 0;
    private static final int firstInputSlot = 1;
    private static final int inputSlotCount = 9;
    private static final ImmutableList<Integer> gridSlots = Lists.immutable.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
    private static final int gridWidth = (int) Math.round(Math.sqrt(gridSlots.size()));
    private static final int firstInventorySlot = 10;
    private static final int inventorySlotCount = 36;
    private static final SlotLayout craftingTableLayout = new CraftingTableLayout();
    private static final SlotTrace.Palette tracePalette = new SlotTrace.Palette(
        0xC082C5FF,
        0xB467C887,
        0xB8F4C56D,
        0xA882C5FF
    );

    private @Nullable TrackingSnapshot cachedTrackingSnapshot;
    private @Nullable ExecutionSession cachedTrackingSession;
    private long cachedTrackingFingerprint = Long.MIN_VALUE;
    private long trackingRequestId = 0L;
    private @Nullable PendingTrackingBuild pendingTrackingBuild;

    public record RecipePreviewSlot(int gridIndex, ItemStack template, long amount, @Nullable InputContent input) {
        public RecipePreviewSlot {
            gridIndex = Math.max(0, gridIndex);
            template = normalizedPlacementTemplate(template);
            amount = Math.max(1L, amount);
        }
    }

    private record TrackingSnapshot(
        ImmutableList<OverlayMaterialRequirement> materials,
        @Nullable OverlayTargetProgress targetProgress,
        @Nullable ExecutionOverlayTraceSnapshot trace
    ) {
        private static TrackingSnapshot empty() {
            return new TrackingSnapshot(Lists.immutable.empty(), null, ExecutionOverlayTraceSnapshot.empty());
        }
    }

    private record PendingTrackingBuild(
        long requestId,
        ExecutionSession session,
        long fingerprint,
        CompletableFuture<TrackingSnapshot> future
    ) {}

    /**
     * Creates a standard slot-based client driver with built-in crafting table layout/access/placement profile.
     */
    public CraftingTableExecutionDriver() {
        super(
            new SlotExecutionDriver.Profile(
                interactionId,
                CraftingTableLikeInteraction.automatic(interactionId),
                craftingTableLayout,
                new CraftingTableAccess(),
                new CraftingTableAccess(),
                new CraftingTablePlacementResolver(),
                null
            )
        );
    }

    public static CraftingTableExecutionDriver create(DriverLookup lookup) {
        return new CraftingTableExecutionDriver();
    }

    public boolean isStationOpen() {
        return interactionOpen();
    }

    @Override
    public void initialize(ExecutionSession session) {
        invalidateTrackingCache();
        super.initialize(session);
        autoAdvanceSatisfiedOpenStep();
    }

    @Override
    public void tick() {
        autoAdvanceSatisfiedOpenStep();
        super.tick();
        autoAdvanceSatisfiedOpenStep();
    }

    private void autoAdvanceSatisfiedOpenStep() {
        ExecutionSession current = currentSession();
        if (current == null || current.completed() || current.paused()) {
            return;
        }
        if (current.currentStep() instanceof InteractionStep.OpenInteractionStep && interactionOpen()) {
            advanceSteps(1);
            invalidateTrackingCache();
        }
    }

    @Override
    public void reset() {
        invalidateTrackingCache();
        super.reset();
    }

    @Override
    public void jumpToStep(int index) {
        invalidateTrackingCache();
        super.jumpToStep(index);
    }

    private void invalidateTrackingCache() {
        pendingTrackingBuild = null;
        cachedTrackingSnapshot = null;
        cachedTrackingSession = null;
        cachedTrackingFingerprint = Long.MIN_VALUE;
    }

    @Override
    public ExecutionDriverSnapshot snapshot() {
        ExecutionSession current = currentSession();
        if (current == null) {
            invalidateTrackingCache();
            return pendingSnapshot();
        }
        if (cachedTrackingSession != current) {
            invalidateTrackingCache();
            cachedTrackingSession = current;
        }
        pollPendingTrackingBuild(current);

        TrackingInput trackingInput = captureTrackingInput();
        if (cachedTrackingSnapshot == null) {
            cachedTrackingSnapshot = buildTracking(current, trackingInput);
            cachedTrackingFingerprint = trackingInput.fingerprint();
        } else if (pendingTrackingBuild == null && trackingInput.fingerprint() != cachedTrackingFingerprint) {
            startTrackingBuild(current, trackingInput);
        }

        TrackingSnapshot tracking = cachedTrackingSnapshot == null ? TrackingSnapshot.empty() : cachedTrackingSnapshot;
        ExecutionTelemetrySnapshot telemetry = ExecutionTelemetrySnapshot
                .from(current, currentRatePerSecond(), currentAutoAdvanceEnabled());
        return new ExecutionDriverSnapshot(
            current,
            ExecutionOverlayViewModel.derive(current),
            telemetry,
            tracking.materials(),
            tracking.targetProgress(),
            tracking.trace(),
            currentRatePerSecond(),
            currentAutoAdvanceEnabled(),
            current.reTranslationHint()
        );
    }

    private ExecutionDriverSnapshot pendingSnapshot() {
        return new ExecutionDriverSnapshot(
            null,
            null,
            new ExecutionTelemetrySnapshot(
                "pending",
                false,
                false,
                0,
                0,
                0.0d,
                null,
                null,
                null,
                Lists.immutable.empty(),
                Lists.immutable.empty(),
                null,
                null,
                currentRatePerSecond(),
                currentAutoAdvanceEnabled()
            ),
            Lists.immutable.empty(),
            null,
            null,
            currentRatePerSecond(),
            currentAutoAdvanceEnabled(),
            null
        );
    }

    @Override
    protected TrackingInput captureTrackingInput() {
        TrackingInput base = super.captureTrackingInput();
        Stock accessibleStock = accessibleItemStock(base.context());
        ExecutionSession current = currentSession();
        long fingerprint = 31L * base.fingerprint() + accessibleStock.snapshotResources().hashCode();
        if (current != null) {
            fingerprint = 31L * fingerprint + current.nextStepIndex();
            fingerprint = 31L * fingerprint + System.identityHashCode(current.plan());
        }
        return new TrackingInput(base.context(), fingerprint, accessibleStock);
    }

    private void startTrackingBuild(ExecutionSession session, TrackingInput input) {
        long requestId = ++trackingRequestId;
        pendingTrackingBuild = new PendingTrackingBuild(
            requestId,
            session,
            input.fingerprint(),
            CirrusUiAsync.recipeTask(() -> buildTracking(session, input))
        );
    }

    private void pollPendingTrackingBuild(ExecutionSession current) {
        PendingTrackingBuild pending = pendingTrackingBuild;
        if (pending == null || !pending.future().isDone()) {
            return;
        }
        pendingTrackingBuild = null;
        try {
            TrackingSnapshot tracking = pending.future().join();
            if (pending.requestId() == trackingRequestId && pending.session() == current) {
                cachedTrackingSnapshot = tracking;
                cachedTrackingFingerprint = pending.fingerprint();
            }
        } catch (RuntimeException ignored) {
            cachedTrackingSnapshot = cachedTrackingSnapshot == null ? TrackingSnapshot.empty() : cachedTrackingSnapshot;
        }
    }

    @Override
    protected SlotFlow.Tracking customizeTracking(@Nullable ExecutionSession session, SlotFlow.Tracking tracking) {
        if (session == null) {
            return tracking;
        }
        return buildTracking(session);
    }

    //region materials and target progress

    private static SlotFlow.Tracking buildTracking(ExecutionSession session) {
        SlotFlow.Context context = traceContext();
        Stock accessibleStock = accessibleItemStock(context);
        TrackingSnapshot snapshot = buildTracking(session, new TrackingInput(context, 0L, accessibleStock));
        ExecutionOverlayViewModel viewModel = ExecutionOverlayViewModel.derive(session);
        return new SlotFlow.Tracking(viewModel, snapshot.materials(), snapshot.targetProgress(), snapshot.trace());
    }

    private static TrackingSnapshot buildTracking(ExecutionSession session, TrackingInput input) {
        Stock accessibleStock = input.extra() instanceof Stock stock ? stock : Stock.empty();
        SlotFlow.Context context = input.context() == null ? traceContext() : input.context();
        return new TrackingSnapshot(
            collectRemainingMaterialRequirements(session, accessibleStock),
            overlayTargetProgress(session, accessibleStock),
            traceSnapshot(session, context)
        );
    }

    static ImmutableList<OverlayMaterialRequirement> collectRemainingMaterialRequirements(
        ExecutionSession sourceSession,
        Stock accessibleStock
    ) {
        ImmutableList<InteractionStep> steps = sourceSession.plan().steps();
        int startIndex = Math.max(0, Math.min(sourceSession.nextStepIndex(), steps.size()));
        if (startIndex >= steps.size()) {
            return Lists.immutable.empty();
        }
        boolean hasExplicitWithdraws = false;
        for (int i = startIndex; i < steps.size(); i++) {
            if (steps.get(i) instanceof InteractionStep.WithdrawResourceStep) {
                hasExplicitWithdraws = true;
                break;
            }
        }
        MutableList<TrackedMaterialRequirement> grouped = Lists.mutable.empty();
        if (hasExplicitWithdraws) {
            for (int i = startIndex; i < steps.size(); i++) {
                collectRemainingMaterialRequirement(grouped, steps.get(i));
            }
        } else {
            Stock producedIntermediates = Stock.empty();
            for (int i = startIndex; i < steps.size(); i++) {
                InteractionStep step = steps.get(i);
                switch (step) {
                    case InteractionStep.ReserveCatalystStep reserveCatalystStep -> addTrackedMaterialRequirement(
                        grouped,
                        reserveCatalystStep.input().copyWithAmount(reserveCatalystStep.amount())
                    );
                    case InteractionStep.ExecuteRecipeStep executeStep -> {
                        for (InputBinding binding : executeStep.inputs()) {
                            long neededAmount = Math.max(1L, binding.amount());
                            InputContent needed = binding.input().copyWithAmount(neededAmount);
                            long coveredByUpstream = producedIntermediates.consumeInput(needed, neededAmount);
                            long netExternal = Math.max(0L, neededAmount - coveredByUpstream);
                            if (netExternal > 0L) {
                                addTrackedMaterialRequirement(grouped, needed.copyWithAmount(netExternal));
                            }
                        }
                        for (OutputBinding output : executeStep.expectedOutputs()) {
                            long outAmount = Math.max(1L, output.amount());
                            if (output.resource() instanceof ContentStack<?, ?> stack && !stack.isEmpty()) {
                                producedIntermediates.add(stack.copyWithAmount(outAmount));
                            }
                        }
                    }
                    default -> {}
                }
            }
        }
        if (grouped.isEmpty()) {
            return Lists.immutable.empty();
        }
        Stock accessible = accessibleStock.copy();
        MutableList<OverlayMaterialRequirement> out = Lists.mutable.empty();
        for (TrackedMaterialRequirement requirement : grouped) {
            long available = consumeAccessible(accessible, requirement.material, requirement.required);
            long missing = Math.max(0L, requirement.required - available);
            out.add(new OverlayMaterialRequirement(requirement.material, requirement.required, available, missing));
        }
        var sorted = out.toSortedList(
            Comparator.comparingLong(OverlayMaterialRequirement::missing).reversed()
                    .thenComparingLong(OverlayMaterialRequirement::required).reversed()
                    .thenComparing(requirement -> materialLabel(requirement.material()), String.CASE_INSENSITIVE_ORDER)
        );
        return sorted.toImmutable();
    }

    private static void collectRemainingMaterialRequirement(
        MutableList<TrackedMaterialRequirement> grouped,
        InteractionStep step
    ) {
        switch (step) {
            case InteractionStep.WithdrawResourceStep withdrawStep -> addTrackedMaterialRequirement(
                grouped,
                materialFromWithdrawTarget(withdrawStep.target(), withdrawStep.requestedAmount())
            );
            case InteractionStep.ReserveCatalystStep reserveCatalystStep -> addTrackedMaterialRequirement(
                grouped,
                reserveCatalystStep.input().copyWithAmount(reserveCatalystStep.amount())
            );
            default -> {}
        }
    }

    private static void addTrackedMaterialRequirement(
        MutableList<TrackedMaterialRequirement> grouped,
        InputContent material
    ) {
        if (!ExecutionOverlayContentHelper.supportsItemMatching(material)) {
            return;
        }
        for (TrackedMaterialRequirement requirement : grouped) {
            if (requirement.material.mergeable(material) || material.mergeable(requirement.material)) {
                requirement.required = safeAdd(requirement.required, material.amount());
                return;
            }
        }
        grouped.add(new TrackedMaterialRequirement(material));
    }

    private static InputContent materialFromWithdrawTarget(InteractionStep.WithdrawTarget target, long amount) {
        long normalizedAmount = Math.max(1L, amount);
        return switch (target) {
            case InteractionStep.WithdrawTarget.Resource resource ->
                materialFromResource(resource.resource(), normalizedAmount);
            case InteractionStep.WithdrawTarget.Input input -> input.input().copyWithAmount(normalizedAmount);
        };
    }

    private static Stock accessibleItemStock(SlotFlow.@Nullable Context context) {
        Stock stock = Stock.empty();
        if (context != null) {
            addAccessibleRoleStacks(stock, context, SlotLayout.Role.inventory);
            addAccessibleRoleStacks(stock, context, SlotLayout.Role.input);
        }
        addAccessibleStacks(stock, offhandStacks());
        return stock;
    }

    private static void addAccessibleRoleStacks(Stock stock, SlotFlow.Context context, SlotLayout.Role role) {
        SlotLayout.Index index = context.layout().slots(role);
        if (index.keys().isEmpty()) {
            return;
        }
        ArrayList<SlotLayout.Key> keys = new ArrayList<>(index.keys());
        keys.sort(index.order());
        for (SlotLayout.Key key : keys) {
            SlotLayout.Reference reference = index.get(key);
            if (reference != null) {
                addAccessibleStack(stock, context.access().stack(reference));
            }
        }
    }

    private static Iterable<ItemStack> offhandStacks() {
        Inventory inventory = inventory();
        return inventory == null ? List.of() : inventory.offhand;
    }

    private static void addAccessibleStacks(Stock stock, Iterable<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            addAccessibleStack(stock, stack);
        }
    }

    private static void addAccessibleStack(Stock stock, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        stock.add(ContentStack.fromStack(stack.copy()));
    }

    private static long consumeAccessible(Stock stock, InputContent material, long required) {
        return stock.consumeInput(material, required);
    }

    private static @Nullable OverlayTargetProgress overlayTargetProgress(
        ExecutionSession sourceSession,
        Stock accessibleStock
    ) {
        if (sourceSession.plan().isEmpty()) {
            return null;
        }
        RecipeEntry<?> targetRecipe = sourceSession.plan().targetRecipe();
        if (targetRecipe == null) {
            return null;
        }
        ImmutableList<OutputBinding> outputs = resolveTargetRecipeOutputs(sourceSession, targetRecipe);
        if (outputs.isEmpty()) {
            outputs = resolveTargetRecipeExecuteOutputs(sourceSession, targetRecipe);
        }
        if (outputs.isEmpty()) {
            return null;
        }
        ImmutableList<ItemRequirement> targetOutputs = collapseOutputRequirements(outputs);
        if (targetOutputs.isEmpty()) {
            return null;
        }
        ItemRequirement selected = targetOutputs.getFirst();
        if (selected.template().isEmpty()) {
            return null;
        }
        long bestScore = Long.MIN_VALUE;
        for (ItemRequirement candidate : targetOutputs) {
            ItemStack stack = candidate.template();
            if (stack.isEmpty()) {
                continue;
            }
            long required = Math.max(0L, candidate.amount());
            long current = countAccessible(accessibleStock, stack);
            long missing = Math.max(0L, required - current);
            long score = missing * 1_000_000L + required;
            if (score > bestScore) {
                bestScore = score;
                selected = candidate;
            }
        }
        ItemStack displayStack = selected.template();
        if (displayStack.isEmpty()) {
            return null;
        }
        long required = Math.max(0L, selected.amount());
        long current = countAccessible(accessibleStock, displayStack);
        return new OverlayTargetProgress(OutputContent.fromItemStack(displayStack, 1), required, current);
    }

    private static ExecutionOverlayTraceSnapshot traceSnapshot(ExecutionSession session, SlotFlow.Context context) {
        InteractionStep step = session.currentStep();
        if (step == null) {
            return ExecutionOverlayTraceSnapshot.empty();
        }
        return switch (step) {
            case InteractionStep.OpenInteractionStep openStep -> traceOpenLookahead(session, openStep, context);
            case InteractionStep.PrepareInputsStep prepareInputsStep -> tracePrepareInputs(prepareInputsStep, context);
            case InteractionStep.ExecuteRecipeStep executeRecipeStep -> traceExecuteRecipe(executeRecipeStep, context);
            case InteractionStep.CollectOutputsStep collectOutputsStep ->
                traceCollectOutputs(collectOutputsStep, context);
            default -> ExecutionOverlayTraceSnapshot.empty();
        };
    }

    private static ExecutionOverlayTraceSnapshot traceOpenLookahead(
        ExecutionSession session,
        InteractionStep.OpenInteractionStep openStep,
        SlotFlow.Context context
    ) {
        ImmutableList<InteractionStep> steps = session.plan().steps();
        int start = Math.max(0, session.nextStepIndex() + 1);
        for (int i = start; i < steps.size(); i++) {
            InteractionStep candidate = steps.get(i);
            if (candidate instanceof InteractionStep.OpenInteractionStep) {
                break;
            }
            if (candidate instanceof InteractionStep.PrepareInputsStep prepare
                    && Objects.equals(openStep.recipe(), prepare.recipe())) {
                return tracePrepareInputs(prepare, context);
            }
            if (candidate instanceof InteractionStep.ExecuteRecipeStep execute
                    && Objects.equals(openStep.recipe(), execute.recipe())) {
                return traceExecuteRecipe(execute, context);
            }
        }
        return ExecutionOverlayTraceSnapshot.empty();
    }

    private static ExecutionOverlayTraceSnapshot tracePrepareInputs(
        InteractionStep.PrepareInputsStep step,
        SlotFlow.Context context
    ) {
        ImmutableList<SlotFlow.Placement> placements = resolveTracePlacements(step.recipe(), step.inputs(), context);
        if (placements.isEmpty()) {
            return ExecutionOverlayTraceSnapshot.empty();
        }
        ItemStack recipeIcon = firstOutputStack(step.recipe(), step.inputs(), null);
        return SlotTrace.prepareFlow(
            context,
            traceRecipeLabel(step.recipe()),
            recipeIcon,
            recipeIcon,
            placements,
            tracePalette
        );
    }

    private static ExecutionOverlayTraceSnapshot traceExecuteRecipe(
        InteractionStep.ExecuteRecipeStep step,
        SlotFlow.Context context
    ) {
        ItemStack output = firstOutputStack(step.recipe(), step.inputs(), step.expectedOutputs());
        if (output.isEmpty()) {
            return ExecutionOverlayTraceSnapshot.empty();
        }
        ImmutableList<SlotFlow.Placement> placements = resolveTracePlacements(step.recipe(), step.inputs(), context);
        return SlotTrace.executeFlow(
            context,
            traceRecipeLabel(step.recipe()),
            output,
            output,
            placements,
            tracePalette,
            SlotTrace.InventoryTargetSelector.moveItemStackTo(true)
        );
    }

    private static ExecutionOverlayTraceSnapshot traceCollectOutputs(
        InteractionStep.CollectOutputsStep step,
        SlotFlow.Context context
    ) {
        ItemStack output = firstOutputStack(step.recipe(), Lists.immutable.empty(), step.expectedOutputs());
        return SlotTrace.collectFlow(
            context,
            traceRecipeLabel(step.recipe()),
            output,
            output,
            tracePalette,
            SlotTrace.InventoryTargetSelector.moveItemStackTo(true)
        );
    }

    private static String traceRecipeLabel(@Nullable RecipeEntry<?> recipe) {
        if (recipe == null) {
            return "-";
        }
        if (recipe.id() != null) {
            return recipe.id().toString();
        }
        return recipe.toString();
    }

    private static SlotFlow.Context traceContext() {
        return new SlotFlow.Context(
            craftingTableLayout,
            new CraftingTableAccess(),
            new CraftingTableAccess(),
            new CraftingTablePlacementResolver()
        );
    }

    private static ImmutableList<SlotFlow.Placement> resolveTracePlacements(
        @Nullable RecipeEntry<?> recipe,
        ImmutableList<InputBinding> inputs,
        SlotFlow.Context context
    ) {
        ImmutableList<GridPlacement> gridPlacements = resolveGridPlacements(recipe, context, inputs, true, true);
        if (gridPlacements.isEmpty()) {
            gridPlacements = resolveGridPlacements(recipe, context, inputs, true, false);
        }
        if (gridPlacements.isEmpty()) {
            return Lists.immutable.empty();
        }
        return toSlotPlacements(gridPlacements);
    }

    private static ImmutableList<OutputBinding> resolveTargetRecipeOutputs(
        ExecutionSession session,
        RecipeEntry<?> targetRecipe
    ) {
        if (session.plan().steps().isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<OutputBinding> outputs = Lists.mutable.empty();
        for (InteractionStep step : session.plan().steps()) {
            if (!(step instanceof InteractionStep.CollectOutputsStep collectOutputsStep)) {
                continue;
            }
            if (!targetRecipe.equals(collectOutputsStep.recipe())) {
                continue;
            }
            outputs.addAllIterable(collectOutputsStep.expectedOutputs());
        }
        return outputs.toImmutable();
    }

    private static ImmutableList<OutputBinding> resolveTargetRecipeExecuteOutputs(
        ExecutionSession session,
        RecipeEntry<?> targetRecipe
    ) {
        if (session.plan().steps().isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<OutputBinding> outputs = Lists.mutable.empty();
        for (InteractionStep step : session.plan().steps()) {
            if (!(step instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep)) {
                continue;
            }
            if (!targetRecipe.equals(executeRecipeStep.recipe())) {
                continue;
            }
            for (OutputBinding output : executeRecipeStep.expectedOutputs()) {
                outputs.add(output);
            }
        }
        return outputs.toImmutable();
    }

    private static ItemStack normalizedStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        if (copy.getCount() <= 0) {
            copy.setCount(1);
        }
        return copy;
    }

    private static ItemStack firstOutputStack(
        @Nullable RecipeEntry<?> recipe,
        ImmutableList<InputBinding> inputs,
        @Nullable ImmutableList<OutputBinding> expectedOutputs
    ) {
        if (expectedOutputs != null) {
            for (OutputBinding output : expectedOutputs) {
                ItemStack stack = stackFromContentResource(output.resource());
                if (!stack.isEmpty()) {
                    stack.setCount((int) Math.max(1L, output.amount()));
                    return stack;
                }
            }
        }
        CraftingRecipe craftingRecipe = unwrapCraftingRecipe(recipe);
        if (craftingRecipe != null) {
            ItemStack result = RecipeHelper.getOutput(craftingRecipe);
            if (!result.isEmpty()) {
                return result.copy();
            }
        }
        for (InputBinding input : inputs) {
            ItemStack stack = stackFromInputContent(input.input());
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    //endregion

    //region slot driver

    private static final class CraftingTableLayout implements SlotLayout {
        private final SlotLayout.Index outputSlots;
        private final SlotLayout.Index inputSlots;
        private final SlotLayout.Index inventorySlots;
        private final SlotLayout.Index emptySlots;

        private CraftingTableLayout() {
            this.outputSlots = buildIndex(SlotLayout.Role.output, outputSlot, 1);
            this.inputSlots = buildIndex(SlotLayout.Role.input, firstInputSlot, inputSlotCount);
            this.inventorySlots = buildIndex(SlotLayout.Role.inventory, firstInventorySlot, inventorySlotCount);
            this.emptySlots = new MapSlotIndex(Collections.emptyMap());
        }

        @Override
        public SlotLayout.Index slots(SlotLayout.Role role) {
            if (role.equals(SlotLayout.Role.output)) {
                return outputSlots;
            }
            if (role.equals(SlotLayout.Role.input)) {
                return inputSlots;
            }
            if (role.equals(SlotLayout.Role.inventory)) {
                return inventorySlots;
            }
            return emptySlots;
        }
    }

    private static SlotLayout.Index buildIndex(SlotLayout.Role role, int firstSlot, int count) {
        if (count <= 0) {
            return new MapSlotIndex(Collections.emptyMap());
        }
        Map<SlotLayout.Key, SlotLayout.Reference> mapping = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            mapping.put(slotKey(role, i), new SlotLayout.Reference.Index(firstSlot + i));
        }
        return new MapSlotIndex(mapping);
    }

    private static SlotLayout.Key slotKey(SlotLayout.Role role, int order) {
        Namespace id = Namespace.of(interactionId.root(), interactionId.path() + "/" + role.id().path() + "/" + order);
        return new SlotLayout.Key(id, order);
    }

    private static final class MapSlotIndex implements SlotLayout.Index {
        private final Map<SlotLayout.Key, SlotLayout.Reference> slots;
        private final Set<SlotLayout.Key> keys;

        private MapSlotIndex(Map<SlotLayout.Key, SlotLayout.Reference> slots) {
            this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
            this.keys = Collections.unmodifiableSet(new LinkedHashSet<>(this.slots.keySet()));
        }

        @Override
        public SlotLayout.@Nullable Reference get(SlotLayout.Key key) {
            return slots.get(key);
        }

        @Override
        public Set<SlotLayout.Key> keys() {
            return keys;
        }

        @Override
        public Map<SlotLayout.Key, SlotLayout.Reference> mapping() {
            return slots;
        }
    }

    private record CraftingTableAccess() implements SlotAccess, SlotAccess.Interaction {
        @Override
        public State state() {
            return new State(interactionOpen(), interactionId, ExecutionSideRequirement.client);
        }

        @Override
        public ItemStack stack(SlotLayout.Reference slot) {
            CraftingMenu menu = craftingMenu();
            int slotId = slotIndex(slot);
            if (menu == null || slotId < 0 || slotId >= menu.slots.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = menu.getSlot(slotId).getItem();
            return stack.copy();
        }

        @Override
        public boolean click(Click click) {
            if (click == null) {
                return false;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.gameMode == null) {
                return false;
            }
            CraftingMenu menu = craftingMenu();
            if (menu == null) {
                return false;
            }
            int slotId = slotIndex(click.slot());
            if (slotId < 0 || slotId >= menu.slots.size()) {
                return false;
            }
            try {
                minecraft.gameMode.handleInventoryMouseClick(
                    menu.containerId,
                    slotId,
                    click.button(),
                    click.type(),
                    minecraft.player
                );
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override
        public ItemStack carried() {
            CraftingMenu menu = craftingMenu();
            if (menu == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = menu.getCarried();
            return stack.copy();
        }

        private static int slotIndex(SlotLayout.Reference slot) {
            if (slot == null) {
                return -1;
            }
            if (slot instanceof SlotLayout.Reference.Index index) {
                return index.index();
            }
            if (slot instanceof SlotLayout.Reference.Key key) {
                try {
                    return Integer.parseInt(key.key());
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
            return -1;
        }
    }

    private static final class CraftingTablePlacementResolver implements SlotFlow.PlacementResolver {
        @Override
        public ImmutableList<SlotFlow.Placement> resolve(
            SlotFlow.Context context,
            @Nullable RecipeEntry<?> recipe,
            ImmutableList<InputBinding> inputs
        ) {
            return toSlotPlacements(resolveGridPlacements(recipe, context, inputs));
        }
    }

    static ImmutableList<SlotFlow.Placement> resolvePlacementsForTesting(
        List<Ingredient> ingredients,
        int width,
        int height,
        SlotFlow.Context context,
        ImmutableList<InputBinding> inputs
    ) {
        return toSlotPlacements(
            resolveIngredientGridPlacements(ingredients, width, height, context, inputs, false, true)
        );
    }

    static ImmutableList<RecipePreviewSlot> resolvePreviewSlotsForTesting(
        List<Ingredient> ingredients,
        int width,
        int height,
        ImmutableList<InputBinding> inputs
    ) {
        return toPreviewSlots(resolveIngredientGridPlacements(ingredients, width, height, null, inputs, true, true));
    }

    public static ImmutableList<RecipePreviewSlot> resolvePreviewSlots(
        @Nullable RecipeEntry<?> recipe,
        ImmutableList<InputBinding> inputs
    ) {
        return toPreviewSlots(resolveGridPlacements(recipe, null, inputs, true, true));
    }

    private static ImmutableList<SlotFlow.Placement> toSlotPlacements(ImmutableList<GridPlacement> gridPlacements) {
        if (gridPlacements == null || gridPlacements.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<SlotFlow.Placement> placements = Lists.mutable.empty();
        for (GridPlacement placement : gridPlacements) {
            placements.add(
                new SlotFlow.Placement(
                    new SlotLayout.Reference.Index(placement.slotId()),
                    placement.template(),
                    placement.amountPerCraft(),
                    placement.input()
                )
            );
        }
        return placements.toImmutable();
    }

    private static ImmutableList<RecipePreviewSlot> toPreviewSlots(ImmutableList<GridPlacement> gridPlacements) {
        if (gridPlacements == null || gridPlacements.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<RecipePreviewSlot> previewSlots = Lists.mutable.empty();
        for (GridPlacement placement : gridPlacements) {
            int gridIndex = gridSlots.indexOf(placement.slotId());
            if (gridIndex < 0) {
                continue;
            }
            previewSlots.add(
                new RecipePreviewSlot(gridIndex, placement.template(), placement.amountPerCraft(), placement.input())
            );
        }
        return previewSlots.toImmutable();
    }

    //endregion

    //region requirement helpers

    private static ImmutableList<ItemRequirement> collapseInputRequirements(ImmutableList<InputBinding> inputs) {
        return collapseInputRequirements(inputs, 1L);
    }

    private static ImmutableList<ItemRequirement> collapseInputRequirements(
        ImmutableList<InputBinding> inputs,
        long multiplier
    ) {
        if (inputs.isEmpty()) {
            return Lists.immutable.empty();
        }
        long normalizedMultiplier = Math.max(1L, multiplier);
        LinkedHashMap<String, RequirementAccumulator> grouped = new LinkedHashMap<>();
        for (InputBinding input : inputs) {
            ItemStack stack = stackFromInputContent(input.input());
            if (stack.isEmpty()) {
                continue;
            }
            long amount = safeMultiply(Math.max(1L, input.amount()), normalizedMultiplier);
            String key = stack.getDescriptionId();
            RequirementAccumulator accumulator = grouped
                    .computeIfAbsent(key, ignored -> new RequirementAccumulator(stack.copy(), 0L));
            accumulator.required = safeAdd(accumulator.required, amount);
        }
        MutableList<ItemRequirement> out = Lists.mutable.empty();
        for (RequirementAccumulator accumulator : grouped.values()) {
            out.add(new ItemRequirement(accumulator.stack, accumulator.required));
        }
        return out.toImmutable();
    }

    private static ImmutableList<ItemRequirement> collapseOutputRequirements(ImmutableList<OutputBinding> outputs) {
        return collapseOutputRequirements(outputs, 1L);
    }

    private static ImmutableList<ItemRequirement> collapseOutputRequirements(
        ImmutableList<OutputBinding> outputs,
        long multiplier
    ) {
        if (outputs == null || outputs.isEmpty()) {
            return Lists.immutable.empty();
        }
        long normalizedMultiplier = Math.max(1L, multiplier);
        LinkedHashMap<String, RequirementAccumulator> grouped = new LinkedHashMap<>();
        for (OutputBinding output : outputs) {
            ItemStack stack = stackFromContentResource(output.resource());
            if (stack.isEmpty()) {
                continue;
            }
            long amount = safeMultiply(Math.max(1L, output.amount()), normalizedMultiplier);
            String key = stack.getDescriptionId();
            RequirementAccumulator accumulator = grouped
                    .computeIfAbsent(key, ignored -> new RequirementAccumulator(stack.copy(), 0L));
            accumulator.required = safeAdd(accumulator.required, amount);
        }
        MutableList<ItemRequirement> out = Lists.mutable.empty();
        for (RequirementAccumulator accumulator : grouped.values()) {
            out.add(new ItemRequirement(accumulator.stack, accumulator.required));
        }
        return out.toImmutable();
    }

    private static ItemStack stackFromInputContent(InputContent input) {
        if (input == null || input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (input instanceof StackContent<?, ?> stackContent) {
            ContentStack<?, ?> stack = stackContent.content();
            if (stack.notEmpty() && stack.isItem()) {
                return normalizedStack(stack.<Item, ItemStack>cast().value());
            }
            return ItemStack.EMPTY;
        }
        if (input instanceof StackListContent list) {
            return firstItemStack(list.stacks());
        }
        if (input instanceof TypedStackListContent<?, ?> typed) {
            return firstItemStack(typed.stacks());
        }
        if (input instanceof TagStackContent<?> tag) {
            for (Object value : tag.values()) {
                if (value instanceof Item item) {
                    return normalizedStack(new ItemStack(item));
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack firstItemStack(Iterable<? extends ContentStack<?, ?>> stacks) {
        for (ContentStack<?, ?> contentStack : stacks) {
            if (contentStack == null || !contentStack.isItem()) {
                continue;
            }
            ItemStack itemStack = contentStack.<Item, ItemStack>cast().value().copy();
            if (itemStack.isEmpty()) {
                continue;
            }
            itemStack.setCount(1);
            return itemStack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack stackFromContentResource(ContentResource resource) {
        if (resource instanceof ContentValue<?>) {
            return ItemStack.EMPTY;
        }
        if (!(resource instanceof ContentStack<?, ?> contentStack) || !contentStack.isItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = contentStack.<Item, ItemStack>cast().value().copy();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        stack.setCount(1);
        return stack;
    }

    private static InputContent materialFromResource(ContentResource resource, long amount) {
        if (resource instanceof ContentStack<?, ?> stack && !stack.isEmpty()) {
            return InputContent.fromStack(stack.copyWithAmount(Math.max(1L, amount)));
        }
        if (resource instanceof ContentValue<?> value) {
            return InputContent.fromValue(value);
        }
        return RecipeContent.empty();
    }

    private static String materialLabel(RecipeContent material) {
        String label = ExecutionOverlayContentHelper.label(material);
        return label.isBlank() ? "-" : label;
    }

    private static final class RequirementAccumulator {
        private final ItemStack stack;
        private long required;

        private RequirementAccumulator(ItemStack stack, long required) {
            this.stack = stack;
            this.required = Math.max(0L, required);
        }
    }

    private static final class TrackedMaterialRequirement {
        private final InputContent material;
        private long required;

        private TrackedMaterialRequirement(InputContent material) {
            this.material = material;
            this.required = Math.max(0L, material.amount());
        }
    }

    private record ItemRequirement(ItemStack template, long amount) {
        private ItemRequirement {
            template = template.copy();
            if (!template.isEmpty()) {
                template.setCount(1);
            }
            amount = Math.max(0L, amount);
        }
    }

    private record GridPlacement(int slotId, ItemStack template, long amountPerCraft, @Nullable InputContent input) {
        private GridPlacement {
            slotId = Math.max(0, slotId);
            template = template.copy();
            if (!template.isEmpty()) {
                template.setCount(1);
            }
            amountPerCraft = Math.max(1L, amountPerCraft);
        }
    }

    private record IngredientSlot(int slotId, Ingredient ingredient) {
        private IngredientSlot {
            slotId = Math.max(0, slotId);
        }
    }

    //endregion

    //region grid placement

    private static ImmutableList<GridPlacement> resolveGridPlacements(
        @Nullable RecipeEntry<?> recipe,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs
    ) {
        return resolveGridPlacements(recipe, context, inputs, false, true);
    }

    private static ImmutableList<GridPlacement> resolveGridPlacements(
        @Nullable RecipeEntry<?> recipe,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs,
        boolean ignoreAvailabilityForTemplateSelection
    ) {
        return resolveGridPlacements(recipe, context, inputs, ignoreAvailabilityForTemplateSelection, true);
    }

    private static ImmutableList<GridPlacement> resolveGridPlacements(
        @Nullable RecipeEntry<?> recipe,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs,
        boolean ignoreAvailabilityForTemplateSelection,
        boolean useBoundInputAmounts
    ) {
        ImmutableList<GridPlacement> fromRecipe = resolveRecipeGridPlacements(
            recipe,
            context,
            inputs,
            ignoreAvailabilityForTemplateSelection,
            useBoundInputAmounts
        );
        if (!fromRecipe.isEmpty()) {
            return fromRecipe;
        }
        return splitInputRequirementsToGridPlacements(collapseInputRequirements(inputs));
    }

    private static @Nullable CraftingRecipe unwrapCraftingRecipe(@Nullable RecipeEntry<?> recipe) {
        if (recipe == null) {
            return null;
        }
        Object raw = recipe.recipe();
        if (raw instanceof RecipeHolder<?> holder && holder.value() instanceof CraftingRecipe craftingRecipe) {
            return craftingRecipe;
        }
        if (raw instanceof CraftingRecipe craftingRecipe) {
            return craftingRecipe;
        }
        return null;
    }

    private static ImmutableList<GridPlacement> resolveRecipeGridPlacements(
        @Nullable RecipeEntry<?> recipe,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs,
        boolean ignoreAvailabilityForTemplateSelection,
        boolean useBoundInputAmounts
    ) {
        CraftingRecipe craftingRecipe = unwrapCraftingRecipe(recipe);
        if (craftingRecipe == null) {
            return Lists.immutable.empty();
        }
        return resolveRecipeGridPlacements(
            craftingRecipe,
            context,
            inputs,
            ignoreAvailabilityForTemplateSelection,
            useBoundInputAmounts
        );
    }

    private static ImmutableList<GridPlacement> resolveShapedPlacements(
        ShapedRecipe shapedRecipe,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs,
        boolean ignoreAvailabilityForTemplateSelection,
        boolean useBoundInputAmounts
    ) {
        return resolveIngredientSlotPlacements(
            resolveShapedIngredientSlots(
                shapedRecipe.getIngredients(),
                shapedRecipe.getWidth(),
                shapedRecipe.getHeight()
            ),
            context,
            inputs,
            ignoreAvailabilityForTemplateSelection,
            useBoundInputAmounts
        );
    }

    private static ImmutableList<GridPlacement> resolveIngredientPlacements(
        List<Ingredient> ingredients,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs,
        boolean ignoreAvailabilityForTemplateSelection,
        boolean useBoundInputAmounts
    ) {
        return resolveIngredientSlotPlacements(
            resolveIngredientSlots(ingredients, context),
            context,
            inputs,
            ignoreAvailabilityForTemplateSelection,
            useBoundInputAmounts
        );
    }

    private static ImmutableList<GridPlacement> resolveRecipeGridPlacements(
        CraftingRecipe craftingRecipe,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs,
        boolean ignoreAvailabilityForTemplateSelection,
        boolean useBoundInputAmounts
    ) {
        if (craftingRecipe instanceof ShapedRecipe shapedRecipe) {
            return resolveShapedPlacements(
                shapedRecipe,
                context,
                inputs,
                ignoreAvailabilityForTemplateSelection,
                useBoundInputAmounts
            );
        }
        return resolveIngredientPlacements(
            craftingRecipe.getIngredients(),
            context,
            inputs,
            ignoreAvailabilityForTemplateSelection,
            useBoundInputAmounts
        );
    }

    private static ImmutableList<GridPlacement> resolveIngredientGridPlacements(
        List<Ingredient> ingredients,
        int width,
        int height,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs,
        boolean ignoreAvailabilityForTemplateSelection,
        boolean useBoundInputAmounts
    ) {
        if (width > 1 || height > 1) {
            return resolveIngredientSlotPlacements(
                resolveShapedIngredientSlots(ingredients, width, height),
                context,
                inputs,
                ignoreAvailabilityForTemplateSelection,
                useBoundInputAmounts
            );
        }
        return resolveIngredientPlacements(
            ingredients,
            context,
            inputs,
            ignoreAvailabilityForTemplateSelection,
            useBoundInputAmounts
        );
    }

    private static ImmutableList<IngredientSlot> resolveShapedIngredientSlots(
        List<Ingredient> ingredients,
        int width,
        int height
    ) {
        if (ingredients == null
                || ingredients.isEmpty()
                || width <= 0
                || width > gridWidth
                || height <= 0
                || height > gridWidth) {
            return Lists.immutable.empty();
        }
        MutableList<IngredientSlot> out = Lists.mutable.empty();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int ingredientIndex = y * width + x;
                if (ingredientIndex < 0 || ingredientIndex >= ingredients.size()) {
                    continue;
                }
                Ingredient ingredient = ingredients.get(ingredientIndex);
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                int gridIndex = y * gridWidth + x;
                if (gridIndex >= gridSlots.size()) {
                    return Lists.immutable.empty();
                }
                out.add(new IngredientSlot(gridSlots.get(gridIndex), ingredient));
            }
        }
        return out.toImmutable();
    }

    private static ImmutableList<IngredientSlot> resolveIngredientSlots(
        List<Ingredient> ingredients,
        SlotFlow.@Nullable Context context
    ) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<Ingredient> normalizedIngredients = Lists.mutable.empty();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            normalizedIngredients.add(ingredient);
        }
        if (normalizedIngredients.isEmpty()) {
            return Lists.immutable.empty();
        }

        MutableList<IngredientSlot> out = Lists.mutable.empty();
        MutableList<Integer> remainingSlots = Lists.mutable.ofAll(gridSlots.castToList());
        MutableList<Integer> preservedSlots = Lists.mutable.empty();

        if (context != null) {
            // Shapeless recipes should preserve any currently valid grid arrangement instead of
            // forcing ingredients back into the first N slots. Otherwise a valid player/manual
            // placement can be misdetected as a conflict and get cleared unnecessarily.
            for (int slotId : gridSlots) {
                ItemStack stack = normalizedPlacementTemplate(slotStack(context, slotId));
                if (stack.isEmpty()) {
                    continue;
                }
                int ingredientIndex = findMatchingIngredientIndex(normalizedIngredients, stack);
                if (ingredientIndex < 0) {
                    continue;
                }
                preservedSlots.add(slotId);
                remainingSlots.remove(Integer.valueOf(slotId));
            }
        }

        MutableList<IngredientSlot> ordered = Lists.mutable.empty();
        for (Ingredient ingredient : normalizedIngredients) {
            int preservedSlot = findPreservedSlotForIngredient(context, preservedSlots, ingredient);
            if (preservedSlot >= 0) {
                preservedSlots.remove(Integer.valueOf(preservedSlot));
                ordered.add(new IngredientSlot(preservedSlot, ingredient));
                continue;
            }
            if (remainingSlots.isEmpty()) {
                return Lists.immutable.empty();
            }
            ordered.add(new IngredientSlot(remainingSlots.remove(0), ingredient));
        }
        return ordered.toImmutable();
    }

    private static int findMatchingIngredientIndex(MutableList<Ingredient> ingredients, ItemStack stack) {
        for (int index = 0; index < ingredients.size(); index++) {
            Ingredient ingredient = ingredients.get(index);
            if (ingredient != null && !ingredient.isEmpty() && ingredient.test(stack)) {
                return index;
            }
        }
        return -1;
    }

    private static int findPreservedSlotForIngredient(
        SlotFlow.@Nullable Context context,
        MutableList<Integer> preservedSlots,
        Ingredient ingredient
    ) {
        if (context == null || preservedSlots.isEmpty() || ingredient == null || ingredient.isEmpty()) {
            return -1;
        }
        for (int slotId : preservedSlots) {
            ItemStack stack = normalizedPlacementTemplate(slotStack(context, slotId));
            if (!stack.isEmpty() && ingredient.test(stack)) {
                return slotId;
            }
        }
        return -1;
    }

    private static ImmutableList<GridPlacement> resolveIngredientSlotPlacements(
        ImmutableList<IngredientSlot> ingredientSlots,
        SlotFlow.@Nullable Context context,
        ImmutableList<InputBinding> inputs,
        boolean ignoreAvailabilityForTemplateSelection,
        boolean useBoundInputAmounts
    ) {
        if (ingredientSlots.isEmpty()) {
            return Lists.immutable.empty();
        }
        ImmutableList<InputBinding> effectiveInputs = inputs;
        int[] matchedBindings = new int[ingredientSlots.size()];
        Arrays.fill(matchedBindings, -1);
        int[] bindingSlotCounts = null;
        if (useBoundInputAmounts) {
            if (matchesPositionalOneToOne(ingredientSlots, inputs)) {
                bindingSlotCounts = new int[inputs.size()];
                for (int slotIndex = 0; slotIndex < ingredientSlots.size(); slotIndex++) {
                    matchedBindings[slotIndex] = slotIndex;
                    bindingSlotCounts[slotIndex] = 1;
                }
            } else {
                effectiveInputs = mergeCompatibleInputBindings(inputs);
                bindingSlotCounts = new int[effectiveInputs.size()];
                for (int slotIndex = 0; slotIndex < ingredientSlots.size(); slotIndex++) {
                    int bindingIndex = selectMatchingInputBinding(
                        ingredientSlots.get(slotIndex).ingredient(),
                        effectiveInputs
                    );
                    if (bindingIndex < 0) {
                        return Lists.immutable.empty();
                    }
                    matchedBindings[slotIndex] = bindingIndex;
                    bindingSlotCounts[bindingIndex]++;
                }
            }
        }

        LinkedHashMap<String, Long> remainingTemplateCounts = ignoreAvailabilityForTemplateSelection
                ? new LinkedHashMap<>()
                : remainingTemplateCounts(ingredientSlots, context);
        MutableList<GridPlacement> placements = Lists.mutable.empty();
        for (int slotIndex = 0; slotIndex < ingredientSlots.size(); slotIndex++) {
            IngredientSlot ingredientSlot = ingredientSlots.get(slotIndex);
            InputContent matchedInput = useBoundInputAmounts
                    ? effectiveInputs.get(matchedBindings[slotIndex]).input()
                    : null;
            long targetAmount = useBoundInputAmounts && bindingSlotCounts != null
                    ? amountPerIngredientSlot(
                        effectiveInputs.get(matchedBindings[slotIndex]),
                        bindingSlotCounts[matchedBindings[slotIndex]]
                    )
                    : 1L;
            if (targetAmount <= 0L) {
                return Lists.immutable.empty();
            }
            ItemStack template = selectIngredientTemplate(
                ingredientSlot,
                matchedInput,
                targetAmount,
                context,
                ignoreAvailabilityForTemplateSelection,
                remainingTemplateCounts
            );
            if (template.isEmpty()) {
                return Lists.immutable.empty();
            }
            placements.add(new GridPlacement(ingredientSlot.slotId(), template, targetAmount, matchedInput));
        }
        return placements.toImmutable();
    }

    private static boolean matchesPositionalOneToOne(
        ImmutableList<IngredientSlot> ingredientSlots,
        ImmutableList<InputBinding> inputs
    ) {
        if (inputs.size() != ingredientSlots.size() || inputs.isEmpty()) {
            return false;
        }
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (!ingredientMatchesInput(ingredientSlots.get(i).ingredient(), inputs.get(i).input())) {
                return false;
            }
        }
        return true;
    }

    private static ImmutableList<InputBinding> mergeCompatibleInputBindings(ImmutableList<InputBinding> inputs) {
        if (inputs.size() <= 1) {
            return inputs;
        }
        MutableList<InputBinding> merged = Lists.mutable.empty();
        for (InputBinding binding : inputs) {
            int existingIdx = -1;
            for (int i = 0; i < merged.size(); i++) {
                InputContent prev = merged.get(i).input();
                if (prev.mergeable(binding.input()) || binding.input().mergeable(prev)) {
                    existingIdx = i;
                    break;
                }
            }
            if (existingIdx >= 0) {
                InputBinding prev = merged.get(existingIdx);
                long combined = safeAdd(Math.max(1L, prev.amount()), Math.max(1L, binding.amount()));
                merged.set(existingIdx, new InputBinding(prev.input(), combined));
            } else {
                merged.add(binding);
            }
        }
        return merged.toImmutable();
    }

    private static int selectMatchingInputBinding(Ingredient ingredient, ImmutableList<InputBinding> inputs) {
        int selected = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int index = 0; index < inputs.size(); index++) {
            int score = inputMatchScore(ingredient, inputs.get(index).input());
            if (score > bestScore) {
                bestScore = score;
                selected = index;
            }
        }
        return selected;
    }

    private static int inputMatchScore(Ingredient ingredient, InputContent input) {
        if (!ingredientMatchesInput(ingredient, input)) {
            return Integer.MIN_VALUE;
        }
        return switch (input) {
            case StackContent<?, ?> ignored -> 4;
            case TypedStackListContent<?, ?> ignored -> 3;
            case StackListContent ignored -> 3;
            case TagStackContent<?> ignored -> 2;
            default -> 1;
        };
    }

    private static boolean ingredientMatchesInput(Ingredient ingredient, InputContent input) {
        if (ingredient == null || ingredient.isEmpty() || input == null || input.isEmpty()) {
            return false;
        }
        for (ItemStack option : normalizedIngredientOptions(ingredient)) {
            if (input.satisfy(ContentStack.fromStack(option))) {
                return true;
            }
        }
        return false;
    }

    private static long amountPerIngredientSlot(InputBinding input, int slotCount) {
        if (slotCount <= 0) {
            return 0L;
        }
        long total = Math.max(1L, input.amount());
        if (slotCount == 1) {
            return total;
        }
        if (total % slotCount != 0L) {
            return 0L;
        }
        return total / slotCount;
    }

    private static LinkedHashMap<String, Long> remainingTemplateCounts(
        ImmutableList<IngredientSlot> ingredientSlots,
        SlotFlow.@Nullable Context context
    ) {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (IngredientSlot ingredientSlot : ingredientSlots) {
            for (ItemStack option : normalizedIngredientOptions(ingredientSlot.ingredient())) {
                counts.computeIfAbsent(stackKey(option), ignored -> availableTemplateCount(context, option));
            }
        }
        return counts;
    }

    private static long availableTemplateCount(SlotFlow.@Nullable Context context, ItemStack template) {
        if (context == null) {
            return count(template);
        }
        SlotLayout.Index inventorySlots = context.layout().slots(SlotLayout.Role.inventory);
        SlotLayout.Index inputSlots = context.layout().slots(SlotLayout.Role.input);
        boolean hasInventorySlots = !inventorySlots.keys().isEmpty();
        return hasInventorySlots ? countSlots(context, template, inventorySlots, inputSlots) : count(template);
    }

    private static ItemStack selectIngredientTemplate(
        IngredientSlot ingredientSlot,
        @Nullable InputContent matchedInput,
        long targetAmount,
        SlotFlow.@Nullable Context context,
        boolean ignoreAvailabilityForTemplateSelection,
        LinkedHashMap<String, Long> remainingTemplateCounts
    ) {
        ImmutableList<ItemStack> options = normalizedIngredientOptions(ingredientSlot.ingredient());
        if (options.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ImmutableList<ItemStack> preferredOptions = filterOptionsForMatchedInput(options, matchedInput);
        if (ignoreAvailabilityForTemplateSelection || context == null) {
            return selectBestEnvironmentTemplate(preferredOptions.isEmpty() ? options : preferredOptions);
        }

        ItemStack current = normalizedPlacementTemplate(slotStack(context, ingredientSlot.slotId()));
        if (!current.isEmpty()
                && ingredientSlot.ingredient().test(current)
                && remainingTemplateCounts.getOrDefault(stackKey(current), 0L) >= targetAmount) {
            reserveTemplateCount(remainingTemplateCounts, current, targetAmount);
            return current;
        }

        ItemStack selected = pickAvailableOption(preferredOptions, targetAmount, remainingTemplateCounts);
        if (selected.isEmpty() && preferredOptions.size() < options.size()) {
            selected = pickAvailableOption(options, targetAmount, remainingTemplateCounts);
        }
        if (selected.isEmpty()) {
            return ItemStack.EMPTY;
        }
        reserveTemplateCount(remainingTemplateCounts, selected, targetAmount);
        return selected;
    }

    private static ImmutableList<ItemStack> filterOptionsForMatchedInput(
        ImmutableList<ItemStack> options,
        @Nullable InputContent matchedInput
    ) {
        if (matchedInput == null || matchedInput.isEmpty()) {
            return options;
        }
        MutableList<ItemStack> filtered = Lists.mutable.empty();
        for (ItemStack option : options) {
            if (matchedInput.satisfy(ContentStack.fromStack(option))) {
                filtered.add(option);
            }
        }
        return filtered.isEmpty() ? options : filtered.toImmutable();
    }

    private static ItemStack pickAvailableOption(
        ImmutableList<ItemStack> options,
        long targetAmount,
        LinkedHashMap<String, Long> remainingTemplateCounts
    ) {
        ItemStack selected = ItemStack.EMPTY;
        long bestAvailable = -1L;
        for (ItemStack option : options) {
            long available = remainingTemplateCounts.getOrDefault(stackKey(option), 0L);
            if (available < targetAmount || available <= bestAvailable) {
                continue;
            }
            selected = option;
            bestAvailable = available;
        }
        return selected;
    }

    private static ItemStack selectBestEnvironmentTemplate(ImmutableList<ItemStack> options) {
        ItemStack selected = ItemStack.EMPTY;
        long bestAvailable = -1L;
        for (ItemStack option : options) {
            long available = count(option);
            if (available > bestAvailable) {
                selected = option;
                bestAvailable = available;
            }
        }
        return selected;
    }

    private static void reserveTemplateCount(
        LinkedHashMap<String, Long> remainingTemplateCounts,
        ItemStack template,
        long amount
    ) {
        String key = stackKey(template);
        long available = remainingTemplateCounts.getOrDefault(key, 0L);
        remainingTemplateCounts.put(key, Math.max(0L, available - Math.max(0L, amount)));
    }

    private static ImmutableList<ItemStack> normalizedIngredientOptions(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return Lists.immutable.empty();
        }
        LinkedHashMap<String, ItemStack> options = new LinkedHashMap<>();
        ItemStack[] stacks = ingredient.getItems();
        if (stacks == null || stacks.length == 0) {
            return Lists.immutable.empty();
        }
        for (ItemStack stack : stacks) {
            ItemStack normalized = normalizedPlacementTemplate(stack);
            if (normalized.isEmpty()) {
                continue;
            }
            options.putIfAbsent(stackKey(normalized), normalized);
        }
        return Lists.immutable.ofAll(options.values());
    }

    private static ItemStack slotStack(SlotFlow.Context context, int slotId) {
        return context.access().stack(new SlotLayout.Reference.Index(slotId));
    }

    private static String stackKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        ItemStack normalized = normalizedPlacementTemplate(stack);
        return normalized.getItem().builtInRegistryHolder().key().location() + "|" + normalized.getComponentsPatch();
    }

    private static ItemStack normalizedPlacementTemplate(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static long countSlots(SlotFlow.Context context, ItemStack template, SlotLayout.Index... indices) {
        if (template == null || template.isEmpty() || indices == null || indices.length == 0) {
            return 0L;
        }
        long total = 0L;
        for (SlotLayout.Index index : indices) {
            if (index == null || index.keys().isEmpty()) {
                continue;
            }
            ArrayList<SlotLayout.Key> orderedKeys = new ArrayList<>(index.keys());
            orderedKeys.sort(index.order());
            for (SlotLayout.Key key : orderedKeys) {
                SlotLayout.Reference ref = index.get(key);
                if (ref == null) {
                    continue;
                }
                ItemStack stack = context.access().stack(ref);
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                    total = safeAdd(total, stack.getCount());
                }
            }
        }
        return total;
    }

    private static ImmutableList<GridPlacement> splitInputRequirementsToGridPlacements(
        ImmutableList<ItemRequirement> requirements
    ) {
        if (requirements == null || requirements.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<GridPlacement> placements = Lists.mutable.empty();
        int slotIndex = 0;
        for (ItemRequirement requirement : requirements) {
            if (requirement.template().isEmpty() || requirement.amount() <= 0L) {
                continue;
            }
            if (requirement.amount() > gridSlots.size()) {
                return Lists.immutable.empty();
            }
            if (slotIndex + requirement.amount() > gridSlots.size()) {
                return Lists.immutable.empty();
            }
            for (long index = 0L; index < requirement.amount(); index++) {
                placements.add(new GridPlacement(gridSlots.get(slotIndex), requirement.template(), 1L, null));
                slotIndex++;
            }
        }
        return placements.toImmutable();
    }

    //endregion

    //region crafting helpers

    private static @Nullable CraftingMenu craftingMenu() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        if (minecraft.screen instanceof CraftingScreen craftingScreen) {
            return craftingScreen.getMenu();
        }
        if (minecraft.screen instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getMenu() instanceof CraftingMenu menu) {
            return menu;
        }
        if (minecraft.player != null && minecraft.player.containerMenu instanceof CraftingMenu menu) {
            return menu;
        }
        return null;
    }

    private static @Nullable Inventory inventory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        return minecraft.player.getInventory();
    }

    private static long count(ItemStack template) {
        Inventory inventory = inventory();
        if (inventory == null || template.isEmpty()) {
            return 0L;
        }
        return countInventory(inventory, template);
    }

    private static long countAccessible(Stock accessibleStock, ItemStack template) {
        if (template.isEmpty()) {
            return 0L;
        }
        ItemStack normalized = template.copy();
        normalized.setCount(1);
        return accessibleStock.countMatching(ContentStack.fromStack(normalized));
    }

    private static long countAccessible(ItemStack template) {
        if (template.isEmpty()) {
            return 0L;
        }
        long total = count(template);
        CraftingMenu menu = craftingMenu();
        if (menu == null) {
            return total;
        }
        for (int slotId : gridSlots) {
            if (slotId < 0 || slotId >= menu.slots.size()) {
                continue;
            }
            ItemStack stack = menu.getSlot(slotId).getItem();
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                total = safeAdd(total, stack.getCount());
            }
        }
        return total;
    }

    private static boolean interactionOpen() {
        if (craftingMenu() != null) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.screen instanceof CraftingScreen;
    }

    private static long countInventory(Inventory inventory, ItemStack template) {
        if (template.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                total = safeAdd(total, stack.getCount());
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                total = safeAdd(total, stack.getCount());
            }
        }
        return total;
    }

    //endregion

    //region math helpers

    private static long safeAdd(long first, long second) {
        if (first <= 0L && second <= 0L) {
            return 0L;
        }
        if (second > 0L && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        if (second < 0L && first < Long.MIN_VALUE - second) {
            return Long.MIN_VALUE;
        }
        return first + second;
    }

    private static long safeMultiply(long first, long second) {
        if (first <= 0L || second <= 0L) {
            return 0L;
        }
        if (first > Long.MAX_VALUE / second) {
            return Long.MAX_VALUE;
        }
        return first * second;
    }

    //endregion
}
