package dev.vfyjxf.cirrus.api.recipe.rule;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the unified rule language: condition ops, actions, engine
 * compilation/evaluation, and codec round-trips. Uses synthetic
 * {@link RuleInput} fixtures only — the language is deliberately
 * registry-free so it can be tested without a game server.
 */
class RuleEngineTest {

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static final class TestSlot implements RuleInput.Slot {
        private final Set<String> ids;
        private final @Nullable String tagId;
        private final double amount;
        private final double chance;

        TestSlot(Set<String> ids, @Nullable String tagId, double amount, double chance) {
            this.ids = ids;
            this.tagId = tagId;
            this.amount = amount;
            this.chance = chance;
        }

        @Override
        public Set<String> ids() {
            return ids;
        }

        @Override
        public @Nullable String tagId() {
            return tagId;
        }

        @Override
        public double amount() {
            return amount;
        }

        @Override
        public double chance() {
            return chance;
        }
    }

    /** Builder-style synthetic recipe input. */
    private static final class TestInput implements RuleInput {
        private String systemId = "minecraft:crafting";
        private String recipeId = "minecraft:stick";
        private final List<String> machines = new ArrayList<>();
        private final List<TestSlot> inputs = new ArrayList<>();
        private final List<TestSlot> outputs = new ArrayList<>();
        private final Set<String> tags = new LinkedHashSet<>();
        private boolean candidateFields;
        private final Set<RuleFlag> flags = EnumSet.noneOf(RuleFlag.class);
        private final Map<RuleScalar, Double> scalars = new HashMap<>();
        private double coverage = Double.NaN;
        private @Nullable RecipeUsePolicy hintPolicy;

        TestInput system(String systemId) {
            this.systemId = systemId;
            return this;
        }

        TestInput recipe(String recipeId) {
            this.recipeId = recipeId;
            return this;
        }

        TestInput machine(String machine) {
            machines.add(machine);
            return this;
        }

        TestInput input(String id, double amount) {
            inputs.add(new TestSlot(Set.of(id), null, amount, 1.0d));
            return this;
        }

        TestInput tagInput(String tagId, Set<String> members) {
            inputs.add(new TestSlot(members, tagId, 1, 1.0d));
            return this;
        }

        TestInput chanceOutput(String id, double chance) {
            outputs.add(new TestSlot(Set.of(id), null, 1, chance));
            return this;
        }

        TestInput output(String id, double amount) {
            outputs.add(new TestSlot(Set.of(id), null, amount, 1.0d));
            return this;
        }

        TestInput tag(String tag) {
            tags.add(tag);
            return this;
        }

        TestInput candidate() {
            this.candidateFields = true;
            return this;
        }

        TestInput withFlag(RuleFlag flag) {
            flags.add(flag);
            return this;
        }

        TestInput scalar(RuleScalar field, double value) {
            scalars.put(field, value);
            return this;
        }

        TestInput hint(RecipeUsePolicy policy) {
            this.hintPolicy = policy;
            return this;
        }

        @Override
        public String systemId() {
            return systemId;
        }

        @Override
        public String recipeId() {
            return recipeId;
        }

        @Override
        public List<String> machines() {
            return machines;
        }

        @Override
        public int slotCount(boolean inputSide) {
            return (inputSide ? inputs : outputs).size();
        }

        @Override
        public TestSlot slot(boolean inputSide, int index) {
            return (inputSide ? inputs : outputs).get(index);
        }

        @Override
        public Set<String> inputIds() {
            Set<String> ids = new LinkedHashSet<>();
            for (Slot slot : inputs) {
                ids.addAll(slot.ids());
            }
            return ids;
        }

        @Override
        public Set<String> outputIds() {
            Set<String> ids = new LinkedHashSet<>();
            for (TestSlot slot : outputs) {
                ids.addAll(slot.ids());
            }
            return ids;
        }

        @Override
        public Set<String> tags() {
            return tags;
        }

        @Override
        public boolean hasCandidateFields() {
            return candidateFields;
        }

        @Override
        public boolean flag(RuleFlag flag) {
            return flags.contains(flag);
        }

        @Override
        public double scalar(RuleScalar scalar) {
            return scalars.getOrDefault(scalar, Double.NaN);
        }

        @Override
        public double coverage() {
            return coverage;
        }

        @Override
        public @Nullable RecipeUsePolicy hintPolicy() {
            return hintPolicy;
        }
    }

