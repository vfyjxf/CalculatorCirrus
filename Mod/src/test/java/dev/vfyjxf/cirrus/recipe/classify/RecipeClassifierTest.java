package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationResult;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-fixture tests for {@link RecipeClassifier}: one minimal recipe per
 * classification category, asserting the expected tags (and absence of tags on
 * look-alike recipes that must NOT be classified).
 */
class RecipeClassifierTest {

    private static RecipeClassifier classifier() {
        return RecipeClassifier.createDefault();
    }

    private static RecipeClassifyView view(int index, String system, String recipeId, List<ClassifyContent> inputs, List<ClassifyContent> outputs) {
        return new RecipeClassifyView(index, system, recipeId, List.of(), inputs, outputs);
    }

    private static ClassifyContent stack(String id) {
        return stack(id, 1);
    }

    private static ClassifyContent stack(String id, double amount) {
        return ClassifyContent.stack(id, amount, 1.0);
    }

    private static ClassifyContent tag(String tagId, Set<String> members) {
        return ClassifyContent.tag(tagId, members, 1, 1.0);
    }

    private static ClassificationResult classify(RecipeClassifyView... views) {
        return classifier().classifyAll(List.of(views));
    }

    @Test
    void builtinPatternRulesAreLoaded() {
        assertTrue(RecipeClassifier.loadBuiltinRuleSpecs().size() >= 10,
            "expected the builtin unified rule document to load");
    }

    @Test
    void questRecipeIsJunk() {
        ClassificationResult result = classify(view(0,
            "jei:category/ftbquests/quest", "ftbquests:chapter1/stone",
            List.of(stack("minecraft:stone")), List.of(stack("minecraft:dirt"))));
        assertTrue(result.hasTag(0, ClassificationTags.JUNK));
    }

    @Test
    void archaeologyRecipeIsJunk() {
        ClassificationResult result = classify(view(0,
            "jei:category/jearchaeology/brush", "minecraft:archaeology/desert_well",
            List.of(stack("minecraft:suspicious_sand")), List.of(stack("minecraft:sniffer_egg"))));
        assertTrue(result.hasTag(0, ClassificationTags.JUNK));
    }

    @Test
    void colonyViewWithMirrorIsDuplicate() {
        // the teachable view mirrors a real craft (other producer exists) => duplicate
        ClassificationResult result = classify(
            view(0, "jei:category/minecolonies/stonemason", "minecolonies:stonemason/stone_bricks",
                List.of(stack("minecraft:stone")), List.of(stack("minecraft:stone_bricks"))),
            view(1, "minecraft:crafting", "minecraft:stone_bricks",
                List.of(stack("minecraft:stone")), List.of(stack("minecraft:stone_bricks"))));
        assertTrue(result.hasTag(0, ClassificationTags.DUPLICATE));
        assertFalse(result.hasTag(1, ClassificationTags.DUPLICATE));
    }

    @Test
    void soleColonyViewIsSoftNotExcluded() {
        // no other producer: the teachable view IS the production path and must
        // stay reachable (soft exchange tag, never duplicate/junk)
        ClassificationResult result = classify(view(0,
            "jei:category/minecolonies/baker", "minecolonies:baker/cake_batter",
            List.of(stack("minecraft:wheat")), List.of(stack("minecolonies:cake_batter"))));
        assertTrue(result.hasTag(0, ClassificationTags.EXCHANGE));
        assertFalse(result.hasTag(0, ClassificationTags.DUPLICATE));
        assertFalse(result.hasTag(0, ClassificationTags.JUNK));
    }

    @Test
    void jeiBrewingMirrorIsDuplicate() {
        ClassificationResult result = classify(view(0,
            "jei:category/minecraft/brewing", "minecraft:brewing/awkward",
            List.of(stack("minecraft:nether_wart")), List.of(stack("minecraft:potion"))));
        assertTrue(result.hasTag(0, ClassificationTags.DUPLICATE));
    }

