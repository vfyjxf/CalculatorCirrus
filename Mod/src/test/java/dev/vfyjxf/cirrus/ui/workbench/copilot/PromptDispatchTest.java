package dev.vfyjxf.cirrus.ui.workbench.copilot;

import com.google.gson.JsonElement;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPrompt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.coal;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.cost;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.followUpOption;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.furnace;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.ironOre;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.machineRule;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.option;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.prompt;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.resolved;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.smeltIron;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.smelting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UI-TC-27: dispatch of the four decision card kinds, the already_owned follow-up feedback and a persistToWorld round trip. */
class PromptDispatchTest {

    private static ResolvedPrompt promptOf(DecisionPromptKind kind) {
        DecisionPrompt raw = switch (kind) {
            case machineAvailability -> prompt(
                "prompt:machine_fork:" + furnace,
                kind,
                0.82,
                option(
                    "accept_build",
                    cost(45.2, 0.0, 60.0, 0.0, 0.0),
                    List.of(machineRule("r1", furnace, RuleStrength.hardPin, true))
                ),
                option("reject_build", cost(0.0, 0.0, 0.0, 0.0, 0.0), List.of())
            );
            case materialFamilyAnchor -> prompt(
                "prompt:tag_choice:minecraft:logs",
                kind,
                0.44,
                option(
                    "pick_oak",
                    cost(0.0, 0.0, 0.0, 12.0, 0.0),
                    List.of(
                        new TagDefaultRule("r2", GuidanceScope.session, RuleStrength.hardPin, "minecraft:logs", coal)
                    )
                ),
                option("pick_spruce", cost(0.0, 0.0, 0.0, 9.0, 0.0), List.of())
            );
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm -> prompt(
                "prompt:expensive_leaf:[minecraft:item]" + ironOre.resourceId(),
                kind,
                0.61,
                option(
                    "treat_free",
                    cost(0.0, 0.0, 0.0, 30.0, 0.0),
                    List.of(new ResourceRule("r3", GuidanceScope.session, RuleStrength.hardPin, ironOre, true))
                ),
                option("keep_gather", cost(0.0, 0.0, 0.0, 0.0, 0.0), List.of())
            );
            case suspiciousJeiVerify, ruleConflictResolution -> prompt(
                "prompt:suspicious:" + smelting + "::minecraft:iron_ingot_from_smelting",
                kind,
                0.55,
                option(
                    "ban_recipe",
                    cost(0.0, 0.0, 0.0, 0.0, 4.0),
                    List.of(new RecipeRule("r4", GuidanceScope.session, RuleStrength.hardBan, smeltIron))
                ),
                option("accept_recipe", cost(0.0, 0.0, 0.0, 0.0, 0.0), List.of())
            );
        };
        return switch (kind) {
            case machineAvailability -> resolved(raw, null, furnace, null, null, null, Set.of("s-prov"));
            case materialFamilyAnchor -> resolved(raw, coal, null, "minecraft:logs", null, null, Set.of("s-craft"));
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm ->
                resolved(raw, ironOre, null, null, null, null, Set.of("s-acq"));
            case suspiciousJeiVerify, ruleConflictResolution ->
                resolved(raw, null, null, null, smeltIron, smelting, Set.of("s-craft"));
        };
    }

    @Test
    void eachPromptKindDispatchesExactlyOneAnswer() {
        for (DecisionPromptKind kind : DecisionPromptKind.values()) {
            CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(CoPilotTestFixtures.snapshot(null));
            ResolvedPrompt resolvedPrompt = promptOf(kind);
            PromptCardWidget card = new PromptCardWidget(host, resolvedPrompt, false, false);

            card.choose(0);

            assertEquals(1, host.feedbacks.size(), kind.name());
            HumanFeedback feedback = host.feedbacks.get(0);
            assertTrue(feedback instanceof AnswerPromptFeedback, kind.name());
            AnswerPromptFeedback answer = (AnswerPromptFeedback) feedback;
            assertEquals(resolvedPrompt.raw().promptId(), answer.promptId());
            assertEquals(resolvedPrompt.options().get(0).raw().optionId(), answer.chosenOptionId());
            assertFalse(answer.persistToWorld());
            assertTrue(card.answered(), kind.name());
            assertEquals(answer.chosenOptionId(), card.answeredOptionId());
        }
    }

