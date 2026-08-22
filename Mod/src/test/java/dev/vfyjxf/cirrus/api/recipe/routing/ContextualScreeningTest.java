package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ContextualRecipeRules;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ContextualRuleContext;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the contextual screening rules ({@code BuiltInContextualRules}):
 * penalties that depend on the live request rather than the recipe's static
 * shape — target loop-family conversions deeper in the plan tree, and machine
 * ownership against the request stock.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class ContextualScreeningTest {

    @Test
    void targetFamilyConversionPenalizesDeeperInFamilySteps(MinecraftServer server) {
        RoutingService service = loopFamilyService();
        RecipeGraph.Node dyeWhite = node(service.graph(), "dye_white");
        RecipeGraph.Node greenDirect = node(service.graph(), "green_direct");
        RecipeGraph.Node glowstoneSource = node(service.graph(), "glowstone_source");
        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .targetRecipe(Objects.requireNonNull(RoutingTestFixtures.findRecipeById(service.graph(), "dye_white")), 1)
            .build();

        // depth-1 candidate consuming a different family member (red_concrete)
        // while the target (white_concrete) belongs to the loop family:
        // penalized. A candidate consuming the target resource itself is
        // reproduction, not conversion, and correctly ignored by the rule.
        double conversionPenalty = penaltyOf(service, request, dyeWhite, "minecraft:white_concrete", 1);
        assertEquals(5_000.0d, conversionPenalty, "in-family conversion below the root must be penalized");

        // the same candidate at the root is left to the intra-loop guard
        assertEquals(0.0d, penaltyOf(service, request, dyeWhite, "minecraft:white_concrete", 0));

        // the direct craft consumes glowstone (outside the family): no opinion
        assertEquals(0.0d, penaltyOf(service, request, greenDirect, "minecraft:white_concrete", 1));

        // a candidate outside the family entirely: no opinion
        assertEquals(0.0d, penaltyOf(service, request, glowstoneSource, "minecraft:white_concrete", 1));
    }

    @Test
    void targetFamilyConversionSilentWhenTargetOutsideFamily(MinecraftServer server) {
        RoutingService service = loopFamilyService();
        RecipeGraph.Node dyeWhite = node(service.graph(), "dye_white");
        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .targetRecipe(Objects.requireNonNull(RoutingTestFixtures.findRecipeById(service.graph(), "glowstone_source")), 1)
            .build();

        // target is glowstone (not a looping resource): rule stays silent
        assertEquals(0.0d, penaltyOf(service, request, dyeWhite, "minecraft:glowstone", 1));
    }

    @Test
    void missingMachinePenalizedOnlyWhenStockProvesAbsence(MinecraftServer server) {
        RoutingService service = machinedService();
        RecipeGraph.Node viaSmoker = node(service.graph(), "a_via_smoker");
        RoutingRequest.Builder requestBuilder = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .targetRecipe(Objects.requireNonNull(RoutingTestFixtures.findRecipeById(service.graph(), "target")), 1);

        // empty stock: ownership unknown, rule stays silent
        assertEquals(0.0d, penaltyOf(service, requestBuilder.build(), viaSmoker, "minecraft:torch", 1));

        // stock without the machine: penalized
        Stock withoutSmoker = Stock.empty();
        withoutSmoker.add(ContentStack.fromStack(new ItemStack(Items.COAL, 8)));
        assertEquals(
            2_000.0d,
            penaltyOf(service, requestBuilder.stock(withoutSmoker.copy()).build(), viaSmoker, "minecraft:torch", 1)
        );

        // stock with the machine: no opinion
        Stock withSmoker = withoutSmoker.copy();
        withSmoker.add(ContentStack.fromStack(new ItemStack(Items.SMOKER, 1)));
        assertEquals(
            0.0d,
            penaltyOf(service, requestBuilder.stock(withSmoker).build(), viaSmoker, "minecraft:torch", 1)
        );
    }

    @Test
    void plannerPrefersOwnedMachinePath(MinecraftServer server) {
        RoutingService service = machinedService();
        RecipeEntry<?> target = RoutingTestFixtures.findRecipeById(service.graph(), "target");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.COAL, 8)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.FURNACE, 1)));

        RoutingOutcome outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.success() || outcome.partial());
        // without the contextual penalty the alphabetical tie-break would
        // pick "a_via_smoker"; the missing-machine penalty must flip it.
        assertTrue(executesRecipe(outcome, "z_via_furnace"), "owned-machine path should win");
        assertFalse(executesRecipe(outcome, "a_via_smoker"), "missing-machine path must be avoided");
    }

    //region helpers

    private static double penaltyOf(
        RoutingService service,
        RoutingRequest request,
        RecipeGraph.Node candidate,
        String targetResourceId,
        int depth
    ) {
        // the built-in rules key off the candidate and the request, not the
        // demanded input — a placeholder demand keeps the context valid.
        InputContent demand = InputContent.fromItemStack(new ItemStack(Items.TORCH), 1);
        return ContextualRecipeRules.totalPenalty(new ContextualRuleContext(
            service,
            request,
            candidate,
            demand,
            null,
            targetResourceId,
            depth
        ));
    }

    private static RecipeGraph.Node node(RecipeGraph graph, String recipePath) {
        for (RecipeGraph.Node node : graph.stableNodes()) {
            if (node.recipe().id() != null && recipePath.equals(node.recipe().id().getPath())) {
                return node;
            }
        }
        throw new AssertionError("recipe not in graph: " + recipePath);
    }

    /** The four-color dye loop plus a direct green craft and a neutral recipe. */
    private static RoutingService loopFamilyService() {
        return RoutingTestFixtures.service(
            dye("dye_white", Items.RED_CONCRETE, Items.WHITE_CONCRETE),
            dye("dye_red", Items.BLUE_CONCRETE, Items.RED_CONCRETE),
            dye("dye_blue", Items.GREEN_CONCRETE, Items.BLUE_CONCRETE),
            dye("dye_green", Items.WHITE_CONCRETE, Items.GREEN_CONCRETE),
            new RoutingTestFixtures.FakeRecipe(
                "green_direct",
                List.of(RoutingTestFixtures.in(Items.GLOWSTONE, 1)),
                List.of(RoutingTestFixtures.out(Items.GREEN_CONCRETE, 1))
            ),
            new RoutingTestFixtures.FakeRecipe(
                "glowstone_source",
                List.of(RoutingTestFixtures.in(Items.REDSTONE, 1)),
                List.of(RoutingTestFixtures.out(Items.GLOWSTONE, 1))
            )
        );
    }

    private static RoutingTestFixtures.FakeRecipe dye(String id, Item input, Item output) {
        return new RoutingTestFixtures.FakeRecipe(
            id,
            List.of(RoutingTestFixtures.in(input, 1)),
            List.of(RoutingTestFixtures.out(output, 1))
        );
    }

    /**
     * Two systems with distinct convention machines: the smoker path sorts
     * first alphabetically, so only the missing-machine penalty can make the
     * furnace path win.
     */
    private static RoutingService machinedService() {
        MachinedSystem smokerSystem = new MachinedSystem(
            "smoker",
            MachineEntry.fromItemStack(new ItemStack(Items.SMOKER)),
            new RoutingTestFixtures.FakeRecipe(
                "a_via_smoker",
                List.of(RoutingTestFixtures.in(Items.COAL, 1)),
                List.of(RoutingTestFixtures.out(Items.TORCH, 1))
            )
        );
        MachinedSystem furnaceSystem = new MachinedSystem(
            "furnace",
            MachineEntry.fromItemStack(new ItemStack(Items.FURNACE)),
            new RoutingTestFixtures.FakeRecipe(
                "z_via_furnace",
                List.of(RoutingTestFixtures.in(Items.COAL, 1)),
                List.of(RoutingTestFixtures.out(Items.TORCH, 1))
            ),
            new RoutingTestFixtures.FakeRecipe(
                "target",
                List.of(RoutingTestFixtures.in(Items.TORCH, 1)),
                List.of(RoutingTestFixtures.out(Items.STONE_BUTTON, 1))
            )
        );
        var entries = Lists.mutable.<RecipeEntry<?>>empty();
        smokerSystem.allRecipes().forEach(recipe -> entries.add(RecipeEntry.of(smokerSystem, recipe)));
        furnaceSystem.allRecipes().forEach(recipe -> entries.add(RecipeEntry.of(furnaceSystem, recipe)));
        return new RoutingService(RecipeGraph.buildGraph(entries));
    }

    private static boolean executesRecipe(RoutingOutcome outcome, String recipePath) {
        if (outcome.plan() == null) {
            return false;
        }
        for (PlanAction action : outcome.plan().actions()) {
            if (action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && recipePath.equals(execute.recipe().id().getPath())) {
                return true;
            }
        }
        return false;
    }

    private static final class MachinedSystem implements RecipeSystem<RoutingTestFixtures.FakeRecipe> {
        private final String name;
        private final MachineEntry machine;
        private final RichIterable<RoutingTestFixtures.FakeRecipe> recipes;

        private MachinedSystem(String name, MachineEntry machine, RoutingTestFixtures.FakeRecipe... recipes) {
            this.name = name;
            this.machine = machine;
            this.recipes = Lists.immutable.of(recipes);
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("test", name);
        }

        @Override
        public Class<?> basicType() {
            return RoutingTestFixtures.FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(RoutingTestFixtures.FakeRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable RoutingTestFixtures.FakeRecipe findRecipe(String uniqueId) {
            for (RoutingTestFixtures.FakeRecipe recipe : recipes) {
                if (recipe.id().equals(uniqueId)) {
                    return recipe;
                }
            }
            return null;
        }

        @Override
        public @Nullable RoutingTestFixtures.FakeRecipe findRecipe(ResourceLocation registryName) {
            return findRecipe(registryName.getPath());
        }

        @Override
        public int hash(RoutingTestFixtures.FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(RoutingTestFixtures.FakeRecipe first, RoutingTestFixtures.FakeRecipe second) {
            return Objects.equals(first.id(), second.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, RoutingTestFixtures.FakeRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public MachineEntry conventionMachine() {
            return machine;
        }

        @Override
        public RichIterable<RoutingTestFixtures.FakeRecipe> allRecipes() {
            return recipes;
        }
    }

    //endregion
}
