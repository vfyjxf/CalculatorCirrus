package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer.ActionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.*;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuard;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuardFinding;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuardResult;
import dev.vfyjxf.cirrus.recipe.clause.ResolvePolicyClause;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class ProjectionEngine {

    private static final EffectSource unknownSource = new EffectSource(
            Namespace.of(CirrusConstants.MOD_ID, "clause/unknown"),
            "unknown"
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
        projectionTransformers.put(type.id(), transformer);
    }

    public void addContentTransformer(ClauseType type, ContentTransformer<?, ?> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        contentTransformers.put(type.id(), transformer);
    }

    @Deprecated(forRemoval = false)
    public void addClauseTransformer(ClauseType type, ContentTransformer<?, ?> transformer) {
        addContentTransformer(type, transformer);
    }

    //endregion

    //region project

    public ProjectedRecipe project(ExecutionContext context, RecipeProjection projection) {
        return projectReport(context, projection).recipe();
    }

    @SuppressWarnings("unchecked")
    public ProjectionReport projectReport(ExecutionContext context, RecipeProjection projection) {
        Checks.checkNotNull(context, "context");
        Checks.checkNotNull(projection, "projection");

        MutableList<ProjectionEffect> effects = MutableLists.empty();
        EngineIncrementOps increment = new EngineIncrementOps();
        EngineDecrementOps decrement = new EngineDecrementOps();

        for (var clause : projection.clauses().allClauses()) {
            ProjectionTransformer<RecipeClause> projectionTransformer = (ProjectionTransformer<RecipeClause>) projectionTransformers.get(clause.id());
            if (projectionTransformer == null) continue;
            if (projectionTransformer.selectProjection(context, projection)) {
                EffectSource source = new EffectSource(clause.id(), "projectionTransformer");
                increment.bindSource(source);
                decrement.bindSource(source);
                projectionTransformer.project(context, projection, clause, increment, decrement);
            }
        }

        for (var entry : contentTransformers.entrySet()) {
            Namespace typeId = entry.getKey();
            ContentTransformer<?, ?> contentTransformer = entry.getValue();
            for (int inputIndex = 0; inputIndex < projection.inputs().size(); inputIndex++) {
                var input = projection.inputs().get(inputIndex);
                if (decrement.matchInput(input.content()) != null) continue;

                boolean marked = input.clauses().hasClause(typeId);
                boolean selected = contentTransformer.select(context, projection, input.content(), true);
                if (!marked || !selected) continue;

                var clause = input.clauses().getClause(typeId);
                EffectSource source = new EffectSource(clause.id(), "contentTransformer");
                increment.bindSource(source);
                decrement.bindSource(source);
                RecipeContent before = snapshot(input.content());

                ActionResult action = applyTransformer(contentTransformer, context, projection.recipe(), input.content(), clause, true, increment, decrement);
                if (action == ActionResult.REMOVE) decrement.rejectInput(input.content());
                else appendContentChangedEffect(effects, source, EffectPath.input(inputIndex), before, input.content());
            }

            for (int outputIndex = 0; outputIndex < projection.outputs().size(); outputIndex++) {
                var output = projection.outputs().get(outputIndex);
                if (decrement.matchOutput(output.content()) != null) continue;

                boolean marked = output.clauses().hasClause(typeId);
                boolean selected = contentTransformer.select(context, projection, output.content(), false);
                if (!marked || !selected) continue;

                var clause = output.clauses().getClause(typeId);
                EffectSource source = new EffectSource(clause.id(), "contentTransformer");
                increment.bindSource(source);
                decrement.bindSource(source);
                RecipeContent before = snapshot(output.content());

                ActionResult action = applyTransformer(contentTransformer, context, projection.recipe(), output.content(), clause, false, increment, decrement);
                if (action == ActionResult.REMOVE) decrement.rejectOutput(output.content());
                else
                    appendContentChangedEffect(effects, source, EffectPath.output(outputIndex), before, output.content());
            }
        }

        ImmutableList<InputContent> transformedInputs = collectInputs(projection, increment, decrement, effects);
        ImmutableList<OutputContent> transformedOutputs = collectOutputs(projection, increment, decrement, effects);
        ProjectedRecipe projectedRecipe = new ProjectedRecipe(projection.recipe(), transformedInputs, transformedOutputs);
        boolean runnable = true;
        ImmutableList<ResolveGuardFinding> guardFindings = Lists.immutable.empty();

        ResolvePolicyClause resolvePolicyClause = findResolvePolicyClause(projection);
        if (resolvePolicyClause != null) {
            ResolveOutcome resolveOutcome = applyResolvePolicy(context, projectedRecipe, effects, resolvePolicyClause);
            projectedRecipe = resolveOutcome.recipe();
            runnable = resolveOutcome.runnable();
            guardFindings = resolveOutcome.guardFindings();
        }

        return new ProjectionReport(projectedRecipe, effects.toImmutable(), runnable, guardFindings);
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
            EffectMeta guardMeta = appendGuardMeta(result.meta(), guard, result.reasonCode());
            guardFindings.add(new ResolveGuardFinding(guard.id(), result.passed(), result.reasonCode(), guardMeta));
        }
        boolean runnable = guardFindings.allSatisfy(ResolveGuardFinding::passed);
        if (!runnable) {
            return new ResolveOutcome(projectedRecipe, guardFindings.toImmutable(), false);
        }

        MutableList<InputContent> resolvedInputs = projectedRecipe.inputs().toList();
        MutableList<OutputContent> resolvedOutputs = projectedRecipe.outputs().toList();
        ImmutableList<ResolveEffect> declaredEffects = policyClause.policy().effects();
        ImmutableList<ResolveEffect> orderedEffects = policyClause.policy().orderedEffects();

        for (int effectOrder = 0; effectOrder < orderedEffects.size(); effectOrder++) {
            ResolveEffect effect = orderedEffects.get(effectOrder);
            int declaredIndex = declaredIndexOf(declaredEffects, effect);

            MutableList<InputContent> beforeInputs = copyContents(resolvedInputs);
            MutableList<OutputContent> beforeOutputs = copyContents(resolvedOutputs);
            effect.apply(context, resolvedInputs, resolvedOutputs);

            if (effect.scope().allowInput()) {
                appendResolveDiff(
                    effects,
                    policyClause,
                    effect,
                    effectOrder,
                    declaredIndex,
                    "input",
                    beforeInputs,
                    resolvedInputs
                );
            }
            if (effect.scope().allowOutput()) {
                appendResolveDiff(
                    effects,
                    policyClause,
                    effect,
                    effectOrder,
                    declaredIndex,
                    "output",
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

    private static <T extends RecipeContent> MutableList<T> copyContents(MutableList<T> contents) {
        MutableList<T> copied = MutableLists.empty();
        for (T content : contents) {
            @SuppressWarnings("unchecked") T snapshot = (T) snapshot(content);
            copied.add(snapshot);
        }
        return copied;
    }

    private static <T extends RecipeContent> void appendResolveDiff(
        MutableList<ProjectionEffect> effects,
        ResolvePolicyClause policyClause,
        ResolveEffect effect,
        int effectOrder,
        int declaredIndex,
        String channel,
        MutableList<T> before,
        MutableList<T> after
    ) {
        EffectSource source = new EffectSource(policyClause.id(), "resolveEffect");
        int sameCount = Math.min(before.size(), after.size());

        for (int index = 0; index < sameCount; index++) {
            T beforeContent = before.get(index);
            T afterContent = after.get(index);
            if (sameContent(beforeContent, afterContent)) continue;

            EffectMeta meta = appendResolveEffectMeta(buildMeta(beforeContent, afterContent), policyClause, effect, effectOrder, declaredIndex);
            effects.add(new ProjectionEffect(
                ProjectionEffectTypes.contentChanged,
                source,
                pathOf(channel, index).withField("content"),
                new EffectChange(beforeContent, afterContent),
                meta
            ));
        }

        if (before.size() > after.size()) {
            for (int index = after.size(); index < before.size(); index++) {
                T beforeContent = before.get(index);
                EffectMeta meta = appendResolveEffectMeta(buildMeta(beforeContent, null), policyClause, effect, effectOrder, declaredIndex);
                effects.add(new ProjectionEffect(
                    ProjectionEffectTypes.contentRejected,
                    source,
                    pathOf(channel, index).withField("content"),
                    new EffectChange(beforeContent, null),
                    meta
                ));
            }
        } else if (after.size() > before.size()) {
            for (int index = before.size(); index < after.size(); index++) {
                T afterContent = after.get(index);
                EffectMeta meta = appendResolveEffectMeta(buildMeta(null, afterContent), policyClause, effect, effectOrder, declaredIndex);
                effects.add(new ProjectionEffect(
                    ProjectionEffectTypes.contentInjected,
                    source,
                    pathOf(channel, index).withField("content"),
                    new EffectChange(null, afterContent),
                    meta
                ));
            }
        }
    }

    private static EffectPath pathOf(String channel, int index) {
        return switch (channel) {
            case "input" -> EffectPath.input(index);
            case "output" -> EffectPath.output(index);
            default -> throw new IllegalArgumentException("Unknown channel: " + channel);
        };
    }

    private static EffectMeta appendResolveEffectMeta(
        EffectMeta meta,
        ResolvePolicyClause policyClause,
        ResolveEffect effect,
        int effectOrder,
        int declaredIndex
    ) {
        return meta.with(ProjectionEffectMetaKeys.resolvePolicy, policyClause.id().toString())
                   .with(ProjectionEffectMetaKeys.resolveEffect, effect.id().toString())
                   .with(ProjectionEffectMetaKeys.resolveEffectOrder, effectOrder)
                   .with(ProjectionEffectMetaKeys.resolveEffectDeclaredIndex, declaredIndex);
    }

    private static EffectMeta appendGuardMeta(EffectMeta meta, ResolveGuard guard, @Nullable String reasonCode) {
        EffectMeta merged = meta.with(ProjectionEffectMetaKeys.guardId, guard.id().toString());
        if (reasonCode != null) {
            merged = merged.with(ProjectionEffectMetaKeys.guardReasonCode, reasonCode);
        }
        return merged;
    }

    private record ResolveOutcome(
        ProjectedRecipe recipe,
        ImmutableList<ResolveGuardFinding> guardFindings,
        boolean runnable
    ) {}

    //endregion

    //region effect build

    private static ImmutableList<InputContent> collectInputs(
            RecipeProjection projection,
            EngineIncrementOps increment,
            EngineDecrementOps decrement,
            MutableList<ProjectionEffect> effects
    ) {
        MutableList<InputContent> transformed = MutableLists.empty();
        for (int i = 0; i < projection.inputs().size(); i++) {
            InputContent input = projection.inputs().get(i).content();
            EngineDecrementOps.InputRule rejectedBy = decrement.matchInput(input);
            if (rejectedBy != null) {
                effects.add(new ProjectionEffect(
                        ProjectionEffectTypes.contentRejected,
                        rejectedBy.source(),
                        EffectPath.input(i).withField("content"),
                        new EffectChange(input, null),
                        buildMeta(input, null)
                ));
                continue;
            }
            transformed.add(input);
        }

        for (var injection : increment.inputInjections) {
            int index = transformed.size();
            transformed.add(injection.content());
            effects.add(new ProjectionEffect(
                    ProjectionEffectTypes.contentInjected,
                    injection.source(),
                    EffectPath.input(index).withField("content"),
                    new EffectChange(null, injection.content()),
                    buildMeta(null, injection.content())
            ));
        }

        return transformed.toImmutable();
    }

    private static ImmutableList<OutputContent> collectOutputs(
            RecipeProjection projection,
            EngineIncrementOps increment,
            EngineDecrementOps decrement,
            MutableList<ProjectionEffect> effects
    ) {
        MutableList<OutputContent> transformed = MutableLists.empty();
        for (int i = 0; i < projection.outputs().size(); i++) {
            OutputContent output = projection.outputs().get(i).content();
            EngineDecrementOps.OutputRule rejectedBy = decrement.matchOutput(output);
            if (rejectedBy != null) {
                effects.add(new ProjectionEffect(
                        ProjectionEffectTypes.contentRejected,
                        rejectedBy.source(),
                        EffectPath.output(i).withField("content"),
                        new EffectChange(output, null),
                        buildMeta(output, null)
                ));
                continue;
            }
            transformed.add(output);
        }

        for (var injection : increment.outputInjections) {
            int index = transformed.size();
            transformed.add(injection.content());
            effects.add(new ProjectionEffect(
                    ProjectionEffectTypes.contentInjected,
                    injection.source(),
                    EffectPath.output(index).withField("content"),
                    new EffectChange(null, injection.content()),
                    buildMeta(null, injection.content())
            ));
        }

        return transformed.toImmutable();
    }

    private static void appendContentChangedEffect(
            MutableList<ProjectionEffect> effects,
            EffectSource source,
            EffectPath path,
            RecipeContent before,
            RecipeContent after
    ) {
        if (sameContent(before, after)) return;
        effects.add(new ProjectionEffect(
                ProjectionEffectTypes.contentChanged,
                source,
                path.withField("content"),
                new EffectChange(before, after),
                buildMeta(before, after)
        ));
    }

    private static EffectMeta buildMeta(@Nullable RecipeContent before, @Nullable RecipeContent after) {
        EffectMeta meta = EffectMeta.empty();
        if (before != null) {
            meta = meta.with(ProjectionEffectMetaKeys.beforeAmount, before.amount());
            meta = meta.with(ProjectionEffectMetaKeys.beforeType, before.getClass().getName());
        }
        if (after != null) {
            meta = meta.with(ProjectionEffectMetaKeys.afterAmount, after.amount());
            meta = meta.with(ProjectionEffectMetaKeys.afterType, after.getClass().getName());
        }
        if (before != null && after != null) {
            long delta = after.amount() - before.amount();
            meta = meta.with(ProjectionEffectMetaKeys.amountDelta, delta);
            if (before.amount() > 0) {
                meta = meta.with(ProjectionEffectMetaKeys.amountFactor, (double) after.amount() / (double) before.amount());
            }
        }
        return meta;
    }

    private static boolean sameContent(RecipeContent before, RecipeContent after) {
        return before == after || before.equals(after);
    }

    private static RecipeContent snapshot(RecipeContent content) {
        return content.copyWithAmount(content.amount());
    }

    //endregion

    //region internal

    @SuppressWarnings("unchecked")
    private static <T extends RecipeContent, F extends RecipeClause> ActionResult applyTransformer(
            ContentTransformer<?, ?> transformer,
            ExecutionContext context, RecipeEntry<?> recipe,
            T content, F clause, boolean asInput,
            EngineIncrementOps increment, EngineDecrementOps decrement
    ) {
        ContentTransformer<T, F> typedTransformer = (ContentTransformer<T, F>) transformer;
        return typedTransformer.transform(context, recipe, content, clause, asInput, increment, decrement);
    }

    private static final class EngineIncrementOps implements ContentTransformer.IncrementOps {

        private final MutableList<InputInjection> inputInjections = MutableLists.empty();
        private final MutableList<OutputInjection> outputInjections = MutableLists.empty();
        private EffectSource currentSource = unknownSource;

        void bindSource(EffectSource source) {
            currentSource = source;
        }

        @Override
        public ContentTransformer.IncrementOps injectInput(InputContent content) {
            Checks.checkNotNull(content, "content");
            inputInjections.add(new InputInjection(content, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.IncrementOps injectOutput(OutputContent content) {
            Checks.checkNotNull(content, "content");
            outputInjections.add(new OutputInjection(content, currentSource));
            return this;
        }

        record InputInjection(
                InputContent content,
                EffectSource source
        ) {}

        record OutputInjection(
                OutputContent content,
                EffectSource source
        ) {}
    }

    private static final class EngineDecrementOps implements ContentTransformer.DecrementOps {

        private final MutableList<InputRule> inputRules = MutableLists.empty();
        private final MutableList<OutputRule> outputRules = MutableLists.empty();
        private EffectSource currentSource = unknownSource;

        void bindSource(EffectSource source) {
            currentSource = source;
        }

        @Nullable InputRule matchInput(InputContent input) {
            for (var rule : inputRules) {
                if (rule.predicate().test(input)) return rule;
            }
            return null;
        }

        @Nullable OutputRule matchOutput(OutputContent output) {
            for (var rule : outputRules) {
                if (rule.predicate().test(output)) return rule;
            }
            return null;
        }

        @Override
        public ContentTransformer.DecrementOps rejectInput(Predicate<InputContent> predicate) {
            Checks.checkNotNull(predicate, "predicate");
            inputRules.add(new InputRule(predicate, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.DecrementOps rejectInput(InputContent content) {
            Checks.checkNotNull(content, "content");
            inputRules.add(new InputRule(input -> input == content, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.DecrementOps rejectOutput(Predicate<OutputContent> predicate) {
            Checks.checkNotNull(predicate, "predicate");
            outputRules.add(new OutputRule(predicate, currentSource));
            return this;
        }

        @Override
        public ContentTransformer.DecrementOps rejectOutput(OutputContent content) {
            Checks.checkNotNull(content, "content");
            outputRules.add(new OutputRule(output -> output == content, currentSource));
            return this;
        }

        record InputRule(
                Predicate<InputContent> predicate,
                EffectSource source
        ) {}

        record OutputRule(
                Predicate<OutputContent> predicate,
                EffectSource source
        ) {}
    }

    //endregion
}