    @Test
    void alreadyOwnedOptionUsesFollowUpDeclareOwned() {
        DecisionOption owned = followUpOption(
            "already_owned",
            cost(0.0, 0.0, 0.0, 0.0, 0.0),
            List.of(machineRule("r5", furnace, RuleStrength.hardPin, false)),
            new DeclareOwnedMachineFeedback(furnace, 1, false)
        );
        DecisionPrompt raw = prompt(
            "prompt:machine_fork:" + furnace,
            DecisionPromptKind.machineAvailability,
            0.9,
            option(
                "accept_build",
                cost(10.0, 0.0, 20.0, 0.0, 0.0),
                List.of(machineRule("r6", furnace, RuleStrength.hardPin, true))
            ),
            owned
        );
        ResolvedPrompt resolvedPrompt = resolved(raw, null, furnace, null, null, null, Set.of("s-prov"));

        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(CoPilotTestFixtures.snapshot(null));
        PromptCardWidget card = new PromptCardWidget(host, resolvedPrompt, true, false);

        card.choose(1);

        assertEquals(1, host.feedbacks.size());
        assertTrue(host.feedbacks.get(0) instanceof DeclareOwnedMachineFeedback);
        DeclareOwnedMachineFeedback declare = (DeclareOwnedMachineFeedback) host.feedbacks.get(0);
        assertEquals(furnace, declare.machineId());
        assertEquals(1, declare.ownedCount());
        assertFalse(declare.persistToWorld());

        CoPilotTestFixtures.FakeHost worldHost = new CoPilotTestFixtures.FakeHost(CoPilotTestFixtures.snapshot(null));
        PromptCardWidget worldCard = new PromptCardWidget(worldHost, resolvedPrompt, true, true);
        worldCard.choose(1);
        DeclareOwnedMachineFeedback scoped = (DeclareOwnedMachineFeedback) worldHost.feedbacks.get(0);
        assertTrue(scoped.persistToWorld());
    }

    @Test
    void persistToWorldAnswersAndRoundTripsWorldSaveRules() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(CoPilotTestFixtures.snapshot(null));
        ResolvedPrompt resolvedPrompt = promptOf(DecisionPromptKind.baseOrSupplyBoundary);
        PromptCardWidget card = new PromptCardWidget(host, resolvedPrompt, false, false);
        assertFalse(card.persistToWorld());

        card.setPersistToWorld(true);
        card.choose(0);

        AnswerPromptFeedback answer = (AnswerPromptFeedback) host.feedbacks.get(0);
        assertTrue(answer.persistToWorld());

        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();
        GuidanceRule freeRule = CoPilotDnd.freeSourceRule(ironOre, true);
        GuidanceRule banRule = CoPilotDnd.banRule(coal, true);
        assertEquals(GuidanceScope.worldSave, freeRule.scope());
        assertEquals(GuidanceScope.worldSave, banRule.scope());
        assertEquals(GuidanceScope.session, CoPilotDnd.freeSourceRule(ironOre, false).scope());
        registry.putWorldSaveRule(freeRule);
        registry.putWorldSaveRule(banRule);

        JsonElement encoded = registry.encodeToJson().orElseThrow();
        WorldFacilityRegistry reloaded = WorldFacilityRegistry.loadFromJson(encoded);

        assertEquals(registry.worldSaveRules(), reloaded.worldSaveRules());
        assertEquals(2, reloaded.worldSaveRules().size());
    }

    @Test
    void hoverPreviewWaitsForTheHoverThreshold() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(CoPilotTestFixtures.snapshot(null));
        ResolvedPrompt resolvedPrompt = promptOf(DecisionPromptKind.machineAvailability);
        PromptCardWidget card = new PromptCardWidget(host, resolvedPrompt, false, false);
        List<PromptCardWidget.PreviewSubject> previews = new ArrayList<>();
        card.previewListener(previews::add);

        assertNull(card.hoverUpdate(0, 1_000L));
        assertNull(card.hoverUpdate(0, 1_119L));
        PromptCardWidget.PreviewSubject subject = card.hoverUpdate(0, 1_120L);
        assertNotNull(subject);
        assertEquals(PromptCardWidget.PreviewKind.buildMachine, subject.kind());
        assertEquals(Set.of("s-prov"), subject.stepIds());
        assertEquals(1, previews.size());
        assertNull(card.hoverUpdate(0, 2_000L));
        assertEquals(1, previews.size());
        assertNull(card.hoverUpdate(-1, 2_010L));
        assertEquals(2, previews.size());
        assertNull(previews.get(1));
    }

    @Test
    void deltaChipsFollowCostSemantics() {
        List<PromptDeltaFormat.Chip> chips = PromptDeltaFormat.deltaChips(cost(45.2, 0.0, 60.0, 12.0, 0.0));
        assertEquals(3, chips.size());
        assertEquals(ParetoGeometry.Axis.capital, chips.get(0).axis());
        assertEquals(ParetoGeometry.Axis.human, chips.get(1).axis());
        assertEquals(ParetoGeometry.Axis.scarcity, chips.get(2).axis());
        assertEquals("h", PromptDeltaFormat.styleClass(ParetoGeometry.Axis.human));
        assertEquals("k", PromptDeltaFormat.styleClass(ParetoGeometry.Axis.capital));
        assertEquals(10, PromptDeltaFormat.leverageCells(1.0));
        assertEquals(8, PromptDeltaFormat.leverageCells(0.8));
        assertTrue(PromptDeltaFormat.isHighLeverage(0.75));
        assertFalse(PromptDeltaFormat.isHighLeverage(0.74));
        CostVector before = cost(10.0, 2.0, 4.0, 6.0, 8.0);
        CostVector after = cost(12.5, 1.0, 9.0, 3.0, 8.0);
        double[] delta = PromptDeltaFormat.signedDelta(before, after);
        assertEquals("+2.5s", PromptDeltaFormat.signedDeltaText(delta[0], true));
        assertEquals("-1s", PromptDeltaFormat.signedDeltaText(delta[1], true));
        assertEquals("+5", PromptDeltaFormat.signedDeltaText(delta[2], false));
    }
}