    private static RuleSpec spec(String id, RuleStage stage, RuleCondition condition, RuleAction... actions) {
        return new RuleSpec(Namespace.parse(id), "", true, stage, condition, List.of(actions));
    }

    /** Vacuously-true condition (empty all_of) for engine-level tests. */
    private static final RuleCondition always = new RuleCondition.AllOf(List.of());

    // ------------------------------------------------------------------
    // condition leaves
    // ------------------------------------------------------------------

    @Test
    void textMatchOpsBehaveAsDocumented() {
        RuleInput input = new TestInput().system("jei:category/ftbquests/quest");

        assertTrue(new RuleCondition.SystemId(TextMatchOp.contains, List.of("category/ftbquests/")).matches(input));
        assertTrue(new RuleCondition.SystemId(TextMatchOp.prefix, List.of("jei:category/")).matches(input));
        assertTrue(new RuleCondition.SystemId(TextMatchOp.suffix, List.of("/quest")).matches(input));
        assertTrue(new RuleCondition.SystemId(TextMatchOp.in, List.of("jei:category/ftbquests/quest")).matches(input));
        assertTrue(new RuleCondition.SystemId(TextMatchOp.glob, List.of("jei:category/ftbquests/*")).matches(input));
        assertFalse(new RuleCondition.SystemId(TextMatchOp.glob, List.of("jei:category/ftbquests/?")).matches(input));
        // regex keeps substring (find) semantics like the historical pattern rules
        assertTrue(new RuleCondition.SystemId(TextMatchOp.regex, List.of("ftbquests")).matches(input));
        assertFalse(new RuleCondition.SystemId(TextMatchOp.regex, List.of("^ftbquests")).matches(input));
        // mimic namespace compatibility: contains on the path fragment
        assertTrue(
            new RuleCondition.SystemId(TextMatchOp.contains, List.of("category/ftbquests/"))
                    .matches(new TestInput().system("cirrus_test:jei__category/ftbquests/quest"))
        );
        // empty values never match
        assertFalse(new RuleCondition.SystemId(TextMatchOp.in, List.of()).matches(input));
    }

    @Test
    void machineConditionMatchesMachineList() {
        RuleCondition condition = new RuleCondition.Machine(TextMatchOp.prefix, List.of("minecraft:furnace"));
        assertTrue(condition.matches(new TestInput().machine("minecraft:furnace")));
        assertFalse(condition.matches(new TestInput()));
    }

    @Test
    void materialConditionMatchesIdsTagsAndAmounts() {
        RuleInput compression = new TestInput().input("minecraft:iron_ingot", 9).output("minecraft:iron_block", 1);
        RuleInput decompression = new TestInput().input("minecraft:iron_block", 1).output("minecraft:iron_ingot", 9);

        RuleCondition nineToOne = RuleCondition.Material.ofTokens(
            RuleChannel.input,
            List.of("item:minecraft:iron_ingot"),
            new RuleCondition.Material.AmountSpec(NumOp.ge, 9),
            false
        );
        assertTrue(nineToOne.matches(compression));
        assertFalse(nineToOne.matches(decompression));

        RuleCondition tagSlot = RuleCondition.Material
                .ofTokens(RuleChannel.input, List.of("#c:plates/tin"), null, false);
        assertTrue(tagSlot.matches(new TestInput().tagInput("c:plates/tin", Set.of("alltheores:tin_plate"))));
        assertFalse(tagSlot.matches(new TestInput().input("alltheores:tin_plate", 1)));

        RuleCondition allInputs = RuleCondition.Material
                .ofTokens(RuleChannel.input, List.of("item:a", "item:b"), null, true);
        assertTrue(allInputs.matches(new TestInput().input("a", 1).input("b", 1)));
        assertFalse(allInputs.matches(new TestInput().input("a", 1).input("c", 1)));
        assertFalse(allInputs.matches(new TestInput()), "all-quantifier with zero slots must fail");
    }

    @Test
    void slotCountAndShapeConditions() {
        RuleInput nineToOne = new TestInput().input("minecraft:iron_ingot", 9).output("minecraft:iron_block", 1);

        assertTrue(new RuleCondition.SlotCount(RuleChannel.input, NumOp.eq, 1).matches(nineToOne));
        assertTrue(new RuleCondition.SlotCount(RuleChannel.output, NumOp.eq, 1).matches(nineToOne));
        assertTrue(new RuleCondition.SlotCount(RuleChannel.any, NumOp.eq, 2).matches(nineToOne));
        assertTrue(new RuleCondition.HasConcreteInput(true).matches(nineToOne));

        RuleInput generator = new TestInput().output("minecraft:cobblestone", 1);
        assertTrue(new RuleCondition.HasConcreteInput(false).matches(generator));
        assertTrue(new RuleCondition.SlotCount(RuleChannel.output, NumOp.ge, 1).matches(generator));
    }

