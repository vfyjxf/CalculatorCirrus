package dev.vfyjxf.cirrus.ui.workbench.facilities;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.PlanningTagKeys;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.ui.workbench.facilities.RuleSentenceBuilder.Field;
import dev.vfyjxf.cirrus.ui.workbench.facilities.RuleSentenceBuilder.RuleKind;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-31: the sentence builder produces all eight guidance rule kinds and their JSON survives a SESSION / WORLD_SAVE round trip.
 */
class GuidanceRuleBuilderTest {

    private static final ComponentStackKey ironIngot = OfflineContentTypes.item("minecraft:iron_ingot");
    private static final ComponentStackKey oakLog = OfflineContentTypes.item("minecraft:oak_log");
    private static final MachineEntry furnace = OfflineContentTypes.machine("minecraft:furnace");
    private static final Namespace craftingSystem = Namespace.parse("minecraft:crafting");
    private static final Namespace smeltingSystem = Namespace.parse("minecraft:smelting");
    private static final Namespace ae2Charger = Namespace.parse("ae2:charger");
    private static final TagKey<?> logsTag = PlanningTagKeys.itemTag(ResourceLocation.parse("minecraft:logs"));
    private static final CompiledRecipeKey packIron = new CompiledRecipeKey(
        craftingSystem,
        Optional.of(ResourceLocation.parse("minecraft:iron_block")),
        11L
    );
    private static final CompiledRecipeKey smeltIron = new CompiledRecipeKey(
        smeltingSystem,
        Optional.of(ResourceLocation.parse("minecraft:iron_ingot_from_smelting")),
        23L
    );
    private static final CostVector unitCost = new CostVector(1.5, 2.5, 3.5, 4.5, 5.5);

    @Test
    void everyRuleKindExposesAFieldTableAndSentenceTemplate() {
        assertEquals(8, RuleSentenceBuilder.kindCount());
        for (RuleKind kind : RuleKind.values()) {
            assertFalse(kind.fields().isEmpty(), "field table must not be empty for " + kind);
            assertTrue(
                kind.fields().stream().anyMatch(field -> field.required()),
                "kind " + kind + " needs at least one required field"
            );
        }
    }

    @Test
    void missingAndInvalidFieldsAreReportedBeforeBuild() {
        RuleSentenceBuilder recipe = RuleSentenceBuilder.of(RuleKind.recipe);
        RuleSentenceBuilder.Validation missing = recipe.validate();
        assertFalse(missing.ok());
        assertTrue(keysOf(missing.missing()).contains(RuleSentenceBuilder.fieldRecipe));
        assertTrue(recipe.tryBuild("rule:missing").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> recipe.build("rule:missing"));

        recipe.put(RuleSentenceBuilder.fieldStrength, RuleStrength.hardBan)
                .put(RuleSentenceBuilder.fieldRecipe, "not-a-recipe-key");
        RuleSentenceBuilder.Validation invalid = recipe.validate();
        assertTrue(invalid.missing().isEmpty());
        assertEquals(List.of(RuleSentenceBuilder.fieldRecipe), keysOf(invalid.invalid()));
        assertTrue(recipe.tryBuild("rule:invalid").isEmpty());

        assertThrows(IllegalArgumentException.class, () -> recipe.put(RuleSentenceBuilder.fieldResource, ironIngot));
    }

    @Test
    void eightRuleKindsBuildFromSentences() {
        assertRecipe();
        assertSystem();
        assertMachine();
        assertResource();
        assertTagDefault();
        assertCycleBreak();
        assertCostOverride();
        assertModNamespace();
    }

    private void assertRecipe() {
        RuleSentenceBuilder draft = RuleSentenceBuilder.of(RuleKind.recipe).strength(RuleStrength.hardBan)
                .recipe(packIron);
        assertEquals(
            new RecipeRule("rule:recipe", GuidanceScope.session, RuleStrength.hardBan, packIron),
            draft.build("rule:recipe")
        );
    }

    private void assertSystem() {
        RuleSentenceBuilder draft = RuleSentenceBuilder.of(RuleKind.system).strength(RuleStrength.softPrefer)
                .system(ae2Charger).flag(RuleSentenceBuilder.fieldOptIn, true);
        assertEquals(
            new SystemRule("rule:system", GuidanceScope.session, RuleStrength.softPrefer, ae2Charger, true),
            draft.build("rule:system")
        );
    }

    private void assertMachine() {
        RuleSentenceBuilder draft = RuleSentenceBuilder.of(RuleKind.machine).strength(RuleStrength.softPenalize)
                .machine(furnace).flag(RuleSentenceBuilder.fieldAllowBuild, false);
        assertEquals(
            new MachineRule(
                "rule:machine",
                GuidanceScope.session,
                RuleStrength.softPenalize,
                furnace,
                false
            ),
            draft.build("rule:machine")
        );
    }

    private void assertResource() {
        RuleSentenceBuilder draft = RuleSentenceBuilder.of(RuleKind.resource).strength(RuleStrength.hardPin)
                .resource(ironIngot).flag(RuleSentenceBuilder.fieldFreeBase, true);
        assertEquals(
            new ResourceRule("rule:resource", GuidanceScope.session, RuleStrength.hardPin, ironIngot, true),
            draft.build("rule:resource")
        );
    }

    private void assertTagDefault() {
        RuleSentenceBuilder draft = RuleSentenceBuilder.of(RuleKind.tagDefault).tag(logsTag)
                .preferredMember(oakLog).strength(RuleStrength.softPrefer);
        assertEquals(
            new TagDefaultRule("rule:tag", GuidanceScope.session, RuleStrength.softPrefer, logsTag, oakLog),
            draft.build("rule:tag")
        );
    }

