package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHints;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routing scenarios over real {@link ItemStack} {@code StackContent}s, mirroring
 * {@link dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario} topologies but exercising the
 * component part of {@link dev.vfyjxf.cirrus.api.content.MatchStrategy#identityAndComponent}:
 * stage outputs carry data components, and competing recipes produce equal component variants
 * built from independent construction sites (the cross-origin deep-equals hot path).
 */
public final class ItemComponentRoutingScenario {
    private ItemComponentRoutingScenario() {
    }

    //region scenarios

    /**
     * Deep chain where every stage item is the same physical item (iron ingot) distinguished only by
     * components; competing recipes per step produce the identical component variant.
     */
    public static Scenario createComponentChain(int depth, int competingPerStep) {
        return createComponentChain(depth, competingPerStep, false);
    }

    /**
     * Realistic planning workload: component chain with byproduct outputs on every step (the ledger
     * byproduct pool fills during the search), plus a builder for a partially-stocked inventory —
     * even stages and some noise inputs are stocked, odd stages must be crafted, so consume-demand
     * traffic mixes stock hits, byproduct consumption and crafting with candidate rollback.
     */
    public static Scenario createComponentChain(int depth, int competingPerStep, boolean withByproducts) {
        int safeDepth = Math.max(2, depth);
        int safeCompeting = Math.max(0, competingPerStep);
        PlainVariantPool plainPool = new PlainVariantPool();
        MutableRecipeList recipes = new MutableRecipeList();

        for (int step = 1; step <= safeDepth; step++) {
            ContentStack<Item, ItemStack> previous = stageVariant(step - 1);
            ContentStack<Item, ItemStack> current = stageVariant(step);
            List<OutputContent> outputs = new ArrayList<>();
            outputs.add(output(current, 1));
            if (withByproducts) {
                outputs.add(output(byproductVariant(step), 1 + step % 2));
            }
            recipes.add(
                    "chain_step_" + step,
                    List.of(input(previous, 1 + step % 3)),
                    outputs
            );
            // Competing recipes produce the identical variant from independent noise inputs:
            // output comparison against demand is an equal-but-distinct-origin component match.
            for (int j = 0; j < safeCompeting; j++) {
                recipes.add(
                        "chain_step_" + step + "_alt_" + j,
                        List.of(input(PlainVariantPool.variantFor("chain_noise_" + step + "_" + j), 1)),
                        List.of(output(stageVariant(step), 1))
                );
            }
        }
        ContentStack<Item, ItemStack> target = stageVariant(safeDepth + 1);
        recipes.add(
                "chain_target",
                List.of(input(stageVariant(safeDepth), 1), input(plainPool.next(), 3)),
                List.of(output(target, 1))
        );

        ItemRecipeSystem system = new ItemRecipeSystem(recipes.toImmutable());
        ImmutableList<RecipeEntry<?>> entries = recipes.entries(system);
        RecipeEntry<ItemRecipe> recipeTarget = RecipeEntry.of(system, recipes.require("chain_target"));
        RuleDefinition rule = DefaultRuleDefinitions.calculatorPreview();
        return new Scenario(
                "chainComponent:" + safeDepth + ":" + safeCompeting,
                system,
                entries,
                recipeTarget,
                target,
                rule
        );
    }

    /**
     * Builds a partially-stocked inventory for the component chain: every even stage has limited
     * coverage, a third of the noise inputs is stocked, and unrelated component-bearing swords add
     * scan noise — mirroring a player inventory with component items.
     */
    public static Stock chainComponentStock(int depth, int competingPerStep, int noiseEntries) {
        Stock stock = Stock.empty();
        for (int step = 2; step <= depth; step += 2) {
            stock.add(ContentStack.fromStack(stageItemVariant(step), 3));
        }
        for (int step = 1; step <= depth; step++) {
            for (int j = 0; j < competingPerStep; j += 3) {
                stock.add(PlainVariantPool.variantFor("chain_noise_" + step + "_" + j).copyWithAmount(8));
            }
        }
        for (int i = 0; i < noiseEntries; i++) {
            stock.add(ContentStack.fromStack(noiseSword(i)));
        }
        return stock;
    }

    private static ItemStack stageItemVariant(int stage) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("bench_stage_" + stage));
        CompoundTag tag = new CompoundTag();
        tag.putString("variant", "bench_stage_" + stage);
        tag.putInt("stage", stage);
        CompoundTag nested = new CompoundTag();
        nested.putLong("seed", 1000L + stage);
        tag.put("meta", nested);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    private static ContentStack<Item, ItemStack> byproductVariant(int step) {
        ItemStack stack = new ItemStack(Items.COPPER_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("bench_byproduct_" + step));
        CompoundTag tag = new CompoundTag();
        tag.putInt("step", step);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return ContentStack.fromStack(stack);
    }

    private static ItemStack noiseSword(int index) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("inventory sword #" + index));
        stack.set(DataComponents.DAMAGE, index % 64);
        return stack;
    }
    /** Same chain shape with plain, component-free stacks — the no-regression reference. */
    public static Scenario createPlainChain(int depth, int competingPerStep) {
        int safeDepth = Math.max(2, depth);
        int safeCompeting = Math.max(0, competingPerStep);
        PlainVariantPool plainPool = new PlainVariantPool();
        MutableRecipeList recipes = new MutableRecipeList();

        for (int step = 1; step <= safeDepth; step++) {
            recipes.add(
                    "plain_step_" + step,
                    List.of(input(PlainVariantPool.variantFor("plain_stage_" + (step - 1)), 1 + step % 3)),
                    List.of(output(PlainVariantPool.variantFor("plain_stage_" + step), 1))
            );
            for (int j = 0; j < safeCompeting; j++) {
                recipes.add(
                        "plain_step_" + step + "_alt_" + j,
                        List.of(input(plainPool.next(), 1)),
                        List.of(output(PlainVariantPool.variantFor("plain_stage_" + step), 1))
                );
            }
        }
        ContentStack<Item, ItemStack> target = plainPool.next();
        recipes.add(
                "plain_target",
                List.of(input(PlainVariantPool.variantFor("plain_stage_" + safeDepth), 1), input(plainPool.next(), 3)),
                List.of(output(target, 1))
        );

        ItemRecipeSystem system = new ItemRecipeSystem(recipes.toImmutable());
        ImmutableList<RecipeEntry<?>> entries = recipes.entries(system);
        RecipeEntry<ItemRecipe> recipeTarget = RecipeEntry.of(system, recipes.require("plain_target"));
        RuleDefinition rule = DefaultRuleDefinitions.calculatorPreview();
        return new Scenario(
                "chainPlain:" + safeDepth + ":" + safeCompeting,
                system,
                entries,
                recipeTarget,
                target,
                rule
        );
    }

    /**
     * Wide gear/machine shape with component-bearing intermediates and many noise alternatives
     * producing the same gear variant.
     */
    public static Scenario createComponentWide(int noiseRecipes) {
        int safeNoise = Math.max(0, noiseRecipes);
        PlainVariantPool plainPool = new PlainVariantPool();
        MutableRecipeList recipes = new MutableRecipeList();
        ContentStack<Item, ItemStack> ore = stageVariant(100);
        ContentStack<Item, ItemStack> ingot = stageVariant(101);
        ContentStack<Item, ItemStack> plate = stageVariant(102);
        ContentStack<Item, ItemStack> gear = stageVariant(103);
        ContentStack<Item, ItemStack> machine = stageVariant(104);
        ContentStack<Item, ItemStack> target = stageVariant(105);

        recipes.add("wide_ore", List.of(input(ore, 1)), List.of(output(ingot, 2)));
        recipes.add("wide_plate", List.of(input(ingot, 2)), List.of(output(plate, 1)));
        recipes.add("wide_gear", List.of(input(plate, 4)), List.of(output(gear, 1)));
        recipes.add("wide_machine", List.of(input(gear, 2), input(plate, 1)), List.of(output(machine, 1)));
        recipes.add("wide_target", List.of(input(machine, 1), input(ore, 4)), List.of(output(target, 1)));
        for (int i = 0; i < safeNoise; i++) {
            recipes.add(
                    "wide_alt_gear_" + i,
                    List.of(input(plainPool.next(), 1)),
                    List.of(output(stageVariant(103), 1))
            );
        }

        ItemRecipeSystem system = new ItemRecipeSystem(recipes.toImmutable());
        ImmutableList<RecipeEntry<?>> entries = recipes.entries(system);
        RecipeEntry<ItemRecipe> recipeTarget = RecipeEntry.of(system, recipes.require("wide_target"));
        RuleDefinition rule = DefaultRuleDefinitions.calculatorPreview();
        return new Scenario(
                "wideComponent:" + safeNoise,
                system,
                entries,
                recipeTarget,
                target,
                rule
        );
    }

    //endregion

    //region fixtures

    private static ContentStack<Item, ItemStack> stageVariant(int stage) {
        return componentVariant("bench_stage_" + stage, stage);
    }

    /**
     * Independently rebuildable component-bearing variant of {@link Items#IRON_INGOT}: custom name
     * plus nested custom data. Each call constructs a fresh lineage so equal variants from different
     * call sites compare through the deep-equals path, exactly like recipe output vs stock demand.
     */
    private static ContentStack<Item, ItemStack> componentVariant(String name, int stage) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CompoundTag tag = new CompoundTag();
        tag.putString("variant", name);
        tag.putInt("stage", stage);
        CompoundTag nested = new CompoundTag();
        nested.putLong("seed", 1000L + stage);
        tag.put("meta", nested);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return ContentStack.fromStack(stack);
    }

    /**
     * Assigns distinct component-free registry items to plain variants. Named assignment is
     * process-wide deterministic ({@link #variantFor(String)}) so a scenario and its stock build
     * agree on which item a name maps to; anonymous {@link #next()} items draw from a local cursor.
     */
    private static final class PlainVariantPool {
        private static final java.util.Map<String, Integer> NAMED_INDEX = new java.util.concurrent.ConcurrentHashMap<>();
        private static final java.util.concurrent.atomic.AtomicInteger NAMED_CURSOR =
                new java.util.concurrent.atomic.AtomicInteger();

        static ContentStack<Item, ItemStack> variantFor(String name) {
            int index = NAMED_INDEX.computeIfAbsent(name, n -> NAMED_CURSOR.getAndIncrement());
            return ContentStack.fromStack(new ItemStack(plainItems[index % plainItems.length]));
        }

        ContentStack<Item, ItemStack> next() {
            return ContentStack.fromStack(new ItemStack(plainItems[NAMED_CURSOR.getAndIncrement() % plainItems.length]));
        }
    }

    private static final Item[] plainItems = plainItemPool();

    private static Item[] plainItemPool() {
        List<Item> items = new ArrayList<>();
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item != Items.AIR) items.add(item);
        }
        return items.toArray(new Item[0]);
    }

    private static InputContent input(ContentStack<Item, ItemStack> stack, long amount) {
        return InputContent.fromStack(stack.copyWithAmount(amount));
    }

    private static OutputContent output(ContentStack<Item, ItemStack> stack, long amount) {
        return OutputContent.fromStack(stack.copyWithAmount(amount));
    }

    public static @Nullable RecipeApiImpl attachRecipeApi(ItemRecipeSystem system) {
        RecipeApi current = RecipeApiImpl.instance();
        RecipeApiImpl previous = current instanceof RecipeApiImpl impl ? impl : null;
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        RecipeApiImpl.attach(new RecipeApiImpl(builder));
        return previous;
    }

    public static void restoreRecipeApi(@Nullable RecipeApiImpl previous) {
        if (previous != null) {
            RecipeApiImpl.attach(previous);
        }
    }

    //endregion

    //region scenario plumbing

    public record Scenario(
            String name,
            ItemRecipeSystem system,
            ImmutableList<RecipeEntry<?>> entries,
            RecipeEntry<ItemRecipe> recipeTarget,
            ContentResource targetOutput,
            RuleDefinition calculatorRule
    ) {
        public RecipeGraph buildGraph() {
            return RecipeGraph.buildGraph(entries);
        }

        public RoutingService newService(RecipeGraph graph) {
            RoutingService service = new RoutingService(graph);
            service.registerRuleDefinition(calculatorRule);
            return service;
        }

        public RoutingRequest request(int times) {
            return request(times, Stock.empty());
        }

        public RoutingRequest request(int times, Stock stock) {
            return RoutingRequest.builder()
                    .plannerMode(dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode.standard)
                    .targetRecipe(recipeTarget, Math.max(1, times))
                    .ruleProfileId(calculatorRule.profile().profileId())
                    .sourcePolicyId(calculatorRule.sourcePolicy().policyId())
                    .stock(stock)
                    .loopPolicy(dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy.allowBoundedAmplification)
                    .build();
        }
    }

    public record ItemRecipe(
            String id,
            List<InputContent> inputs,
            List<OutputContent> outputs
    ) {
    }

    public static final class ItemRecipeSystem implements RecipeSystem<ItemRecipe> {
        private final RichIterable<ItemRecipe> recipes;
        private final Map<String, ItemRecipe> byId = new ConcurrentHashMap<>();

        private ItemRecipeSystem(RichIterable<ItemRecipe> recipes) {
            this.recipes = recipes;
            recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("bench", "item_components");
        }

        @Override
        public Class<?> basicType() {
            return ItemRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(ItemRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("bench", recipe.id());
        }

        @Override
        public @Nullable ItemRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable ItemRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(ItemRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(ItemRecipe left, ItemRecipe right) {
            return Objects.equals(left.id(), right.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, ItemRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RoutingRecipeView routingView(ResolveContext context, ItemRecipe recipe) {
            return new RoutingRecipeView(recipe.inputs(), recipe.outputs());
        }

        @Override
        public RecipeRoutingHints routingHints(ItemRecipe recipe) {
            if (recipe.id().contains("_alt_")) {
                return RecipeRoutingHints.builder()
                        .lastResort()
                        .penalty(100_000)
                        .reasonKey("bench_noise")
                        .build();
            }
            return RecipeRoutingHints.empty();
        }

        @Override
        public RichIterable<ItemRecipe> allRecipes() {
            return recipes;
        }
    }

    private static final class MutableRecipeList {
        private final List<ItemRecipe> recipes = new ArrayList<>();

        private void add(String id, Collection<InputContent> inputs, Collection<OutputContent> outputs) {
            recipes.add(new ItemRecipe(id, List.copyOf(inputs), List.copyOf(outputs)));
        }

        private int size() {
            return recipes.size();
        }

        private ItemRecipe require(String id) {
            for (ItemRecipe recipe : recipes) {
                if (recipe.id().equals(id)) {
                    return recipe;
                }
            }
            throw new IllegalArgumentException("Missing benchmark recipe: " + id);
        }

        private RichIterable<ItemRecipe> toImmutable() {
            return Lists.immutable.ofAll(recipes);
        }

        private ImmutableList<RecipeEntry<?>> entries(ItemRecipeSystem system) {
            MutableList<RecipeEntry<?>> entries = Lists.mutable.empty();
            for (ItemRecipe recipe : recipes) {
                entries.add(RecipeEntry.of(system, recipe));
            }
            return entries.toImmutable();
        }
    }

    //endregion
}