    @Test
    void idOverlapDetectsSwaps() {
        RuleInput swap = new TestInput().input("chipped:gray_concrete_bricks", 1)
                .output("chipped:gray_concrete_bricks", 1);
        assertTrue(new RuleCondition.IdOverlap().matches(swap));

        RuleInput normal = new TestInput().input("minecraft:stone", 1).output("minecraft:stone_bricks", 4);
        assertFalse(new RuleCondition.IdOverlap().matches(normal));
    }

    @Test
    void chanceConditionDetectsProbabilisticOutputs() {
        RuleInput chancy = new TestInput().input("minecraft:stone", 1).chanceOutput("minecraft:flint", 0.25d);
        assertTrue(new RuleCondition.Chance(RuleChannel.output, NumOp.lt, 1).matches(chancy));
        assertFalse(
            new RuleCondition.Chance(RuleChannel.any, NumOp.lt, 1)
                    .matches(new TestInput().input("minecraft:stone", 1).output("minecraft:stone_bricks", 4))
        );
    }

    @Test
    void candidateLeavesRequireCandidateFields() {
        RuleCondition roundTrip = new RuleCondition.FlagCondition(RuleFlag.roundTrip);
        assertFalse(
            roundTrip.matches(new TestInput().withFlag(RuleFlag.roundTrip)),
            "catalog domain must not see flags"
        );
        assertTrue(roundTrip.matches(new TestInput().candidate().withFlag(RuleFlag.roundTrip)));

        RuleCondition deep = new RuleCondition.ScalarCondition(RuleScalar.depth, NumOp.ge, 3);
        assertFalse(deep.matches(new TestInput().candidate().scalar(RuleScalar.depth, 2)));
        assertTrue(deep.matches(new TestInput().candidate().scalar(RuleScalar.depth, 5)));

        RuleCondition lastResort = new RuleCondition.HintCondition(RecipeUsePolicy.lastResort);
        assertTrue(lastResort.matches(new TestInput().candidate().hint(RecipeUsePolicy.lastResort)));
        assertFalse(lastResort.matches(new TestInput().candidate()));
    }

    @Test
    void classificationTagLeafSeesCatalogTags() {
        RuleCondition junk = new RuleCondition.ClassificationTag("cirrus:quality/junk");
        assertTrue(junk.matches(new TestInput().tag("cirrus:quality/junk")));
        assertFalse(junk.matches(new TestInput()));
    }

    @Test
    void combinatorsCompose() {
        RuleCondition condition = new RuleCondition.AllOf(
            List.of(
                new RuleCondition.SystemId(TextMatchOp.prefix, List.of("minecraft:")),
                new RuleCondition.Not(new RuleCondition.SystemId(TextMatchOp.suffix, List.of("anvil")))
            )
        );
        assertTrue(condition.matches(new TestInput().system("minecraft:crafting")));
        assertFalse(condition.matches(new TestInput().system("minecraft:anvil")));
        assertFalse(condition.matches(new TestInput().system("jei:category/create/mixing")));
        assertTrue(condition.dependsOnlyOnSystem());

        RuleCondition mixed = new RuleCondition.AnyOf(
            List.of(
                new RuleCondition.SystemId(TextMatchOp.in, List.of("minecraft:anvil")),
                new RuleCondition.IdOverlap()
            )
        );
        assertFalse(mixed.dependsOnlyOnSystem(), "id_overlap depends on recipe content");
    }

    // ------------------------------------------------------------------
    // engine
    // ------------------------------------------------------------------

