package dev.vfyjxf.cirrus.ui.execution.workbench;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionRuntime;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
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
import dev.vfyjxf.cirrus.ui.widget.execution.ExecutionOverlayHost;
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
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-backed workbench executor used by the crafting-screen overlay.
 * It validates live inventory state on client and keeps a deterministic fallback for tests.
 */
public final class WorkbenchExecutionDemoController implements ExecutionOverlayHost {

    //region runtime constants

    private static final int autoAdvanceIntervalTicks = 8;
    private static final int craftCycleCount = 6;
    private static final int logsPerCraftAmount = 1;
    private static final int planksPerCraftAmount = 4;
    private static final int totalLogAmount = logsPerCraftAmount * craftCycleCount;
    private static final int totalPlankAmount = planksPerCraftAmount * craftCycleCount;

    private static final ItemStack oakLogStack = new ItemStack(Items.OAK_LOG);
    private static final ItemStack oakPlankStack = new ItemStack(Items.OAK_PLANKS);
    private static final ValueType<Long> fallbackLogAmountType = new FallbackAmountType(Namespace.of("cirrus", "workbench_fallback_log"));
    private static final ValueType<Long> fallbackPlankAmountType = new FallbackAmountType(Namespace.of("cirrus", "workbench_fallback_plank"));

    private static final DemoRecipeSystem recipeSystem = new DemoRecipeSystem();
    private static final RecipeEntry<DemoRecipe> workbenchRecipe = RecipeEntry.of(recipeSystem, new DemoRecipe("oak_planks_batch"));

    private static final ExecutionGuidance openWorkbenchGuidance = new ExecutionGuidance(
        Component.translatable("cirrus.ui.execution.workbench.manual.open.summary"),
        Component.translatable("cirrus.ui.execution.workbench.manual.open.detail"),
        Lists.immutable.of(
            Component.translatable("cirrus.ui.execution.workbench.manual.open.step1"),
            Component.translatable("cirrus.ui.execution.workbench.manual.open.step2")
        ),
        Sets.immutable.empty()
    );

    private static final ExecutionGuidance autoCraftGuidance = ExecutionGuidance.simple(
        Component.translatable("cirrus.ui.execution.workbench.flow.execute.detail")
    );

    //endregion

    //region state

    private final SessionEnvironment environment;
    private final ContentResource oakLogResource;
    private final ContentResource oakPlankResource;
    private final InputContent oakLogInput;
    private final WorkbenchStorageApi storageApi;
    private final WorkbenchInteractionApi interactionApi;
    private final ExecutionRuntime runtime;
    private final ExecutionSession initialSession;
    private final boolean forceFallbackResources;

    private ExecutionSession session;
    private boolean autoAdvance;
    private int autoAdvanceTicks;

    //endregion

    public WorkbenchExecutionDemoController() {
        this(resolveDefaultEnvironment(), false);
    }

    WorkbenchExecutionDemoController(long simulatedLogs, long simulatedPlanks, int simulatedEmptySlots) {
        this(new SimulatedEnvironment(simulatedLogs, simulatedPlanks, simulatedEmptySlots, true), false);
    }

    WorkbenchExecutionDemoController(long simulatedLogs, long simulatedPlanks, int simulatedEmptySlots, boolean forceFallbackResources) {
        this(new SimulatedEnvironment(simulatedLogs, simulatedPlanks, simulatedEmptySlots, true), forceFallbackResources);
    }