    @Test
    void realCraftingIsNotExcluded() {
        ClassificationResult result = classify(view(0,
            "minecraft:crafting", "minecraft:stick",
            List.of(stack("minecraft:oak_planks")), List.of(stack("minecraft:stick"))));
        assertTrue(result.tagsOf(0).isEmpty(), "plain crafting must not be classified: " + result.tagsOf(0));
    }

    @Test
    void chiselSwapIsExchange() {
        ClassificationResult result = classify(view(0,
            "jei:category/rechiseled/chiseling", "rechiseled:chiseling/gray_concrete",
            List.of(tag("chipped:gray_concrete", Set.of("minecraft:gray_concrete", "chipped:gray_concrete_bricks"))),
            List.of(stack("chipped:gray_concrete_bricks"))));
        assertTrue(result.hasTag(0, ClassificationTags.EXCHANGE));
    }

    @Test
    void stackRecolorIsRecolor() {
        ClassificationResult result = classify(view(0,
            "minecraft:crafting", "utilitarian:utility/redying/white_concrete_powder_single",
            List.of(stack("minecraft:red_concrete_powder"), stack("minecraft:white_dye")),
            List.of(stack("minecraft:white_concrete_powder"))));
        assertTrue(result.hasTag(0, ClassificationTags.RECOLOR));
    }

    @Test
    void normalDyeingIsNotRecolor() {
        // uncolored base + dye -> colored result is the primary production path
        ClassificationResult result = classify(view(0,
            "minecraft:crafting", "minecraft:red_stained_glass",
            List.of(stack("minecraft:glass"), stack("minecraft:red_dye")),
            List.of(stack("minecraft:red_stained_glass"))));
        assertFalse(result.hasTag(0, ClassificationTags.RECOLOR));
    }

    @Test
    void directColorCraftIsNotRecolor() {
        // sand + gravel + red dye -> red concrete powder: red_dye has a color
        // token but a different base family, so this must not be a recolor
        ClassificationResult result = classify(view(0,
            "minecraft:crafting", "minecraft:red_concrete_powder",
            List.of(stack("minecraft:sand"), stack("minecraft:gravel"), stack("minecraft:red_dye")),
            List.of(stack("minecraft:red_concrete_powder"))));
        assertFalse(result.hasTag(0, ClassificationTags.RECOLOR));
    }

    @Test
    void arcFurnaceRecyclingIsRecycling() {
        ClassificationResult result = classify(view(0,
            "jei:category/immersiveengineering/arc_furnace", "alltheores:arcfurnace/tin/from_plate",
            List.of(tag("c:plates/tin", Set.of("alltheores:tin_plate"))),
            List.of(stack("alltheores:tin_ingot"))));
        assertTrue(result.hasTag(0, ClassificationTags.RECYCLING));
    }

    @Test
    void oreSmeltingIsNotRecycling() {
        ClassificationResult result = classify(view(0,
            "minecraft:smelting", "minecraft:iron_ingot_from_smelting_iron_ore",
            List.of(stack("minecraft:iron_ore")), List.of(stack("minecraft:iron_ingot"))));
        assertFalse(result.hasTag(0, ClassificationTags.RECYCLING));
    }

    @Test
    void blockDecompressionIsNotRecycling() {
        ClassificationResult result = classify(view(0,
            "minecraft:crafting", "minecraft:iron_ingot_from_iron_block",
            List.of(stack("minecraft:iron_block")), List.of(stack("minecraft:iron_ingot", 9))));
        assertFalse(result.hasTag(0, ClassificationTags.RECYCLING));
    }

    @Test
    void smeltingWithExistingReverseIsNotExchange() {
        // sky_stone -> smooth_sky_stone (smelt) with an existing crusher
        // reverse (smooth -> sky stone) must NOT be tagged: the smelt is the
        // primary production path. Genuine oscillation is the planner's
        // cycle guard's job, not the classifier's.
        ClassificationResult result = classify(
            view(0, "minecraft:smelting", "ae2:smelting/smooth_sky_stone_block",
                List.of(stack("ae2:sky_stone_block")), List.of(stack("ae2:smooth_sky_stone_block"))),
            view(1, "jei:category/mekanism/crushing", "mekanism:compat/ae2/smooth_to_stone",
                List.of(stack("ae2:smooth_sky_stone_block")), List.of(stack("ae2:sky_stone_block")))
        );
        assertTrue(result.tagsOf(0).isEmpty(), "smelting must stay untagged: " + result.tagsOf(0));
        assertTrue(result.tagsOf(1).isEmpty(), "crushing must stay untagged: " + result.tagsOf(1));
    }

