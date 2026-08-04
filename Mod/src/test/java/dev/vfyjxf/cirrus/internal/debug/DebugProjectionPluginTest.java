package dev.vfyjxf.cirrus.internal.debug;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputRejected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputReplaced;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputRejected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputInjected;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionReport;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.clause.DurabilityCostClause;
import dev.vfyjxf.cirrus.recipe.clause.FluidContainerUnpackClause;
import dev.vfyjxf.cirrus.recipe.clause.GuaranteedOutputClause;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class DebugProjectionPluginTest {

    @Test
    void debugProjectionPluginRegistersAConfigurableDevelopmentProbeRecipe() {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        DebugProjectionPlugin plugin = new DebugProjectionPlugin();
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerProjectionPlugin(builder);
        internal.registerRecipeClause(builder);
        plugin.registerProjectionPlugin(builder);
        plugin.registerProjectionContextOption(builder);
        plugin.registerRecipeSystem(builder);
        plugin.registerRecipeClause(builder);
        plugin.registerMachineEntry(builder);

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
        try {
            assertNotNull(api.findProjectionPlugin(DebugProjectionPlugin.plugin.id()));
            assertTrue(api.projectionContextOptions().anySatisfy(option -> option.key().equals(DebugProjectionPlugin.scaleKey)));
            assertTrue(api.projectionContextOptions().anySatisfy(option -> option.key().equals(DebugProjectionPlugin.extraOutputKey)));
            assertTrue(api.projectionContextOptions().anySatisfy(option -> option.key().equals(DebugProjectionPlugin.channelKey)));

            @SuppressWarnings("rawtypes")
            RecipeSystem system = api.findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            Object recipe = system.findRecipe("projection_probe");
            assertNotNull(recipe);
            @SuppressWarnings("unchecked")
            RecipeProjection projection = api.resolve(ResolveContext.empty(), system, recipe).projection();
            assertNotNull(projection);

            ExecutionContext context = new ExecutionContext()
                .set(DebugProjectionPlugin.scaleKey, 2L)
                .set(DebugProjectionPlugin.extraOutputKey, true)
                .set(DebugProjectionPlugin.channelKey, "input");
            ProjectionEngine engine = new ProjectionEngine();
            ProjectionReport report = engine.projectReport(context, projection);
            ProjectionReport secondReport = engine.projectReport(context, projection);
            ProjectionReport scaledReport = engine.projectReport(
                context.set(DebugProjectionPlugin.scaleKey, 3L),
                projection
            );
            ProjectionReport restoredReport = engine.projectReport(
                context.set(DebugProjectionPlugin.scaleKey, 2L),
                projection
            );

            assertTrue(report.effects().size() >= 2);
            assertTrue(report.effects().anySatisfy(effect -> effect instanceof InputReplaced));
            assertEquals(1, report.effects().count(effect -> effect instanceof OutputInjected));
            assertEquals(4L, ((InputContent) report.projectedRecipe().inputs().getFirst()).amount());
            assertEquals(4L, ((InputContent) secondReport.projectedRecipe().inputs().getFirst()).amount());
            assertEquals(6L, ((InputContent) scaledReport.projectedRecipe().inputs().getFirst()).amount());
            assertEquals(4L, ((InputContent) restoredReport.projectedRecipe().inputs().getFirst()).amount());
            assertEquals(2L, projection.inputs().getFirst().content().amount());
            assertTrue(report.pluginReport(DebugProjectionPlugin.plugin).inputChangeCount() >= 1);
            assertTrue(report.pluginReport(DebugProjectionPlugin.plugin).outputChangeCount() >= 1);
        } finally {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    @Test
    void debugProjectionPluginPublishesBuiltInClauseProbeRecipes(MinecraftServer server) {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = attachDebugRecipeApi();
        try {
            @SuppressWarnings("unchecked")
            RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system =
                (RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe>) api.findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            assertEquals(6 + DebugProjectionPlugin.longChainDepth + 1, system.allRecipes().size());

            ProjectionReport unpack = report(api, system, "fluid_container_unpack");
            assertTrue(hasClauseEffect(unpack, InputReplaced.class, FluidContainerUnpackClause.id));
            assertEquals(2, unpack.projectedRecipe().inputs().size());

            ProjectionReport durability = report(api, system, "durability_cost");
            assertTrue(hasClauseEffect(durability, InputRejected.class, DurabilityCostClause.id));
            assertTrue(durability.projectedRecipe().inputs().isEmpty());

            ProjectionReport guaranteed = report(api, system, "guaranteed_output");
            assertTrue(hasClauseEffect(guaranteed, OutputRejected.class, GuaranteedOutputClause.id));
            assertEquals(1, guaranteed.projectedRecipe().outputs().size());

            ProjectionReport resolveEffect = report(api, system, "resolve_policy_effect");
            assertTrue(resolveEffect.runnable());
            assertEquals(5L, ((InputContent) resolveEffect.projectedRecipe().inputs().getFirst()).amount());
            assertTrue(resolveEffect.effects().anySatisfy(effect ->
                DebugProjectionPlugin.resolveBoostInputId.equals(effect.source().resolveEffectId())
            ));

            ProjectionReport resolveGuard = report(api, system, "resolve_policy_guard");
            assertFalse(resolveGuard.runnable());
            assertEquals(1, resolveGuard.guardFindings().size());
            assertEquals(DebugProjectionPlugin.resolveGuardFailId, resolveGuard.guardFindings().getFirst().guardId());
            assertEquals("debug_guard_failed", resolveGuard.guardFindings().getFirst().reasonCode());
            assertTrue(resolveGuard.effects().isEmpty());
            assertEquals(2L, ((InputContent) resolveGuard.projectedRecipe().inputs().getFirst()).amount());
        } finally {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    @Test
    void debugProjectionPluginPublishesLongCalculatorStressChain(MinecraftServer server) {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previous = RecipeApiImpl.instance();
        ContentApiImpl previousContent = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = attachDebugRecipeApi();
        try {
            @SuppressWarnings("unchecked")
            RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system =
                (RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe>) api.findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            DebugProjectionPlugin.DebugProjectionRecipe targetRecipe = system.findRecipe(DebugProjectionPlugin.longChainTargetId);
            assertNotNull(targetRecipe);

            ProjectionReport targetReport = report(api, system, DebugProjectionPlugin.longChainTargetId);
            assertTrue(targetReport.runnable());
            assertTrue(targetReport.effects().anySatisfy(effect ->
                DebugProjectionPlugin.resolveBoostInputId.equals(effect.source().resolveEffectId())
            ));
            assertTrue(targetReport.projectedRecipe().outputs().anySatisfy(DebugProjectionPluginTest::isEnergyOutput));
            assertTrue(targetReport.projectedRecipe().outputs().anySatisfy(DebugProjectionPluginTest::isTimeOutput));

            ProcessGraph graph = CalculatorProcessGraphBuilder.build(RecipeEntry.of(system, targetRecipe), 1);

            assertTrue(graph.nodeCount() >= DebugProjectionPlugin.longChainDepth);
            assertTrue(graph.edgeCount() >= DebugProjectionPlugin.longChainDepth);
            assertTrue(graph.nodes().stream().anyMatch(node -> node.durationTicks() > 0));
        } finally {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    private static RecipeApiImpl attachDebugRecipeApi() {
        DebugProjectionPlugin plugin = new DebugProjectionPlugin();
        InternalPlugin internal = new InternalPlugin();
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        internal.registerProjectionPlugin(builder);
        internal.registerRecipeClause(builder);
        plugin.registerProjectionPlugin(builder);
        plugin.registerProjectionContextOption(builder);
        plugin.registerRecipeSystem(builder);
        plugin.registerRecipeClause(builder);
        plugin.registerMachineEntry(builder);
        RecipeApiImpl api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
        return api;
    }

    private static ProjectionReport report(
        RecipeApi api,
        RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system,
        String recipeId
    ) {
        DebugProjectionPlugin.DebugProjectionRecipe recipe = system.findRecipe(recipeId);
        assertNotNull(recipe);
        RecipeProjection projection = api.resolve(ResolveContext.empty(), system, recipe).projection();
        assertNotNull(projection);
        return new ProjectionEngine().projectReport(new ExecutionContext(), projection);
    }

    private static boolean hasClauseEffect(
        ProjectionReport report,
        Class<? extends ProjectionEffect> effectType,
        Namespace clauseId
    ) {
        return report.effects().anySatisfy(effect ->
            effectType.isInstance(effect) && clauseId.equals(effect.source().clauseId())
        );
    }

    private static boolean isEnergyOutput(OutputContent output) {
        return output instanceof dev.vfyjxf.cirrus.api.recipe.content.ValueContent<?> value
            && value.type().typeId().equals(Namespace.of("calculator_cirrus", "energy"));
    }

    private static boolean isTimeOutput(OutputContent output) {
        return output instanceof dev.vfyjxf.cirrus.api.recipe.content.ValueContent<?> value
            && value.type().typeId().equals(Namespace.of("calculator_cirrus", "time"));
    }

    private static void attachContentApi() {
        ContentRegistryBuilder builder = new ContentRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerContentType(builder);
        internal.registerContentVariant(builder);
        internal.registerContentResolver(builder);
        ContentApiImpl.attach(new ContentApiImpl(builder));
    }
}
