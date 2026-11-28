package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.cost;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.diamond;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.graph;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.ironOre;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.oakLog;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.option;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.optionWithFollowUp;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.prompt;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.sevenTypePlan;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.smeltIron;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.taggedPlan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptSubjectResolverTest {

    private static final MachineEntry furnace = OfflineContentTypes.machine("minecraft:furnace");
    private static final MachineEntry mechanicalPress = OfflineContentTypes.machine("create:mechanical_press");
    private static final MachineEntry charger = OfflineContentTypes.machine("ae2:charger");
    private static final TagKey<?> logsTag = OfflineContentTypes
            .tagFor(OfflineContentTypes.itemContentType, "minecraft:logs");

    @Test
    void machineForkStripsPrefixAndAnchorsMachineSteps() {
        DecisionPrompt fork = prompt(
            "prompt:machine:" + furnace.canonicalKey(),
            DecisionPromptKind.machineAvailability,
            0.9,
            option(
                "accept_build",
                cost(0.5, 1.0),
                List.of(new MachineRule("mr", GuidanceScope.session, RuleStrength.softPrefer, furnace, true))
            ),
            optionWithFollowUp(
                "already_owned",
                cost(0.1, 0.0),
                List.of(),
                new DeclareOwnedMachineFeedback(furnace, 2, false)
            )
        );

        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(fork), sevenTypePlan(), graph()).get(0);

        assertSame(fork, resolved.raw());
        assertEquals(furnace, resolved.primaryMachine());
        assertNull(resolved.primaryResource());
        assertNull(resolved.tag());
        assertNull(resolved.recipeKey());
        assertNull(resolved.systemId());
        assertEquals(Set.of("s003", "s004"), resolved.anchoredStepIds());
        assertEquals(2, resolved.options().size());

        ResolvedPromptOption accept = resolved.options().get(0);
        assertEquals(furnace, accept.machine());
        assertSame(fork.options().get(0).estimatedDelta(), accept.delta());
        assertFalse(accept.hasFollowUp());

        ResolvedPromptOption owned = resolved.options().get(1);
        assertTrue(owned.hasFollowUp());
        assertSame(fork.options().get(1).estimatedDelta(), owned.delta());
    }

    @Test
    void machineForkFallsBackToCompiledRulesAndFollowUp() {
        DecisionPrompt fromRule = prompt(
            "prompt:opaque",
            DecisionPromptKind.machineAvailability,
            0.4,
            option(
                "a",
                cost(0.0, 0.0),
                List.of(new MachineRule("mr", GuidanceScope.session, RuleStrength.softPrefer, mechanicalPress, true))
            ),
            option("b", cost(0.0, 0.0), List.of())
        );
        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(fromRule), sevenTypePlan(), graph()).get(0);
        assertEquals(mechanicalPress, resolved.primaryMachine());
        assertEquals(Set.of("s007"), resolved.anchoredStepIds());

        DecisionPrompt fromFollowUp = prompt(
            "prompt:opaque",
            DecisionPromptKind.machineAvailability,
            0.4,
            option("a", cost(0.0, 0.0), List.of()),
            optionWithFollowUp("b", cost(0.0, 0.0), List.of(), new DeclareOwnedMachineFeedback(charger, 1, false))
        );
        ResolvedPrompt followUpResolved = PromptSubjectResolver
                .resolveAll(List.of(fromFollowUp), sevenTypePlan(), graph()).get(0);
        assertEquals(charger, followUpResolved.primaryMachine());
        assertTrue(followUpResolved.anchoredStepIds().isEmpty());
    }

    @Test
    void tagChoiceExtractsTagAndPreferredMemberVariant() {
        TagDefaultRule tagRule = new TagDefaultRule(
            "tag-rule",
            GuidanceScope.session,
            RuleStrength.hardPin,
            logsTag,
            oakLog
        );
        DecisionPrompt choice = prompt(
            "prompt:tag:minecraft:logs",
            DecisionPromptKind.materialFamilyAnchor,
            0.7,
            option("use_log", cost(1.0, 0.0), List.of(tagRule)),
            option("pick_any", cost(2.0, 0.0), List.of())
        );

        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(choice), taggedPlan(), graph()).get(0);

        assertEquals(logsTag, resolved.tag());
        assertNull(resolved.primaryResource());
        assertNull(resolved.primaryMachine());
        assertEquals(Set.of("c001"), resolved.anchoredStepIds());
        assertEquals(oakLog, resolved.options().get(0).variantResource());
        assertNull(resolved.options().get(1).variantResource());
        assertNull(resolved.options().get(0).machine());
    }

    @Test
    void tagChoiceFallsBackToCompiledRuleTag() {
        DecisionPrompt choice = prompt(
            "prompt:opaque",
            DecisionPromptKind.materialFamilyAnchor,
            0.5,
            option(
                "a",
                cost(0.0, 0.0),
                List.of(new TagDefaultRule("tag-rule", GuidanceScope.session, RuleStrength.hardPin, logsTag, oakLog))
            ),
            option("b", cost(0.0, 0.0), List.of())
        );

        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(choice), taggedPlan(), graph()).get(0);

        assertEquals(logsTag, resolved.tag());
        assertEquals(oakLog, resolved.options().get(0).variantResource());
        assertEquals(Set.of("c001"), resolved.anchoredStepIds());
    }

    @Test
    void expensiveLeafParsesResourceKeyAndAnchorsGatheringSteps() {
        DecisionPrompt leaf = prompt(
            "prompt:supply:minecraft:iron_ore",
            DecisionPromptKind.baseOrSupplyBoundary,
            0.8,
            option(
                "free",
                cost(0.0, 0.0),
                List.of(new ResourceRule("rr", GuidanceScope.session, RuleStrength.hardPin, ironOre, true))
            ),
            option("normal", cost(3.0, 0.0), List.of())
        );

        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(leaf), sevenTypePlan(), graph()).get(0);

        assertEquals(ironOre, resolved.primaryResource());
        assertNull(resolved.primaryMachine());
        assertEquals(Set.of("s001"), resolved.anchoredStepIds());
        assertSame(leaf.options().get(1).estimatedDelta(), resolved.options().get(1).delta());
    }

    @Test
    void fluidResourceRuleKeepsContentType() {
        DecisionPrompt fluid = prompt(
            "prompt:supply:[minecraft:fluid]minecraft:water#abc123",
            DecisionPromptKind.baseOrSupplyBoundary,
            0.3,
            option(
                "a",
                cost(0.0, 0.0),
                List.of(
                    new ResourceRule(
                        "rr",
                        GuidanceScope.session,
                        RuleStrength.hardPin,
                        OfflineContentTypes.fluid("minecraft:water"),
                        true
                    )
                )
            ),
            option("b", cost(0.0, 0.0), List.of())
        );

        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(fluid), sevenTypePlan(), graph()).get(0);

        assertEquals(OfflineContentTypes.fluid("minecraft:water"), resolved.primaryResource());
        assertTrue(resolved.anchoredStepIds().isEmpty());
    }

    @Test
    void expensiveLeafFallsBackToResourceRule() {
        DecisionPrompt leaf = prompt(
            "prompt:opaque",
            DecisionPromptKind.baseOrSupplyBoundary,
            0.2,
            option(
                "a",
                cost(0.0, 0.0),
                List.of(new ResourceRule("rr", GuidanceScope.session, RuleStrength.softPenalize, diamond, false))
            ),
            option("b", cost(0.0, 0.0), List.of())
        );

        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(leaf), sevenTypePlan(), graph()).get(0);

        assertEquals(diamond, resolved.primaryResource());
        assertEquals(Set.of("s011"), resolved.anchoredStepIds());
    }

    @Test
    void suspiciousRecipeParsesRecipeKeyAndSystemAndAnchors() {
        DecisionPrompt smelt = prompt(
            "prompt:unverified:minecraft:smelting::minecraft:iron_ingot_from_smelting",
            DecisionPromptKind.suspiciousJeiVerify,
            0.6,
            option(
                "ban_recipe",
                cost(0.0, 0.0),
                List.of(new RecipeRule("rr", GuidanceScope.session, RuleStrength.hardBan, smeltIron))
            ),
            option("allow", cost(0.0, 0.0), List.of())
        );

        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(smelt), sevenTypePlan(), graph()).get(0);

        assertEquals(smeltIron, resolved.recipeKey());
        assertEquals(Namespace.parse("minecraft:smelting"), resolved.systemId());
        assertNull(resolved.primaryResource());
        assertEquals(Set.of("s004"), resolved.anchoredStepIds());
        assertEquals(
            List.of("rr"),
            resolved.options().get(0).raw().compiledRules().stream().map(GuidanceRule::ruleId).toList()
        );

        DecisionPrompt convert = prompt(
            "prompt:unverified:minecraft:crafting::minecraft:iron_ingot_from_iron_block",
            DecisionPromptKind.suspiciousJeiVerify,
            0.5,
            option("a", cost(0.0, 0.0), List.of()),
            option("b", cost(0.0, 0.0), List.of())
        );
        assertEquals(
            Set.of("s006"),
            PromptSubjectResolver.resolveAll(List.of(convert), sevenTypePlan(), graph()).get(0).anchoredStepIds()
        );

        DecisionPrompt loop = prompt(
            "prompt:unverified:minecraft:crafting::loop/shaft",
            DecisionPromptKind.suspiciousJeiVerify,
            0.5,
            option("a", cost(0.0, 0.0), List.of()),
            option("b", cost(0.0, 0.0), List.of())
        );
        assertEquals(
            Set.of("s009"),
            PromptSubjectResolver.resolveAll(List.of(loop), sevenTypePlan(), graph()).get(0).anchoredStepIds()
        );
    }

    @Test
    void suspiciousStructuralHashKeyIsParsed() {
        DecisionPrompt hashed = prompt(
            "prompt:unverified:minecraft:smelting::#deadbeef",
            DecisionPromptKind.suspiciousJeiVerify,
            0.4,
            option("a", cost(0.0, 0.0), List.of()),
            option("b", cost(0.0, 0.0), List.of())
        );

        ResolvedPrompt resolved = PromptSubjectResolver.resolveAll(List.of(hashed), sevenTypePlan(), graph()).get(0);

        assertEquals(
            new CompiledRecipeKey(Namespace.parse("minecraft:smelting"), Optional.empty(), 0xdeadbeefL),
            resolved.recipeKey()
        );
        assertEquals(Namespace.parse("minecraft:smelting"), resolved.systemId());
        assertTrue(resolved.anchoredStepIds().isEmpty());
    }

    @Test
    // Negative test: the resolver rejects a null graph, so the NPE contract is asserted by passing null.
    @SuppressWarnings("NullAway")
    void suspiciousFallsBackToRulesAndToleratesNullPlan() {
        DecisionPrompt prompt = prompt(
            "prompt:opaque",
            DecisionPromptKind.suspiciousJeiVerify,
            0.3,
            option(
                "ban_system",
                cost(0.0, 0.0),
                List.of(
                    new SystemRule(
                        "sr",
                        GuidanceScope.session,
                        RuleStrength.hardBan,
                        Namespace.parse("create:pressing"),
                        false
                    )
                )
            ),
            option("allow", cost(0.0, 0.0), List.of())
        );

        ResolvedPrompt onPlan = PromptSubjectResolver.resolveAll(List.of(prompt), sevenTypePlan(), graph()).get(0);
        assertNull(onPlan.recipeKey());
        assertEquals(Namespace.parse("create:pressing"), onPlan.systemId());
        assertTrue(onPlan.anchoredStepIds().isEmpty());

        ResolvedPrompt withoutPlan = PromptSubjectResolver.resolveAll(List.of(prompt), null, graph()).get(0);
        assertEquals(Namespace.parse("create:pressing"), withoutPlan.systemId());
        assertTrue(withoutPlan.anchoredStepIds().isEmpty());

        assertThrows(NullPointerException.class, () -> PromptSubjectResolver.resolveAll(List.of(prompt), null, null));
    }

    @Test
    void resolveAllSkipsNullEntriesAndPreservesOrder() {
        DecisionPrompt first = prompt(
            "prompt:supply:minecraft:iron_ore",
            DecisionPromptKind.baseOrSupplyBoundary,
            0.2,
            option(
                "a",
                cost(0.0, 0.0),
                List.of(new ResourceRule("rr", GuidanceScope.session, RuleStrength.softPenalize, ironOre, false))
            ),
            option("b", cost(0.0, 0.0), List.of())
        );
        DecisionPrompt second = prompt(
            "prompt:supply:minecraft:diamond",
            DecisionPromptKind.baseOrSupplyBoundary,
            0.1,
            option(
                "a",
                cost(0.0, 0.0),
                List.of(new ResourceRule("rr", GuidanceScope.session, RuleStrength.softPenalize, diamond, false))
            ),
            option("b", cost(0.0, 0.0), List.of())
        );

        List<ResolvedPrompt> resolved = PromptSubjectResolver
                .resolveAll(Arrays.asList(first, null, second), sevenTypePlan(), graph());

        assertEquals(2, resolved.size());
        assertSame(first, resolved.get(0).raw());
        assertSame(second, resolved.get(1).raw());
        assertEquals(ironOre, resolved.get(0).primaryResource());
        assertEquals(diamond, resolved.get(1).primaryResource());
    }
}
