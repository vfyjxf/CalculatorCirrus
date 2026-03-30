package dev.vfyjxf.cirrus.ui.execution.workbench;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionRuntime;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageAccessibility;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageGuidance;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceRenderer;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferRequest;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferResult;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.ui.widget.execution.ExecutionOverlayController;
import dev.vfyjxf.cirrus.ui.widget.execution.ExecutionOverlayControlsWidget;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime-backed workbench executor used by the crafting-screen overlay.
 * It validates live inventory state on client and keeps a deterministic fallback for tests.
 */
public final class WorkbenchExecutionDemoController implements ExecutionOverlayController {

    //region runtime constants

    private static final int craftCycleCount = 20;
    private static final int logsPerCraftAmount = 2;
    private static final int sticksPerCraftAmount = 3;
    private static final int sticksFromLogPerCraftAmount = 4;
    private static final int planksFromLogsPerCraftAmount = 8;
    private static final int planksPerCraftAmount = planksFromLogsPerCraftAmount;
    private static final int logsForTargetCraftAmount = logsPerCraftAmount * craftCycleCount;
    private static final int totalStickAmount = sticksPerCraftAmount * craftCycleCount;
    private static final int stickCraftCycleCount = ceilDiv(totalStickAmount, sticksFromLogPerCraftAmount);
    private static final int logsForStickCraftAmount = stickCraftCycleCount;
    private static final int totalLogAmount = logsForTargetCraftAmount + logsForStickCraftAmount;
    private static final int totalPlankAmount = planksPerCraftAmount * craftCycleCount;

    private static final ItemStack oakLogStack = new ItemStack(Items.OAK_LOG);
    private static final ItemStack stickStack = new ItemStack(Items.STICK);
    private static final ItemStack oakPlankStack = new ItemStack(Items.OAK_PLANKS);
    private static final ValueType<Long> fallbackLogAmountType = new FallbackAmountType(Namespace.of("cirrus", "workbench_fallback_log"));
    private static final ValueType<Long> fallbackStickAmountType = new FallbackAmountType(Namespace.of("cirrus", "workbench_fallback_stick"));
    private static final ValueType<Long> fallbackPlankAmountType = new FallbackAmountType(Namespace.of("cirrus", "workbench_fallback_plank"));
    private static final ResourceLocation workbenchDatapackLogToPlanksRecipeId = ResourceLocation.fromNamespaceAndPath("calculator_cirrus", "workbench_oak_planks_demo");
    private static final ResourceLocation workbenchDatapackLogToSticksRecipeId = ResourceLocation.fromNamespaceAndPath("calculator_cirrus", "workbench_oak_sticks_demo");

    private static final DemoRecipeSystem fallbackRecipeSystem = new DemoRecipeSystem();
    private static final RecipeEntry<DemoRecipe> fallbackWorkbenchLogToSticksRecipe = RecipeEntry.of(
        fallbackRecipeSystem,
        new DemoRecipe("oak_sticks_from_log_batch")
    );
    private static final RecipeEntry<DemoRecipe> fallbackWorkbenchLogToPlanksRecipe = RecipeEntry.of(
        fallbackRecipeSystem,
        new DemoRecipe("oak_planks_from_log_and_stick_batch")
    );

    private static final ExecutionGuidance openWorkbenchGuidance = new ExecutionGuidance(
        Component.translatable("cirrus.ui.execution.workbench.manual.open.summary"),
        Component.translatable("cirrus.ui.execution.workbench.manual.open.detail"),
        Lists.immutable.of(
            Component.translatable("cirrus.ui.execution.workbench.manual.open.step1"),
            Component.translatable("cirrus.ui.execution.workbench.manual.open.step2")
        ),
        Sets.immutable.empty()
    );

    //endregion

    //region state

    private final SessionEnvironment environment;
    private final ContentResource oakLogResource;
    private final ContentResource stickResource;
    private final ContentResource oakPlankResource;
    private final InputContent oakLogInput;
    private final InputContent stickInput;
    private final InputContent oakPlankInput;
    private RecipeEntry<?> workbenchLogToSticksRecipe;
    private RecipeEntry<?> workbenchLogToPlanksRecipe;
    private final WorkbenchStorageApi storageApi;
    private final WorkbenchInteractionApi interactionApi;
    private final ExecutionRuntime runtime;
    private final boolean forceFallbackResources;
    private double overlayRatePerSecond = 5.0d;

    private ExecutionSession session;
    private boolean autoAdvance;
    private double autoAdvanceProgress;

    //endregion

    public WorkbenchExecutionDemoController() {
        this(resolveDefaultEnvironment(), false);
    }

    WorkbenchExecutionDemoController(long simulatedLogs, long simulatedPlanks, int simulatedEmptySlots) {
        this(new SimulatedEnvironment(simulatedLogs, totalStickAmount, simulatedPlanks, simulatedEmptySlots, true), false);
    }

    WorkbenchExecutionDemoController(long simulatedLogs, long simulatedPlanks, int simulatedEmptySlots, boolean forceFallbackResources) {
        this(new SimulatedEnvironment(simulatedLogs, totalStickAmount, simulatedPlanks, simulatedEmptySlots, true), forceFallbackResources);
    }

    WorkbenchExecutionDemoController(
        long simulatedLogs,
        long simulatedSticks,
        long simulatedPlanks,
        int simulatedEmptySlots,
        boolean simulatedInteractionOpen,
        boolean forceFallbackResources
    ) {
        this(
            new SimulatedEnvironment(
                simulatedLogs,
                simulatedSticks,
                simulatedPlanks,
                simulatedEmptySlots,
                simulatedInteractionOpen
            ),
            forceFallbackResources
        );
    }

    private WorkbenchExecutionDemoController(SessionEnvironment environment, boolean forceFallbackResources) {
        this.environment = environment;
        this.forceFallbackResources = forceFallbackResources;
        var fixtures = createRuntimeFixtures();
        oakLogResource = fixtures.logResource();
        stickResource = fixtures.stickResource();
        oakPlankResource = fixtures.plankResource();
        oakLogInput = fixtures.logInput();
        stickInput = fixtures.stickInput();
        oakPlankInput = fixtures.plankInput();
        storageApi = new WorkbenchStorageApi(environment, oakLogResource, stickResource, oakPlankResource);
        interactionApi = new WorkbenchInteractionApi(environment);
        runtime = new ExecutionRuntime(storageApi, interactionApi);
        session = rebuildSession();
    }

    //region access

    public ExecutionSession session() {
        return session;
    }

    @Override
    public ExecutionSession executionOverlaySession() {
        return session;
    }

    public boolean autoAdvance() {
        return autoAdvance;
    }

    @Override
    public boolean overlayAutoAdvanceEnabled() {
        return autoAdvance();
    }

    @Override
    public double overlayRatePerSecond() {
        return overlayRatePerSecond;
    }

    @Override
    public void overlaySetRatePerSecond(double value) {
        overlayRatePerSecond = ExecutionOverlayControlsWidget.clampRate(value);
    }

    @Override
    public ExecutionTelemetrySnapshot overlayTelemetrySnapshot() {
        return ExecutionTelemetrySnapshot.from(session, overlayRatePerSecond, autoAdvance);
    }

    @Override
    public ImmutableList<OverlayMaterialRequirement> overlayMaterialRequirements() {
        return collectRemainingMaterialRequirements(session);
    }

    @Override
    public @Nullable OverlayTargetProgress overlayTargetProgress() {
        if (session == null || session.plan() == null || session.plan().isEmpty()) {
            return null;
        }
        RecipeEntry<?> targetRecipe = session.plan().targetRecipe();
        if (targetRecipe == null) {
            return null;
        }
        ImmutableList<ItemRequirement> targetOutputs = collapseOutputRequirements(resolveTargetRecipeOutputs(targetRecipe));
        if (targetOutputs.isEmpty()) {
            targetOutputs = collapseOutputRequirements(resolveTargetRecipeExecuteOutputs(targetRecipe));
        }
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
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            long required = Math.max(0L, candidate.amount());
            long current = environment.count(stack);
            long missing = Math.max(0L, required - current);
            long score = missing * 1_000_000L + required;
            if (score > bestScore) {
                bestScore = score;
                selected = candidate;
            }
        }