    @Test
    void catalogVerdictCollectsTagsExclusionAndHints() {
        RuleEngine engine = RuleEngine.compileCatalogOnly(
            List.of(
                spec(
                    "cirrus:exclude_junk_systems",
                    RuleStage.catalog,
                    new RuleCondition.SystemId(TextMatchOp.contains, List.of("category/ftbquests/")),
                    new RuleAction.Tag("cirrus:quality/junk")
                ),
                spec(
                    "cirrus:hint_recolor",
                    RuleStage.catalog,
                    new RuleCondition.ClassificationTag("cirrus:pattern/recolor"),
                    new RuleAction.Hint(RecipeUsePolicy.discouraged)
                ),
                spec(
                    "cirrus:exclude_ladder",
                    RuleStage.catalog,
                    RuleCondition.Material.ofTokens(
                        RuleChannel.input,
                        List.of("item:minecraft:iron_ingot"),
                        new RuleCondition.Material.AmountSpec(NumOp.ge, 9),
                        false
                    ),
                    new RuleAction.Tag("cirrus:pattern/storage_ladder"),
                    new RuleAction.Hint(RecipeUsePolicy.discouraged)
                )
            )
        );

        RuleEngine.CatalogVerdict quest = engine
                .evaluateCatalog(new TestInput().system("jei:category/ftbquests/quest"));
        assertTrue(quest.tags().contains("cirrus:quality/junk"));
        assertFalse(quest.excluded());

        RuleEngine.CatalogVerdict ladder = engine
                .evaluateCatalog(new TestInput().input("minecraft:iron_ingot", 9).output("minecraft:iron_block", 1));
        assertTrue(ladder.tags().contains("cirrus:pattern/storage_ladder"));
        assertEquals(RecipeUsePolicy.discouraged, ladder.hint());

        // hint strictness wins
        RuleEngine strict = RuleEngine.compileCatalogOnly(
            List.of(
                spec("cirrus:soft", RuleStage.catalog, always, new RuleAction.Hint(RecipeUsePolicy.discouraged)),
                spec("cirrus:hard", RuleStage.catalog, always, new RuleAction.Hint(RecipeUsePolicy.lastResort))
            )
        );
        assertEquals(RecipeUsePolicy.lastResort, strict.evaluateCatalog(new TestInput()).hint());
    }

    @Test
    void includeOverridesExcludeInDocumentOrder() {
        RuleEngine engine = RuleEngine.compileCatalogOnly(
            List.of(
                spec("cirrus:cut", RuleStage.catalog, always, new RuleAction.Exclude()),
                spec(
                    "cirrus:rescue",
                    RuleStage.catalog,
                    new RuleCondition.SystemId(TextMatchOp.in, List.of("minecraft:crafting")),
                    new RuleAction.Include()
                )
            )
        );
        assertTrue(engine.evaluateCatalog(new TestInput().system("minecraft:smelting")).excluded());
        assertFalse(engine.evaluateCatalog(new TestInput().system("minecraft:crafting")).excluded());
    }

    @Test
    void systemOnlyFastPathMatchesSystemExclusion() {
        RuleEngine engine = RuleEngine.compileCatalogOnly(
            List.of(
                spec(
                    "cirrus:quests",
                    RuleStage.catalog,
                    new RuleCondition.SystemId(TextMatchOp.contains, List.of("category/ftbquests/")),
                    new RuleAction.Tag("cirrus:quality/junk")
                )
            )
        );
        assertTrue(engine.isExcludedSystem("jei:category/ftbquests/quest"));
        assertTrue(engine.isExcludedSystem("cirrus_test:jei__category/ftbquests/quest"));
        assertFalse(engine.isExcludedSystem("minecraft:crafting"));
    }

    @Test
    void candidateVerdictRejectsAndAdjusts() {
        RuleEngine engine = RuleEngine.compile(
            List.of(
                spec(
                    "cirrus:no_chance",
                    RuleStage.candidate,
                    new RuleCondition.Chance(RuleChannel.any, NumOp.lt, 1),
                    new RuleAction.Reject("probabilistic content")
                ),
                spec(
                    "cirrus:deep_penalty",
                    RuleStage.candidate,
                    new RuleCondition.ScalarCondition(RuleScalar.depth, NumOp.ge, 8),
                    RuleAction.ScoreAdjust.penalize("contextual", 500)
                )
            ),
            Set.of("contextual")
        );

        RuleEngine.CandidateVerdict chancy = engine
                .evaluateCandidate(new TestInput().candidate().input("a", 1).chanceOutput("b", 0.5d));
        assertTrue(chancy.rejected());
        assertEquals("probabilistic content", chancy.rejectReason());

        RuleEngine.CandidateVerdict deep = engine.evaluateCandidate(
            new TestInput().candidate().scalar(RuleScalar.depth, 10).input("a", 1).output("b", 1)
        );
        assertFalse(deep.rejected());
        assertEquals(1, deep.adjustments().size());
        assertEquals(500, deep.adjustments().getFirst().signedAmount(1.0d));

        RuleEngine.CandidateVerdict fine = engine.evaluateCandidate(
            new TestInput().candidate().scalar(RuleScalar.depth, 1).input("a", 1).output("b", 1)
        );
        assertTrue(fine.adjustments().isEmpty());
    }

