package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer.ActionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputInjected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputRejected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputReplaced;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputSchemaPut;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputInjected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputRejected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputReplaced;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputSchemaPut;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffectPhase;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffectSource;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionReport;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionSchemaPut;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ReplaceMode;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuard;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuardFinding;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuardResult;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemaKey;
import dev.vfyjxf.cirrus.recipe.clause.ResolvePolicyClause;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class ProjectionEngine {

    private static final ProjectionEffectSource unknownSource = ProjectionEffectSource.clause(
        ProjectionPlugin.builtin,
        Namespace.of(CirrusConstants.modId, "clause/unknown"),
        ProjectionEffectPhase.contentTransformer
    );

    private final Map<Namespace, ProjectionTransformer<?>> projectionTransformers = new LinkedHashMap<>();
    private final Map<Namespace, ContentTransformer<?, ?>> contentTransformers = new LinkedHashMap<>();

    //region registry

    public void detectClause(ClauseType clauseType) {
        var api = RecipeApi.instance();
        ProjectionTransformer<?> projectionTransformer = api.findProjectionTransformer(clauseType);
        if (projectionTransformer != null) addProjectionTransformer(clauseType, projectionTransformer);

        ContentTransformer<?, ?> contentTransformer = api.findContentTransformer(clauseType);
        if (contentTransformer != null) addContentTransformer(clauseType, contentTransformer);
    }

    public void addProjectionTransformer(ClauseType type, ProjectionTransformer<?> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        Checks.checkArgument(
            type.plugin().equals(transformer.plugin()),
            "Projection transformer plugin must match clause type plugin"
        );
        projectionTransformers.put(type.id(), transformer);
    }

    public void addContentTransformer(ClauseType type, ContentTransformer<?, ?> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        Checks.checkArgument(
            type.plugin().equals(transformer.plugin()),
            "Content transformer plugin must match clause type plugin"
        );
        contentTransformers.put(type.id(), transformer);
    }

    public void detectProjection(RecipeProjection projection) {
        Checks.checkNotNull(projection, "projection");

        RecipeApi api;
        try {
            api = RecipeApi.instance();
        } catch (IllegalStateException ignored) {
            return;
        }

        detectRegisteredClauses(api, projection.allClauses());
        for (var input : projection.inputs()) {
            detectRegisteredClauses(api, input.allClauses());
        }
        for (var output : projection.outputs()) {
            detectRegisteredClauses(api, output.allClauses());
        }
    }

    //endregion

    //region project

    public ProjectedRecipe project(ExecutionContext context, RecipeProjection projection) {
        return projectReport(context, projection).projectedRecipe();
    }

    @SuppressWarnings("unchecked")
    public ProjectionReport projectReport(ExecutionContext context, RecipeProjection projection) {
        Checks.checkNotNull(context, "context");
        Checks.checkNotNull(projection, "projection");
        detectProjection(projection);

        RecipeProjection workingProjection = workingCopy(projection);
        MutableList<ProjectionEffect> effects = MutableLists.empty();
        ProjectionActionLog actions = new ProjectionActionLog();
        EngineIncrementOps increment = new EngineIncrementOps(actions);
        EngineDecrementOps decrement = new EngineDecrementOps(actions);

        for (var clause : workingProjection.clauses().allClauses()) {
            ProjectionTransformer<RecipeClause> projectionTransformer = (ProjectionTransformer<RecipeClause>) projectionTransformers
                    .get(clause.id());
            if (projectionTransformer == null) continue;
            ProjectionPlugin plugin = clause.plugin();
            if (!plugin.equals(projectionTransformer.plugin())) continue;
            if (!plugin.enabled(context)) continue;
            if (!projectionTransformer.selectProjection(context, workingProjection)) continue;

            ProjectionEffectSource source = ProjectionEffectSource
                    .clause(plugin, clause.id(), ProjectionEffectPhase.projectionTransformer);
            increment.bindSource(source);
            decrement.bindSource(source);
            projectionTransformer.project(context, workingProjection, clause, increment, decrement);
        }

        for (var entry : contentTransformers.entrySet()) {
            Namespace typeId = entry.getKey();
            ContentTransformer<?, ?> contentTransformer = entry.getValue();
            for (int inputIndex = 0; inputIndex < workingProjection.inputs().size(); inputIndex++) {
                var input = workingProjection.inputs().get(inputIndex);
                if (actions.matchInputRejection(input.content()) != null) continue;
                if (actions.matchInputReplacement(input.content()) != null) continue;
                if (!input.clauses().hasClause(typeId)) continue;

                var clause = input.clauses().getClause(typeId);
                ProjectionPlugin plugin = clause.plugin();
                if (!plugin.equals(contentTransformer.plugin())) continue;
                if (!plugin.enabled(context)) continue;
                if (!contentTransformer.select(context, workingProjection, input.content(), true)) continue;

                ProjectionEffectSource source = ProjectionEffectSource
                        .clause(plugin, clause.id(), ProjectionEffectPhase.contentTransformer);
                increment.bindSource(source);
                decrement.bindSource(source);
                InputContent before = snapshotInput(input.content());

                ActionResult action = applyTransformer(
                    contentTransformer,
                    context,
                    workingProjection.recipe(),
                    input.content(),
                    clause,
                    true,
                    increment,
                    decrement
                );
                if (actions.matchInputReplacement(input.content()) != null) {
                    continue;
                }
                if (action == ActionResult.remove) {
                    decrement.rejectInput(input.content());
                } else {
                    appendInputMutationEffect(effects, source, inputIndex, before, input.content());
                }
            }

            for (int outputIndex = 0; outputIndex < workingProjection.outputs().size(); outputIndex++) {
                var output = workingProjection.outputs().get(outputIndex);
                if (actions.matchOutputRejection(output.content()) != null) continue;
                if (actions.matchOutputReplacement(output.content()) != null) continue;
                if (!output.clauses().hasClause(typeId)) continue;

                var clause = output.clauses().getClause(typeId);
                ProjectionPlugin plugin = clause.plugin();
                if (!plugin.equals(contentTransformer.plugin())) continue;
                if (!plugin.enabled(context)) continue;
                if (!contentTransformer.select(context, workingProjection, output.content(), false)) continue;

                ProjectionEffectSource source = ProjectionEffectSource
                        .clause(plugin, clause.id(), ProjectionEffectPhase.contentTransformer);
                increment.bindSource(source);
                decrement.bindSource(source);
                OutputContent before = snapshotOutput(output.content());

                ActionResult action = applyTransformer(
                    contentTransformer,
                    context,
                    workingProjection.recipe(),
                    output.content(),
                    clause,
                    false,
                    increment,
                    decrement
                );
                if (actions.matchOutputReplacement(output.content()) != null) {
                    continue;
                }
                if (action == ActionResult.remove) {
                    decrement.rejectOutput(output.content());
                } else {
                    appendOutputMutationEffect(effects, source, outputIndex, before, output.content());
                }
            }
        }

        RecipeProjection projectedProjection = collectProjection(workingProjection, actions, effects);
        ProjectedRecipe projectedRecipe = projectedRecipe(projectedProjection);
        boolean runnable = true;
        ImmutableList<ResolveGuardFinding> guardFindings = Lists.immutable.empty();

        ResolvePolicyClause resolvePolicyClause = findResolvePolicyClause(workingProjection);
        if (resolvePolicyClause != null) {
            ResolveOutcome resolveOutcome = applyResolvePolicy(context, projectedRecipe, effects, resolvePolicyClause);
            projectedRecipe = resolveOutcome.recipe();
            runnable = resolveOutcome.runnable();
            guardFindings = resolveOutcome.guardFindings();
            projectedProjection = projectionWithProjectedContents(projectedProjection, projectedRecipe);
        }

        return new ProjectionReport(projectedProjection, effects.toImmutable(), runnable, guardFindings);
    }

    //endregion

    //region resolve policy

    private static ResolveOutcome applyResolvePolicy(
        ExecutionContext context,
        ProjectedRecipe projectedRecipe,
        MutableList<ProjectionEffect> effects,
        ResolvePolicyClause policyClause
    ) {
        MutableList<ResolveGuardFinding> guardFindings = MutableLists.empty();
        for (ResolveGuard guard : policyClause.policy().guards()) {
            ResolveGuardResult result = guard.evaluate(context, projectedRecipe);
            guardFindings.add(new ResolveGuardFinding(guard.id(), result.passed(), result.reasonCode()));
        }
        boolean runnable = guardFindings.allSatisfy(ResolveGuardFinding::passed);
        if (!runnable) {
            return new ResolveOutcome(projectedRecipe, guardFindings.toImmutable(), false);
        }

        MutableList<InputContent> resolvedInputs = copyContents(projectedRecipe.inputs());
        MutableList<OutputContent> resolvedOutputs = copyContents(projectedRecipe.outputs());
        ImmutableList<ResolveEffect> declaredEffects = policyClause.policy().effects();
        ImmutableList<ResolveEffect> orderedEffects = policyClause.policy().orderedEffects();

        for (int effectOrder = 0; effectOrder < orderedEffects.size(); effectOrder++) {
            ResolveEffect effect = orderedEffects.get(effectOrder);
            int declaredIndex = declaredIndexOf(declaredEffects, effect);

            MutableList<InputContent> beforeInputs = copyContents(resolvedInputs);
            MutableList<OutputContent> beforeOutputs = copyContents(resolvedOutputs);
            effect.apply(context, resolvedInputs, resolvedOutputs);

            if (effect.scope().allowInput()) {
                appendResolveInputDiff(
                    effects,
                    policyClause,
                    effect,
                    effectOrder,
                    declaredIndex,
                    beforeInputs,
                    resolvedInputs
                );
            }
            if (effect.scope().allowOutput()) {
                appendResolveOutputDiff(
                    effects,
                    policyClause,
                    effect,
                    effectOrder,
                    declaredIndex,
                    beforeOutputs,
                    resolvedOutputs
                );
            }
        }

        ProjectedRecipe resolvedRecipe = new ProjectedRecipe(
            projectedRecipe.recipe(),
            resolvedInputs.toImmutable(),
            resolvedOutputs.toImmutable()
        );
        return new ResolveOutcome(resolvedRecipe, guardFindings.toImmutable(), true);
    }

    private static @Nullable ResolvePolicyClause findResolvePolicyClause(RecipeProjection projection) {
        if (!projection.hasClause(ResolvePolicyClause.type)) return null;
        return (ResolvePolicyClause) projection.getClause(ResolvePolicyClause.type);
    }

    private static int declaredIndexOf(ImmutableList<ResolveEffect> declared, ResolveEffect target) {
        for (int i = 0; i < declared.size(); i++) {
            if (declared.get(i) == target) return i;
        }
        return -1;
    }

    private static <T extends RecipeContent> MutableList<T> copyContents(Iterable<T> contents) {
        MutableList<T> copied = MutableLists.empty();
        for (T content : contents) {
            @SuppressWarnings("unchecked")
            T snapshot = (T) snapshotContent(content);
            copied.add(snapshot);
        }
        return copied;
    }

    private static void appendResolveInputDiff(
        MutableList<ProjectionEffect> effects,
        ResolvePolicyClause policyClause,
        ResolveEffect effect,
        int effectOrder,
        int declaredIndex,
        MutableList<InputContent> before,
        MutableList<InputContent> after
    ) {
        ProjectionEffectSource source = ProjectionEffectSource
                .resolveEffect(policyClause.plugin(), policyClause.id(), effect.id(), effectOrder, declaredIndex);
        int sameCount = Math.min(before.size(), after.size());

        for (int index = 0; index < sameCount; index++) {
            InputContent beforeContent = before.get(index);
            InputContent afterContent = after.get(index);
            if (sameContent(beforeContent, afterContent)) continue;
            effects.add(
                new InputReplaced(source, index, beforeContent, snapshotInput(afterContent), ReplaceMode.adjusted)
            );
        }

        if (before.size() > after.size()) {
            for (int index = after.size(); index < before.size(); index++) {
                effects.add(new InputRejected(source, index, before.get(index)));
            }
        } else if (after.size() > before.size()) {
            for (int index = before.size(); index < after.size(); index++) {
                effects.add(new InputInjected(source, index, snapshotInput(after.get(index))));
            }
        }
    }

    private static void appendResolveOutputDiff(
        MutableList<ProjectionEffect> effects,
        ResolvePolicyClause policyClause,
        ResolveEffect effect,
        int effectOrder,
        int declaredIndex,
        MutableList<OutputContent> before,
        MutableList<OutputContent> after
    ) {
        ProjectionEffectSource source = ProjectionEffectSource
                .resolveEffect(policyClause.plugin(), policyClause.id(), effect.id(), effectOrder, declaredIndex);
        int sameCount = Math.min(before.size(), after.size());

        for (int index = 0; index < sameCount; index++) {
            OutputContent beforeContent = before.get(index);
            OutputContent afterContent = after.get(index);
            if (sameContent(beforeContent, afterContent)) continue;
            effects.add(
                new OutputReplaced(source, index, beforeContent, snapshotOutput(afterContent), ReplaceMode.adjusted)
            );
        }

        if (before.size() > after.size()) {
            for (int index = after.size(); index < before.size(); index++) {
                effects.add(new OutputRejected(source, index, before.get(index)));
            }
        } else if (after.size() > before.size()) {
            for (int index = before.size(); index < after.size(); index++) {
                effects.add(new OutputInjected(source, index, snapshotOutput(after.get(index))));
            }
        }
    }

    private record ResolveOutcome(
        ProjectedRecipe recipe,
        ImmutableList<ResolveGuardFinding> guardFindings,
        boolean runnable
    ) {}

    //endregion

    //region effect build

    private static RecipeProjection collectProjection(
        RecipeProjection projection,
        ProjectionActionLog actions,
        MutableList<ProjectionEffect> effects
    ) {
        ProjectionSchema schema = applyProjectionSchemaWrites(projection.schema(), actions, effects);
        return new RecipeProjection(
            projection.recipe(),
            collectInputs(projection, actions, effects),
            collectOutputs(projection, actions, effects),
            schema,
            projection.clauses().merge()
        );
    }

    private static ImmutableList<ClausedContent<InputContent>> collectInputs(
        RecipeProjection projection,
        ProjectionActionLog actions,
        MutableList<ProjectionEffect> effects
    ) {
        MutableList<ClausedContent<InputContent>> transformed = MutableLists.empty();
        for (int originalIndex = 0; originalIndex < projection.inputs().size(); originalIndex++) {
            ClausedContent<InputContent> input = projection.inputs().get(originalIndex);
            InputContent before = input.content();
            InputReplacement replacement = actions.matchInputReplacement(before);
            if (replacement != null) {
                InputContent after = snapshotInput(replacement.after());
                ProjectionSchema schema = applyInputSchemaWrites(
                    input.schema(),
                    before,
                    replacement.after(),
                    originalIndex,
                    actions,
                    effects
                );
                transformed.add(new ClausedContent<>(after, schema, input.clauses().merge()));
                effects.add(
                    new InputReplaced(
                        replacement.source(),
                        originalIndex,
                        snapshotInput(before),
                        after,
                        replacement.mode()
                    )
                );
                continue;
            }

            InputRule rejectedBy = actions.matchInputRejection(before);
            if (rejectedBy != null) {
                effects.add(new InputRejected(rejectedBy.source(), originalIndex, snapshotInput(before)));
                continue;
            }

            ProjectionSchema schema = applyInputSchemaWrites(
                input.schema(),
                before,
                before,
                originalIndex,
                actions,
                effects
            );
            transformed.add(new ClausedContent<>(snapshotInput(before), schema, input.clauses().merge()));
        }

        for (InputInjection injection : actions.inputInjections) {
            int index = transformed.size();
            InputContent after = snapshotInput(injection.content());
            ProjectionSchema schema = applyInputSchemaWrites(
                ProjectionSchema.empty(),
                injection.content(),
                injection.content(),
                index,
                actions,
                effects
            );
            transformed.add(new ClausedContent<>(after, schema, ClauseSet.create()));
            effects.add(new InputInjected(injection.source(), index, after));
        }

        return transformed.toImmutable();
    }

    private static ImmutableList<ClausedContent<OutputContent>> collectOutputs(
        RecipeProjection projection,
        ProjectionActionLog actions,
        MutableList<ProjectionEffect> effects
    ) {
        MutableList<ClausedContent<OutputContent>> transformed = MutableLists.empty();
        for (int originalIndex = 0; originalIndex < projection.outputs().size(); originalIndex++) {
            ClausedContent<OutputContent> output = projection.outputs().get(originalIndex);
            OutputContent before = output.content();
            OutputReplacement replacement = actions.matchOutputReplacement(before);
            if (replacement != null) {
                OutputContent after = snapshotOutput(replacement.after());
                ProjectionSchema schema = applyOutputSchemaWrites(
                    output.schema(),
                    before,
                    replacement.after(),
                    originalIndex,
                    actions,
                    effects
                );
                transformed.add(new ClausedContent<>(after, schema, output.clauses().merge()));
                effects.add(
                    new OutputReplaced(
                        replacement.source(),
                        originalIndex,
                        snapshotOutput(before),
                        after,
                        replacement.mode()
                    )
                );
                continue;
            }

            OutputRule rejectedBy = actions.matchOutputRejection(before);
            if (rejectedBy != null) {
                effects.add(new OutputRejected(rejectedBy.source(), originalIndex, snapshotOutput(before)));
                continue;
            }

            ProjectionSchema schema = applyOutputSchemaWrites(
                output.schema(),
                before,
                before,
                originalIndex,
                actions,
                effects
            );
            transformed.add(new ClausedContent<>(snapshotOutput(before), schema, output.clauses().merge()));
        }

        for (OutputInjection injection : actions.outputInjections) {
            int index = transformed.size();
            OutputContent after = snapshotOutput(injection.content());
            ProjectionSchema schema = applyOutputSchemaWrites(
                ProjectionSchema.empty(),
                injection.content(),
                injection.content(),
                index,
                actions,
                effects
            );
            transformed.add(new ClausedContent<>(after, schema, ClauseSet.create()));
            effects.add(new OutputInjected(injection.source(), index, after));
        }

        return transformed.toImmutable();
    }

    private static ProjectionSchema applyProjectionSchemaWrites(
        ProjectionSchema base,
        ProjectionActionLog actions,
        MutableList<ProjectionEffect> effects
    ) {
        ProjectionSchema schema = base;
        for (ProjectionSchemaWrite<?> write : actions.projectionSchemaWrites) {
            schema = applyProjectionSchemaWrite(schema, write, effects);
        }
        return schema;
    }

    private static <T> ProjectionSchema applyProjectionSchemaWrite(
        ProjectionSchema schema,
        ProjectionSchemaWrite<T> write,
        MutableList<ProjectionEffect> effects
    ) {
        effects.add(new ProjectionSchemaPut<>(write.source(), write.key(), write.value()));
        return schema.with(write.key(), write.value());
    }

    private static ProjectionSchema applyInputSchemaWrites(
        ProjectionSchema base,
        InputContent before,
        InputContent after,
        int index,
        ProjectionActionLog actions,
        MutableList<ProjectionEffect> effects
    ) {
        ProjectionSchema schema = base;
        for (InputSchemaWrite<?> write : actions.inputSchemaWrites) {
            if (write.owner() != before && write.owner() != after) continue;
            schema = applyInputSchemaWrite(schema, write, index, effects);
        }
        return schema;
    }

    private static <T> ProjectionSchema applyInputSchemaWrite(
        ProjectionSchema schema,
        InputSchemaWrite<T> write,
        int index,
        MutableList<ProjectionEffect> effects
    ) {
        effects.add(new InputSchemaPut<>(write.source(), index, write.key(), write.value()));
        return schema.with(write.key(), write.value());
    }

    private static ProjectionSchema applyOutputSchemaWrites(
        ProjectionSchema base,
        OutputContent before,
        OutputContent after,
        int index,
        ProjectionActionLog actions,
        MutableList<ProjectionEffect> effects
    ) {
        ProjectionSchema schema = base;
        for (OutputSchemaWrite<?> write : actions.outputSchemaWrites) {
            if (write.owner() != before && write.owner() != after) continue;
            schema = applyOutputSchemaWrite(schema, write, index, effects);
        }
        return schema;
    }

    private static <T> ProjectionSchema applyOutputSchemaWrite(
        ProjectionSchema schema,
        OutputSchemaWrite<T> write,
        int index,
        MutableList<ProjectionEffect> effects
    ) {
        effects.add(new OutputSchemaPut<>(write.source(), index, write.key(), write.value()));
        return schema.with(write.key(), write.value());
    }

    private static void appendInputMutationEffect(
        MutableList<ProjectionEffect> effects,
        ProjectionEffectSource source,
        int index,
        InputContent before,
        InputContent after
    ) {
        if (sameContent(before, after)) return;
        effects.add(new InputReplaced(source, index, before, snapshotInput(after), ReplaceMode.adjusted));
    }

    private static void appendOutputMutationEffect(
        MutableList<ProjectionEffect> effects,
        ProjectionEffectSource source,
        int index,
        OutputContent before,
        OutputContent after
    ) {
        if (sameContent(before, after)) return;
        effects.add(new OutputReplaced(source, index, before, snapshotOutput(after), ReplaceMode.adjusted));
    }

    private static boolean sameContent(RecipeContent before, RecipeContent after) {
        return before == after
                || before.getClass() == after.getClass()
                        && before.amount() == after.amount()
                        && Float.compare(before.chance(), after.chance()) == 0
                        && before.toString().equals(after.toString());
    }

    private static InputContent snapshotInput(InputContent content) {
        return (InputContent) snapshotContent(content);
    }

    private static OutputContent snapshotOutput(OutputContent content) {
        return (OutputContent) snapshotContent(content);
    }

    private static RecipeContent snapshotContent(RecipeContent content) {
        return content.copyWithAmountAndChance(content.amount(), content.chance());
    }

    private static RecipeProjection workingCopy(RecipeProjection projection) {
        MutableList<ClausedContent<InputContent>> inputs = MutableLists.empty();
        for (ClausedContent<InputContent> input : projection.inputs()) {
            inputs.add(copyInput(input));
        }
        MutableList<ClausedContent<OutputContent>> outputs = MutableLists.empty();
        for (ClausedContent<OutputContent> output : projection.outputs()) {
            outputs.add(copyOutput(output));
        }
        return new RecipeProjection(
            projection.recipe(),
            inputs.toImmutable(),
            outputs.toImmutable(),
            projection.schema(),
            projection.clauses().merge()
        );
    }

    private static ClausedContent<InputContent> copyInput(ClausedContent<InputContent> input) {
        return new ClausedContent<>(snapshotInput(input.content()), input.schema(), input.clauses().merge());
    }

    private static ClausedContent<OutputContent> copyOutput(ClausedContent<OutputContent> output) {
        return new ClausedContent<>(snapshotOutput(output.content()), output.schema(), output.clauses().merge());
    }

    private static ProjectedRecipe projectedRecipe(RecipeProjection projection) {
        MutableList<InputContent> inputs = MutableLists.empty();
        for (ClausedContent<InputContent> input : projection.inputs()) {
            inputs.add(input.content());
        }
        MutableList<OutputContent> outputs = MutableLists.empty();
        for (ClausedContent<OutputContent> output : projection.outputs()) {
            outputs.add(output.content());
        }
        return new ProjectedRecipe(projection.recipe(), inputs.toImmutable(), outputs.toImmutable());
    }

    private static RecipeProjection projectionWithProjectedContents(RecipeProjection base, ProjectedRecipe projected) {
        MutableList<ClausedContent<InputContent>> inputs = MutableLists.empty();
        for (int i = 0; i < projected.inputs().size(); i++) {
            InputContent input = projected.inputs().get(i);
            ProjectionSchema schema = ProjectionSchema.empty();
            ClauseSet clauses = ClauseSet.create();
            if (i < base.inputs().size() && sameContent(base.inputs().get(i).content(), input)) {
                schema = base.inputs().get(i).schema();
                clauses = base.inputs().get(i).clauses().merge();
            }
            inputs.add(new ClausedContent<>(snapshotInput(input), schema, clauses));
        }

        MutableList<ClausedContent<OutputContent>> outputs = MutableLists.empty();
        for (int i = 0; i < projected.outputs().size(); i++) {
            OutputContent output = projected.outputs().get(i);
            ProjectionSchema schema = ProjectionSchema.empty();
            ClauseSet clauses = ClauseSet.create();
            if (i < base.outputs().size() && sameContent(base.outputs().get(i).content(), output)) {
                schema = base.outputs().get(i).schema();
                clauses = base.outputs().get(i).clauses().merge();
            }
            outputs.add(new ClausedContent<>(snapshotOutput(output), schema, clauses));
        }

        return new RecipeProjection(
            base.recipe(),
            inputs.toImmutable(),
            outputs.toImmutable(),
            base.schema(),
            base.clauses().merge()
        );
    }

    private void detectRegisteredClauses(RecipeApi api, Iterable<? extends RecipeClause> clauses) {
        for (RecipeClause clause : clauses) {
            if (!api.hasClause(clause.id())) {
                continue;
            }
            detectClause(api.getClauseType(clause.id()));
        }
    }

    //endregion

    //region internal

    @SuppressWarnings("unchecked")
    private static <T extends RecipeContent, F extends RecipeClause> ActionResult applyTransformer(
        ContentTransformer<?, ?> transformer,
        ExecutionContext context,
        RecipeEntry<?> recipe,
        T content,
        F clause,
        boolean asInput,
        EngineIncrementOps increment,
        EngineDecrementOps decrement
    ) {
        ContentTransformer<T, F> typedTransformer = (ContentTransformer<T, F>) transformer;
        return typedTransformer.transform(context, recipe, content, clause, asInput, increment, decrement);
    }

    private static final class ProjectionActionLog {
        private final MutableList<InputInjection> inputInjections = MutableLists.empty();
        private final MutableList<OutputInjection> outputInjections = MutableLists.empty();
        private final MutableList<InputReplacement> inputReplacements = MutableLists.empty();
        private final MutableList<OutputReplacement> outputReplacements = MutableLists.empty();
        private final MutableList<InputRule> inputRules = MutableLists.empty();
        private final MutableList<OutputRule> outputRules = MutableLists.empty();
        private final MutableList<ProjectionSchemaWrite<?>> projectionSchemaWrites = MutableLists.empty();
        private final MutableList<InputSchemaWrite<?>> inputSchemaWrites = MutableLists.empty();
        private final MutableList<OutputSchemaWrite<?>> outputSchemaWrites = MutableLists.empty();

        private @Nullable InputRule matchInputRejection(InputContent input) {
            for (var rule : inputRules) {
                if (rule.predicate().test(input)) return rule;
            }
            return null;
        }

        private @Nullable OutputRule matchOutputRejection(OutputContent output) {
            for (var rule : outputRules) {
                if (rule.predicate().test(output)) return rule;
            }
            return null;
        }

        private @Nullable InputReplacement matchInputReplacement(InputContent input) {
            for (int i = inputReplacements.size() - 1; i >= 0; i--) {
                InputReplacement replacement = inputReplacements.get(i);
                if (replacement.before() == input) return replacement;
            }
            return null;
        }

        private @Nullable OutputReplacement matchOutputReplacement(OutputContent output) {
            for (int i = outputReplacements.size() - 1; i >= 0; i--) {
                OutputReplacement replacement = outputReplacements.get(i);
                if (replacement.before() == output) return replacement;
            }
            return null;
        }
    }

    private static final class EngineIncrementOps implements ContentTransformer.IncrementOps {

        private final ProjectionActionLog actions;
        private ProjectionEffectSource currentSource = unknownSource;

        private EngineIncrementOps(ProjectionActionLog actions) {
            this.actions = actions;
        }

        void bindSource(ProjectionEffectSource source) {
            currentSource = source;
        }

        @Override
        public ContentTransformer.IncrementOps injectInput(InputContent content) {
            Checks.checkNotNull(content, "content");
            actions.inputInjections.add(new InputInjection(content, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.IncrementOps injectOutput(OutputContent content) {
            Checks.checkNotNull(content, "content");
            actions.outputInjections.add(new OutputInjection(content, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.IncrementOps replaceInput(InputContent before, InputContent after, ReplaceMode mode) {
            Checks.checkNotNull(before, "before");
            Checks.checkNotNull(after, "after");
            Checks.checkNotNull(mode, "mode");
            actions.inputReplacements.add(new InputReplacement(before, after, mode, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.IncrementOps replaceOutput(
            OutputContent before,
            OutputContent after,
            ReplaceMode mode
        ) {
            Checks.checkNotNull(before, "before");
            Checks.checkNotNull(after, "after");
            Checks.checkNotNull(mode, "mode");
            actions.outputReplacements.add(new OutputReplacement(before, after, mode, currentSource));
            return this;
        }

        @Override
        public <T> ContentTransformer.IncrementOps putProjectionSchema(ProjectionSchemaKey<T> key, T value) {
            actions.projectionSchemaWrites.add(new ProjectionSchemaWrite<>(key, value, currentSource));
            return this;
        }

        @Override
        public <T> ContentTransformer.IncrementOps putInputSchema(
            InputContent owner,
            ProjectionSchemaKey<T> key,
            T value
        ) {
            Checks.checkNotNull(owner, "owner");
            actions.inputSchemaWrites.add(new InputSchemaWrite<>(owner, key, value, currentSource));
            return this;
        }

        @Override
        public <T> ContentTransformer.IncrementOps putOutputSchema(
            OutputContent owner,
            ProjectionSchemaKey<T> key,
            T value
        ) {
            Checks.checkNotNull(owner, "owner");
            actions.outputSchemaWrites.add(new OutputSchemaWrite<>(owner, key, value, currentSource));
            return this;
        }
    }

    private static final class EngineDecrementOps implements ContentTransformer.DecrementOps {

        private final ProjectionActionLog actions;
        private ProjectionEffectSource currentSource = unknownSource;

        private EngineDecrementOps(ProjectionActionLog actions) {
            this.actions = actions;
        }

        void bindSource(ProjectionEffectSource source) {
            currentSource = source;
        }

        @Override
        public ContentTransformer.DecrementOps rejectInput(Predicate<InputContent> predicate) {
            Checks.checkNotNull(predicate, "predicate");
            actions.inputRules.add(new InputRule(predicate, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.DecrementOps rejectInput(InputContent content) {
            Checks.checkNotNull(content, "content");
            actions.inputRules.add(new InputRule(input -> input == content, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.DecrementOps rejectOutput(Predicate<OutputContent> predicate) {
            Checks.checkNotNull(predicate, "predicate");
            actions.outputRules.add(new OutputRule(predicate, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.DecrementOps rejectOutput(OutputContent content) {
            Checks.checkNotNull(content, "content");
            actions.outputRules.add(new OutputRule(output -> output == content, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.DecrementOps replaceInput(InputContent before, InputContent after, ReplaceMode mode) {
            Checks.checkNotNull(before, "before");
            Checks.checkNotNull(after, "after");
            Checks.checkNotNull(mode, "mode");
            actions.inputReplacements.add(new InputReplacement(before, after, mode, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.DecrementOps replaceOutput(
            OutputContent before,
            OutputContent after,
            ReplaceMode mode
        ) {
            Checks.checkNotNull(before, "before");
            Checks.checkNotNull(after, "after");
            Checks.checkNotNull(mode, "mode");
            actions.outputReplacements.add(new OutputReplacement(before, after, mode, currentSource));
            return this;
        }

        @Override
        public <T> ContentTransformer.DecrementOps putProjectionSchema(ProjectionSchemaKey<T> key, T value) {
            actions.projectionSchemaWrites.add(new ProjectionSchemaWrite<>(key, value, currentSource));
            return this;
        }

        @Override
        public <T> ContentTransformer.DecrementOps putInputSchema(
            InputContent owner,
            ProjectionSchemaKey<T> key,
            T value
        ) {
            Checks.checkNotNull(owner, "owner");
            actions.inputSchemaWrites.add(new InputSchemaWrite<>(owner, key, value, currentSource));
            return this;
        }

        @Override
        public <T> ContentTransformer.DecrementOps putOutputSchema(
            OutputContent owner,
            ProjectionSchemaKey<T> key,
            T value
        ) {
            Checks.checkNotNull(owner, "owner");
            actions.outputSchemaWrites.add(new OutputSchemaWrite<>(owner, key, value, currentSource));
            return this;
        }
    }

    private record InputInjection(InputContent content, ProjectionEffectSource source) {}

    private record OutputInjection(OutputContent content, ProjectionEffectSource source) {}

    private record InputReplacement(
        InputContent before,
        InputContent after,
        ReplaceMode mode,
        ProjectionEffectSource source
    ) {}

    private record OutputReplacement(
        OutputContent before,
        OutputContent after,
        ReplaceMode mode,
        ProjectionEffectSource source
    ) {}

    private record InputRule(Predicate<InputContent> predicate, ProjectionEffectSource source) {}

    private record OutputRule(Predicate<OutputContent> predicate, ProjectionEffectSource source) {}

    private record ProjectionSchemaWrite<T>(ProjectionSchemaKey<T> key, T value, ProjectionEffectSource source) {
        private ProjectionSchemaWrite {
            Checks.checkNotNull(key, "key");
            Checks.checkNotNull(value, "value");
            Checks.checkNotNull(source, "source");
            key.cast(value);
        }
    }

    private record InputSchemaWrite<T>(
        InputContent owner,
        ProjectionSchemaKey<T> key,
        T value,
        ProjectionEffectSource source
    ) {
        private InputSchemaWrite {
            Checks.checkNotNull(owner, "owner");
            Checks.checkNotNull(key, "key");
            Checks.checkNotNull(value, "value");
            Checks.checkNotNull(source, "source");
            key.cast(value);
        }
    }

    private record OutputSchemaWrite<T>(
        OutputContent owner,
        ProjectionSchemaKey<T> key,
        T value,
        ProjectionEffectSource source
    ) {
        private OutputSchemaWrite {
            Checks.checkNotNull(owner, "owner");
            Checks.checkNotNull(key, "key");
            Checks.checkNotNull(value, "value");
            Checks.checkNotNull(source, "source");
            key.cast(value);
        }
    }

    //endregion
}
