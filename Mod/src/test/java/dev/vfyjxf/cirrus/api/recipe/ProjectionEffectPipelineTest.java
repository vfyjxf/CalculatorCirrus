package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.*;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.*;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.*;
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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
public class ProjectionEffectPipelineTest {

    //region single clause scenarios

    @Test
    void testUnwrapClauseProducesInputRejectionAndTwoInputInjections(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
                .input(InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)), FluidContainerUnpackClause.instance)
                .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
                .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(CirrusTypes.Clauses.fluidContainerUnpack, FluidContainerUnpackClause.transformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);
        ProjectedRecipe projected = report.recipe();

        MutableList<ProjectionEffect> rejected = effectsByType(report, ProjectionEffectTypes.contentRejected);
        MutableList<ProjectionEffect> injected = effectsByType(report, ProjectionEffectTypes.contentInjected);

        assertEquals(1, rejected.size(), "unwrap should reject original container input");
        assertEquals(2, injected.size(), "unwrap should inject remaining container + fluid input");
        assertEquals(2, projected.inputs().size(), "raw single input should become two transformed inputs");

        ProjectionEffect rejectedEffect = rejected.getOnly();
        assertEffectSourcePath(rejectedEffect, FluidContainerUnpackClause.id, "contentTransformer", "input", 0);
        assertNotNull(rejectedEffect.change().before());
        assertNull(rejectedEffect.change().after());
        assertEquals(1L, rejectedEffect.meta().get(ProjectionEffectMetaKeys.beforeAmount));
        assertFalse(rejectedEffect.meta().has(ProjectionEffectMetaKeys.afterAmount));

        Set<Integer> injectedIndices = new HashSet<>();
        for (ProjectionEffect injectedEffect : injected) {
            assertEffectSourcePath(
                    injectedEffect,
                    FluidContainerUnpackClause.id,
                    "contentTransformer",
                    "input",
                    injectedEffect.path().index()
            );
            assertNull(injectedEffect.change().before());
            assertNotNull(injectedEffect.change().after());
            assertTrue(injectedEffect.meta().has(ProjectionEffectMetaKeys.afterAmount));
            assertTrue(injectedEffect.meta().has(ProjectionEffectMetaKeys.afterType));
            injectedIndices.add(injectedEffect.path().index());
        }
        assertEquals(Set.of(0, 1), injectedIndices);
    }

    @Test
    void testSkipChancedOutputUsesProjectionTransformerSource(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK)))
                .output(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.25F))
                .build();
        raw.addClause(GuaranteedOutputClause.instance);

        ProjectionEngine engine = new ProjectionEngine();
        engine.addProjectionTransformer(GuaranteedOutputClause.type, GuaranteedOutputClause.projectionTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        MutableList<ProjectionEffect> rejected = effectsByType(report, ProjectionEffectTypes.contentRejected);
        assertEquals(1, rejected.size(), "skip clause should reject chanced output");
        assertTrue(report.recipe().outputs().isEmpty(), "chanced output should be removed from projected output list");

        ProjectionEffect rejectedEffect = rejected.getOnly();
        assertEffectSourcePath(rejectedEffect, GuaranteedOutputClause.id, "projectionTransformer", "output", 0);
        assertNotNull(rejectedEffect.change().before());
        assertNull(rejectedEffect.change().after());
        assertTrue(rejectedEffect.meta().has(ProjectionEffectMetaKeys.beforeAmount));
        assertFalse(rejectedEffect.meta().has(ProjectionEffectMetaKeys.afterAmount));
    }

    @Test
    void testScaleAmountClauseEmitsChangedEffectWithDeltaAndFactor(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)), scaleAmountClause)
                .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
                .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(scaleAmountClauseType, scaleAmountTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        MutableList<ProjectionEffect> changed = effectsByType(report, ProjectionEffectTypes.contentChanged);
        assertEquals(1, changed.size(), "scale clause should emit one contentChanged effect");

        ProjectionEffect changedEffect = changed.getOnly();
        assertEffectSourcePath(changedEffect, scaleAmountClauseId, "contentTransformer", "input", 0);
        assertNotNull(changedEffect.change().before());
        assertNotNull(changedEffect.change().after());

        assertEquals(2L, changedEffect.meta().get(ProjectionEffectMetaKeys.beforeAmount));
        assertEquals(4L, changedEffect.meta().get(ProjectionEffectMetaKeys.afterAmount));
        assertEquals(2L, changedEffect.meta().get(ProjectionEffectMetaKeys.amountDelta));

        Double factor = changedEffect.meta().get(ProjectionEffectMetaKeys.amountFactor);
        assertNotNull(factor);
        assertEquals(2.0D, factor, 0.00001D);
        assertEquals(4L, report.recipe().inputs().get(0).amount());
    }

    //endregion

    //region composed scenarios

    @Test
    void testCombinedClausesTrackEffectOwnershipAcrossChannels(MinecraftServer server) {
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

        assertEquals(3, report.recipe().inputs().size(), "second input retained, first input replaced by two injections");
        assertEquals(1, report.recipe().outputs().size(), "chanced output rejected, constant output retained");

        MutableList<ProjectionEffect> rejected = effectsByType(report, ProjectionEffectTypes.contentRejected);
        MutableList<ProjectionEffect> injected = effectsByType(report, ProjectionEffectTypes.contentInjected);
        MutableList<ProjectionEffect> changed = effectsByType(report, ProjectionEffectTypes.contentChanged);

        assertEquals(2, rejected.size());
        assertEquals(2, injected.size());
        assertEquals(1, changed.size());

        boolean hasInputRejectedByUnwrap = false;
        boolean hasOutputRejectedBySkip = false;
        for (ProjectionEffect rejectedEffect : rejected) {
            if (rejectedEffect.source().clauseId().equals(FluidContainerUnpackClause.id)) {
                hasInputRejectedByUnwrap = rejectedEffect.path().channel().equals("input");
            }
            if (rejectedEffect.source().clauseId().equals(GuaranteedOutputClause.id)) {
                hasOutputRejectedBySkip = rejectedEffect.path().channel().equals("output");
            }
        }
        assertTrue(hasInputRejectedByUnwrap, "missing input rejection from unwrap clause");
        assertTrue(hasOutputRejectedBySkip, "missing output rejection from skip clause");

        ProjectionEffect changedEffect = changed.getOnly();
        assertEffectSourcePath(changedEffect, scaleAmountClauseId, "contentTransformer", "input", 1);
        assertEquals(2L, changedEffect.meta().get(ProjectionEffectMetaKeys.beforeAmount));
        assertEquals(4L, changedEffect.meta().get(ProjectionEffectMetaKeys.afterAmount));

        Set<Integer> injectedIndices = new HashSet<>();
        for (ProjectionEffect injectedEffect : injected) {
            injectedIndices.add(injectedEffect.path().index());
            assertEquals("input", injectedEffect.path().channel());
            assertEquals(FluidContainerUnpackClause.id, injectedEffect.source().clauseId());
        }
        assertEquals(Set.of(1, 2), injectedIndices);
    }

    @Test
    void testContentTransformerCanRejectInputAndInjectOutput(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)), mirrorToOutputClause)
                .output(OutputContent.fromItemStack(new ItemStack(Items.COBBLESTONE)))
                .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(mirrorToOutputClauseType, mirrorToOutputTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        assertTrue(report.recipe().inputs().isEmpty(), "source input should be removed");
        assertEquals(2, report.recipe().outputs().size(), "original output + mirrored injected output");

        MutableList<ProjectionEffect> rejected = effectsByType(report, ProjectionEffectTypes.contentRejected);
        MutableList<ProjectionEffect> injected = effectsByType(report, ProjectionEffectTypes.contentInjected);
        MutableList<ProjectionEffect> changed = effectsByType(report, ProjectionEffectTypes.contentChanged);

        assertEquals(1, rejected.size());
        assertEquals(1, injected.size());
        assertTrue(changed.isEmpty());

        ProjectionEffect rejectedEffect = rejected.getOnly();
        assertEffectSourcePath(rejectedEffect, mirrorToOutputClauseId, "contentTransformer", "input", 0);

        ProjectionEffect injectedEffect = injected.getOnly();
        assertEffectSourcePath(injectedEffect, mirrorToOutputClauseId, "contentTransformer", "output", 1);
        assertEquals(6L, injectedEffect.meta().get(ProjectionEffectMetaKeys.afterAmount));
    }

    @Test
    void testSelectiveClauseApplicationKeepsChangedEffectIndicesStable(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 1)), scaleAmountClause)
                .input(InputContent.fromItemStack(new ItemStack(Items.COBBLESTONE, 1)))
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 3)), scaleAmountClause)
                .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
                .build();

        ProjectionEngine engine = new ProjectionEngine();
        engine.addContentTransformer(scaleAmountClauseType, scaleAmountTransformer);
        ProjectionReport report = engine.projectReport(new ExecutionContext(), raw);

        MutableList<ProjectionEffect> changed = effectsByType(report, ProjectionEffectTypes.contentChanged);
        assertEquals(2, changed.size(), "only inputs tagged by scale clause should change");

        Set<Integer> indices = new HashSet<>();
        for (ProjectionEffect changedEffect : changed) {
            assertEquals(scaleAmountClauseId, changedEffect.source().clauseId());
            indices.add(changedEffect.path().index());
        }
        assertEquals(Set.of(0, 2), indices);

        assertEquals(2L, report.recipe().inputs().get(0).amount());
        assertEquals(1L, report.recipe().inputs().get(1).amount());
        assertEquals(6L, report.recipe().inputs().get(2).amount());
    }

    @Test
    void testSemanticClauseNamesPublishedInCirrusTypes(MinecraftServer server) {
        assertSame(FluidContainerUnpackClause.type, CirrusTypes.Clauses.fluidContainerUnpack);
        assertSame(GuaranteedOutputClause.type, CirrusTypes.Clauses.guaranteedOutput);
        assertSame(ResolvePolicyClause.type, CirrusTypes.Clauses.resolvePolicy);
    }

    @Test
    void testResolvePolicyGuardBlocksEffectsAndExposesFinding(MinecraftServer server) {
        RecipeProjection raw = new ProjectionRecipeBuilder()
                .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)))
                .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
                .build();

        ResolvePolicy policy = new ResolvePolicy(
                Lists.immutable.with(
                        ResolveGuard.of(
                                guardMinInputAmountId,
                                (context, recipe) -> ResolveGuardResult.fail("input_amount_too_low", EffectMeta.empty())
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

        assertEquals(2L, report.recipe().inputs().getFirst().amount(), "guard blocked effect, amount should stay unchanged");
        assertTrue(report.effects().isEmpty(), "blocked resolve effect should emit no projection effect");
    }

    @Test
    void testResolvePolicyEffectsEmitObservableMetaAndRespectExtraOrder(MinecraftServer server) {
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

        assertEquals(7L, report.recipe().inputs().getFirst().amount());

        MutableList<ProjectionEffect> changed = effectsByType(report, ProjectionEffectTypes.contentChanged);
        assertEquals(2, changed.size(), "two resolve effects should each emit one changed effect");

        ProjectionEffect first = changed.get(0);
        assertEquals("resolveEffect", first.source().phase());
        assertEquals(resolveDoubleInputEffectId.toString(), first.meta().get(ProjectionEffectMetaKeys.resolveEffect));
        assertEquals(0, first.meta().get(ProjectionEffectMetaKeys.resolveEffectOrder));
        assertEquals(1, first.meta().get(ProjectionEffectMetaKeys.resolveEffectDeclaredIndex));

        ProjectionEffect second = changed.get(1);
        assertEquals(resolveAddThreeEffectId.toString(), second.meta().get(ProjectionEffectMetaKeys.resolveEffect));
        assertEquals(1, second.meta().get(ProjectionEffectMetaKeys.resolveEffectOrder));
        assertEquals(0, second.meta().get(ProjectionEffectMetaKeys.resolveEffectDeclaredIndex));
    }

    //endregion

    //region assertions helper

    private static MutableList<ProjectionEffect> effectsByType(ProjectionReport report, EffectType effectType) {
        MutableList<ProjectionEffect> effects = MutableLists.empty();
        for (ProjectionEffect effect : report.effects()) {
            if (effect.type().equals(effectType)) effects.add(effect);
        }
        return effects;
    }

    private static void assertEffectSourcePath(
            ProjectionEffect effect,
            Namespace clauseId,
            String phase,
            String channel,
            int index
    ) {
        assertEquals(clauseId, effect.source().clauseId());
        assertEquals(phase, effect.source().phase());
        assertEquals(channel, effect.path().channel());
        assertEquals(index, effect.path().index());
        assertEquals("content", effect.path().field());
    }

    //endregion

    //region test clauses

    private static final Namespace scaleAmountClauseId = Namespace.of(CirrusConstants.MOD_ID, "test/scale_amount");
    private static final ScaleAmountClause scaleAmountClause = new ScaleAmountClause(2.0D);
    private static final ClauseType scaleAmountClauseType = new ClauseType(
            scaleAmountClauseId,
            (entry, content) -> content instanceof StackContent<?, ?>
    );
    private static final ContentTransformer<StackContent<Item, ItemStack>, ScaleAmountClause> scaleAmountTransformer = ContentTransformer.of(
            (context, projection, content, asInput) -> asInput && content instanceof StackContent<?, ?> stack && stack.isItem(),
            (context, recipe, content, clause, asInput, increment, decrement) -> {
                long current = content.amount();
                long next = (long) Math.ceil(current * clause.factor());
                content.content().setAmount(next);
                return ContentTransformer.ActionResult.RETAIN;
            }
    );

    private static final Namespace mirrorToOutputClauseId = Namespace.of(CirrusConstants.MOD_ID, "test/mirror_to_output");
    private static final MirrorToOutputClause mirrorToOutputClause = new MirrorToOutputClause(3);
    private static final ClauseType mirrorToOutputClauseType = new ClauseType(
            mirrorToOutputClauseId,
            (entry, content) -> content instanceof StackContent<?, ?>
    );
    private static final ContentTransformer<StackContent<Item, ItemStack>, MirrorToOutputClause> mirrorToOutputTransformer = ContentTransformer.of(
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
    }

    private record MirrorToOutputClause(int multiplier) implements RecipeClause {
        @Override
        public Namespace id() {
            return mirrorToOutputClauseId;
        }
    }

    private static final Namespace alwaysPassGuardId = Namespace.of(CirrusConstants.MOD_ID, "test/resolve_guard_pass");
    private static final Namespace guardMinInputAmountId = Namespace.of(CirrusConstants.MOD_ID, "test/resolve_guard_min_input_amount");
    private static final Namespace resolveAddThreeEffectId = Namespace.of(CirrusConstants.MOD_ID, "test/resolve_add_three");
    private static final Namespace resolveDoubleInputEffectId = Namespace.of(CirrusConstants.MOD_ID, "test/resolve_double_input");

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

    //endregion

    //region projection fixture

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

        public ProjectionRecipeBuilder output(OutputContent content, RecipeClause... clauses) {
            ClausedContent<OutputContent> from = ClausedContent.from(content);
            for (RecipeClause clause : clauses) from.clauses().addClause(clause);
            outputs.add(from);
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
            return new RecipeProjection(RecipeEntry.of(TestRecipeSystem.instance, this), inputs.toImmutable(), outputs.toImmutable());
        }
    }

    //endregion

    //region plugin fixture

    @PluginMarker
    public static final class TestCirrusPlugin implements CirrusPlugin {

        @Override
        public @NotNull Namespace pluginId() {
            return Namespace.of(CirrusConstants.MOD_ID, "test");
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
            super(Namespace.of(CirrusConstants.MOD_ID, "test"));
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
        public RecipeProjection resolveRecipe(ProjectionFixtureRecipe recipeHolder) {
            return recipeHolder.toRecipeProjection();
        }

        @Override
        public @Unmodifiable RichIterable<ProjectionFixtureRecipe> allRecipes() {
            return MutableLists.empty();
        }
    }

    //endregion

}
