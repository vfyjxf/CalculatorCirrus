package dev.vfyjxf.cirrus.api.recipe.routing;

import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.recipe.codec.CodecJsonIO;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ActiveRuleSelection;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ActiveRoutingRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.FileRuleRepository;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleCollection;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleCollectionKey;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleCollectionScope;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleClauseSpec;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleContentChannel;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfilePreset;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfileSpec;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyRuleSpec;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.TagExpr;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.UnresolvedInputMode;
import dev.vfyjxf.cirrus.config.CalculatorRuleSelection;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class RuleSerializationRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void sourcePolicyAndRuleDefinitionRoundTripThroughCodec() {
        RuleDefinition rule = customRule("world_a");

        var encoded = RuleDefinition.CODEC.encodeStart(JsonOps.INSTANCE, rule).result().orElseThrow();
        RuleDefinition decoded = RuleDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(rule.ruleId(), decoded.ruleId());
        assertEquals(rule.profile().shortlistK(), decoded.profile().shortlistK());
        assertEquals(rule.sourcePolicy().rules().size(), decoded.sourcePolicy().rules().size());
        assertEquals(rule.clauses().size(), decoded.clauses().size());
        assertEquals(rule.toProfile().profileId(), decoded.toProfile().profileId());
    }

    @Test
    void repositoryKeepsWorldRulesIsolatedUnlessRequested() throws Exception {
        FileRuleRepository repository = new FileRuleRepository(tempDir);
        RuleDefinition worldA = customRule("world_a");
        RuleDefinition worldB = customRule("world_b");
        RuleCollectionKey keyA = new RuleCollectionKey(RuleCollectionScope.world, "world_a");
        RuleCollectionKey keyB = new RuleCollectionKey(RuleCollectionScope.world, "world_b");

        repository.saveCollection(new RuleCollection(keyA, List.of(worldA)));
        repository.saveCollection(new RuleCollection(keyB, List.of(worldB)));
        repository.saveActiveSelection("world_a", new ActiveRuleSelection(keyA, worldA.ruleId()));

        var isolated = repository.loadView("world_a", false);
        var shared = repository.loadView("world_a", true);

        assertEquals(worldA.ruleId(), isolated.activeRuleOrDefault().ruleId());
        assertFalse(isolated.visibleCollections().stream().anyMatch(collection -> collection.key().equals(keyB)));
        assertTrue(shared.visibleCollections().stream().anyMatch(collection -> collection.key().equals(keyB)));
    }

    @Test
    void codecJsonIoPersistsRuleCollection() throws Exception {
        RuleCollection collection = new RuleCollection(
            new RuleCollectionKey(RuleCollectionScope.player, "player"),
            List.of(customRule("player"))
        );
        Path file = tempDir.resolve("rules.json");

        CodecJsonIO.write(file, RuleCollection.CODEC, collection);
        RuleCollection decoded = CodecJsonIO.read(file, RuleCollection.CODEC);

        assertEquals(collection.key(), decoded.key());
        assertEquals(collection.rules().getFirst().ruleId(), decoded.rules().getFirst().ruleId());
    }

    @Test
    void defaultRulesIncludeCalculatorPreviewWithoutExecutionShortageLifting() {
        RuleDefinition preview = DefaultRuleDefinitions.calculatorPreview();

        assertEquals(DefaultRuleDefinitions.calculatorPreviewProfileId, preview.profile().profileId());
        assertEquals(UnresolvedInputMode.directSupplyOnNoSourceOrSafeOrCycleExhausted, preview.profile().unresolvedInputMode());
        assertFalse(preview.profile().liftMultiSemanticChildShortages());
        assertTrue(DefaultRuleDefinitions.standardExecution().profile().liftMultiSemanticChildShortages());
        assertTrue(DefaultRuleDefinitions.defaultCollection().rules().stream().anyMatch(rule ->
            rule.ruleId().equals(DefaultRuleDefinitions.calculatorPreviewRuleId)
        ));
    }

    @Test
    void calculatorRuleSelectionCanUseActiveRuleDefinition() {
        RuleDefinition custom = customRule("active_calculator");
        RuleDefinition previous = ActiveRoutingRule.activeRule();
        try {
            ActiveRoutingRule.setActiveRule(custom);

            RuleDefinition resolved = CalculatorRuleSelection.active.resolve();

            assertEquals(custom.ruleId(), resolved.ruleId());
            assertEquals(custom.profile().profileId(), resolved.profile().profileId());
            assertEquals(custom.sourcePolicy().policyId(), resolved.sourcePolicy().policyId());
        } finally {
            ActiveRoutingRule.setActiveRule(previous);
        }
    }

    @Test
    void materialClauseContributesScorePenaltyWhenCandidateUsesMaterial(MinecraftServer server) {
        var recipe = new RoutingTestFixtures.FakeRecipe(
            "stone_tool",
            List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 1)),
            List.of(RoutingTestFixtures.out(Items.STICK, 1))
        );
        RecipeGraph graph = RoutingTestFixtures.graph(recipe);
        RecipeGraph.Node node = null;
        for (RecipeGraph.Node candidate : graph.nodes()) {
            if (candidate.recipe().id() != null && "stone_tool".equals(candidate.recipe().id().getPath())) {
                node = candidate;
                break;
            }
        }
        assertTrue(node != null);
        var context = new dev.vfyjxf.cirrus.api.recipe.routing.rule.CandidateContext(
            node,
            InputContent.fromItemStack(new ItemStack(Items.STICK), 1),
            1,
            1,
            0,
            false,
            0,
            1
        );
        var profile = customRule("score").toProfile();
        double[] score = profile.newScoreVector();
        var accumulator = profile.newAccumulator(score);

        for (var rule : profile.scoreRules()) {
            rule.contribute(context, accumulator);
        }

        assertEquals(1.5d, score[0], 0.0001d);
    }

    private static RuleDefinition customRule(String suffix) {
        Namespace id = Namespace.of("test", "routing/rule/" + suffix);
        return new RuleDefinition(
            id,
            "Custom " + suffix,
            new RuleProfileSpec(
                Namespace.of("test", "routing/profile/" + suffix),
                RuleProfilePreset.standardExecution,
                UnresolvedInputMode.directSupplyOnNoSource,
                6,
                3,
                0.02,
                0.55,
                0.80,
                true
            ),
            new SourcePolicy(
                Namespace.of("test", "routing/source_policy/" + suffix),
                1,
                Lists.immutable.of(new SourcePolicyRuleSpec(
                    Namespace.of("test", "routing/source_policy_rule/" + suffix),
                    true,
                    TagExpr.not(TagExpr.has("test:blocked")),
                    SourcePolicyAction.allowOnlyMatch,
                    false
                ))
            ),
            Lists.immutable.of(RuleClauseSpec.materialScoreAdjustment(
                Namespace.of("test", "routing/clause/material_penalty_" + suffix),
                "Deweight cobblestone",
                RuleContentChannel.input,
                List.of("minecraft:cobblestone"),
                "craft",
                0.5d
            )),
            true
        );
    }
}