    @Test
    void compressionPairIsNotExchange() {
        // 9 ingots <-> 1 block: amounts differ, must not be tagged as a swap
        ClassificationResult result = classify(
            view(0, "minecraft:crafting", "minecraft:iron_block",
                List.of(stack("minecraft:iron_ingot", 9)), List.of(stack("minecraft:iron_block"))),
            view(1, "minecraft:crafting", "minecraft:iron_ingot_from_iron_block",
                List.of(stack("minecraft:iron_block")), List.of(stack("minecraft:iron_ingot", 9)))
        );
        assertFalse(result.hasTag(0, ClassificationTags.EXCHANGE));
        assertFalse(result.hasTag(1, ClassificationTags.EXCHANGE));
    }

    @Test
    void massSinkIsRecycling() {
        List<RecipeClassifyView> views = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            views.add(view(i, "jei:category/example/composter_like", "example:compost/" + i,
                List.of(stack("example:organic_" + i)), List.of(stack("example:compost"))));
        }
        ClassificationResult result = classifier().classifyAll(views);
        assertTrue(result.hasTag(0, ClassificationTags.RECYCLING));
        assertTrue(result.hasTag(63, ClassificationTags.RECYCLING));
        assertTrue(result.tagCount(ClassificationTags.RECYCLING) == 64);
    }

    @Test
    void smallSharedOutputIsNotMassSink() {
        List<RecipeClassifyView> views = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            views.add(view(i, "minecraft:crafting", "example:shared/" + i,
                List.of(stack("example:part_" + i)), List.of(stack("example:shared"))));
        }
        ClassificationResult result = classifier().classifyAll(views);
        assertTrue(result.tagsOf(0).isEmpty());
    }

    @Test
    void generatorIsGeneration() {
        ClassificationResult result = classify(view(0,
            "jei:category/cobblegengalore/blockgen", "cobblegengalore:cobblegen",
            List.of(), List.of(stack("minecraft:cobblestone"))));
        assertTrue(result.hasTag(0, ClassificationTags.GENERATION));
    }

    @Test
    void complexInputIsNotGeneration() {
        // unresolved complex inputs mean "unknown contents", not "no inputs"
        ClassificationResult result = classify(view(0,
            "minecraft:crafting", "supplementaries:way_sign_salak",
            List.of(new ClassifyContent(ClassifyContent.Kind.complex, Set.of(), null, 1, 1.0)),
            List.of(stack("supplementaries:productivetrees/way_sign_salak"))));
        assertFalse(result.hasTag(0, ClassificationTags.GENERATION));
    }

    @Test
    void anvilRepairIsExchange() {
        // damaged item + material -> same item id: a self-loop, useless as a
        // production path
        ClassificationResult result = classify(view(0,
            "jei:category/minecraft/anvil", "minecraft:anvil/repair_pickaxe",
            List.of(stack("minecraft:iron_pickaxe"), stack("minecraft:iron_ingot")),
            List.of(stack("minecraft:iron_pickaxe"))));
        assertTrue(result.hasTag(0, ClassificationTags.EXCHANGE));
    }

    @Test
    void exclusionSystemDetection() {
        RecipeClassifier classifier = classifier();
        assertTrue(classifier.isExcludedSystem("jei:category/ftbquests/quest"));
        // colony views are no longer system-excluded: their disposition (mirror vs
        // sole producer) is decided per recipe by ColonyViewMirrorRule
        assertFalse(classifier.isExcludedSystem("jei:category/minecolonies/stonemason"));
        assertTrue(classifier.isExcludedSystem("cirrus_test:jei__category/ftbquests/quest"));
        assertFalse(classifier.isExcludedSystem("minecraft:crafting"));
        assertFalse(classifier.isExcludedSystem("jei:category/create/milling"));
    }

    @Test
    void builtinDocumentCarriesExpectedRules() {
        List<RuleSpec> specs = RecipeClassifier.loadBuiltinRuleSpecs();
        assertTrue(specs.stream().anyMatch(rule -> rule.id().toString().equals("cirrus:quests")));
        assertTrue(specs.stream().anyMatch(rule ->
            rule.actions().stream().anyMatch(action -> action instanceof dev.vfyjxf.cirrus.api.recipe.rule.RuleAction.Tag tag
                && tag.tag().equals(ClassificationTags.RECYCLING))));
        // no dangling tags: every tagged value has a classification-tag constant or consumer
        assertTrue(specs.stream().anyMatch(rule -> rule.id().toString().equals("cirrus:storage_compression_shape")),
            "the structured compression-shape rule must be present");
    }

    @Test
    void unifiedHintsAreExposedThroughLocalOutcome() {
        RecipeClassifier classifier = classifier();
        // recolor rules declare a discouraged hint action in the v3 document
        RecipeClassifyView recolor = view(0,
            "minecraft:crafting", "utilitarian:utility/redying/white_concrete_powder_single",
            List.of(stack("minecraft:red_concrete_powder"), stack("minecraft:white_dye")),
            List.of(stack("minecraft:white_concrete_powder")));
        assertEquals(dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy.discouraged,
            classifier.hintOf(recolor));
        // plain crafting carries no hint
        RecipeClassifyView plain = view(1,
            "minecraft:crafting", "minecraft:stick",
            List.of(stack("minecraft:oak_planks")), List.of(stack("minecraft:stick")));
        assertNull(classifier.hintOf(plain));
    }

    @Test
    void classifyAllAndClassifyLocalShareOneMemo() {
        // graph build derives hints through classifyLocal, the catalog populate
        // pass through classifyAll; both must agree regardless of run order
        RecipeClassifier classifier = classifier();
        RecipeClassifyView junk = view(0,
            "jei:category/ftbquests/quest", "ftbquests:chapter1/stone",
            List.of(stack("minecraft:stone")), List.of(stack("minecraft:dirt")));
        RecipeClassifyView recolor = view(1,
            "minecraft:crafting", "utilitarian:utility/redying/white_concrete_powder_single",
            List.of(stack("minecraft:red_concrete_powder"), stack("minecraft:white_dye")),
            List.of(stack("minecraft:white_concrete_powder")));

        ClassificationResult populateFirst = classifier.classifyAll(List.of(junk, recolor));
        org.junit.jupiter.api.Assertions.assertEquals(populateFirst.tagsOf(0), classifier.classifyLocal(junk));
        org.junit.jupiter.api.Assertions.assertEquals(populateFirst.tagsOf(1), classifier.classifyLocal(recolor));

        RecipeClassifier reverse = classifier();
        Set<String> localFirst = reverse.classifyLocal(junk);
        ClassificationResult populateAfter = reverse.classifyAll(List.of(junk));
        org.junit.jupiter.api.Assertions.assertEquals(localFirst, populateAfter.tagsOf(0));
    }

    @Test
    void emptyRecipeIdRecipesBypassTheMemo() {
        // pseudo recipes carry no id; keying them by system alone would merge
        // distinct recipes into one memo entry
        RecipeClassifier classifier = classifier();
        RecipeClassifyView plain = view(0, "minecraft:crafting", "",
            List.of(stack("minecraft:stone")), List.of(stack("minecraft:dirt")));
        RecipeClassifyView recolor = view(1, "minecraft:crafting", "",
            List.of(stack("minecraft:red_concrete")), List.of(stack("minecraft:blue_concrete")));
        assertTrue(classifier.classifyLocal(plain).isEmpty());
        assertTrue(classifier.classifyLocal(recolor).contains(ClassificationTags.RECOLOR));
        assertTrue(classifier.classifyLocal(plain).isEmpty(), "plain recipe must not inherit the recolor tag");
    }
}
