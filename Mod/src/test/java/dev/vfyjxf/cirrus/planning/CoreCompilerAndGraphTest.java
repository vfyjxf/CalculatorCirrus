package dev.vfyjxf.cirrus.planning;

import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cirrus.planning.adapter.RawRecordMapper;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultRecipeGraphCompiler;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.exactSlot;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCompilerAndGraphTest {

    @Test
    void tc01_deterministicFilteringAtCompilerAndAdapter() {
        ResourceKey a = item("minecraft:stone");
        ResourceKey b = item("mod:good");
        RawRecipeRecord valid = rec(
            "minecraft:crafting",
            "mod:ok",
            List.of("minecraft:crafting_table"),
            List.of(slot(a, 2)),
            List.of(out(b, 1))
        );
        RawRecipeRecord insane = rec(
            "minecraft:crafting",
            "mod:insane",
            List.of("minecraft:crafting_table"),
            List.of(slot(a, DefaultRecipeGraphCompiler.maxSensibleAmount + 1L)),
            List.of(out(b, 1))
        );
        RawRecipeRecord noInputs = new RawRecipeRecord(
            "minecraft:crafting",
            "vanilla",
            Optional.of("mod:no_in"),
            List.of("minecraft:crafting_table"),
            List.of(),
            List.of(out(b, 1)),
            Map.of()
        );
        RawRecipeRecord noOutputs = new RawRecipeRecord(
            "minecraft:crafting",
            "vanilla",
            Optional.of("mod:no_out"),
            List.of("minecraft:crafting_table"),
            List.of(slot(a, 1)),
            List.of(),
            Map.of()
        );
        RawRecipeRecord excludedDisplay = rec(
            "jei:category/minecraft/crafting",
            "jei:dup",
            List.of("minecraft:crafting_table"),
            List.of(slot(a, 1)),
            List.of(out(b, 1))
        );

        DefaultSystemAdmissionPolicy policy = DefaultSystemAdmissionPolicy.defaults()
                .withSystemOverride("jei:category/minecraft/crafting", SystemAdmission.excludedDisplay);
        CompiledRecipeGraph g = compile(List.of(valid, insane, noInputs, noOutputs, excludedDisplay), policy);
        GraphCompilationStats stats = g.stats();
        assertEquals(5, stats.totalRawRecipes());
        assertEquals(1, stats.compiledRecipes());
        assertEquals(1, stats.excludedInsaneAmount());
        assertEquals(1, stats.excludedNoInputs());
        assertEquals(1, stats.excludedInvalidOrComplex());
        assertEquals(1, stats.excludedDisplaySystems());

        // Adapter D3 rules: reject fractional chance, promote chance==0 input to catalyst, drop chance==0 output, reject decimal amount.
        RawRecordMapper mapper = new RawRecordMapper();
        var fracOut = mapper.mapJsonRecipe(
            JsonParser.parseString(
                """
                        {"system":"minecraft:crafting","source":"vanilla","id":"t:frac","machines":["minecraft:crafting_table"],
                         "inputs":[{"kind":"stack","stack":{"type":"item","id":"minecraft:stone","count":1},"amount":1,"chance":1.0,"matchStrategy":"identity"}],
                         "outputs":[{"kind":"stack","stack":{"type":"item","id":"mod:x","count":1},"amount":1,"chance":0.25,"matchStrategy":"identity"}]}
                        """
            ).getAsJsonObject(),
            1L,
            0
        );
        assertEquals(DropReason.probabilistic, fracOut.dropReason());

        var decimalAmt = mapper.mapJsonRecipe(
            JsonParser.parseString(
                """
                        {"system":"minecraft:crafting","source":"vanilla","id":"t:dec","machines":["minecraft:crafting_table"],
                         "inputs":[{"kind":"stack","stack":{"type":"item","id":"minecraft:stone","count":1},"amount":1.5,"chance":1.0,"matchStrategy":"identity"}],
                         "outputs":[{"kind":"stack","stack":{"type":"item","id":"mod:x","count":1},"amount":1,"chance":1.0,"matchStrategy":"identity"}]}
                        """
            ).getAsJsonObject(),
            2L,
            1
        );
        assertEquals(DropReason.invalidAmount, decimalAmt.dropReason());

        var chanceZero = mapper.mapJsonRecipe(
            JsonParser.parseString(
                """
                        {"system":"minecraft:crafting","source":"vanilla","id":"t:cz","machines":["minecraft:crafting_table"],
                         "inputs":[
                           {"kind":"stack","stack":{"type":"item","id":"mod:hammer","count":1},"amount":1,"chance":0.0,"matchStrategy":"identity"},
                           {"kind":"stack","stack":{"type":"item","id":"minecraft:stone","count":1},"amount":1,"chance":1.0,"matchStrategy":"identity"}],
                         "outputs":[
                           {"kind":"stack","stack":{"type":"item","id":"mod:out","count":1},"amount":1,"chance":1.0,"matchStrategy":"identity"},
                           {"kind":"stack","stack":{"type":"item","id":"mod:ghost","count":1},"amount":1,"chance":0.0,"matchStrategy":"identity"}]}
                        """
            ).getAsJsonObject(),
            3L,
            2
        );
        assertTrue(chanceZero.isKept());
        CompiledRecipeGraph cg = compile(List.of(chanceZero.record()));
        CompiledRecipe compiled = cg.producersOf(item("mod:out")).get(0);
        assertEquals(1, compiled.catalysts().size());
        assertEquals(item("mod:hammer"), compiled.catalysts().get(0).candidates().get(0));
        assertEquals(1, compiled.outputs().size());
        assertEquals(item("mod:out"), compiled.outputs().get(0).resource());
    }

    @Test
    void tc02_emptyIdStableKeyDeduplicationAndFiveRunDeterminism() {
        ResourceKey a = item("minecraft:stick");
        ResourceKey b = item("mod:empty_prod_1");
        ResourceKey c = item("mod:empty_prod_2");
        List<RawRecipeRecord> recs = List.of(
            new RawRecipeRecord(
                "minecraft:crafting",
                "vanilla",
                Optional.empty(),
                List.of("minecraft:crafting_table"),
                List.of(slot(a, 2)),
                List.of(out(b, 1)),
                Map.of(),
                101L
            ),
            new RawRecipeRecord(
                "minecraft:crafting",
                "vanilla",
                Optional.empty(),
                List.of("minecraft:crafting_table"),
                List.of(slot(a, 2)),
                List.of(out(b, 1)),
                Map.of(),
                102L
            ),
            new RawRecipeRecord(
                "minecraft:crafting",
                "vanilla",
                Optional.empty(),
                List.of("minecraft:crafting_table"),
                List.of(slot(a, 3)),
                List.of(out(c, 1)),
                Map.of(),
                103L
            )
        );
        CompiledRecipeGraph first = compile(recs);
        assertEquals(2, first.stats().compiledRecipes());
        assertEquals(1, first.stats().deduplicatedRecipes());
        CompiledRecipe prodB = first.producersOf(b).get(0);
        assertTrue(prodB.key().explicitRecipeId().isEmpty());
        assertEquals(101L, first.sourceRefOf(prodB.compactId()));

        List<String> baselineOrder = snapshotKeys(first);
        for (int run = 0; run < 5; run++) {
            CompiledRecipeGraph again = compile(List.of(recs.get(2), recs.get(1), recs.get(0)));
            assertEquals(baselineOrder, snapshotKeys(again));
        }
    }

    @Test
    void tc03_strictCatalystPromotionRespectsVariantHashAndNetDelta() {
        ResourceKey plainMold = item("mod:mold");
        ResourceKey hotMold = new ResourceKey("minecraft:item", "mod:mold", Optional.of("0123456789abcdef"));
        ResourceKey coldMold = new ResourceKey("minecraft:item", "mod:mold", Optional.of("fedcba9876543210"));
        ResourceKey ingot = item("minecraft:iron_ingot");
        ResourceKey plate = item("mod:plate");
        ResourceKey seed = item("mod:seed");
        ResourceKey part = item("mod:part");

        // Exact equal key -> promoted to catalyst.
        RawRecipeRecord equalCat = rec(
            "mod:press",
            "r_cat",
            List.of("mod:presser"),
            List.of(exactSlot(plainMold, 1), slot(ingot, 1)),
            List.of(out(plainMold, 1), out(plate, 1))
        );
        // Same resourceId but different canonicalVariantHash -> MUST NOT promote.
        RawRecipeRecord diffVariant = rec(
            "mod:press",
            "r_diff",
            List.of("mod:presser"),
            List.of(exactSlot(hotMold, 1), slot(ingot, 1)),
            List.of(out(coldMold, 1), out(part, 1))
        );
        // in > out (3 -> 1) -> net consumption of 2, NOT pure zero-consumption catalyst.
        RawRecipeRecord netConsume = rec(
            "mod:press",
            "r_net",
            List.of("mod:presser"),
            List.of(slot(seed, 3)),
            List.of(out(seed, 1), out(item("mod:net_out"), 1))
        );

        CompiledRecipeGraph g = compile(List.of(equalCat, diffVariant, netConsume));

        CompiledRecipe rCat = g.producersOf(plate).get(0);
        assertEquals(1, rCat.catalysts().size());
        assertEquals(plainMold, rCat.catalysts().get(0).candidates().get(0));
        assertEquals(1, rCat.inputs().size());
        assertEquals(1, rCat.outputs().size());

        CompiledRecipe rDiff = g.producersOf(part).get(0);
        assertTrue(rDiff.catalysts().isEmpty());
        assertEquals(2, rDiff.inputs().size());
        assertEquals(2, rDiff.outputs().size());

        CompiledRecipe rNet = g.producersOf(item("mod:net_out")).get(0);
        assertEquals(1, rNet.inputs().size());
        assertEquals(2L, rNet.inputs().get(0).amountPerBatch());
        assertEquals(1, rNet.outputs().size());
    }

    @Test
    void jeiFourPlusOutputsSplitIntoDiscouragedSingleOutputRecipes() {
        ResourceKey in = item("minecraft:book");
        List<RawOutputSlot> fiveOuts = List.of(
            out(item("mod:eb_1"), 1),
            out(item("mod:eb_2"), 1),
            out(item("mod:eb_3"), 1),
            out(item("mod:eb_4"), 1),
            out(item("mod:eb_5"), 1)
        );
        RawRecipeRecord jeiMulti = rec(
            "jei:category/mod/enchanter",
            "jei:enc",
            List.of("mod:enchanter"),
            List.of(slot(in, 1)),
            fiveOuts
        );
        RawRecipeRecord vanillaMulti = rec(
            "minecraft:crafting",
            "van:multi",
            List.of("minecraft:crafting_table"),
            List.of(slot(in, 1)),
            fiveOuts
        );

        CompiledRecipeGraph g = compile(List.of(jeiMulti, vanillaMulti));
        List<CompiledRecipe> prods1 = g.producersOf(item("mod:eb_1"));
        assertEquals(2, prods1.size());
        CompiledRecipe jeiSplit = prods1.stream().filter(r -> r.key().systemId().startsWith("jei:")).findFirst()
                .orElseThrow();
        CompiledRecipe vanKept = prods1.stream().filter(r -> r.key().systemId().equals("minecraft:crafting"))
                .findFirst().orElseThrow();
        assertEquals(1, jeiSplit.outputs().size());
        assertEquals(RecipeAdvisory.discouraged, jeiSplit.advisory());
        assertEquals(5, vanKept.outputs().size());
        assertEquals(RecipeAdvisory.none, vanKept.advisory());
    }

    @Test
    void crossSystemOneToOneSmeltCrushStaysStandardWhilePackingIsReversible() {
        ResourceKey cobble = item("minecraft:cobblestone");
        ResourceKey stone = item("minecraft:stone");
        ResourceKey ingot = item("minecraft:iron_ingot");
        ResourceKey block = item("minecraft:iron_block");

        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:smelting",
                    "minecraft:stone",
                    List.of("minecraft:furnace"),
                    List.of(slot(cobble, 1)),
                    List.of(out(stone, 1))
                ),
                rec(
                    "jei:category/create/crushing",
                    "create:crush_stone",
                    List.of("create:crushing_wheel"),
                    List.of(slot(stone, 1)),
                    List.of(out(cobble, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "pack_iron",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(ingot, 9)),
                    List.of(out(block, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "unpack_iron",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(block, 1)),
                    List.of(out(ingot, 9))
                )
            )
        );

        CompiledRecipe smeltStone = g.producersOf(stone).get(0);
        assertEquals(RecipeRole.standardProduction, smeltStone.role());
        assertFalse(smeltStone.isReversibleConversion());

        CompiledRecipe packIron = g.producersOf(block).get(0);
        assertEquals(RecipeRole.reversiblePacking, packIron.role());
        assertTrue(packIron.isReversibleConversion());
    }

    @Test
    void systemPolicyAndNativeMachineOrdering() {
        DefaultSystemAdmissionPolicy policy = DefaultSystemAdmissionPolicy.defaults()
                .withSystemOverride("jei:category/minecraft/crafting", SystemAdmission.excludedDisplay)
                .withSystemOverride("jei:category/gregtech/assembler", SystemAdmission.optInOnly);
        assertEquals(SystemAdmission.excludedDisplay, policy.classify("jei:category/minecraft/crafting", "jei"));
        assertEquals(SystemAdmission.optInOnly, policy.classify("jei:category/gregtech/assembler", "jei"));
        assertEquals(SystemAdmission.enabled, policy.classify("minecraft:smelting", "vanilla"));

        List<String> stonecutters = MachineCapabilityGroups.orderMachines(
            "minecraft:stonecutting",
            List.of("sophisticatedbackpacks:stonecutter_upgrade", "minecraft:stonecutter")
        );
        assertEquals("minecraft:stonecutter", stonecutters.get(0));

        List<String> inscribers = MachineCapabilityGroups.orderMachines(
            "jei:category/ae2/inscriber",
            List.of("extendedae:ex_inscriber", "ae2:inscriber", "aa_addon:super_inscriber")
        );
        assertEquals("ae2:inscriber", inscribers.get(0));
    }

    private static List<String> snapshotKeys(CompiledRecipeGraph graph) {
        List<String> out = new ArrayList<>();
        for (int cid = 0; cid < graph.stats().compiledRecipes(); cid++) {
            CompiledRecipe r = graph.recipeByCompactId(cid).orElseThrow();
            out.add(cid + ":" + r.key());
        }
        return out;
    }
}
