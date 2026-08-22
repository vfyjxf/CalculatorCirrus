package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.recipe.routing.topology.GraphTopology;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the {@link PlannerMode#auto} unified entry: resource-level topology
 * (SCC) analysis, cone-based planner dispatch, the exact DAG planner beating
 * the greedy one, and loop-family (recolor) handling.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class AutoPlannerDispatchTest {

    @Test
    void topologyDetectsLoopFamilyAndScopesCones(MinecraftServer server) {
        var whiteFromRed = recipe("dye_white", Items.RED_CONCRETE, Items.WHITE_CONCRETE);
        var redFromBlue = recipe("dye_red", Items.BLUE_CONCRETE, Items.RED_CONCRETE);
        var blueFromGreen = recipe("dye_blue", Items.GREEN_CONCRETE, Items.BLUE_CONCRETE);
        var greenFromWhite = recipe("dye_green", Items.WHITE_CONCRETE, Items.GREEN_CONCRETE);
        var greenDirect = new RoutingTestFixtures.FakeRecipe(
            "green_direct",
            List.of(RoutingTestFixtures.in(Items.GLOWSTONE, 1)),
            List.of(RoutingTestFixtures.out(Items.GREEN_CONCRETE, 1))
        );
        RoutingService service = RoutingTestFixtures.service(
            whiteFromRed, redFromBlue, blueFromGreen, greenFromWhite, greenDirect
        );
        GraphTopology topology = service.topology();

        assertTrue(topology.isLoopingResource("minecraft:white_concrete"));
        assertTrue(topology.isLoopingResource("minecraft:green_concrete"));
        assertFalse(topology.isLoopingResource("minecraft:glowstone"));
        assertTrue(topology.sharesLoopingScc("minecraft:white_concrete", "minecraft:blue_concrete"));
        assertFalse(topology.sharesLoopingScc("minecraft:white_concrete", "minecraft:glowstone"));
        assertEquals(1, topology.nonTrivialSccCount());

        BitSet allAllowed = new BitSet(topology.nodeCount());
        allAllowed.set(0, topology.nodeCount());
        int whiteRecipe = nodeIndex(service.graph(), "dye_white");
        int greenDirectRecipe = nodeIndex(service.graph(), "green_direct");
        // target inside the loop family: cone is cyclic
        assertTrue(topology.coneHasNontrivialCycle(whiteRecipe, allAllowed));
        // target outside the loop family: cone is acyclic even though the
        // graph contains a loop elsewhere
        assertFalse(topology.coneHasNontrivialCycle(greenDirectRecipe, allAllowed));

        // masking: disallow every dye recipe, then white's cone is acyclic
        BitSet masked = new BitSet(topology.nodeCount());
        masked.set(greenDirectRecipe);
        assertFalse(topology.coneHasNontrivialCycle(greenDirectRecipe, masked));
    }

    @Test
    void acyclicConeFindsGlobalOptimumThatGreedyMisses(MinecraftServer server) {
        // target T needs A. Two producers for A:
        //  - "z_good_path": A <- B, B <- RAW (one externally supplied unit)
        // target T needs A. Two producers for A:
        //  - "z_good_path": A <- B, B <- BONE_BLOCK(stock)         — 2 crafts
        //  - "a_bad_path":  A <- C <- D <- E <- F <- G <- REDSTONE(stock) — 6 crafts
        // Both paths terminate in stock. Greedy scoring ties on the first
        // step and the alphabetical tie-break wins for "a_bad_path"; the DP
        // cost model counts total crafts and picks the globally cheaper path.
        var goodA = new RoutingTestFixtures.FakeRecipe(
            "z_good_path",
            List.of(RoutingTestFixtures.in(Items.BONE, 1)),
            List.of(RoutingTestFixtures.out(Items.BLAZE_ROD, 1))
        );
        var goodB = new RoutingTestFixtures.FakeRecipe(
            "z_good_b",
            List.of(RoutingTestFixtures.in(Items.BONE_BLOCK, 1)),
            List.of(RoutingTestFixtures.out(Items.BONE, 1))
        );
        var badA = new RoutingTestFixtures.FakeRecipe(
            "a_bad_path",
            List.of(RoutingTestFixtures.in(Items.COAL, 1)),
            List.of(RoutingTestFixtures.out(Items.BLAZE_ROD, 1))
        );
        var badC = chain("a_bad_c", Items.COAL, Items.DIAMOND);
        var badD = chain("a_bad_d", Items.DIAMOND, Items.EMERALD);
        var badE = chain("a_bad_e", Items.EMERALD, Items.LAPIS_LAZULI);
        var badF = chain("a_bad_f", Items.LAPIS_LAZULI, Items.REDSTONE);
        var target = new RoutingTestFixtures.FakeRecipe(
            "target",
            List.of(RoutingTestFixtures.in(Items.BLAZE_ROD, 1)),
            List.of(RoutingTestFixtures.out(Items.BLAZE_POWDER, 2))
        );
        RoutingService service = RoutingTestFixtures.service(goodA, goodB, badA, badC, badD, badE, badF, target);
        RecipeEntry<?> targetEntry = RoutingTestFixtures.findRecipeById(service.graph(), "target");
        assertNotNull(targetEntry);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.BONE_BLOCK, 1)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.REDSTONE, 1)));

        RoutingOutcome greedy = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(targetEntry, 1)
            .build());
        assertTrue(greedy.success() || greedy.partial());
        assertTrue(executesRecipe(greedy, "a_bad_path"), "greedy should tie-break into the worse path");

        RoutingOutcome auto = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.auto)
            .stock(stock)
            .targetRecipe(targetEntry, 1)
            .build());
        assertTrue(auto.success(), "auto mode should plan the acyclic cone exactly");
        assertTrue(executesRecipe(auto, "z_good_path"), "auto should pick the globally cheaper path");
        assertFalse(executesRecipe(auto, "a_bad_path"), "auto must not execute the worse path");
        assertEquals(3, countActions(auto, PlanAction.ExecuteRecipe.class),
            "target + B + A via the good path");
        // both chain bases come from stock, nothing is left pending
        assertEquals(0, countActions(auto, PlanAction.PendingInput.class));
    }

    @Test
    void autoModePrefersDirectCraftOverLoopFamilyConversion(MinecraftServer server) {
        var whiteFromRed = recipe("dye_white", Items.RED_CONCRETE, Items.WHITE_CONCRETE);
        var redFromBlue = recipe("dye_red", Items.BLUE_CONCRETE, Items.RED_CONCRETE);
        var blueFromGreen = recipe("dye_blue", Items.GREEN_CONCRETE, Items.BLUE_CONCRETE);
        var greenFromWhite = recipe("dye_green", Items.WHITE_CONCRETE, Items.GREEN_CONCRETE);
        var greenDirect = new RoutingTestFixtures.FakeRecipe(
            "green_direct",
            List.of(RoutingTestFixtures.in(Items.GLOWSTONE, 1)),
            List.of(RoutingTestFixtures.out(Items.GREEN_CONCRETE, 1))
        );
        RoutingService service = RoutingTestFixtures.service(
            whiteFromRed, redFromBlue, blueFromGreen, greenFromWhite, greenDirect
        );
        RecipeEntry<?> target = RoutingTestFixtures.findRecipeById(service.graph(), "dye_white");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.GLOWSTONE, 1)));

        RoutingOutcome outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.auto)
            .stock(stock)
            .targetRecipe(target, 1)
            .build());

        assertTrue(outcome.success() || outcome.partial());
        assertTrue(executesRecipe(outcome, "green_direct"), "should exit the loop family via the direct craft");
        assertFalse(executesRecipe(outcome, "dye_green"), "must not ping-pong back into the family");
        // every dye step runs at most once: no repeated conversions
        for (String dye : List.of("dye_white", "dye_red", "dye_blue")) {
            assertEquals(1, countExecutions(outcome, dye), dye + " must not repeat");
        }
    }

    @Test
    void autoModeStillHandlesPureAcyclicTargets(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick);
        RecipeEntry<?> target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingOutcome outcome = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.auto)
            .stock(stock)
            .targetRecipe(target, 1)
            .build());
        assertTrue(outcome.success());
        assertNotNull(outcome.plan());
        assertTrue(outcome.plan().actions().size() >= 2, "expected at least consume+execute actions");
        assertTrue(executesRecipe(outcome, "make_planks"));
        assertTrue(executesRecipe(outcome, "make_stick"));
    }

    private static RoutingTestFixtures.FakeRecipe recipe(String id, Item input, Item output) {
        return new RoutingTestFixtures.FakeRecipe(
            id,
            List.of(RoutingTestFixtures.in(input, 1)),
            List.of(RoutingTestFixtures.out(output, 1))
        );
    }

    /** A one-in one-out chain link: {@code id}: input -> output. */
    private static RoutingTestFixtures.FakeRecipe chain(String id, Item output, Item input) {
        return new RoutingTestFixtures.FakeRecipe(
            id,
            List.of(RoutingTestFixtures.in(input, 1)),
            List.of(RoutingTestFixtures.out(output, 1))
        );
    }

    private static int nodeIndex(RecipeGraph graph, String recipePath) {
        int index = 0;
        for (RecipeGraph.Node node : graph.stableNodes()) {
            if (node.recipe().id() != null && recipePath.equals(node.recipe().id().getPath())) {
                return index;
            }
            index++;
        }
        throw new AssertionError("recipe not in graph: " + recipePath);
    }

    private static boolean executesRecipe(RoutingOutcome outcome, String recipePath) {
        return countExecutions(outcome, recipePath) > 0;
    }

    private static int countExecutions(RoutingOutcome outcome, String recipePath) {
        if (outcome.plan() == null) {
            return 0;
        }
        int count = 0;
        for (PlanAction action : outcome.plan().actions()) {
            if (action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && recipePath.equals(execute.recipe().id().getPath())) {
                count++;
            }
        }
        return count;
    }

    private static <T extends PlanAction> int countActions(RoutingOutcome outcome, Class<T> type) {
        if (outcome.plan() == null) {
            return 0;
        }
        int count = 0;
        for (PlanAction action : outcome.plan().actions()) {
            if (type.isInstance(action)) {
                count++;
            }
        }
        return count;
    }
}
