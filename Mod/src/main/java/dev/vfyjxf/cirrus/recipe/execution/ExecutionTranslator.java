package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.planning.bridge.CirrusCatalogAdapter;
import dev.vfyjxf.cirrus.planning.bridge.ExecutionPlanBridge;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 把规划产物翻译成可执行的交互步骤（§10.5.2）。
 *
 * <p>两条入口：{@link #translate} 消费 {@link ProductionPlan}（经 {@link ExecutionPlanBridge} 的批次绑定），
 * {@link #translateTarget} 服务无 ProductionPlan 的客户端目标配方直执行路径。</p>
 */
public final class ExecutionTranslator {

    private final InteractionApi interactionApi;

    public ExecutionTranslator(InteractionApi interactionApi) {
        this.interactionApi = Objects.requireNonNull(interactionApi, "interactionApi");
    }

    //region plan translation

    /** 规划批次 → 交互执行计划；任一配方批次无法反查 {@link RecipeEntry} 时返回空。 */
    public Optional<InteractionExecutionPlan> translate(
            ProductionPlan plan,
            ExecutionPlanBridge.ExecutionOutline outline,
            Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> recipeResolver
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(outline, "outline");
        Objects.requireNonNull(recipeResolver, "recipeResolver");
        if (outline.batches().isEmpty()) {
            return Optional.empty();
        }
        MutableList<InteractionStep> steps = Lists.mutable.empty();
        RecipeEntry<?> target = null;
        int targetTimes = 1;
        for (ExecutionPlanBridge.RecipeBatch batch : outline.batches()) {
            Optional<RecipeEntry<?>> recipe = recipeResolver.apply(batch.recipeKey());
            if (recipe.isEmpty()) {
                return Optional.empty();
            }
            steps.addAllIterable(
                    batchSteps(recipe.get(), batch.times(), batch.inputs(), batch.outputs(), batch.catalysts()));
            target = recipe.get();
            targetTimes = batch.times();
        }
        if (target == null) {
            return Optional.empty();
        }
        return Optional.of(new InteractionExecutionPlan(target, targetTimes, steps.toImmutable()));
    }

    //endregion

    //region target translation

    /** 客户端目标配方直执行：内容取自 routingView / 投影管线，概率字段不读取。 */
    public Optional<InteractionExecutionPlan> translateTarget(RecipeEntry<?> recipe, int times) {
        Objects.requireNonNull(recipe, "recipe");
        if (times <= 0) {
            return Optional.empty();
        }
        Optional<CirrusCatalogAdapter.RecipeContentSource> contentSource = CirrusCatalogAdapter.contentSource(recipe);
        if (contentSource.isEmpty()) {
            return Optional.empty();
        }
        List<InputBinding> inputs = new ArrayList<>();
        List<InputContent> catalysts = new ArrayList<>();
        for (InputContent input : contentSource.get().inputs()) {
            if (input == null || input.isEmpty()) {
                continue;
            }
            if (input.notConsumed()) {
                catalysts.add(input);
                continue;
            }
            if (input.amount() <= 0L) {
                return Optional.empty();
            }
            inputs.add(new InputBinding(input, input.amount()));
        }
        List<OutputBinding> outputs = new ArrayList<>();
        for (OutputContent output : contentSource.get().outputs()) {
            if (output == null || output.isEmpty()) {
                continue;
            }
            if (!(output instanceof dev.vfyjxf.cirrus.api.recipe.content.StackContent<?, ?> stack) || stack.amount() <= 0L) {
                continue;
            }
            outputs.add(new OutputBinding(stack.content(), stack.amount()));
        }
        if (outputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new InteractionExecutionPlan(
                recipe,
                times,
                batchSteps(recipe, times, inputs, outputs, catalysts)
        ));
    }

    //endregion

    //region step synthesis

    private ImmutableList<InteractionStep> batchSteps(
            RecipeEntry<?> recipe,
            int times,
            List<InputBinding> perCraftInputs,
            List<OutputBinding> perCraftOutputs,
            List<InputContent> catalysts
    ) {
        MutableList<InteractionStep> steps = Lists.mutable.empty();
        steps.add(new InteractionStep.OpenInteractionStep(
                ExecutionSideRequirement.clientSide,
                ExecutionCapability.interactionAccess,
                recipe,
                times,
                interactionIdOf(recipe)
        ));
        for (InputContent catalyst : catalysts) {
            reserveCatalyst(steps, catalyst);
        }
        ImmutableList<InputBinding> totals = scaleInputs(perCraftInputs, times);
        ImmutableList<OutputBinding> totalOutputs = scaleOutputs(perCraftOutputs, times);
        int remaining = times;
        while (remaining > 0) {
            int batchTimes = batchSize(recipe, remaining, totals, totalOutputs, times);
            ImmutableList<InputBinding> batchInputs = scaleInputs(perCraftInputs, batchTimes);
            ImmutableList<OutputBinding> batchOutputs = scaleOutputs(perCraftOutputs, batchTimes);
            steps.add(new InteractionStep.PrepareInputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.inventoryTransfer,
                    recipe,
                    batchInputs
            ));
            steps.add(new InteractionStep.ExecuteRecipeStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.interactionPerform,
                    recipe,
                    batchTimes,
                    batchInputs,
                    batchOutputs
            ));
            steps.add(new InteractionStep.CollectOutputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.inventoryTransfer,
                    recipe,
                    batchOutputs
            ));
            for (OutputBinding output : batchOutputs) {
                steps.add(new InteractionStep.StoreOutputsStep(
                        ExecutionSideRequirement.clientSide,
                        ExecutionCapability.outputStorage,
                        output.resource(),
                        output.amount()
                ));
            }
            remaining -= batchTimes;
        }
        return steps.toImmutable();
    }

    /**
     * 催化剂按"持有 1 件"预留；Known limitation：core 未提供每批周转量（04 §4.4.2 待补）。
     */
    private static void reserveCatalyst(MutableList<InteractionStep> steps, InputContent catalyst) {
        steps.add(new InteractionStep.EnsureAccessibleStep(
                ExecutionSideRequirement.environmentAgnostic,
                ExecutionCapability.storageAccess,
                new InteractionStep.AccessTarget.Input(catalyst),
                1L
        ));
        steps.add(new InteractionStep.ReserveCatalystStep(
                ExecutionSideRequirement.clientSide,
                ExecutionCapability.inventoryTransfer,
                catalyst,
                1L
        ));
    }

    private Namespace interactionIdOf(RecipeEntry<?> recipe) {
        InteractionProbeResult probe = interactionApi.probe(recipe);
        return probe instanceof InteractionProbeResult.Supported supported
                ? supported.interactionId()
                : recipe.systemId();
    }

    private int batchSize(
            RecipeEntry<?> recipe,
            int remaining,
            ImmutableList<InputBinding> totals,
            ImmutableList<OutputBinding> totalOutputs,
            int totalTimes
    ) {
        int requested = Math.max(1, remaining);
        int limit = interactionApi.maxExecuteBatchSize(
                new InteractionContext(recipe, totalTimes, totals, totalOutputs));
        return Math.max(1, Math.min(requested, limit));
    }

    private static ImmutableList<InputBinding> scaleInputs(List<InputBinding> perCraftInputs, int batchTimes) {
        MutableList<InputBinding> scaled = Lists.mutable.empty();
        for (InputBinding input : perCraftInputs) {
            scaled.add(new InputBinding(input.input(), Math.multiplyExact(input.amount(), (long) batchTimes)));
        }
        return scaled.toImmutable();
    }

    private static ImmutableList<OutputBinding> scaleOutputs(List<OutputBinding> perCraftOutputs, int batchTimes) {
        MutableList<OutputBinding> scaled = Lists.mutable.empty();
        for (OutputBinding output : perCraftOutputs) {
            scaled.add(new OutputBinding(output.resource(), Math.multiplyExact(output.amount(), (long) batchTimes)));
        }
        return scaled.toImmutable();
    }

    //endregion
}
