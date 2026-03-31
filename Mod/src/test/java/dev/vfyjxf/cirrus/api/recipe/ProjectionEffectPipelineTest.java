package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.FluidContainerContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseSet;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputInjected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputRejected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputReplaced;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputInjected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputRejected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputReplaced;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffectPhase;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffectSource;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionPluginReport;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionReport;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuard;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuardResult;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolvePolicy;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveScope;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cirrus.recipe.clause.FluidContainerUnpackClause;
import dev.vfyjxf.cirrus.recipe.clause.GuaranteedOutputClause;
import dev.vfyjxf.cirrus.recipe.clause.ResolvePolicyClause;
import dev.vfyjxf.cirrus.recipe.system.BasicRecipeSystem;
import dev.vfyjxf.cloudlib.api.annotation.NotNullByDefault;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
public class ProjectionEffectPipelineTest {

    @Test
    void unwrapClauseProducesFluidInputAndGenericContainerRequirement(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)), FluidContainerUnpackClause.instance)
            .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
            .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(CirrusTypes.Clauses.fluidContainerUnpack, FluidContainerUnpackClause.transformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);
        ProjectedRecipe projected = report.projectedRecipe();

        MutableList<ProjectionEffect> removed = removedEffects(report);
        MutableList<ProjectionEffect> added = addedEffects(report);
        MutableList<ProjectionEffect> replaced = replacedEffects(report);

        assertTrue(removed.isEmpty());
        assertEquals(1, replaced.size());
        assertEquals(1, added.size());
        assertEquals(2, projected.inputs().size());
        assertTrue(projected.inputs().anySatisfy(FluidContainerContent.class::isInstance));

        ProjectionEffect replacedEffect = replaced.getOnly();
        assertContentEffect(replacedEffect, FluidContainerUnpackClause.id, ProjectionEffectPhase.contentTransformer, EffectChannel.input, 0);
        assertNotNull(effectBefore(replacedEffect));
        assertNotNull(effectAfter(replacedEffect));
        assertEquals(1L, beforeAmount(replacedEffect));
        assertTrue(afterAmount(replacedEffect) > 0L);

        Set<Integer> addedIndices = new HashSet<>();
        for (ProjectionEffect addedEffect : added) {
            assertContentEffect(
                addedEffect,
                FluidContainerUnpackClause.id,
                ProjectionEffectPhase.contentTransformer,
                EffectChannel.input,
                effectIndex(addedEffect)
            );
            assertNull(effectBefore(addedEffect));
            assertNotNull(effectAfter(addedEffect));
            assertTrue(effectAfter(addedEffect) instanceof FluidContainerContent);
            FluidContainerContent containerRequirement = (FluidContainerContent) effectAfter(addedEffect);
            assertFalse(containerRequirement.isEnumerable());
            assertTrue(containerRequirement.satisfy(ContentStack.fromStack(new ItemStack(Items.WATER_BUCKET))));
            assertTrue(afterAmount(addedEffect) > 0L);
            addedIndices.add(effectIndex(addedEffect));
        }
        assertEquals(Set.of(1), addedIndices);
    }

    @Test
    void skipChancedOutputUsesProjectionTransformerSource(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK)))
            .output(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.25F))
            .build();
        raw.addClause(GuaranteedOutputClause.instance);

        ProjectionEngine engine = new ProjectionEngine();
        engine.addProjectionTransformer(GuaranteedOutputClause.type, GuaranteedOutputClause.projectionTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        MutableList<ProjectionEffect> removed = removedEffects(report);
        assertEquals(1, removed.size());
        assertTrue(report.projectedRecipe().outputs().isEmpty());

        ProjectionEffect removedEffect = removed.getOnly();
        assertContentEffect(removedEffect, GuaranteedOutputClause.id, ProjectionEffectPhase.projectionTransformer, EffectChannel.output, 0);
        assertNotNull(effectBefore(removedEffect));
        assertNull(effectAfter(removedEffect));
        assertEquals(1L, beforeAmount(removedEffect));
    }

    @Test
    void scaleAmountClauseEmitsReplacedEffectWithDeltaAndFactor(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)), scaleAmountClause)
            .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
            .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(scaleAmountClauseType, scaleAmountTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        MutableList<ProjectionEffect> replaced = replacedEffects(report);
        assertEquals(1, replaced.size());

        ProjectionEffect replacedEffect = replaced.getOnly();
        assertContentEffect(replacedEffect, scaleAmountClauseId, ProjectionEffectPhase.contentTransformer, EffectChannel.input, 0);
        assertEquals(2L, beforeAmount(replacedEffect));
        assertEquals(4L, afterAmount(replacedEffect));
        assertEquals(2L, amountDelta(replacedEffect));
        assertEquals(2.0D, amountFactor(replacedEffect), 0.00001D);
        assertEquals(4L, report.projectedRecipe().inputs().get(0).amount());

        ProjectionPluginReport pluginReport = report.pluginReport(ProjectionPlugin.builtin);
        assertEquals(1, pluginReport.effectCount());
        assertEquals(1, pluginReport.inputChangeCount());
        assertEquals(0, pluginReport.outputChangeCount());
        assertTrue(pluginReport.changesInputs());
        assertFalse(pluginReport.changesOutputs());
    }

    @Test
    void projectionReportDoesNotMutateSourceProjection(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)), scaleAmountClause)
            .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
            .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(scaleAmountClauseType, scaleAmountTransformer);

        ProjectionReport first = engine.projectReport(new ExecutionContext(), raw);
        ProjectionReport second = engine.projectReport(new ExecutionContext(), raw);

        assertEquals(2L, raw.inputs().getFirst().content().amount());
        assertEquals(4L, first.projectedRecipe().inputs().getFirst().amount());
        assertEquals(4L, second.projectedRecipe().inputs().getFirst().amount());
    }

    @Test
    void combinedClausesTrackTypedOwnershipAcrossChannels(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)), FluidContainerUnpackClause.instance)
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)), scaleAmountClause)
            .output(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.25F))
            .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
            .build();
        raw.addClause(GuaranteedOutputClause.instance);

        ProjectionEngine engine = new ProjectionEngine();
        engine.addProjectionTransformer(GuaranteedOutputClause.type, GuaranteedOutputClause.projectionTransformer);
        engine.addContentTransformer(CirrusTypes.Clauses.fluidContainerUnpack, FluidContainerUnpackClause.transformer);
        engine.addContentTransformer(scaleAmountClauseType, scaleAmountTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        assertEquals(3, report.projectedRecipe().inputs().size());
        assertEquals(1, report.projectedRecipe().outputs().size());

        MutableList<ProjectionEffect> removed = removedEffects(report);
        MutableList<ProjectionEffect> added = addedEffects(report);
        MutableList<ProjectionEffect> replaced = replacedEffects(report);

        assertEquals(1, removed.size());
        assertEquals(1, added.size());
        assertEquals(2, replaced.size());

        boolean hasInputReplacedByUnwrap = false;
        boolean hasOutputRemovedBySkip = false;
        for (ProjectionEffect effect : replaced) {
            if (effect.source().clauseId().equals(FluidContainerUnpackClause.id)) {
                hasInputReplacedByUnwrap = effectChannel(effect) == EffectChannel.input;
            }
        }
        for (ProjectionEffect effect : removed) {
            if (effect.source().clauseId().equals(GuaranteedOutputClause.id)) {
                hasOutputRemovedBySkip = effectChannel(effect) == EffectChannel.output;
            }
        }
        assertTrue(hasInputReplacedByUnwrap);
        assertTrue(hasOutputRemovedBySkip);

        ProjectionEffect replacedEffect = replaced.detect(effect -> effect.source().clauseId().equals(scaleAmountClauseId));
        assertNotNull(replacedEffect);
        assertContentEffect(replacedEffect, scaleAmountClauseId, ProjectionEffectPhase.contentTransformer, EffectChannel.input, 1);
        assertEquals(2L, beforeAmount(replacedEffect));
        assertEquals(4L, afterAmount(replacedEffect));

        Set<Integer> addedIndices = new HashSet<>();
        for (ProjectionEffect effect : added) {
            addedIndices.add(effectIndex(effect));
            assertEquals(EffectChannel.input, effectChannel(effect));
            assertEquals(FluidContainerUnpackClause.id, effect.source().clauseId());
        }
        assertEquals(Set.of(2), addedIndices);
    }

    @Test
    void contentTransformerCanRemoveInputAndAddOutput(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)), mirrorToOutputClause)
            .output(OutputContent.fromItemStack(new ItemStack(Items.COBBLESTONE)))
            .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(mirrorToOutputClauseType, mirrorToOutputTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        assertTrue(report.projectedRecipe().inputs().isEmpty());
        assertEquals(2, report.projectedRecipe().outputs().size());

        MutableList<ProjectionEffect> removed = removedEffects(report);
        MutableList<ProjectionEffect> added = addedEffects(report);
        MutableList<ProjectionEffect> replaced = replacedEffects(report);

        assertEquals(1, removed.size());
        assertEquals(1, added.size());
        assertTrue(replaced.isEmpty());

        assertContentEffect(removed.getOnly(), mirrorToOutputClauseId, ProjectionEffectPhase.contentTransformer, EffectChannel.input, 0);
        ProjectionEffect addedEffect = added.getOnly();
        assertContentEffect(addedEffect, mirrorToOutputClauseId, ProjectionEffectPhase.contentTransformer, EffectChannel.output, 1);
        assertEquals(6L, afterAmount(addedEffect));
    }

    @Test
    void selectiveClauseApplicationKeepsChangedEffectIndicesStable(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 1)), scaleAmountClause)
            .input(InputContent.fromItemStack(new ItemStack(Items.COBBLESTONE, 1)))
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 3)), scaleAmountClause)
            .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
            .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(scaleAmountClauseType, scaleAmountTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        MutableList<ProjectionEffect> replaced = replacedEffects(report);
        assertEquals(2, replaced.size());

        Set<Integer> indices = new HashSet<>();
        for (ProjectionEffect effect : replaced) {
            assertEquals(scaleAmountClauseId, effect.source().clauseId());
            indices.add(effectIndex(effect));
        }
        assertEquals(Set.of(0, 2), indices);

        assertEquals(2L, report.projectedRecipe().inputs().get(0).amount());
        assertEquals(1L, report.projectedRecipe().inputs().get(1).amount());
        assertEquals(6L, report.projectedRecipe().inputs().get(2).amount());
    }

    @Test
    void semanticClauseNamesPublishedInCirrusTypes(MinecraftServer server) {
        assertSame(FluidContainerUnpackClause.type, CirrusTypes.Clauses.fluidContainerUnpack);
        assertSame(GuaranteedOutputClause.type, CirrusTypes.Clauses.guaranteedOutput);
        assertSame(ResolvePolicyClause.type, CirrusTypes.Clauses.resolvePolicy);
    }

    @Test
    void resolvePolicyGuardBlocksEffectsAndExposesFinding(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)))
            .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
            .build();

        ResolvePolicy policy = new ResolvePolicy(
            Lists.immutable.with(
                ResolveGuard.of(
                    guardMinInputAmountId,
                    (context, recipe) -> ResolveGuardResult.fail("input_amount_too_low")
                )
            ),
            Lists.immutable.with(resolveDoubleInputEffect)
        );
        raw.addClause(new ResolvePolicyClause(policy));

        ProjectionEngine engine = new ProjectionEngine();
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        assertFalse(report.runnable());
        assertEquals(1, report.guardFindings().size());
        var finding = report.guardFindings().getFirst();
        assertEquals(guardMinInputAmountId, finding.guardId());
        assertEquals("input_amount_too_low", finding.reasonCode());

        assertEquals(2L, report.projectedRecipe().inputs().getFirst().amount());
        assertTrue(report.effects().isEmpty());
    }

    @Test
    void resolvePolicyEffectsEmitTypedSourceAndRespectExtraOrder(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)))
            .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
            .build();

        ResolvePolicy policy = new ResolvePolicy(
            Lists.immutable.with(ResolveGuard.of(alwaysPassGuardId, (context, recipe) -> ResolveGuardResult.pass())),
            Lists.immutable.with(resolveAddThreeEffect, resolveDoubleInputEffect),
            Comparator.comparingInt(ResolveEffect::order)
        );
        raw.addClause(new ResolvePolicyClause(policy));

        ProjectionEngine engine = new ProjectionEngine();
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        assertTrue(report.runnable());
        assertEquals(1, report.guardFindings().size());
        assertTrue(report.guardFindings().getFirst().passed());
        assertEquals(7L, report.projectedRecipe().inputs().getFirst().amount());

        MutableList<ProjectionEffect> replaced = replacedEffects(report);
        assertEquals(2, replaced.size());

        ProjectionEffectSource first = replaced.get(0).source();
        assertEquals(ProjectionPlugin.builtin, first.plugin());
        assertEquals(ProjectionEffectPhase.resolveEffect, first.phase());
        assertEquals(resolveDoubleInputEffectId, first.resolveEffectId());
        assertEquals(0, first.resolveEffectOrder());
        assertEquals(1, first.resolveEffectDeclaredIndex());

        ProjectionEffectSource second = replaced.get(1).source();
        assertEquals(resolveAddThreeEffectId, second.resolveEffectId());
        assertEquals(1, second.resolveEffectOrder());
        assertEquals(0, second.resolveEffectDeclaredIndex());
    }

    @Test
    void disablingProjectionPluginSkipsOwnedEffects(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)), scaleAmountClause)
            .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
            .build();

        ExecutionContext context = new ExecutionContext()
            .set(ProjectionPlugin.builtin.enabledKey(), false);
        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(scaleAmountClauseType, scaleAmountTransformer);
        ProjectionReport report = engine.projectReport(context, raw);

        assertTrue(report.effects().isEmpty());
        assertEquals(2L, report.projectedRecipe().inputs().getFirst().amount());
    }

    private enum EffectChannel {
        input,
        output
    }

    private static MutableList<ProjectionEffect> addedEffects(ProjectionReport report) {
        MutableList<ProjectionEffect> effects = MutableLists.empty();
        for (ProjectionEffect effect : report.effects()) {
            if (effect instanceof InputInjected || effect instanceof OutputInjected) effects.add(effect);
        }
        return effects;
    }

    private static MutableList<ProjectionEffect> removedEffects(ProjectionReport report) {
        MutableList<ProjectionEffect> effects = MutableLists.empty();
        for (ProjectionEffect effect : report.effects()) {
            if (effect instanceof InputRejected || effect instanceof OutputRejected) effects.add(effect);
        }
        return effects;
    }

    private static MutableList<ProjectionEffect> replacedEffects(ProjectionReport report) {
        MutableList<ProjectionEffect> effects = MutableLists.empty();
        for (ProjectionEffect effect : report.effects()) {
            if (effect instanceof InputReplaced || effect instanceof OutputReplaced) effects.add(effect);
        }
        return effects;
    }

    private static void assertContentEffect(
        ProjectionEffect effect,
        Namespace clauseId,
        ProjectionEffectPhase phase,
        EffectChannel channel,
        int index
    ) {
        assertEquals(ProjectionPlugin.builtin, effect.source().plugin());
        assertEquals(clauseId, effect.source().clauseId());
        assertEquals(phase, effect.source().phase());
        assertEquals(channel, effectChannel(effect));
        assertEquals(index, effectIndex(effect));
    }

    private static @Nullable RecipeContent effectBefore(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.before();
        if (effect instanceof OutputEffect output) return output.before();
        return null;
    }

    private static @Nullable RecipeContent effectAfter(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.after();
        if (effect instanceof OutputEffect output) return output.after();
        return null;
    }

    private static EffectChannel effectChannel(ProjectionEffect effect) {
        if (effect instanceof InputEffect) return EffectChannel.input;
        if (effect instanceof OutputEffect) return EffectChannel.output;
        throw new IllegalArgumentException("Not a content effect: " + effect);
    }

    private static int effectIndex(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.index();
        if (effect instanceof OutputEffect output) return output.index();
        throw new IllegalArgumentException("Not a content effect: " + effect);
    }

    private static long beforeAmount(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.beforeAmount();
        if (effect instanceof OutputEffect output) return output.beforeAmount();
        return 0L;
    }

    private static long afterAmount(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.afterAmount();
        if (effect instanceof OutputEffect output) return output.afterAmount();
        return 0L;
    }

    private static long amountDelta(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.amountDelta();
        if (effect instanceof OutputEffect output) return output.amountDelta();
        return 0L;
    }

    private static @Nullable Double amountFactor(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.amountFactor();
        if (effect instanceof OutputEffect output) return output.amountFactor();
        return null;
    }

    private static final Namespace scaleAmountClauseId = Namespace.of(CirrusConstants.modId, "test/scale_amount");
    private static final ScaleAmountClause scaleAmountClause = new ScaleAmountClause(2.0D);
    private static final ClauseType scaleAmountClauseType = new ClauseType(
        scaleAmountClauseId,
        ProjectionPlugin.builtin,
        (entry, content) -> content instanceof StackContent<?, ?>
    );
    private static final ContentTransformer<StackContent<Item, ItemStack>, ScaleAmountClause> scaleAmountTransformer = ContentTransformer.of(
        ProjectionPlugin.builtin,
        (context, projection, content, asInput) -> asInput && content instanceof StackContent<?, ?> stack && stack.isItem(),
        (context, recipe, content, clause, asInput, increment, decrement) -> {
            long current = content.amount();
            long next = (long) Math.ceil(current * clause.factor());
            content.content().setAmount(next);
            return ContentTransformer.ActionResult.RETAIN;
        }
    );

    private static final Namespace mirrorToOutputClauseId = Namespace.of(CirrusConstants.modId, "test/mirror_to_output");
    private static final MirrorToOutputClause mirrorToOutputClause = new MirrorToOutputClause(3);
    private static final ClauseType mirrorToOutputClauseType = new ClauseType(
        mirrorToOutputClauseId,
        ProjectionPlugin.builtin,
        (entry, content) -> content instanceof StackContent<?, ?>
    );
    private static final ContentTransformer<StackContent<Item, ItemStack>, MirrorToOutputClause> mirrorToOutputTransformer = ContentTransformer.of(
        ProjectionPlugin.builtin,
        (context, projection, content, asInput) -> asInput && content instanceof StackContent<?, ?> stack && stack.isItem(),
        (context, recipe, content, clause, asInput, increment, decrement) -> {
            long mirroredAmount = Math.multiplyExact(content.amount(), clause.multiplier());
            increment.injectOutput(OutputContent.fromItemStack(new ItemStack(Items.STICK), mirroredAmount));
            return ContentTransformer.ActionResult.REMOVE;
        }
    );

    private record ScaleAmountClause(double factor) implements RecipeClause {
        @Override
        public Namespace id() {
            return scaleAmountClauseId;
        }

        @Override
        public ProjectionPlugin plugin() {
            return ProjectionPlugin.builtin;
        }
    }

    private record MirrorToOutputClause(int multiplier) implements RecipeClause {
        @Override
        public Namespace id() {
            return mirrorToOutputClauseId;
        }

        @Override
        public ProjectionPlugin plugin() {
            return ProjectionPlugin.builtin;
        }
    }

    private static final Namespace alwaysPassGuardId = Namespace.of(CirrusConstants.modId, "test/resolve_guard_pass");
    private static final Namespace guardMinInputAmountId = Namespace.of(CirrusConstants.modId, "test/resolve_guard_min_input_amount");
    private static final Namespace resolveAddThreeEffectId = Namespace.of(CirrusConstants.modId, "test/resolve_add_three");
    private static final Namespace resolveDoubleInputEffectId = Namespace.of(CirrusConstants.modId, "test/resolve_double_input");

    private static final ResolveEffect resolveAddThreeEffect = ResolveEffect.of(
        resolveAddThreeEffectId,
        10,
        ResolveScope.input,
        (context, inputs, outputs) -> {
            if (inputs.isEmpty()) return;
            var first = inputs.getFirst();
            inputs.set(0, (InputContent) first.copyWithAmount(first.amount() + 3L));
        }
    );

    private static final ResolveEffect resolveDoubleInputEffect = ResolveEffect.of(
        resolveDoubleInputEffectId,
        0,
        ResolveScope.input,
        (context, inputs, outputs) -> {
            if (inputs.isEmpty()) return;
            var first = inputs.getFirst();
            inputs.set(0, (InputContent) first.copyWithAmount(first.amount() * 2L));
        }
    );

    private static class ProjectionRecipeBuilder {
        private final MutableList<ClausedContent<InputContent>> inputs = MutableLists.empty();
        private final MutableList<ClausedContent<OutputContent>> outputs = MutableLists.empty();

        public ProjectionRecipeBuilder input(InputContent content) {
            inputs.add(ClausedContent.from(content));
            return this;
        }

        public ProjectionRecipeBuilder output(OutputContent content) {
            outputs.add(ClausedContent.from(content));
            return this;
        }

        public ProjectionRecipeBuilder input(InputContent content, RecipeClause... clauses) {
            ClausedContent<InputContent> from = ClausedContent.from(content);
            for (RecipeClause clause : clauses) from.clauses().addClause(clause);
            inputs.add(from);
            return this;
        }

        public RecipeProjection build() {
            var recipe = new ProjectionFixtureRecipe(inputs, outputs);
            return recipe.toRecipeProjection();
        }
    }

    record ProjectionFixtureRecipe(
        MutableList<ClausedContent<InputContent>> inputs,
        MutableList<ClausedContent<OutputContent>> outputs
    ) {
        public RecipeProjection toRecipeProjection() {
            return new RecipeProjection(
                RecipeEntry.of(TestRecipeSystem.instance, this),
                inputs.toImmutable(),
                outputs.toImmutable(),
                ProjectionSchema.empty(),
                ClauseSet.create()
            );
        }
    }

    @PluginMarker
    public static final class TestCirrusPlugin implements CirrusPlugin {
        @Override
        public @NotNull Namespace pluginId() {
            return Namespace.of(CirrusConstants.modId, "test");
        }

        @Override
        public void registerRecipeSystem(RecipeSystemRegister register) {
            register.registerSystem(TestRecipeSystem.instance);
        }
    }

    @NotNullByDefault
    static class TestRecipeSystem extends BasicRecipeSystem<ProjectionFixtureRecipe> {
        static final TestRecipeSystem instance = new TestRecipeSystem();

        TestRecipeSystem() {
            super(Namespace.of(CirrusConstants.modId, "test"));
        }

        @Override
        public Class<?> basicType() {
            return ProjectionFixtureRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(ProjectionFixtureRecipe recipe) {
            return null;
        }

        @Override
        public @Nullable ProjectionFixtureRecipe findRecipe(String uniqueId) {
            return null;
        }

        @Override
        public @Nullable ProjectionFixtureRecipe findRecipe(ResourceLocation registryName) {
            return null;
        }

        @Override
        public int hash(ProjectionFixtureRecipe recipe) {
            return recipe.hashCode();
        }

        @Override
        public boolean sameRecipe(ProjectionFixtureRecipe r1, ProjectionFixtureRecipe r2) {
            return r1.equals(r2);
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, ProjectionFixtureRecipe recipeHolder) {
            return ResolveResult.resolved(recipeHolder.toRecipeProjection());
        }

        @Override
        public @Unmodifiable RichIterable<ProjectionFixtureRecipe> allRecipes() {
            return MutableLists.empty();
        }
    }
}