    private void assertCycleBreak() {
        RuleSentenceBuilder draft = RuleSentenceBuilder.of(RuleKind.cycleBreak).targetResource(ironIngot)
                .bannedSourceRecipe(smeltIron);
        assertEquals(
            new CycleBreakRule("rule:cycle", GuidanceScope.session, RuleStrength.hardBan, ironIngot, smeltIron),
            draft.build("rule:cycle")
        );
    }

    private void assertCostOverride() {
        RuleSentenceBuilder draft = RuleSentenceBuilder.of(RuleKind.costOverride).resource(ironIngot)
                .unitCost(unitCost);
        assertEquals(
            new CostOverrideRule("rule:cost", GuidanceScope.session, RuleStrength.softPenalize, ironIngot, unitCost),
            draft.build("rule:cost")
        );
    }

    private void assertModNamespace() {
        RuleSentenceBuilder draft = RuleSentenceBuilder.of(RuleKind.modNamespace).strength(RuleStrength.hardBan)
                .mod("create");
        assertEquals(
            new ModNamespaceRule("rule:mod", GuidanceScope.session, RuleStrength.hardBan, "create"),
            draft.build("rule:mod")
        );
    }

    @Test
    void everyRuleKindRoundTripsThroughTheRuleCodecInBothScopes() {
        for (GuidanceScope scope : List.of(GuidanceScope.session, GuidanceScope.worldSave)) {
            List<GuidanceRule> rules = sampleRules(scope);
            List<GuidanceRule> expected = decodedRules(scope);
            for (int index = 0; index < rules.size(); index++) {
                GuidanceRule rule = rules.get(index);
                JsonElement json = WorldFacilityRegistry.guidanceRuleCodec.encodeStart(JsonOps.INSTANCE, rule)
                        .getOrThrow();
                GuidanceRule decoded = WorldFacilityRegistry.guidanceRuleCodec.parse(JsonOps.INSTANCE, json)
                        .getOrThrow();
                assertEquals(expected.get(index), decoded, "codec round trip lost fields for " + rule.ruleId());
                assertEquals(scope, decoded.scope());
            }
        }
    }

    @Test
    void worldScopedRulesSurviveFacilityRegistryJsonRoundTrip() {
        List<GuidanceRule> rules = sampleRules(GuidanceScope.worldSave);
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();
        for (GuidanceRule rule : rules) {
            registry.putWorldSaveRule(rule);
        }

        JsonElement encoded = registry.encodeToJson().orElseThrow();
        WorldFacilityRegistry reloaded = WorldFacilityRegistry.loadFromJson(encoded);

        assertEquals(decodedRules(GuidanceScope.worldSave), reloaded.worldSaveRules());
        assertEquals(8, reloaded.worldSaveRules().size());
    }

    @Test
    void sentencePreviewAndRuleSentencesRenderForEveryKind() {
        for (GuidanceRule rule : sampleRules(GuidanceScope.session)) {
            assertNotNull(RuleSentenceBuilder.sentenceOf(rule));
            assertTrue(RuleSentenceBuilder.kindOf(rule).isPresent());
        }
    }

    private static List<GuidanceRule> sampleRules(GuidanceScope scope) {
        List<GuidanceRule> rules = new ArrayList<>();
        rules.add(new RecipeRule("ui/recipe", scope, RuleStrength.hardBan, packIron));
        rules.add(new SystemRule("ui/system", scope, RuleStrength.softPrefer, ae2Charger, true));
        rules.add(new MachineRule("ui/machine", scope, RuleStrength.softPenalize, furnace, false));
        rules.add(new ResourceRule("ui/resource", scope, RuleStrength.hardPin, ironIngot, true));
        rules.add(new TagDefaultRule("ui/tag", scope, RuleStrength.softPrefer, logsTag, oakLog));
        rules.add(new CycleBreakRule("ui/cycle", scope, RuleStrength.hardBan, ironIngot, smeltIron));
        rules.add(new CostOverrideRule("ui/cost", scope, RuleStrength.softPenalize, ironIngot, unitCost));
        rules.add(new ModNamespaceRule("ui/mod", scope, RuleStrength.hardBan, "create"));
        return rules;
    }

    private static List<GuidanceRule> decodedRules(GuidanceScope scope) {
        List<GuidanceRule> rules = new ArrayList<>();
        rules.add(new RecipeRule("ui/recipe", scope, RuleStrength.hardBan, packIron));
        rules.add(new SystemRule("ui/system", scope, RuleStrength.softPrefer, ae2Charger, true));
        rules.add(
            new MachineRule(
                "ui/machine",
                scope,
                RuleStrength.softPenalize,
                MachineEntry.fromItemStack(new ItemStack(Items.FURNACE)),
                false
            )
        );
        rules.add(new ResourceRule("ui/resource", scope, RuleStrength.hardPin, ComponentStackKey.ofItem(Items.IRON_INGOT), true));
        rules.add(
            new TagDefaultRule(
                "ui/tag",
                scope,
                RuleStrength.softPrefer,
                logsTag,
                ComponentStackKey.ofItem(Items.OAK_LOG)
            )
        );
        rules.add(
            new CycleBreakRule(
                "ui/cycle",
                scope,
                RuleStrength.hardBan,
                ComponentStackKey.ofItem(Items.IRON_INGOT),
                smeltIron
            )
        );
        rules.add(
            new CostOverrideRule(
                "ui/cost",
                scope,
                RuleStrength.softPenalize,
                ComponentStackKey.ofItem(Items.IRON_INGOT),
                unitCost
            )
        );
        rules.add(new ModNamespaceRule("ui/mod", scope, RuleStrength.hardBan, "create"));
        return rules;
    }

    private static List<String> keysOf(List<Field> fields) {
        return fields.stream().map(Field::key).toList();
    }
}