    @Test
    void compileRejectsInvalidRules() {
        // score action against an undeclared component
        assertThrows(
            IllegalArgumentException.class,
            () -> RuleEngine.compile(
                List.of(
                    spec(
                        "cirrus:bad_component",
                        RuleStage.candidate,
                        always,
                        RuleAction.ScoreAdjust.penalize("nope", 1)
                    )
                ),
                Set.of("contextual")
            )
        );

        // action/stage mismatch
        assertThrows(
            IllegalArgumentException.class,
            () -> RuleEngine.compileCatalogOnly(
                List.of(spec("cirrus:mixed_stage", RuleStage.catalog, always, new RuleAction.Reject("nope")))
            )
        );

        // empty actions
        assertThrows(
            IllegalArgumentException.class,
            () -> RuleEngine.compileCatalogOnly(List.of(spec("cirrus:no_actions", RuleStage.catalog, always)))
        );

        // duplicate ids
        assertThrows(
            IllegalArgumentException.class,
            () -> RuleEngine.compileCatalogOnly(
                List.of(
                    spec("cirrus:dup", RuleStage.catalog, always, new RuleAction.Tag("cirrus:quality/junk")),
                    spec("cirrus:dup", RuleStage.catalog, always, new RuleAction.Tag("cirrus:quality/junk"))
                )
            )
        );

        // invalid regex fails at condition construction
        assertThrows(
            IllegalArgumentException.class,
            () -> new RuleCondition.SystemId(TextMatchOp.regex, List.of("(unclosed"))
        );
    }

    // ------------------------------------------------------------------
    // codec round-trips
    // ------------------------------------------------------------------

    @Test
    void conditionCodecRoundTrips() {
        RuleCondition condition = new RuleCondition.AllOf(
            List.of(
                new RuleCondition.SystemId(TextMatchOp.contains, List.of("category/ftbquests/")),
                RuleCondition.Material.ofTokens(
                    RuleChannel.input,
                    List.of("#c:plates", "item:alltheores:tin_plate"),
                    new RuleCondition.Material.AmountSpec(NumOp.ge, 2),
                    true
                ),
                new RuleCondition.Not(new RuleCondition.FlagCondition(RuleFlag.roundTrip))
            )
        );
        var encoded = RuleCondition.codec.encodeStart(JsonOps.INSTANCE, condition);
        var decoded = encoded.flatMap(json -> RuleCondition.codec.parse(JsonOps.INSTANCE, json));
        RuleCondition roundTripped = decoded.result().orElseThrow();
        assertEquals(condition, roundTripped);
    }

    @Test
    void ruleSpecAndDocumentCodecRoundTrip() {
        RuleDocument document = new RuleDocument(
            RuleDocument.currentVersion,
            Optional.of(
                new ScoreModel(
                    List.of(
                        new ScoreModel.Tier(
                            "safety",
                            List.of(new ScoreModel.Component("cycle", Optional.of("activeDemandCycle"), 1.0))
                        )
                    )
                )
            ),
            List.of(
                spec(
                    "cirrus:quests",
                    RuleStage.catalog,
                    new RuleCondition.SystemId(TextMatchOp.contains, List.of("category/ftbquests/")),
                    new RuleAction.Tag("cirrus:quality/junk")
                ),
                spec(
                    "cirrus:deep_penalty",
                    RuleStage.candidate,
                    new RuleCondition.ScalarCondition(RuleScalar.depth, NumOp.ge, 8),
                    new RuleAction.ScoreAdjust(
                        "contextual",
                        500,
                        RuleAction.ScoreAdjust.ScoreScale.coverageShortage,
                        false
                    ),
                    new RuleAction.Reject("too deep")
                )
            )
        );
        var encoded = RuleDocument.codec.encodeStart(JsonOps.INSTANCE, document);
        var json = encoded.result().orElseThrow();
        RuleDocument decoded = RuleDocument.codec.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(document, decoded);
    }

    @Test
    void documentRejectsWrongVersion() {
        var json = JsonParser.parseString("{\"version\":2,\"rules\":[]}");
        assertTrue(RuleDocument.codec.parse(JsonOps.INSTANCE, json).isError(), "v2 must not decode as v3");
    }

}