    private WorkbenchExecutionDemoController(SessionEnvironment environment, boolean forceFallbackResources) {
        this.environment = environment;
        this.forceFallbackResources = forceFallbackResources;
        var fixtures = createRuntimeFixtures();
        oakLogResource = fixtures.logResource();
        oakPlankResource = fixtures.plankResource();
        oakLogInput = fixtures.logInput();
        storageApi = new WorkbenchStorageApi(environment, oakLogResource, oakPlankResource);
        interactionApi = new WorkbenchInteractionApi(environment);
        runtime = new ExecutionRuntime(storageApi, interactionApi);
        var plan = buildPlan();
        initialSession = ExecutionSession.start(plan, buildFlowRoot(plan));
        session = initialSession;
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

    //endregion

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
            autoAdvanceTicks = 0;
        }
        return advanced;
    }

    public boolean toggleAutoAdvance() {
        if (session.completed()) {
            autoAdvance = false;
            return false;
        }
        autoAdvance = !autoAdvance;
        autoAdvanceTicks = 0;
        return autoAdvance;
    }

    public void reset() {
        session = initialSession;
        autoAdvance = false;
        autoAdvanceTicks = 0;
    }

    public void tick() {
        if (!autoAdvance) {
            return;
        }
        autoAdvanceTicks++;
        if (autoAdvanceTicks < autoAdvanceIntervalTicks) {
            return;
        }
        autoAdvanceTicks = 0;
        advanceSteps(1);
    }

    //endregion

    //region pause gates

    private ExecutionSession resumeIfReady(ExecutionSession pausedSession) {
        InteractionStep step = pausedSession.currentStep();
        if (step == null || !canResumeStep(step)) {
            return pausedSession;
        }
        if (step instanceof InteractionStep.ExecuteRecipeStep ignored) {
            var result = environment.performCraft(oakLogStack, logsPerCraftAmount, oakPlankStack, planksPerCraftAmount);
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
            case InteractionStep.ExecuteRecipeStep ignored -> environment.canAutoCraft(oakLogStack, logsPerCraftAmount, oakPlankStack, planksPerCraftAmount);
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

    private ContentResource requestSourceVariant(ContentResource source, int variant) {
        if ((variant & 1) == 0) {
            return source;
        }
        if (source instanceof ContentStack<?, ?> stack) {
            return stack.copy();
        }
        if (source instanceof ContentValue<?> value) {
            return value.copy();
        }
        return source;
    }

    //endregion

    //region plan fixtures

    private InteractionExecutionPlan buildPlan() {
        MutableList<InteractionStep> steps = Lists.mutable.empty();
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
                oakPlankResource,
                totalPlankAmount
            )
        );
        steps.add(
            new InteractionStep.OpenInteractionStep(
                ExecutionSideRequirement.player,
                ExecutionCapability.interactionAccess,
                workbenchRecipe,
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
        for (int cycle = 0; cycle < craftCycleCount; cycle++) {
            ContentResource cycleLogRequest = requestSourceVariant(oakLogResource, cycle);
            ContentResource cyclePlankRequest = requestSourceVariant(oakPlankResource, cycle + craftCycleCount);
            ImmutableList<PlanAction.InputBinding> inputs = Lists.immutable.of(new PlanAction.InputBinding(oakLogInput, logsPerCraftAmount));
            ImmutableList<PlanAction.OutputBinding> outputs = Lists.immutable.of(new PlanAction.OutputBinding(cyclePlankRequest, planksPerCraftAmount));
            steps.add(
                new InteractionStep.EnsureAccessibleStep(
                    ExecutionSideRequirement.client,
                    ExecutionCapability.storageAccess,
                    new InteractionStep.AccessTarget.Resource(cycleLogRequest),
                    logsPerCraftAmount
                )
            );
            steps.add(
                new InteractionStep.EnsureCapacityStep(
                    ExecutionSideRequirement.client,
                    ExecutionCapability.inventoryCapacity,
                    cyclePlankRequest,
                    planksPerCraftAmount
                )
            );
            steps.add(
                new InteractionStep.PrepareInputsStep(
                    ExecutionSideRequirement.client,
                    ExecutionCapability.inventoryTransfer,
                    workbenchRecipe,
                    inputs
                )
            );
            steps.add(
                new InteractionStep.ExecuteRecipeStep(
                    ExecutionSideRequirement.client,
                    ExecutionCapability.interactionPerform,
                    workbenchRecipe,
                    1,
                    inputs,
                    outputs
                )
            );
            steps.add(
                new InteractionStep.CollectOutputsStep(
                    ExecutionSideRequirement.client,
                    ExecutionCapability.inventoryTransfer,
                    workbenchRecipe,
                    outputs
                )
            );
        }
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
            workbenchRecipe,
            1,
            steps.toImmutable()
        );
    }

    private ExecutionFlowNode buildFlowRoot(InteractionExecutionPlan plan) {
        int craftStartIndex = 5;
        int craftStepCount = craftCycleCount * 5;
        int cleanupStartIndex = craftStartIndex + craftStepCount;
        MutableList<InteractionStep> craftSteps = Lists.mutable.empty();
        MutableList<ExecutionFlowNode> craftCycles = Lists.mutable.empty();
        for (int cycle = 0; cycle < craftCycleCount; cycle++) {
            int stepIndex = craftStartIndex + cycle * 5;
            InteractionStep ensureAccess = plan.steps().get(stepIndex);
            InteractionStep ensureCapacity = plan.steps().get(stepIndex + 1);
            InteractionStep prepare = plan.steps().get(stepIndex + 2);
            InteractionStep execute = plan.steps().get(stepIndex + 3);
            InteractionStep collect = plan.steps().get(stepIndex + 4);
            craftSteps.add(ensureAccess);
            craftSteps.add(ensureCapacity);
            craftSteps.add(prepare);
            craftSteps.add(execute);
            craftSteps.add(collect);
            craftCycles.add(
                ExecutionFlowNode.pending(
                    "craft-cycle-" + (cycle + 1),
                    Component.translatable("cirrus.ui.execution.workbench.flow.cycle", cycle + 1, craftCycleCount),
                    Lists.immutable.of(ensureAccess, ensureCapacity, prepare, execute, collect),
                    ExecutionGuidance.simple(Component.translatable("cirrus.ui.execution.workbench.flow.cycle.detail", logsPerCraftAmount, planksPerCraftAmount)),
                    Lists.immutable.of(
                        ExecutionFlowNode.pending(
                            "craft-cycle-" + (cycle + 1) + "-access",
                            Component.translatable("cirrus.ui.execution.step.ensure_access"),
                            Lists.immutable.of(ensureAccess),
                            null,
                            Lists.immutable.empty()
                        ),
                        ExecutionFlowNode.pending(
                            "craft-cycle-" + (cycle + 1) + "-capacity",
                            Component.translatable("cirrus.ui.execution.step.ensure_capacity"),
                            Lists.immutable.of(ensureCapacity),
                            null,
                            Lists.immutable.empty()
                        ),
                        ExecutionFlowNode.pending(
                            "craft-cycle-" + (cycle + 1) + "-prepare",
                            Component.translatable("cirrus.ui.execution.step.prepare"),
                            Lists.immutable.of(prepare),
                            null,
                            Lists.immutable.empty()
                        ),
                        ExecutionFlowNode.pending(
                            "craft-cycle-" + (cycle + 1) + "-execute",
                            Component.translatable("cirrus.ui.execution.step.execute"),
                            Lists.immutable.of(execute),
                            autoCraftGuidance,
                            Lists.immutable.empty()
                        ),
                        ExecutionFlowNode.pending(
                            "craft-cycle-" + (cycle + 1) + "-collect",
                            Component.translatable("cirrus.ui.execution.step.collect"),
                            Lists.immutable.of(collect),
                            null,
                            Lists.immutable.empty()
                        )
                    )
                )
            );
        }
        return ExecutionFlowNode.pending(
            "workbench-target",
            Component.translatable("cirrus.ui.execution.workbench.flow.target", totalPlankAmount),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "gather",
                    Component.translatable("cirrus.ui.execution.workbench.flow.gather"),
                    Lists.immutable.of(plan.steps().get(0), plan.steps().get(1)),
                    ExecutionGuidance.simple(Component.translatable("cirrus.ui.execution.workbench.flow.gather.detail")),
                    Lists.immutable.empty()
                ),
                ExecutionFlowNode.pending(
                    "setup",
                    Component.translatable("cirrus.ui.execution.workbench.flow.setup"),
                    Lists.immutable.of(plan.steps().get(2), plan.steps().get(3), plan.steps().get(4)),
                    ExecutionGuidance.simple(Component.translatable("cirrus.ui.execution.workbench.flow.setup.detail")),
                    Lists.immutable.empty()
                ),
                ExecutionFlowNode.pending(
                    "craft",
                    Component.translatable("cirrus.ui.execution.workbench.flow.craft"),
                    craftSteps.toImmutable(),
                    ExecutionGuidance.simple(Component.translatable("cirrus.ui.execution.workbench.flow.craft.detail", craftCycleCount)),
                    craftCycles.toImmutable()
                ),
                ExecutionFlowNode.pending(
                    "cleanup",
                    Component.translatable("cirrus.ui.execution.workbench.flow.cleanup"),
                    Lists.immutable.of(plan.steps().get(cleanupStartIndex), plan.steps().get(cleanupStartIndex + 1)),
                    ExecutionGuidance.simple(Component.translatable("cirrus.ui.execution.workbench.flow.cleanup.detail")),
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
            ContentResource plankResource = ContentValue.fromValue(fallbackPlankAmountType, 2L);
            InputContent logInput = InputContent.fromValue(fallbackLogAmountType, 1L);
            return new RuntimeFixtures(logResource, plankResource, logInput);
        }
        try {
            ContentResource logResource = ContentStack.fromStack(oakLogStack);
            ContentResource plankResource = ContentStack.fromStack(oakPlankStack);
            InputContent logInput = InputContent.fromItemStack(oakLogStack, 1);
            return new RuntimeFixtures(logResource, plankResource, logInput);
        } catch (Throwable ignored) {
            ContentResource logResource = ContentValue.fromValue(fallbackLogAmountType, 1L);
            ContentResource plankResource = ContentValue.fromValue(fallbackPlankAmountType, 2L);
            InputContent logInput = InputContent.fromValue(fallbackLogAmountType, 1L);
            return new RuntimeFixtures(logResource, plankResource, logInput);
        }
    }

    private record RuntimeFixtures(ContentResource logResource, ContentResource plankResource, InputContent logInput) {
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
        return new SimulatedEnvironment(totalLogAmount, 0L, 6, true);
    }

    private sealed interface SessionEnvironment permits LiveEnvironment, SimulatedEnvironment {
        long count(ItemStack template);

        long insertCapacity(ItemStack template);

        boolean interactionOpen();

        StorageAccessibility inventoryAccessibility();

        boolean canAutoCraft(ItemStack inputTemplate, long inputAmount, ItemStack outputTemplate, long outputAmount);

        InteractionPerformResult performCraft(ItemStack inputTemplate, long inputAmount, ItemStack outputTemplate, long outputAmount);
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
        public boolean canAutoCraft(ItemStack inputTemplate, long inputAmount, ItemStack outputTemplate, long outputAmount) {
            if (!interactionOpen()) {
                return false;
            }
            if (count(inputTemplate) < inputAmount) {
                return false;
            }
            return insertCapacity(outputTemplate) >= outputAmount;
        }

        @Override
        public InteractionPerformResult performCraft(ItemStack inputTemplate, long inputAmount, ItemStack outputTemplate, long outputAmount) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft == null ? null : minecraft.player;
            MultiPlayerGameMode gameMode = minecraft == null ? null : minecraft.gameMode;
            if (player == null || gameMode == null || !(player.containerMenu instanceof CraftingMenu menu)) {
                return new InteractionPerformResult.Failed(openWorkbenchGuidance);
            }

            if (!menu.getCarried().isEmpty()) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.cursor_occupied.summary",
                    "cirrus.ui.execution.workbench.warning.cursor_occupied.detail"
                ));
            }

            long availableInput = count(inputTemplate);
            if (availableInput < inputAmount) {
                return new InteractionPerformResult.Failed(toGuidance(missingStockGuidance(inputTemplate, availableInput, inputAmount)));
            }

            long capacity = insertCapacity(outputTemplate);
            if (capacity < outputAmount) {
                return new InteractionPerformResult.Failed(toGuidance(missingCapacityGuidance(outputTemplate, capacity, outputAmount)));
            }

            long outputBefore = count(outputTemplate);
            if (hasCraftResult(menu, outputTemplate) && collectCraftResult(gameMode, player, menu, outputTemplate, outputAmount, outputBefore)) {
                return new InteractionPerformResult.Performed();
            }

            int existingInputSlot = findCraftGridInputSlot(menu, inputTemplate);
            if (existingInputSlot >= 0) {
                if (!click(gameMode, player, menu, existingInputSlot, 0, ClickType.QUICK_MOVE)) {
                    return new InteractionPerformResult.Failed(clickFailedGuidance());
                }
                existingInputSlot = findCraftGridInputSlot(menu, inputTemplate);
                if (existingInputSlot >= 0) {
                    return new InteractionPerformResult.Failed(guidance(
                        "cirrus.ui.execution.workbench.warning.result_waiting.summary",
                        "cirrus.ui.execution.workbench.warning.result_waiting.detail"
                    ));
                }
            }

            int sourceSlot = findInventorySlot(menu, inputTemplate);
            if (sourceSlot < 0) {
                return new InteractionPerformResult.Failed(toGuidance(missingStockGuidance(inputTemplate, availableInput, inputAmount)));
            }
            int gridSlot = findCraftGridSlot(menu, inputTemplate);
            if (gridSlot < 0) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.grid_blocked.summary",
                    "cirrus.ui.execution.workbench.warning.grid_blocked.detail"
                ));
            }

            if (!click(gameMode, player, menu, sourceSlot, 0, ClickType.PICKUP)) {
                return new InteractionPerformResult.Failed(clickFailedGuidance());
            }
            if (menu.getCarried().isEmpty()) {
                return new InteractionPerformResult.Failed(clickFailedGuidance());
            }
            if (!click(gameMode, player, menu, gridSlot, 1, ClickType.PICKUP)) {
                restoreCarryToInventory(gameMode, player, menu, sourceSlot);
                return new InteractionPerformResult.Failed(clickFailedGuidance());
            }
            if (!menu.getCarried().isEmpty() && !click(gameMode, player, menu, sourceSlot, 0, ClickType.PICKUP)) {
                restoreCarryToInventory(gameMode, player, menu, sourceSlot);
                return new InteractionPerformResult.Failed(clickFailedGuidance());
            }

            if (!collectCraftResult(gameMode, player, menu, outputTemplate, outputAmount, outputBefore)) {
                return new InteractionPerformResult.Failed(guidance(
                    "cirrus.ui.execution.workbench.warning.result_waiting.summary",
                    "cirrus.ui.execution.workbench.warning.result_waiting.detail"
                ));
            }
            return new InteractionPerformResult.Performed();
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

        private static int findCraftGridSlot(CraftingMenu menu, ItemStack template) {
            int fallbackEmpty = -1;
            for (int slotId = 1; slotId <= 9; slotId++) {
                Slot slot = menu.getSlot(slotId);
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) {
                    if (fallbackEmpty < 0) {
                        fallbackEmpty = slotId;
                    }
                    continue;
                }
                if (ItemStack.isSameItemSameComponents(stack, template) && stack.getCount() < stack.getMaxStackSize()) {
                    return slotId;
                }
            }
            return fallbackEmpty;
        }

        private static int findCraftGridInputSlot(CraftingMenu menu, ItemStack template) {
            for (int slotId = 1; slotId <= 9; slotId++) {
                ItemStack stack = menu.getSlot(slotId).getItem();
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                    return slotId;
                }
            }
            return -1;
        }

        private static boolean hasCraftResult(CraftingMenu menu, ItemStack outputTemplate) {
            ItemStack resultStack = menu.getSlot(0).getItem();
            return !resultStack.isEmpty() && ItemStack.isSameItemSameComponents(resultStack, outputTemplate);
        }

        private static boolean collectCraftResult(
            MultiPlayerGameMode gameMode,
            LocalPlayer player,
            CraftingMenu menu,
            ItemStack outputTemplate,
            long outputAmount,
            long outputBefore
        ) {
            if (!hasCraftResult(menu, outputTemplate)) {
                return false;
            }
            if (!click(gameMode, player, menu, 0, 0, ClickType.QUICK_MOVE)) {
                return false;
            }
            long outputAfter = countInventory(player.getInventory(), outputTemplate);
            return outputAfter >= safeAdd(outputBefore, outputAmount);
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
        private long plankCount;
        private final int emptySlots;
        private final boolean interactionOpen;

        private SimulatedEnvironment(long logCount, long plankCount, int emptySlots, boolean interactionOpen) {
            this.logCount = Math.max(0L, logCount);
            this.plankCount = Math.max(0L, plankCount);
            this.emptySlots = Math.max(0, emptySlots);
            this.interactionOpen = interactionOpen;
        }

        @Override
        public long count(ItemStack template) {
            if (ItemStack.isSameItemSameComponents(template, oakLogStack)) {
                return logCount;
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
        public boolean canAutoCraft(ItemStack inputTemplate, long inputAmount, ItemStack outputTemplate, long outputAmount) {
            if (!interactionOpen) {
                return false;
            }
            if (!ItemStack.isSameItemSameComponents(inputTemplate, oakLogStack)
                || !ItemStack.isSameItemSameComponents(outputTemplate, oakPlankStack)) {
                return false;
            }
            return logCount >= inputAmount && insertCapacity(outputTemplate) >= outputAmount;
        }

        @Override
        public InteractionPerformResult performCraft(ItemStack inputTemplate, long inputAmount, ItemStack outputTemplate, long outputAmount) {
            if (!interactionOpen) {
                return new InteractionPerformResult.Failed(openWorkbenchGuidance);
            }
            if (!canAutoCraft(inputTemplate, inputAmount, outputTemplate, outputAmount)) {
                long available = count(inputTemplate);
                if (available < inputAmount) {
                    return new InteractionPerformResult.Failed(new ExecutionGuidance(
                        missingStockGuidance(inputTemplate, available, inputAmount).summary(),
                        missingStockGuidance(inputTemplate, available, inputAmount).detail(),
                        Lists.immutable.empty(),
                        Sets.immutable.empty()
                    ));
                }
                long capacity = insertCapacity(outputTemplate);
                return new InteractionPerformResult.Failed(new ExecutionGuidance(
                    missingCapacityGuidance(outputTemplate, capacity, outputAmount).summary(),
                    missingCapacityGuidance(outputTemplate, capacity, outputAmount).detail(),
                    Lists.immutable.empty(),
                    Sets.immutable.empty()
                ));
            }

            if (ItemStack.isSameItemSameComponents(inputTemplate, oakLogStack)) {
                logCount = Math.max(0L, logCount - inputAmount);
            }
            if (ItemStack.isSameItemSameComponents(outputTemplate, oakPlankStack)) {
                plankCount = safeAdd(plankCount, outputAmount);
            }
            return new InteractionPerformResult.Performed();
        }
    }

    //endregion

    //region storage

    private static final class WorkbenchStorageApi implements StorageApi {
        private final PlayerInventoryStorageSource playerInventorySource;
        private final ImmutableList<PlayerInventoryStorageSource> sources;

        private WorkbenchStorageApi(SessionEnvironment environment, ContentResource logResource, ContentResource plankResource) {
            playerInventorySource = new PlayerInventoryStorageSource(environment, logResource, plankResource);
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
            ItemStack template = itemTemplate(request.resource(), logResource, plankResource);
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
            ItemStack template = itemTemplate(request.resource(), logResource, plankResource);
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
    }

    //endregion

    //region storage helpers

    private static @Nullable ItemStack itemTemplate(
        ContentResource resource,
        ContentResource logResource,
        ContentResource plankResource
    ) {
        if (!(resource instanceof ContentStack<?, ?> contentStack) || !contentStack.isItem()) {
            boolean matchesLog = resource.matchesResource(logResource);
            boolean matchesPlank = resource.matchesResource(plankResource);
            if (matchesLog && !matchesPlank) {
                return oakLogStack.copy();
            }
            if (matchesPlank && !matchesLog) {
                return oakPlankStack.copy();
            }
            if (resource == logResource) {
                return oakLogStack.copy();
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
            return environment.performCraft(oakLogStack, logsPerCraftAmount, oakPlankStack, planksPerCraftAmount);
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
        private final Map<String, DemoRecipe> byId = new ConcurrentHashMap<>();

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
            byId.put(recipe.id(), recipe);
            return ResourceLocation.fromNamespaceAndPath("cirrus", recipe.id());
        }

        @Override
        public DemoRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public DemoRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
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
            throw new UnsupportedOperationException("Workbench overlay recipe has no projection");
        }

        @Override
        public RichIterable<DemoRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    //endregion
}
