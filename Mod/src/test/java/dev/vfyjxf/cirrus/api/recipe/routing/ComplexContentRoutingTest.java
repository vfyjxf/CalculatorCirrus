package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DifferenceComponent;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlanDebugText;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssuePayload;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class ComplexContentRoutingTest {

    @Test
    void differenceInputConsumesMatchingStockAndDoesNotDisappear(MinecraftServer server) {
        InputContent filteredLogs = DifferenceComponent.from(
            InputContent.fromItemTag(ItemTags.LOGS, 1),
            InputContent.fromItemStack(new ItemStack(Items.BIRCH_LOG), 1),
            1,
            RecipeContent.MAX_CHANCE
        );
        var useFilteredLog = new RoutingTestFixtures.FakeRecipe(
            "use_filtered_log",
            java.util.List.of(filteredLogs),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 1))
        );
        RoutingService service = RoutingTestFixtures.service(useFilteredLog);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "use_filtered_log");

        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .stock(stock)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertNotNull(outcome.plan(), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ConsumeStock consume
                && consume.resource().matches(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)), MatchStrategy.identityAndComponent)
                && consume.amount() == 1L
        ), () -> describePlan(outcome.plan()));

        PlanAction.ExecuteRecipe targetAction = outcome.plan().actions()
            .selectInstancesOf(PlanAction.ExecuteRecipe.class)
            .detect(action -> "use_filtered_log".equals(Objects.requireNonNull(action.recipe().id()).getPath()));
        assertNotNull(targetAction);
        assertTrue(targetAction.boundInputs().size() == 1, () -> describePlan(outcome.plan()));
        assertTrue(targetAction.boundInputs().getFirst().input() instanceof DifferenceComponent, () -> describePlan(outcome.plan()));
    }

    @Test
    void nonEnumerableComplexInputConsumesMatchingStock(MinecraftServer server) {
        InputContent complexOakLog = new NonEnumerableItemContent("oak_log_like", Items.OAK_LOG, 1, RecipeContent.MAX_CHANCE);
        var useComplexLog = new RoutingTestFixtures.FakeRecipe(
            "use_complex_log",
            java.util.List.of(complexOakLog),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 1))
        );
        RoutingService service = RoutingTestFixtures.service(useComplexLog);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "use_complex_log");

        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .stock(stock)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.success() || outcome.partial(), () -> describePlan(outcome.plan()));
        assertNotNull(outcome.plan(), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ConsumeStock consume
                && consume.resource().matches(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)), MatchStrategy.identityAndComponent)
                && consume.amount() == 1L
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "use_complex_log".equals(execute.recipe().id().getPath())
                && execute.boundInputs().anySatisfy(binding -> binding.input() instanceof NonEnumerableItemContent)
        ), () -> describePlan(outcome.plan()));
    }

    @Test
    void nonEnumerableComplexInputMatchesProducerRecipe(MinecraftServer server) {
        var makeOakLog = new RoutingTestFixtures.FakeRecipe(
            "make_oak_log",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_LOG, 1))
        );
        var useComplexLog = new RoutingTestFixtures.FakeRecipe(
            "use_complex_log",
            java.util.List.of(new NonEnumerableItemContent("oak_log_like", Items.OAK_LOG, 1, RecipeContent.MAX_CHANCE)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 1))
        );
        RoutingService service = RoutingTestFixtures.service(makeOakLog, useComplexLog);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "use_complex_log");

        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_PLANKS, 1)));

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .stock(stock)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.success() || outcome.partial(), () -> describePlan(outcome.plan()));
        assertNotNull(outcome.plan(), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_oak_log".equals(execute.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "use_complex_log".equals(execute.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
    }

    @Test
    void executionProfileKeepsNonEnumerableComplexInputAsRootFallback(MinecraftServer server) {
        var makeOakLog = new RoutingTestFixtures.FakeRecipe(
            "make_oak_log",
            java.util.List.of(new UnmaterializableInput("mystery_sap", 1, RecipeContent.MAX_CHANCE)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_LOG, 1))
        );
        var useComplexLog = new RoutingTestFixtures.FakeRecipe(
            "use_complex_log",
            java.util.List.of(new NonEnumerableItemContent("oak_log_like", Items.OAK_LOG, 1, RecipeContent.MAX_CHANCE)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 1))
        );
        RoutingService service = RoutingTestFixtures.service(makeOakLog, useComplexLog);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "use_complex_log");

        assertNotNull(target);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.success() || outcome.partial(), () -> describePlan(outcome.plan()));
        assertNotNull(outcome.plan(), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.PendingInput consume
                && consume.input() instanceof NonEnumerableItemContent content
                && "oak_log_like".equals(content.label())
                && consume.amount() == 1L
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_oak_log".equals(execute.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.issues().anySatisfy(issue ->
            issue.payload() instanceof RoutingIssuePayload.InputShortage shortage
                && shortage.input().contains("mystery_sap")
        ), () -> describeIssues(outcome));
        assertTrue(outcome.issues().anySatisfy(issue ->
            issue.payload() instanceof RoutingIssuePayload.InputShortage shortage
                && shortage.input().contains("oak_log_like")
        ), () -> describeIssues(outcome));
    }

    private static String describeIssues(dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome outcome) {
        return outcome.issues().collect(issue -> switch (issue.payload()) {
            case RoutingIssuePayload.InputShortage shortage -> shortage.input() + "@" + shortage.amount();
            default -> issue.code() + ":" + issue.messageArgs();
        }).makeString("; ");
    }

    private static String describePlan(@Nullable RoutingPlan plan) {
        return RoutingPlanDebugText.describe(plan);
    }

    private record NonEnumerableItemContent(
        String label,
        Item item,
        long amount,
        float chance
    ) implements ComplexContent {
        @Override
        public boolean isEmpty() {
            return amount <= 0;
        }

        @Override
        public NonEnumerableItemContent copy() {
            return this;
        }

        @Override
        public NonEnumerableItemContent copyWithAmount(long amount) {
            return new NonEnumerableItemContent(label, item, amount, chance);
        }

        @Override
        public NonEnumerableItemContent copyWithChance(float chance) {
            return new NonEnumerableItemContent(label, item, amount, chance);
        }

        @Override
        public NonEnumerableItemContent copyWithAmountAndChance(long amount, float chance) {
            return new NonEnumerableItemContent(label, item, amount, chance);
        }

        @Override
        public boolean mergeable(dev.vfyjxf.cirrus.api.recipe.content.RecipeContent other) {
            return other instanceof NonEnumerableItemContent content
                && item == content.item
                && Objects.equals(label, content.label);
        }

        @Override
        public boolean satisfy(ContentVariant actual) {
            return actual instanceof ContentStack<?, ?> stack
                && stack.isItem()
                && stack.<Item, ItemStack>cast().content() == item;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record UnmaterializableInput(
        String label,
        long amount,
        float chance
    ) implements ComplexContent {
        @Override
        public boolean isEmpty() {
            return amount <= 0;
        }

        @Override
        public UnmaterializableInput copy() {
            return this;
        }

        @Override
        public UnmaterializableInput copyWithAmount(long amount) {
            return new UnmaterializableInput(label, amount, chance);
        }

        @Override
        public UnmaterializableInput copyWithChance(float chance) {
            return new UnmaterializableInput(label, amount, chance);
        }

        @Override
        public UnmaterializableInput copyWithAmountAndChance(long amount, float chance) {
            return new UnmaterializableInput(label, amount, chance);
        }

        @Override
        public boolean mergeable(dev.vfyjxf.cirrus.api.recipe.content.RecipeContent other) {
            return other instanceof UnmaterializableInput input && Objects.equals(label, input.label);
        }

        @Override
        public boolean satisfy(ContentVariant actual) {
            return false;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