        ItemStack displayStack = selected.template();
        if (displayStack == null || displayStack.isEmpty()) {
            return null;
        }
        long required = Math.max(0L, selected.amount());
        long current = environment.count(displayStack);
        return new OverlayTargetProgress(displayStack, required, current);
    }

    //endregion

    private ImmutableList<PlanAction.OutputBinding> resolveTargetRecipeOutputs(RecipeEntry<?> targetRecipe) {
        if (session == null || session.plan() == null || session.plan().steps().isEmpty() || targetRecipe == null) {
            return Lists.immutable.empty();
        }
        MutableList<PlanAction.OutputBinding> outputs = Lists.mutable.empty();
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

    private ImmutableList<PlanAction.OutputBinding> resolveTargetRecipeExecuteOutputs(RecipeEntry<?> targetRecipe) {
        if (session == null || session.plan() == null || session.plan().steps().isEmpty() || targetRecipe == null) {
            return Lists.immutable.empty();
        }
        MutableList<PlanAction.OutputBinding> outputs = Lists.mutable.empty();
        for (InteractionStep step : session.plan().steps()) {
            if (!(step instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep)) {
                continue;
            }
            if (!targetRecipe.equals(executeRecipeStep.recipe())) {
                continue;
            }
            long times = Math.max(1L, executeRecipeStep.times());
            for (PlanAction.OutputBinding output : executeRecipeStep.expectedOutputs()) {
                outputs.add(new PlanAction.OutputBinding(output.resource(), safeMultiply(Math.max(1L, output.amount()), times)));
            }
        }
        return outputs.toImmutable();
    }

    private ImmutableList<OverlayMaterialRequirement> collectRemainingMaterialRequirements(ExecutionSession sourceSession) {
        if (sourceSession == null || sourceSession.plan() == null || sourceSession.plan().steps().isEmpty()) {
            return Lists.immutable.empty();
        }
        LinkedHashMap<String, RequirementAccumulator> grouped = new LinkedHashMap<>();
        ImmutableList<InteractionStep> steps = sourceSession.plan().steps();
        int startIndex = Math.max(0, Math.min(sourceSession.nextStepIndex(), Math.max(0, steps.size() - 1)));
        for (int i = startIndex; i < steps.size(); i++) {
            if (!(steps.get(i) instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep)) {
                continue;
            }
            long stepTimes = Math.max(1L, executeRecipeStep.times());
            for (PlanAction.InputBinding input : executeRecipeStep.inputs()) {
                ItemStack stack = stackFromInputContent(input.input());
                if (stack.isEmpty()) {
                    continue;
                }
                long required = safeMultiply(Math.max(1L, input.amount()), stepTimes);
                String key = stack.getDescriptionId();
                RequirementAccumulator accumulator = grouped.computeIfAbsent(key, ignored -> new RequirementAccumulator(stack.copy(), 0L));
                accumulator.required = safeAdd(accumulator.required, required);
            }
        }
        if (grouped.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<OverlayMaterialRequirement> out = Lists.mutable.empty();
        for (RequirementAccumulator accumulator : grouped.values()) {
            ItemStack display = accumulator.stack.copy();
            display.setCount(1);
            long available = environment.count(display);
            long missing = Math.max(0L, accumulator.required - available);
            out.add(new OverlayMaterialRequirement(display, accumulator.required, available, missing));
        }
        var sorted = out.toSortedList(
            Comparator.comparingLong(OverlayMaterialRequirement::missing)
                .reversed()
                .thenComparingLong(OverlayMaterialRequirement::required)
                .reversed()
                .thenComparing(requirement -> requirement.stack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
        );
        return sorted.toImmutable();
    }

    //region control

    public int advanceSteps(int requestedSteps) {
        if (requestedSteps <= 0) {
            return 0;
        }

        int advanced = 0;
        while (advanced < requestedSteps && !session.completed()) {
            ExecutionSession nextSession = session.paused()
                ? resumeIfReady(session)
                : runtime.advance(session);
            if (nextSession == session) {
                break;
            }

            session = nextSession;
            advanced++;
            if (session.paused()) {
                break;
            }
        }

        if (session.completed()) {
            autoAdvance = false;
            autoAdvanceProgress = 0.0d;
        }
        return advanced;
    }

    public boolean toggleAutoAdvance() {
        if (session.completed()) {
            autoAdvance = false;
            return false;
        }
        autoAdvance = !autoAdvance;
        autoAdvanceProgress = 0.0d;
        return autoAdvance;
    }

    public void reset() {
        session = rebuildSession();
        autoAdvance = false;
        autoAdvanceProgress = 0.0d;
    }

    private ExecutionSession rebuildSession() {
        RecipeChain recipeChain = resolveWorkbenchRecipeChain();
        workbenchLogToSticksRecipe = recipeChain.logToSticks();
        workbenchLogToPlanksRecipe = recipeChain.logToPlanks();
        var plan = buildPlan();
        return ExecutionSession.start(plan, buildFlowRoot(plan));
    }

    @Override
    public void overlayAdvanceSteps(int steps) {
        advanceSteps(steps);
    }

    @Override
    public void overlayToggleAutoAdvance() {
        toggleAutoAdvance();
    }

    @Override
    public void overlayReset() {
        reset();
    }

    public void tick() {
        if (!autoAdvance || session.completed() || overlayRatePerSecond <= 0.0d) {
            if (session.completed()) {
                autoAdvance = false;
                autoAdvanceProgress = 0.0d;
            }
            return;
        }
        autoAdvanceProgress += overlayRatePerSecond / 20.0d;
        int stepsToAdvance = (int) Math.floor(autoAdvanceProgress);
        if (stepsToAdvance <= 0) {
            return;
        }
        autoAdvanceProgress -= stepsToAdvance;
        while (stepsToAdvance-- > 0 && autoAdvance && !session.completed()) {
            if (advanceSteps(1) <= 0) {
                break;
            }
        }
    }

    //endregion

    //region pause gates

    private ExecutionSession resumeIfReady(ExecutionSession pausedSession) {
        InteractionStep step = pausedSession.currentStep();
        if (step == null || !canResumeStep(step)) {
            return pausedSession;
        }
        if (step instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep) {
            var result = environment.performCraft(
                executeRecipeStep.recipe(),
                executeRecipeStep.times(),
                executeRecipeStep.inputs(),
                executeRecipeStep.expectedOutputs()
            );
            if (!(result instanceof InteractionPerformResult.Performed)) {
                return pausedSession;
            }
        }
        return runtime.resume(pausedSession);
    }

    private boolean canResumeStep(InteractionStep step) {
        return switch (step) {
            case InteractionStep.EnsureAccessibleStep ensureAccessibleStep -> canResumeEnsureAccessible(ensureAccessibleStep);
            case InteractionStep.WithdrawResourceStep withdrawResourceStep -> storageApi.simulateExtractFirstAvailable(
                new StorageTransferRequest(withdrawResourceStep.resource(), withdrawResourceStep.requestedAmount())
            ).completed();
            case InteractionStep.EnsureCapacityStep ensureCapacityStep -> storageApi.simulateInsertFirstAvailable(
                new StorageTransferRequest(ensureCapacityStep.resource(), ensureCapacityStep.amount())
            ).completed();
            case InteractionStep.StoreOutputsStep storeOutputsStep -> storageApi.simulateInsertFirstAvailable(
                new StorageTransferRequest(storeOutputsStep.resource(), storeOutputsStep.amount())
            ).completed();
            case InteractionStep.OpenInteractionStep ignored -> environment.interactionOpen();
            case InteractionStep.ManualCheckpointStep ignored -> environment.interactionOpen();
            case InteractionStep.ExecuteRecipeStep executeRecipeStep -> environment.canAutoCraft(
                executeRecipeStep.recipe(),
                executeRecipeStep.times(),
                executeRecipeStep.inputs(),
                executeRecipeStep.expectedOutputs()
            );
            default -> true;
        };
    }

    private boolean canResumeEnsureAccessible(InteractionStep.EnsureAccessibleStep step) {
        if (step.target() instanceof InteractionStep.AccessTarget.Resource resourceTarget) {
            return storageApi.simulateExtractFirstAvailable(
                new StorageTransferRequest(resourceTarget.resource(), step.requestedAmount())
            ).completed();
        }
        return true;
    }

    private static long safeAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
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

    private static int ceilDiv(int numerator, int denominator) {
        if (numerator <= 0 || denominator <= 0) {
            return 0;
        }
        return (numerator + denominator - 1) / denominator;
    }

    private static ImmutableList<ItemRequirement> collapseInputRequirements(ImmutableList<PlanAction.InputBinding> inputs) {
        return collapseInputRequirements(inputs, 1L);
    }

    private static ImmutableList<ItemRequirement> collapseInputRequirements(
        ImmutableList<PlanAction.InputBinding> inputs,
        long multiplier
    ) {
        if (inputs == null || inputs.isEmpty()) {
            return Lists.immutable.empty();
        }
        long normalizedMultiplier = Math.max(1L, multiplier);
        LinkedHashMap<String, RequirementAccumulator> grouped = new LinkedHashMap<>();
        for (PlanAction.InputBinding input : inputs) {
            ItemStack stack = stackFromInputContent(input.input());
            if (stack.isEmpty()) {
                continue;
            }
            long amount = safeMultiply(Math.max(1L, input.amount()), normalizedMultiplier);
            String key = stack.getDescriptionId();
            RequirementAccumulator accumulator = grouped.computeIfAbsent(key, ignored -> new RequirementAccumulator(stack.copy(), 0L));
            accumulator.required = safeAdd(accumulator.required, amount);
        }
        MutableList<ItemRequirement> out = Lists.mutable.empty();
        for (RequirementAccumulator accumulator : grouped.values()) {
            out.add(new ItemRequirement(accumulator.stack, accumulator.required));
        }
        return out.toImmutable();
    }

    private static ImmutableList<ItemRequirement> collapseOutputRequirements(ImmutableList<PlanAction.OutputBinding> outputs) {
        return collapseOutputRequirements(outputs, 1L);
    }

    private static ImmutableList<ItemRequirement> collapseOutputRequirements(
        ImmutableList<PlanAction.OutputBinding> outputs,
        long multiplier
    ) {
        if (outputs == null || outputs.isEmpty()) {
            return Lists.immutable.empty();
        }
        long normalizedMultiplier = Math.max(1L, multiplier);
        LinkedHashMap<String, RequirementAccumulator> grouped = new LinkedHashMap<>();
        for (PlanAction.OutputBinding output : outputs) {
            ItemStack stack = stackFromContentResource(output.resource());
            if (stack.isEmpty()) {
                continue;
            }
            long amount = safeMultiply(Math.max(1L, output.amount()), normalizedMultiplier);
            String key = stack.getDescriptionId();
            RequirementAccumulator accumulator = grouped.computeIfAbsent(key, ignored -> new RequirementAccumulator(stack.copy(), 0L));
            accumulator.required = safeAdd(accumulator.required, amount);
        }
        MutableList<ItemRequirement> out = Lists.mutable.empty();
        for (RequirementAccumulator accumulator : grouped.values()) {
            out.add(new ItemRequirement(accumulator.stack, accumulator.required));
        }
        return out.toImmutable();
    }

    private static ItemStack stackFromInputContent(InputContent input) {
        if (input.isEnumerable()) {
            for (ContentVariant variant : input.enumerate()) {
                if (variant instanceof ContentStack<?, ?> contentStack && contentStack.isItem()) {
                    ItemStack itemStack = contentStack.<Item, ItemStack>cast().value().copy();
                    if (!itemStack.isEmpty()) {
                        itemStack.setCount(1);
                        return itemStack;
                    }
                }
                if (variant instanceof ContentValue<?> value) {
                    ItemStack fallbackStack = fallbackStackFromValueType(value.type());
                    if (!fallbackStack.isEmpty()) {
                        return fallbackStack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack stackFromContentResource(ContentResource resource) {
        if (resource instanceof ContentValue<?> value) {
            return fallbackStackFromValueType(value.type());
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

    private static ItemStack fallbackStackFromValueType(ValueType<?> valueType) {
        if (valueType == null) {
            return ItemStack.EMPTY;
        }
        if (valueType == fallbackLogAmountType || valueType.equals(fallbackLogAmountType)) {
            return oakLogStack.copy();
        }
        if (valueType == fallbackStickAmountType || valueType.equals(fallbackStickAmountType)) {
            return stickStack.copy();
        }
        if (valueType == fallbackPlankAmountType || valueType.equals(fallbackPlankAmountType)) {
            return oakPlankStack.copy();
        }
        return ItemStack.EMPTY;
    }

    private static final class RequirementAccumulator {
        private final ItemStack stack;
        private long required;

        private RequirementAccumulator(ItemStack stack, long required) {
            this.stack = stack;
            this.required = Math.max(0L, required);
        }
    }

    private record ItemRequirement(ItemStack template, long amount) {
        private ItemRequirement {
            template = template == null ? ItemStack.EMPTY : template.copy();
            if (!template.isEmpty()) {
                template.setCount(1);
            }
            amount = Math.max(0L, amount);
        }
    }

    private record GridPlacement(int slotId, ItemStack template, long amountPerCraft) {
        private GridPlacement {
            slotId = Math.max(1, Math.min(9, slotId));
            template = template == null ? ItemStack.EMPTY : template.copy();
            if (!template.isEmpty()) {
                template.setCount(1);
            }
            amountPerCraft = Math.max(1L, amountPerCraft);
        }
    }

    //endregion

    //region plan fixtures

    private InteractionExecutionPlan buildPlan() {
        MutableList<InteractionStep> steps = Lists.mutable.empty();
        ImmutableList<PlanAction.InputBinding> stickCraftInputs = Lists.immutable.of(
            new PlanAction.InputBinding(oakLogInput, 1)
        );
        ImmutableList<PlanAction.OutputBinding> stickCraftPerCraftOutputs = Lists.immutable.of(
            new PlanAction.OutputBinding(stickResource, sticksFromLogPerCraftAmount)
        );
        long totalStickCraftOutput = safeMultiply(sticksFromLogPerCraftAmount, stickCraftCycleCount);
        ImmutableList<PlanAction.OutputBinding> stickCraftBatchOutputs = Lists.immutable.of(
            new PlanAction.OutputBinding(stickResource, totalStickCraftOutput)
        );
        ImmutableList<PlanAction.InputBinding> logCraftInputs = Lists.immutable.of(
            new PlanAction.InputBinding(oakLogInput, logsPerCraftAmount),
            new PlanAction.InputBinding(stickInput, sticksPerCraftAmount)
        );
        ImmutableList<PlanAction.OutputBinding> logCraftPerCraftOutputs = Lists.immutable.of(
            new PlanAction.OutputBinding(oakPlankResource, planksFromLogsPerCraftAmount)
        );
        long totalLogCraftOutput = safeMultiply(planksFromLogsPerCraftAmount, craftCycleCount);
        ImmutableList<PlanAction.OutputBinding> logCraftBatchOutputs = Lists.immutable.of(
            new PlanAction.OutputBinding(oakPlankResource, totalLogCraftOutput)
        );

        steps.add(
            new InteractionStep.EnsureAccessibleStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.storageAccess,
                new InteractionStep.AccessTarget.Resource(oakLogResource),
                totalLogAmount
            )
        );
        steps.add(
            new InteractionStep.WithdrawResourceStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.inventoryTransfer,
                oakLogResource,
                totalLogAmount
            )
        );
        steps.add(
            new InteractionStep.EnsureCapacityStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.inventoryCapacity,
                stickResource,
                totalStickCraftOutput
            )
        );
        steps.add(
            new InteractionStep.OpenInteractionStep(
                ExecutionSideRequirement.player,
                ExecutionCapability.interactionAccess,
                workbenchLogToSticksRecipe,
                1,
                "minecraft:crafting_table"
            )
        );
        steps.add(
            new InteractionStep.ManualCheckpointStep(
                ExecutionSideRequirement.player,
                ExecutionCapability.manualCheckpoint,
                openWorkbenchGuidance
            )
        );

        // Batched execution: one stage per recipe, repeat count carried by ExecuteRecipeStep#times.
        steps.add(
            new InteractionStep.EnsureAccessibleStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.storageAccess,
                new InteractionStep.AccessTarget.Resource(oakLogResource),
                logsForStickCraftAmount
            )
        );
        steps.add(
            new InteractionStep.EnsureCapacityStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.inventoryCapacity,
                stickResource,
                totalStickCraftOutput
            )
        );
        steps.add(
            new InteractionStep.PrepareInputsStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.inventoryTransfer,
                workbenchLogToSticksRecipe,
                stickCraftInputs
            )
        );
        steps.add(
            new InteractionStep.ExecuteRecipeStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.interactionPerform,
                workbenchLogToSticksRecipe,
                stickCraftCycleCount,
                stickCraftInputs,
                stickCraftPerCraftOutputs
            )
        );
        steps.add(
            new InteractionStep.CollectOutputsStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.inventoryTransfer,
                workbenchLogToSticksRecipe,
                stickCraftBatchOutputs
            )
        );

        steps.add(
            new InteractionStep.EnsureAccessibleStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.storageAccess,
                new InteractionStep.AccessTarget.Resource(oakLogResource),
                logsForTargetCraftAmount
            )
        );
        steps.add(
            new InteractionStep.EnsureAccessibleStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.storageAccess,
                new InteractionStep.AccessTarget.Resource(stickResource),
                totalStickAmount
            )
        );
        steps.add(
            new InteractionStep.EnsureCapacityStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.inventoryCapacity,
                oakPlankResource,
                totalLogCraftOutput
            )
        );
        steps.add(
            new InteractionStep.PrepareInputsStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.inventoryTransfer,
                workbenchLogToPlanksRecipe,
                logCraftInputs
            )
        );
        steps.add(
            new InteractionStep.ExecuteRecipeStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.interactionPerform,
                workbenchLogToPlanksRecipe,
                craftCycleCount,
                logCraftInputs,
                logCraftPerCraftOutputs
            )
        );
        steps.add(
            new InteractionStep.CollectOutputsStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.inventoryTransfer,
                workbenchLogToPlanksRecipe,
                logCraftBatchOutputs
            )
        );

        steps.add(
            new InteractionStep.EnsureAccessibleStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.outputStorage,
                new InteractionStep.AccessTarget.Resource(oakPlankResource),
                totalPlankAmount
            )
        );
        steps.add(
            new InteractionStep.StoreOutputsStep(
                ExecutionSideRequirement.client,
                ExecutionCapability.outputStorage,
                oakPlankResource,
                totalPlankAmount
            )
        );
        return new InteractionExecutionPlan(
            workbenchLogToPlanksRecipe,
            craftCycleCount,
            steps.toImmutable()
        );
    }

    private ExecutionFlowNode buildFlowRoot(InteractionExecutionPlan plan) {
        InteractionStep stickBatchEnsureLogAccess = plan.steps().get(4);
        InteractionStep stickBatchEnsureCapacity = plan.steps().get(5);
        InteractionStep stickBatchPrepare = plan.steps().get(6);
        InteractionStep stickBatchExecute = plan.steps().get(7);
        InteractionStep stickBatchCollect = plan.steps().get(8);

        InteractionStep logBatchEnsureLogAccess = plan.steps().get(9);
        InteractionStep logBatchEnsureStickAccess = plan.steps().get(10);
        InteractionStep logBatchEnsureCapacity = plan.steps().get(11);
        InteractionStep logBatchPrepare = plan.steps().get(12);
        InteractionStep logBatchExecute = plan.steps().get(13);
        InteractionStep logBatchCollect = plan.steps().get(14);

        int cleanupStartIndex = 15;
        ImmutableList<InteractionStep> stickCraftSteps = Lists.immutable.of(
            stickBatchEnsureLogAccess,
            stickBatchEnsureCapacity,
            stickBatchPrepare,
            stickBatchExecute,
            stickBatchCollect
        );
        ImmutableList<InteractionStep> plankCraftSteps = Lists.immutable.of(
            logBatchEnsureLogAccess,
            logBatchEnsureStickAccess,
            logBatchEnsureCapacity,
            logBatchPrepare,
            logBatchExecute,
            logBatchCollect
        );
        return ExecutionFlowNode.pending(
            "workbench-target",
            Component.literal("Oak Planks x" + totalPlankAmount),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "gather",
                    Component.literal("Gather Materials"),
                    Lists.immutable.of(plan.steps().get(0), plan.steps().get(1)),
                    ExecutionGuidance.simple(Component.literal("Collect logs from inventory/storage.")),
                    Lists.immutable.empty()
                ),
                ExecutionFlowNode.pending(
                    "setup",
                    Component.literal("Workbench Setup"),
                    Lists.immutable.of(plan.steps().get(2), plan.steps().get(3)),
                    ExecutionGuidance.simple(Component.literal("Open crafting table and verify capacity.")),
                    Lists.immutable.empty()
                ),
                ExecutionFlowNode.pending(
                    "craft",
                    Component.literal("Craft Batches"),
                    stickCraftSteps.newWithAll(plankCraftSteps).toImmutable(),
                    ExecutionGuidance.simple(Component.literal("Batch craft " + craftCycleCount + " times.")),
                    Lists.immutable.of(
                        ExecutionFlowNode.pending(
                            "craft-stick-batch",
                            Component.literal("Log -> Sticks x" + stickCraftCycleCount),
                            stickCraftSteps,
                            ExecutionGuidance.simple(Component.literal(
                                "1 log -> " + sticksFromLogPerCraftAmount + " sticks"
                            )),
                            Lists.immutable.empty()
                        ),
                        ExecutionFlowNode.pending(
                            "craft-log-batch",
                            Component.literal("Log -> Planks x" + craftCycleCount),
                            plankCraftSteps,
                            ExecutionGuidance.simple(Component.literal(
                                logsPerCraftAmount + " log + " + sticksPerCraftAmount + " stick -> " + planksFromLogsPerCraftAmount + " planks"
                            )),
                            Lists.immutable.empty()
                        )
                    )
                ),
                ExecutionFlowNode.pending(
                    "cleanup",
                    Component.literal("Store Outputs"),
                    Lists.immutable.of(plan.steps().get(cleanupStartIndex), plan.steps().get(cleanupStartIndex + 1)),
                    ExecutionGuidance.simple(Component.literal("Move final planks to storage.")),
                    Lists.immutable.empty()
                )
            )
        );
    }

    //endregion

    //region fixture creation

    private RuntimeFixtures createRuntimeFixtures() {
        if (forceFallbackResources) {
            ContentResource logResource = ContentValue.fromValue(fallbackLogAmountType, 1L);
            ContentResource stickResource = ContentValue.fromValue(fallbackStickAmountType, 2L);
            ContentResource plankResource = ContentValue.fromValue(fallbackPlankAmountType, 2L);
            InputContent logInput = InputContent.fromValue(fallbackLogAmountType, 1L);
            InputContent stickInput = InputContent.fromValue(fallbackStickAmountType, 1L);
            InputContent plankInput = InputContent.fromValue(fallbackPlankAmountType, 1L);
            return new RuntimeFixtures(logResource, stickResource, plankResource, logInput, stickInput, plankInput);
        }
        try {
            ContentResource logResource = ContentStack.fromStack(oakLogStack);
            ContentResource stickResource = ContentStack.fromStack(stickStack);
            ContentResource plankResource = ContentStack.fromStack(oakPlankStack);
            InputContent logInput = InputContent.fromItemStack(oakLogStack, 1);
            InputContent stickInput = InputContent.fromItemStack(stickStack, 1);
            InputContent plankInput = InputContent.fromItemStack(oakPlankStack, 1);
            return new RuntimeFixtures(logResource, stickResource, plankResource, logInput, stickInput, plankInput);
        } catch (Throwable ignored) {
            ContentResource logResource = ContentValue.fromValue(fallbackLogAmountType, 1L);
            ContentResource stickResource = ContentValue.fromValue(fallbackStickAmountType, 2L);
            ContentResource plankResource = ContentValue.fromValue(fallbackPlankAmountType, 2L);
            InputContent logInput = InputContent.fromValue(fallbackLogAmountType, 1L);
            InputContent stickInput = InputContent.fromValue(fallbackStickAmountType, 1L);
            InputContent plankInput = InputContent.fromValue(fallbackPlankAmountType, 1L);
            return new RuntimeFixtures(logResource, stickResource, plankResource, logInput, stickInput, plankInput);
        }
    }

    private RecipeChain resolveWorkbenchRecipeChain() {
        RecipeEntry<?> logToSticks = null;
        RecipeEntry<?> logToPlanks = null;
        if (!forceFallbackResources) {
            logToSticks = resolveDatapackWorkbenchRecipe(workbenchDatapackLogToSticksRecipeId);
            logToPlanks = resolveDatapackWorkbenchRecipe(workbenchDatapackLogToPlanksRecipeId);
        }
        return new RecipeChain(
            logToSticks == null ? fallbackWorkbenchLogToSticksRecipe : logToSticks,
            logToPlanks == null ? fallbackWorkbenchLogToPlanksRecipe : logToPlanks
        );
    }

    private static @Nullable RecipeEntry<?> resolveDatapackWorkbenchRecipe(ResourceLocation recipeId) {
        RecipeHolder<CraftingRecipe> holder = lookupCraftingRecipe(recipeId);
        if (holder == null) {
            return null;
        }
        RecipeSystem<RecipeHolder<CraftingRecipe>> system = craftingRecipeSystem();
        return RecipeEntry.of(system, holder);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable RecipeHolder<CraftingRecipe> lookupCraftingRecipe(ResourceLocation recipeId) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) {
                return null;
            }
            return (RecipeHolder<CraftingRecipe>) minecraft.level.getRecipeManager()
                                                                .byKey(recipeId)
                                                                .filter(recipeHolder -> recipeHolder.value().getType() == RecipeType.CRAFTING)
                                                                .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static RecipeSystem<RecipeHolder<CraftingRecipe>> craftingRecipeSystem() {
        VanillaRecipeSystem<CraftingRecipe> system = VanillaRecipeSystem.byRecipeType(RecipeType.CRAFTING);
        if (system != null) {
            return system;
        }
        return new CraftingRecipeSystem();
    }

    private record RuntimeFixtures(
        ContentResource logResource,
        ContentResource stickResource,
        ContentResource plankResource,
        InputContent logInput,
        InputContent stickInput,
        InputContent plankInput
    ) {
    }

    private record RecipeChain(
        RecipeEntry<?> logToSticks,
        RecipeEntry<?> logToPlanks
    ) {
    }

    //endregion

    //region environment

    private static SessionEnvironment resolveDefaultEnvironment() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                return new LiveEnvironment();
            }
        } catch (Throwable ignored) {
        }
        return new SimulatedEnvironment(totalLogAmount, totalStickAmount, 0L, 6, true);
    }

    private sealed interface SessionEnvironment permits LiveEnvironment, SimulatedEnvironment {
        long count(ItemStack template);

        long insertCapacity(ItemStack template);

        boolean interactionOpen();

        StorageAccessibility inventoryAccessibility();

        boolean canAutoCraft(
            RecipeEntry<?> recipe,
            int times,
            ImmutableList<PlanAction.InputBinding> inputs,
            ImmutableList<PlanAction.OutputBinding> outputs
        );

        InteractionPerformResult performCraft(
            RecipeEntry<?> recipe,
            int times,
            ImmutableList<PlanAction.InputBinding> inputs,
            ImmutableList<PlanAction.OutputBinding> outputs
        );
    }

    private static final class LiveEnvironment implements SessionEnvironment {
        @Override
        public long count(ItemStack template) {
            Inventory inventory = inventory();
            if (inventory == null || template.isEmpty()) {
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

        @Override
        public long insertCapacity(ItemStack template) {
            Inventory inventory = inventory();
            if (inventory == null || template.isEmpty()) {
                return 0L;
            }
            long capacity = 0L;
            int maxStackSize = Math.max(1, template.getMaxStackSize());
            for (ItemStack stack : inventory.items) {
                capacity = safeAdd(capacity, slotInsertCapacity(stack, template, maxStackSize));
            }
            for (ItemStack stack : inventory.offhand) {
                capacity = safeAdd(capacity, slotInsertCapacity(stack, template, maxStackSize));
            }
            return capacity;
        }

        @Override
        public boolean interactionOpen() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft != null
                && (minecraft.screen instanceof CraftingScreen
                || minecraft.player != null && minecraft.player.containerMenu instanceof CraftingMenu);
        }

        @Override
        public StorageAccessibility inventoryAccessibility() {
            if (inventory() == null) {
                return StorageAccessibility.inaccessible(StorageGuidance.simple(
                    Component.translatable("cirrus.ui.execution.workbench.warning.inventory_unavailable")
                ));
            }
            return StorageAccessibility.accessible;
        }

        @Override
        public boolean canAutoCraft(
            RecipeEntry<?> recipe,
            int times,
            ImmutableList<PlanAction.InputBinding> inputs,
            ImmutableList<PlanAction.OutputBinding> outputs
        ) {
            if (!interactionOpen()) {
                return false;
            }
            int batchTimes = Math.max(1, times);
            ImmutableList<ItemRequirement> inputRequirements = collapseInputRequirements(inputs, batchTimes);
            ImmutableList<ItemRequirement> outputRequirements = collapseOutputRequirements(outputs, batchTimes);
            if (inputRequirements.isEmpty() || outputRequirements.isEmpty()) {
                return false;
            }
            ImmutableList<GridPlacement> gridPlacements = resolveGridPlacements(recipe, this, inputs);
            if (gridPlacements.isEmpty()) {
                return false;
            }
            for (GridPlacement placement : gridPlacements) {
                long targetCount = safeMultiply(placement.amountPerCraft(), batchTimes);
                if (targetCount > placement.template().getMaxStackSize()) {
                    return false;
                }
            }
            for (ItemRequirement inputRequirement : inputRequirements) {
                if (count(inputRequirement.template()) < inputRequirement.amount()) {
                    return false;
                }
            }
            for (ItemRequirement outputRequirement : outputRequirements) {
                if (insertCapacity(outputRequirement.template()) < outputRequirement.amount()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public InteractionPerformResult performCraft(
            RecipeEntry<?> recipe,
            int times,
            ImmutableList<PlanAction.InputBinding> inputs,
            ImmutableList<PlanAction.OutputBinding> outputs
        ) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft == null ? null : minecraft.player;
            MultiPlayerGameMode gameMode = minecraft == null ? null : minecraft.gameMode;
            if (player == null || gameMode == null || !(player.containerMenu instanceof CraftingMenu menu)) {
                return new InteractionPerformResult.Failed(openWorkbenchGuidance);
            }
            int batchTimes = Math.max(1, times);
            ImmutableList<ItemRequirement> inputRequirements = collapseInputRequirements(inputs, batchTimes);
            ImmutableList<ItemRequirement> outputRequirements = collapseOutputRequirements(outputs, batchTimes);
            if (inputRequirements.isEmpty() || outputRequirements.isEmpty()) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.result_waiting.summary",
                    "cirrus.ui.execution.workbench.warning.result_waiting.detail"
                ));
            }
            ImmutableList<GridPlacement> gridPlacements = resolveGridPlacements(recipe, this, inputs);
            if (gridPlacements.isEmpty()) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.grid_blocked.summary",
                    "cirrus.ui.execution.workbench.warning.grid_blocked.detail"
                ));
            }
            if (!menu.getCarried().isEmpty()) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.cursor_occupied.summary",
                    "cirrus.ui.execution.workbench.warning.cursor_occupied.detail"
                ));
            }

            for (ItemRequirement inputRequirement : inputRequirements) {
                long availableInput = count(inputRequirement.template());
                if (availableInput < inputRequirement.amount()) {
                    return new InteractionPerformResult.Failed(
                        toGuidance(missingStockGuidance(inputRequirement.template(), availableInput, inputRequirement.amount()))
                    );
                }
            }

            for (ItemRequirement outputRequirement : outputRequirements) {
                long capacity = insertCapacity(outputRequirement.template());
                if (capacity < outputRequirement.amount()) {
                    return new InteractionPerformResult.Failed(
                        toGuidance(missingCapacityGuidance(outputRequirement.template(), capacity, outputRequirement.amount()))
                    );
                }
            }

            if (!canAutoCraft(recipe, batchTimes, inputs, outputs)) {
                return new InteractionPerformResult.Failed(clickFailedGuidance());
            }

            if (!clearCraftGrid(gameMode, player, menu)) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.grid_blocked.summary",
                    "cirrus.ui.execution.workbench.warning.grid_blocked.detail"
                ));
            }

            for (GridPlacement placement : gridPlacements) {
                long targetCount = safeMultiply(placement.amountPerCraft(), batchTimes);
                if (!placeInputRequirementToSlot(gameMode, player, menu, placement, targetCount)) {
                    return new InteractionPerformResult.Failed(clickFailedGuidance());
                }
            }

            ItemRequirement primaryOutput = resolvePrimaryOutputRequirement(menu, outputRequirements, batchTimes);
            if (primaryOutput.template().isEmpty() || primaryOutput.amount() <= 0L) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.result_waiting.summary",
                    "cirrus.ui.execution.workbench.warning.result_waiting.detail"
                ));
            }
            long outputBefore = count(primaryOutput.template());
            if (!collectCraftResult(gameMode, player, menu, primaryOutput.template(), primaryOutput.amount(), outputBefore, batchTimes)) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.result_waiting.summary",
                    "cirrus.ui.execution.workbench.warning.result_waiting.detail"
                ));
            }
            return new InteractionPerformResult.Performed();
        }

        private static boolean clearCraftGrid(MultiPlayerGameMode gameMode, LocalPlayer player, CraftingMenu menu) {
            for (int slotId = 1; slotId <= 9; slotId++) {
                ItemStack stack = menu.getSlot(slotId).getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                if (!click(gameMode, player, menu, slotId, 0, ClickType.QUICK_MOVE)) {
                    return false;
                }
                if (!menu.getSlot(slotId).getItem().isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private static boolean placeInputRequirementToSlot(
            MultiPlayerGameMode gameMode,
            LocalPlayer player,
            CraftingMenu menu,
            GridPlacement placement,
            long targetCount
        ) {
            if (placement.template().isEmpty() || targetCount <= 0L) {
                return false;
            }
            if (targetCount > placement.template().getMaxStackSize()) {
                return false;
            }
            int gridSlot = placement.slotId();
            Slot slot = menu.getSlot(gridSlot);
            ItemStack slotStack = slot.getItem();
            if (!slotStack.isEmpty() && !ItemStack.isSameItemSameComponents(slotStack, placement.template())) {
                return false;
            }
            while (menu.getSlot(gridSlot).getItem().getCount() < targetCount) {
                int sourceSlot = findInventorySlot(menu, placement.template());
                if (sourceSlot < 0) {
                    return false;
                }
                if (!click(gameMode, player, menu, sourceSlot, 0, ClickType.PICKUP)) {
                    return false;
                }
                if (menu.getCarried().isEmpty()) {
                    return false;
                }
                if (!click(gameMode, player, menu, gridSlot, 1, ClickType.PICKUP)) {
                    restoreCarryToInventory(gameMode, player, menu, sourceSlot);
                    return false;
                }
                if (!menu.getCarried().isEmpty() && !click(gameMode, player, menu, sourceSlot, 0, ClickType.PICKUP)) {
                    restoreCarryToInventory(gameMode, player, menu, sourceSlot);
                    return false;
                }
                ItemStack after = menu.getSlot(gridSlot).getItem();
                if (after.isEmpty() || !ItemStack.isSameItemSameComponents(after, placement.template())) {
                    return false;
                }
            }
            return true;
        }

        private static long slotInsertCapacity(ItemStack stack, ItemStack template, int maxStackSize) {
            if (stack.isEmpty()) {
                return maxStackSize;
            }
            if (!ItemStack.isSameItemSameComponents(stack, template)) {
                return 0L;
            }
            int remaining = Math.max(0, stack.getMaxStackSize() - stack.getCount());
            return remaining;
        }

        private static ExecutionGuidance toGuidance(StorageGuidance guidance) {
            return new ExecutionGuidance(guidance.summary(), guidance.detail(), Lists.immutable.empty(), Sets.immutable.empty());
        }

        private static ExecutionGuidance clickFailedGuidance() {
            return guidance(
                "cirrus.ui.execution.workbench.warning.click_failed.summary",
                "cirrus.ui.execution.workbench.warning.click_failed.detail"
            );
        }

        private static ExecutionGuidance guidance(String summaryKey, String detailKey) {
            return new ExecutionGuidance(
                Component.translatable(summaryKey),
                Component.translatable(detailKey),
                Lists.immutable.empty(),
                Sets.immutable.empty()
            );
        }

        private static int findInventorySlot(CraftingMenu menu, ItemStack template) {
            int first = 10;
            for (int slotId = first; slotId < menu.slots.size(); slotId++) {
                Slot slot = menu.getSlot(slotId);
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                    return slotId;
                }
            }
            return -1;
        }

        private static ImmutableList<GridPlacement> resolveGridPlacements(
            @Nullable RecipeEntry<?> recipe,
            SessionEnvironment environment,
            ImmutableList<PlanAction.InputBinding> inputs
        ) {
            ImmutableList<GridPlacement> fromRecipe = resolveRecipeGridPlacements(recipe, environment);
            if (!fromRecipe.isEmpty()) {
                return fromRecipe;
            }
            return splitInputRequirementsToGridPlacements(collapseInputRequirements(inputs));
        }

        private static ImmutableList<GridPlacement> resolveRecipeGridPlacements(
            @Nullable RecipeEntry<?> recipe,
            SessionEnvironment environment
        ) {
            if (recipe == null || !(recipe.recipe() instanceof RecipeHolder<?> holder)) {
                return Lists.immutable.empty();
            }
            if (!(holder.value() instanceof CraftingRecipe craftingRecipe)) {
                return Lists.immutable.empty();
            }
            if (craftingRecipe instanceof ShapedRecipe shapedRecipe) {
                return resolveShapedPlacements(shapedRecipe, environment);
            }
            return resolveIngredientPlacements(craftingRecipe.getIngredients(), environment);
        }

        private static ImmutableList<GridPlacement> resolveShapedPlacements(ShapedRecipe shapedRecipe, SessionEnvironment environment) {
            int width = shapedRecipe.getWidth();
            int height = shapedRecipe.getHeight();
            if (width <= 0 || width > 3 || height <= 0 || height > 3) {
                return Lists.immutable.empty();
            }
            List<Ingredient> ingredients = shapedRecipe.getIngredients();
            MutableList<GridPlacement> placements = Lists.mutable.empty();
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
                    ItemStack template = selectIngredientTemplate(ingredient, environment);
                    if (template.isEmpty()) {
                        return Lists.immutable.empty();
                    }
                    int slotId = 1 + x + y * 3;
                    placements.add(new GridPlacement(slotId, template, 1L));
                }
            }
            return placements.toImmutable();
        }

        private static ImmutableList<GridPlacement> resolveIngredientPlacements(
            List<Ingredient> ingredients,
            SessionEnvironment environment
        ) {
            if (ingredients == null || ingredients.isEmpty()) {
                return Lists.immutable.empty();
            }
            MutableList<GridPlacement> placements = Lists.mutable.empty();
            int slotId = 1;
            for (Ingredient ingredient : ingredients) {
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                if (slotId > 9) {
                    return Lists.immutable.empty();
                }
                ItemStack template = selectIngredientTemplate(ingredient, environment);
                if (template.isEmpty()) {
                    return Lists.immutable.empty();
                }
                placements.add(new GridPlacement(slotId, template, 1L));
                slotId++;
            }
            return placements.toImmutable();
        }

        private static ItemStack selectIngredientTemplate(Ingredient ingredient, SessionEnvironment environment) {
            if (ingredient == null || ingredient.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack[] options = ingredient.getItems();
            if (options == null || options.length == 0) {
                return ItemStack.EMPTY;
            }
            ItemStack selected = ItemStack.EMPTY;
            long bestAvailable = -1L;
            for (ItemStack option : options) {
                if (option == null || option.isEmpty()) {
                    continue;
                }
                ItemStack template = option.copy();
                template.setCount(1);
                long available = environment.count(template);
                if (available > bestAvailable) {
                    selected = template;
                    bestAvailable = available;
                }
            }
            return selected;
        }

        private static ImmutableList<GridPlacement> splitInputRequirementsToGridPlacements(
            ImmutableList<ItemRequirement> requirements
        ) {
            if (requirements == null || requirements.isEmpty()) {
                return Lists.immutable.empty();
            }
            MutableList<GridPlacement> placements = Lists.mutable.empty();
            int slotId = 1;
            for (ItemRequirement requirement : requirements) {
                if (requirement.template().isEmpty() || requirement.amount() <= 0L) {
                    continue;
                }
                if (requirement.amount() > 9L) {
                    return Lists.immutable.empty();
                }
                if (slotId + requirement.amount() - 1L > 9L) {
                    return Lists.immutable.empty();
                }
                for (long index = 0L; index < requirement.amount(); index++) {
                    placements.add(new GridPlacement(slotId, requirement.template(), 1L));
                    slotId++;
                }
            }
            return placements.toImmutable();
        }

        private static boolean hasCraftResult(CraftingMenu menu, ItemStack outputTemplate) {
            ItemStack resultStack = menu.getSlot(0).getItem();
            return !resultStack.isEmpty() && ItemStack.isSameItemSameComponents(resultStack, outputTemplate);
        }

        private static ItemRequirement resolvePrimaryOutputRequirement(
            CraftingMenu menu,
            ImmutableList<ItemRequirement> outputRequirements,
            int batchTimes
        ) {
            ItemStack resultStack = menu.getSlot(0).getItem();
            if (!resultStack.isEmpty()) {
                ItemStack resultTemplate = resultStack.copy();
                resultTemplate.setCount(1);
                for (ItemRequirement requirement : outputRequirements) {
                    if (!requirement.template().isEmpty()
                        && ItemStack.isSameItemSameComponents(requirement.template(), resultTemplate)
                    ) {
                        return requirement;
                    }
                }
                long fallbackAmount = safeMultiply(Math.max(1L, resultStack.getCount()), Math.max(1L, batchTimes));
                return new ItemRequirement(resultTemplate, fallbackAmount);
            }
            return outputRequirements.isEmpty()
                ? new ItemRequirement(ItemStack.EMPTY, 0L)
                : outputRequirements.getFirst();
        }

        private static boolean collectCraftResult(
            MultiPlayerGameMode gameMode,
            LocalPlayer player,
            CraftingMenu menu,
            ItemStack outputTemplate,
            long outputAmount,
            long outputBefore,
            int batchTimes
        ) {
            long target = safeAdd(outputBefore, Math.max(1L, outputAmount));
            int maxAttempts = Math.max(1, Math.min(96, Math.max(batchTimes, 1) * 3));
            boolean clicked = false;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                long current = countInventory(player.getInventory(), outputTemplate);
                if (current >= target) {
                    return true;
                }
                if (!hasCraftResult(menu, outputTemplate) && clicked) {
                    // Result slot already drained after at least one successful pickup dispatch.
                    return true;
                }
                if (!click(gameMode, player, menu, 0, 0, ClickType.QUICK_MOVE)) {
                    return false;
                }
                clicked = true;
            }
            return countInventory(player.getInventory(), outputTemplate) >= target;
        }

        private static boolean click(
            MultiPlayerGameMode gameMode,
            LocalPlayer player,
            CraftingMenu menu,
            int slotId,
            int mouseButton,
            ClickType clickType
        ) {
            try {
                gameMode.handleInventoryMouseClick(menu.containerId, slotId, mouseButton, clickType, player);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static void restoreCarryToInventory(MultiPlayerGameMode gameMode, LocalPlayer player, CraftingMenu menu, int preferredSlot) {
            if (menu.getCarried().isEmpty()) {
                return;
            }
            if (preferredSlot >= 10 && preferredSlot < menu.slots.size()) {
                click(gameMode, player, menu, preferredSlot, 0, ClickType.PICKUP);
            }
            if (menu.getCarried().isEmpty()) {
                return;
            }
            for (int slotId = 10; slotId < menu.slots.size(); slotId++) {
                if (click(gameMode, player, menu, slotId, 0, ClickType.PICKUP) && menu.getCarried().isEmpty()) {
                    return;
                }
            }
        }

        private static @Nullable Inventory inventory() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return null;
            }
            return minecraft.player.getInventory();
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
    }

    private static final class SimulatedEnvironment implements SessionEnvironment {
        private long logCount;
        private long stickCount;
        private long plankCount;
        private final int emptySlots;
        private final boolean interactionOpen;

        private SimulatedEnvironment(long logCount, long stickCount, long plankCount, int emptySlots, boolean interactionOpen) {
            this.logCount = Math.max(0L, logCount);
            this.stickCount = Math.max(0L, stickCount);
            this.plankCount = Math.max(0L, plankCount);
            this.emptySlots = Math.max(0, emptySlots);
            this.interactionOpen = interactionOpen;
        }

        @Override
        public long count(ItemStack template) {
            if (ItemStack.isSameItemSameComponents(template, oakLogStack)) {
                return logCount;
            }
            if (ItemStack.isSameItemSameComponents(template, stickStack)) {
                return stickCount;
            }
            if (ItemStack.isSameItemSameComponents(template, oakPlankStack)) {
                return plankCount;
            }
            return 0L;
        }

        @Override
        public long insertCapacity(ItemStack template) {
            int maxStackSize = Math.max(1, template.getMaxStackSize());
            long existing = count(template);
            long partialRoom = existing <= 0L ? 0L : maxStackSize - (existing % maxStackSize == 0L ? maxStackSize : existing % maxStackSize);
            long emptyRoom = (long) emptySlots * maxStackSize;
            return safeAdd(partialRoom, emptyRoom);
        }

        @Override
        public boolean interactionOpen() {
            return interactionOpen;
        }

        @Override
        public StorageAccessibility inventoryAccessibility() {
            return StorageAccessibility.accessible;
        }

        @Override
        public boolean canAutoCraft(
            RecipeEntry<?> recipe,
            int times,
            ImmutableList<PlanAction.InputBinding> inputs,
            ImmutableList<PlanAction.OutputBinding> outputs
        ) {
            if (!interactionOpen) {
                return false;
            }
            int batchTimes = Math.max(1, times);
            ImmutableList<ItemRequirement> inputRequirements = collapseInputRequirements(inputs, batchTimes);
            ImmutableList<ItemRequirement> outputRequirements = collapseOutputRequirements(outputs, batchTimes);
            if (inputRequirements.isEmpty() || outputRequirements.isEmpty()) {
                return false;
            }
            for (ItemRequirement inputRequirement : inputRequirements) {
                if (count(inputRequirement.template()) < inputRequirement.amount()) {
                    return false;
                }
            }
            for (ItemRequirement outputRequirement : outputRequirements) {
                if (insertCapacity(outputRequirement.template()) < outputRequirement.amount()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public InteractionPerformResult performCraft(
            RecipeEntry<?> recipe,
            int times,
            ImmutableList<PlanAction.InputBinding> inputs,
            ImmutableList<PlanAction.OutputBinding> outputs
        ) {
            if (!interactionOpen) {
                return new InteractionPerformResult.Failed(openWorkbenchGuidance);
            }
            int batchTimes = Math.max(1, times);
            ImmutableList<ItemRequirement> inputRequirements = collapseInputRequirements(inputs, batchTimes);
            ImmutableList<ItemRequirement> outputRequirements = collapseOutputRequirements(outputs, batchTimes);
            if (inputRequirements.isEmpty() || outputRequirements.isEmpty()) {
                return new InteractionPerformResult.Failed(openWorkbenchGuidance);
            }
            if (!canAutoCraft(recipe, batchTimes, inputs, outputs)) {
                for (ItemRequirement inputRequirement : inputRequirements) {
                    long available = count(inputRequirement.template());
                    if (available >= inputRequirement.amount()) {
                        continue;
                    }
                    return new InteractionPerformResult.Failed(new ExecutionGuidance(
                        missingStockGuidance(inputRequirement.template(), available, inputRequirement.amount()).summary(),
                        missingStockGuidance(inputRequirement.template(), available, inputRequirement.amount()).detail(),
                        Lists.immutable.empty(),
                        Sets.immutable.empty()
                    ));
                }
                for (ItemRequirement outputRequirement : outputRequirements) {
                    long capacity = insertCapacity(outputRequirement.template());
                    if (capacity >= outputRequirement.amount()) {
                        continue;
                    }
                    return new InteractionPerformResult.Failed(new ExecutionGuidance(
                        missingCapacityGuidance(outputRequirement.template(), capacity, outputRequirement.amount()).summary(),
                        missingCapacityGuidance(outputRequirement.template(), capacity, outputRequirement.amount()).detail(),
                        Lists.immutable.empty(),
                        Sets.immutable.empty()
                    ));
                }
            }
            for (ItemRequirement inputRequirement : inputRequirements) {
                adjustTrackedCount(inputRequirement.template(), -inputRequirement.amount());
            }
            for (ItemRequirement outputRequirement : outputRequirements) {
                adjustTrackedCount(outputRequirement.template(), outputRequirement.amount());
            }
            return new InteractionPerformResult.Performed();
        }

        private void adjustTrackedCount(ItemStack template, long delta) {
            if (ItemStack.isSameItemSameComponents(template, oakLogStack)) {
                if (delta >= 0L) {
                    logCount = safeAdd(logCount, delta);
                } else {
                    logCount = Math.max(0L, logCount - Math.abs(delta));
                }
                return;
            }
            if (ItemStack.isSameItemSameComponents(template, stickStack)) {
                if (delta >= 0L) {
                    stickCount = safeAdd(stickCount, delta);
                } else {
                    stickCount = Math.max(0L, stickCount - Math.abs(delta));
                }
                return;
            }
            if (ItemStack.isSameItemSameComponents(template, oakPlankStack)) {
                if (delta >= 0L) {
                    plankCount = safeAdd(plankCount, delta);
                } else {
                    plankCount = Math.max(0L, plankCount - Math.abs(delta));
                }
            }
        }
    }

    //endregion

    //region storage

    private static final class WorkbenchStorageApi implements StorageApi {
        private final PlayerInventoryStorageSource playerInventorySource;
        private final ImmutableList<PlayerInventoryStorageSource> sources;

        private WorkbenchStorageApi(
            SessionEnvironment environment,
            ContentResource logResource,
            ContentResource stickResource,
            ContentResource plankResource
        ) {
            playerInventorySource = new PlayerInventoryStorageSource(environment, logResource, stickResource, plankResource);
            sources = Lists.immutable.of(playerInventorySource);
        }

        @Override
        public void addStorage(StorageSource source) {
            throw new UnsupportedOperationException("Workbench runtime owns storage sources");
        }

        @Override
        public void addStorage(TagKey<StorageSource> tagKey, StorageSource... sources) {
            throw new UnsupportedOperationException("Workbench runtime owns storage sources");
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> storage() {
            return sources;
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> getTaggedStorage(TagKey<StorageSource> tagKey) {
            return Lists.immutable.empty();
        }

        @Override
        public @Nullable StorageSource findStorage(Object identifier) {
            return "player_inventory".equals(identifier) ? playerInventorySource : null;
        }

        @Override
        public <T extends StorageSource> @Nullable StorageSourceRenderer<T> findRenderer(StorageSourceType<T> type) {
            return null;
        }
    }

    private record PlayerInventoryStorageSource(
        SessionEnvironment environment,
        ContentResource logResource,
        ContentResource stickResource,
        ContentResource plankResource
    ) implements StorageSource {
        @SuppressWarnings("unchecked")
        private static final StorageSourceType<PlayerInventoryStorageSource> type =
            new StorageSourceType<>(Namespace.of("cirrus", "workbench_player_inventory"), (Codec<PlayerInventoryStorageSource>) null);

        @Override
        public StorageSourceType<?> type() {
            return type;
        }

        @Override
        public Object identifier() {
            return "player_inventory";
        }

        @Override
        public RichIterable<? extends ContentVariant> contents() {
            MutableList<ContentVariant> variants = Lists.mutable.empty();
            long logAmount = environment.count(oakLogStack);
            if (logAmount > 0) {
                variants.add(logResourceAmount(logAmount));
            }
            long stickAmount = environment.count(stickStack);
            if (stickAmount > 0) {
                variants.add(stickResourceAmount(stickAmount));
            }
            long plankAmount = environment.count(oakPlankStack);
            if (plankAmount > 0) {
                variants.add(plankResourceAmount(plankAmount));
            }
            return variants.toImmutable();
        }

        @Override
        public boolean canProvide() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public StorageAccessibility accessibility() {
            return environment.inventoryAccessibility();
        }

        @Override
        public StorageTransferResult simulateExtract(StorageTransferRequest request) {
            if (!accessibility().accessible()) {
                return StorageTransferResult.none(this, request, accessibility().guidance());
            }
            ItemStack template = itemTemplate(request.resource(), logResource, stickResource, plankResource);
            if (template == null) {
                return StorageTransferResult.none(this, request, null);
            }
            long available = environment.count(template);
            long transferred = Math.min(available, request.amount());
            if (transferred >= request.amount()) {
                return StorageTransferResult.transferred(this, request, transferred);
            }
            return new StorageTransferResult(this, request, transferred, missingStockGuidance(template, available, request.amount()));
        }

        @Override
        public StorageTransferResult simulateInsert(StorageTransferRequest request) {
            if (!accessibility().accessible()) {
                return StorageTransferResult.none(this, request, accessibility().guidance());
            }
            ItemStack template = itemTemplate(request.resource(), logResource, stickResource, plankResource);
            if (template == null) {
                return StorageTransferResult.none(this, request, null);
            }
            long capacity = environment.insertCapacity(template);
            long transferred = Math.min(capacity, request.amount());
            if (transferred >= request.amount()) {
                return StorageTransferResult.transferred(this, request, transferred);
            }
            return new StorageTransferResult(this, request, transferred, missingCapacityGuidance(template, capacity, request.amount()));
        }

        private ContentVariant logResourceAmount(long amount) {
            return logResource instanceof ContentStack<?, ?> stack
                ? stack.copyWithAmount(amount)
                : ContentValue.fromValue(fallbackLogAmountType, amount);
        }

        private ContentVariant plankResourceAmount(long amount) {
            return plankResource instanceof ContentStack<?, ?> stack
                ? stack.copyWithAmount(amount)
                : ContentValue.fromValue(fallbackPlankAmountType, amount);
        }

        private ContentVariant stickResourceAmount(long amount) {
            return stickResource instanceof ContentStack<?, ?> stack
                ? stack.copyWithAmount(amount)
                : ContentValue.fromValue(fallbackStickAmountType, amount);
        }
    }

    //endregion

    //region storage helpers

    private static @Nullable ItemStack itemTemplate(
        ContentResource resource,
        ContentResource logResource,
        ContentResource stickResource,
        ContentResource plankResource
    ) {
        if (!(resource instanceof ContentStack<?, ?> contentStack) || !contentStack.isItem()) {
            boolean matchesLog = resource.matchesResource(logResource);
            boolean matchesStick = resource.matchesResource(stickResource);
            boolean matchesPlank = resource.matchesResource(plankResource);
            if (matchesLog && !matchesStick && !matchesPlank) {
                return oakLogStack.copy();
            }
            if (matchesStick && !matchesLog && !matchesPlank) {
                return stickStack.copy();
            }
            if (matchesPlank && !matchesLog && !matchesStick) {
                return oakPlankStack.copy();
            }
            if (resource == logResource) {
                return oakLogStack.copy();
            }
            if (resource == stickResource) {
                return stickStack.copy();
            }
            if (resource == plankResource) {
                return oakPlankStack.copy();
            }
            return null;
        }
        ItemStack value = contentStack.<Item, ItemStack>cast().value();
        ItemStack copy = value.copy();
        copy.setCount(1);
        return copy;
    }

    private static StorageGuidance missingStockGuidance(ItemStack template, long available, long required) {
        return new StorageGuidance(
            Component.translatable("cirrus.ui.execution.workbench.warning.missing_stock.summary", template.getHoverName(), available, required),
            Component.translatable("cirrus.ui.execution.workbench.warning.missing_stock.detail", Math.max(0L, required - available))
        );
    }

    private static StorageGuidance missingCapacityGuidance(ItemStack template, long capacity, long required) {
        return new StorageGuidance(
            Component.translatable("cirrus.ui.execution.workbench.warning.missing_capacity.summary", template.getHoverName(), capacity, required),
            Component.translatable("cirrus.ui.execution.workbench.warning.missing_capacity.detail", Math.max(0L, required - capacity))
        );
    }

    //endregion

    //region interaction

    private static final class WorkbenchInteractionApi implements InteractionApi {
        private final SessionEnvironment environment;

        private WorkbenchInteractionApi(SessionEnvironment environment) {
            this.environment = environment;
        }

        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return new InteractionProbeResult.Supported("minecraft:crafting_table", false, environment.interactionOpen());
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return new InteractionGuideResult(openWorkbenchGuidance);
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            if (!environment.interactionOpen()) {
                return new InteractionPerformResult.Failed(openWorkbenchGuidance);
            }
            return environment.performCraft(context.recipe(), context.times(), context.inputs(), context.expectedOutputs());
        }
    }

    //endregion

    //region helper types

    private record FallbackAmountType(Namespace id) implements ValueType<Long> {
        @Override
        public Namespace typeId() {
            return id;
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long first, Long second) {
            return first.equals(second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    }

    private record DemoRecipe(String id) {
    }

    private static final class DemoRecipeSystem implements RecipeSystem<DemoRecipe> {
        private static final DemoRecipe logToSticksRecipe = new DemoRecipe("oak_sticks_from_log_batch");
        private static final DemoRecipe logToPlanksRecipe = new DemoRecipe("oak_planks_from_log_and_stick_batch");
        private static final Map<String, DemoRecipe> recipesById = Map.of(
            logToSticksRecipe.id(), logToSticksRecipe,
            logToPlanksRecipe.id(), logToPlanksRecipe
        );

        @Override
        public Namespace systemId() {
            return Namespace.of("cirrus", "workbench_runtime");
        }

        @Override
        public Class<?> basicType() {
            return DemoRecipe.class;
        }

        @Override
        public ResourceLocation getId(DemoRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("cirrus", recipe.id());
        }

        @Override
        public DemoRecipe findRecipe(String uniqueId) {
            return recipesById.get(uniqueId);
        }

        @Override
        public DemoRecipe findRecipe(ResourceLocation registryName) {
            return recipesById.get(registryName.getPath());
        }

        @Override
        public int hash(DemoRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(DemoRecipe first, DemoRecipe second) {
            return first.id().equals(second.id());
        }

        @Override
        public RecipeProjection resolveRecipe(DemoRecipe recipe) {
            if (logToSticksRecipe.id().equals(recipe.id())) {
                return RecipeProjection.create(
                    this,
                    recipe,
                    List.of(InputContent.fromItemStack(oakLogStack, 1)),
                    List.of(OutputContent.fromItemStack(stickStack, sticksFromLogPerCraftAmount))
                );
            }
            return RecipeProjection.create(
                this,
                recipe,
                List.of(
                    InputContent.fromItemStack(oakLogStack, logsPerCraftAmount),
                    InputContent.fromItemStack(stickStack, sticksPerCraftAmount)
                ),
                List.of(OutputContent.fromItemStack(oakPlankStack, planksFromLogsPerCraftAmount))
            );
        }

        @Override
        public RichIterable<DemoRecipe> allRecipes() {
            return Lists.immutable.of(logToSticksRecipe, logToPlanksRecipe);
        }
    }

    //endregion
}
