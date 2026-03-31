package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog;
import dev.vfyjxf.cirrus.api.recipe.routing.state.SccAnalysis;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class SccAnalysisTest {
    @Test
    void tagInputConsumerIndexBuildsProducerConsumerCycle(MinecraftServer server) {
        RoutingTestFixtures.FakeRecipe makeLog = new RoutingTestFixtures.FakeRecipe(
            "make_log",
            List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 1)),
            List.of(RoutingTestFixtures.out(Items.OAK_LOG, 1))
        );
        RoutingTestFixtures.FakeRecipe makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            List.of(InputContent.fromItemTag(ItemTags.LOGS, 1)),
            List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RecipeGraph graph = RoutingTestFixtures.graph(makeLog, makePlanks);
        RoutingRecipeCatalog catalog = RoutingRecipeCatalog.fromGraph(graph);

        var makeLogEntry = RoutingTestFixtures.findRecipeById(graph, "make_log");
        var makePlanksEntry = RoutingTestFixtures.findRecipeById(graph, "make_planks");
        assertNotNull(makeLogEntry);
        assertNotNull(makePlanksEntry);
        assertTrue(graph.consumersFor(OutputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1)).stream().anyMatch(ref ->
            ref.input() instanceof dev.vfyjxf.cirrus.api.recipe.content.TagStackContent<?>
        ));

        BitSet allowed = new BitSet(catalog.size());
        allowed.set(0, catalog.size());
        SccAnalysis analysis = SccAnalysis.build(graph, catalog, allowed);

        int logScc = analysis.sccOf(catalog.indexOf(makeLogEntry));
        int planksScc = analysis.sccOf(catalog.indexOf(makePlanksEntry));
        assertEquals(logScc, planksScc);
        assertTrue(analysis.isCyclicScc(logScc));
    }
}
