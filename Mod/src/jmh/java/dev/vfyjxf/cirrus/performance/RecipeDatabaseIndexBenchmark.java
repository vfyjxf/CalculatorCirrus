package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseDefinition;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseKind;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseCatalogCache;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario;
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
import net.minecraft.world.item.component.CustomData;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Recipe database index build path: {@code RecipeDatabaseIndex.build} over the synthetic
 * catalog, the same build over real {@link ItemStack}-backed contents (the
 * {@code getHoverName} display-name path), and {@code RecipeDatabaseCatalogCache.getOrBuild}
 * with a structurally identical but freshly instantiated system per invocation (the current
 * identity-hash key always misses, so every call rebuilds the whole catalog).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@SuppressWarnings("PMD.PublicNonFinalInstanceField")
public class RecipeDatabaseIndexBenchmark {

    @State(Scope.Benchmark)
    public static class SyntheticCatalog {
        @Param({"4096", "16384"})
        public int competingRecipes;

        private CalculatorSyntheticScenario.Scenario scenario;
        private List<DatabaseDefinition> databases;
        private RecipeApiImpl previousApi;

        @Setup(Level.Trial)
        public void setup() {
            scenario = CalculatorSyntheticScenario.create(competingRecipes);
            previousApi = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
            databases = List.of(
                new DatabaseDefinition(
                    "bench",
                    "Bench",
                    DatabaseKind.system,
                    true,
                    0,
                    List.of(scenario.system().systemId())
                )
            );
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
        }
    }

    @State(Scope.Benchmark)
    public static class ItemStackCatalog {
        @Param({"1024", "4096"})
        public int recipes;

        private StackRecipeSystem system;
        private List<DatabaseDefinition> databases;
        private RecipeApiImpl previousApi;

        @Setup(Level.Trial)
        public void setup() {
            McBenchmarkBootstrap.ensureContentApi();
            List<ContentStack<Item, ItemStack>> pool = itemPool();
            List<StackRecipe> recipeList = new ArrayList<>(recipes);
            for (int i = 0; i < recipes; i++) {
                List<InputContent> inputs = new ArrayList<>(3);
                inputs.add(stackInput(pool.get(3 * i % pool.size()), 1 + i % 3));
                inputs.add(stackInput(pool.get((3 * i + 7) % pool.size()), 1 + (i + 1) % 3));
                if ((i & 1) == 0) {
                    inputs.add(stackInput(pool.get((3 * i + 13) % pool.size()), 1));
                }
                recipeList.add(
                    new StackRecipe(
                        "item_recipe_" + i,
                        List.copyOf(inputs),
                        List.of(stackOutput(pool.get((11 * i + 5) % pool.size()), 1))
                    )
                );
            }
            system = new StackRecipeSystem(Lists.immutable.ofAll(recipeList));
            previousApi = attachRecipeApi(system);
            databases = List.of(
                new DatabaseDefinition("bench", "Bench", DatabaseKind.system, true, 0, List.of(system.systemId()))
            );
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
        }
    }

    @State(Scope.Benchmark)
    public static class RebuiltCatalog {
        @Param({"4096"})
        public int competingRecipes;

        private RichIterable<CalculatorSyntheticScenario.SyntheticRecipe> recipeSnapshot;
        private RecipeApiImpl previousApi;

        @Setup(Level.Trial)
        public void setup() {
            // snapshot the immutable recipe list once; every invocation re-assembles an
            // equivalent-but-distinct SyntheticRecipeSystem instance from it
            recipeSnapshot = CalculatorSyntheticScenario.create(competingRecipes).system().allRecipes();
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
            previousApi = null;
            RecipeDatabaseCatalogCache.invalidate();
        }
    }

    @Benchmark
    public RecipeDatabaseIndex buildIndex(SyntheticCatalog catalog) {
        return RecipeDatabaseIndex.build(catalog.databases, Lists.immutable.of(catalog.scenario.system()), Map.of());
    }

    @Benchmark
    public RecipeDatabaseIndex buildIndexWithRealItemStacks(ItemStackCatalog catalog) {
        return RecipeDatabaseIndex.build(catalog.databases, Lists.immutable.of(catalog.system), Map.of());
    }

    @Benchmark
    public RecipeDatabaseIndex catalogCacheRebuild(RebuiltCatalog catalog) throws ReflectiveOperationException {
        CalculatorSyntheticScenario.SyntheticRecipeSystem system = syntheticSystemConstructor
                .newInstance(catalog.recipeSnapshot);
        catalog.previousApi = CalculatorSyntheticScenario.attachRecipeApi(system);
        return RecipeDatabaseCatalogCache
                .getOrBuild(List.of(system), Map.of(), RecipeDatabaseIndex.BuildProgress.none());
    }

    // SyntheticRecipeSystem's constructor is private (instances normally come from
    // CalculatorSyntheticScenario.create); reflection reuses the production class so the
    // rebuild benchmark exercises the exact resolve path instead of a stand-in.
    private static final Constructor<CalculatorSyntheticScenario.SyntheticRecipeSystem> syntheticSystemConstructor = syntheticSystemConstructor();

    private static Constructor<CalculatorSyntheticScenario.SyntheticRecipeSystem> syntheticSystemConstructor() {
        try {
            Constructor<CalculatorSyntheticScenario.SyntheticRecipeSystem> constructor = CalculatorSyntheticScenario.SyntheticRecipeSystem.class
                    .getDeclaredConstructor(RichIterable.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static @Nullable RecipeApiImpl attachRecipeApi(RecipeSystem<?> system) {
        RecipeApi current = RecipeApiImpl.instance();
        RecipeApiImpl previous = current instanceof RecipeApiImpl impl ? impl : null;
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        RecipeApiImpl.attach(new RecipeApiImpl(builder));
        return previous;
    }

    //region real item stack fixtures

    /** 32-entry deterministic pool: 28 plain vanilla items plus 4 component-bearing variants. */
    private static List<ContentStack<Item, ItemStack>> itemPool() {
        // kept as a method-local array: referencing Items.* in a static field would run
        // the benchmark class' <clinit> before McBenchmarkBootstrap had a chance to boot
        // the vanilla registries (JMH instantiates the benchmark class ahead of @Setup).
        Item[] plainItems = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD,
                Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.COBBLESTONE, Items.OAK_LOG, Items.OAK_PLANKS,
                Items.STICK, Items.GLASS, Items.SAND, Items.CLAY_BALL, Items.BRICK, Items.NETHER_BRICK, Items.QUARTZ,
                Items.AMETHYST_SHARD, Items.GLOWSTONE_DUST, Items.BLAZE_ROD, Items.ENDER_PEARL, Items.SLIME_BALL,
                Items.LEATHER, Items.WHEAT, Items.CARROT, Items.POTATO, Items.SUGAR};
        List<ContentStack<Item, ItemStack>> pool = new ArrayList<>(32);
        for (Item item : plainItems) {
            pool.add(ContentStack.fromStack(new ItemStack(item)));
        }
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("Bench Named Iron"));
        pool.add(ContentStack.fromStack(namedIron));

        ItemStack dataGold = new ItemStack(Items.GOLD_INGOT);
        CompoundTag goldTag = new CompoundTag();
        goldTag.putString("bench", "variant");
        goldTag.putInt("ordinal", 1);
        dataGold.set(DataComponents.CUSTOM_DATA, CustomData.of(goldTag));
        pool.add(ContentStack.fromStack(dataGold));

        ItemStack wornSword = new ItemStack(Items.DIAMOND_SWORD);
        wornSword.set(DataComponents.DAMAGE, 7);
        pool.add(ContentStack.fromStack(wornSword));

        ItemStack namedSword = new ItemStack(Items.DIAMOND_SWORD);
        namedSword.set(DataComponents.CUSTOM_NAME, Component.literal("Bench Sword"));
        namedSword.set(DataComponents.DAMAGE, 13);
        CompoundTag swordTag = new CompoundTag();
        swordTag.putLong("seed", 42L);
        namedSword.set(DataComponents.CUSTOM_DATA, CustomData.of(swordTag));
        pool.add(ContentStack.fromStack(namedSword));
        return List.copyOf(pool);
    }

    private static InputContent stackInput(ContentStack<Item, ItemStack> stack, long amount) {
        return InputContent.fromStack(stack.copyWithAmount(amount));
    }

    private static OutputContent stackOutput(ContentStack<Item, ItemStack> stack, long amount) {
        return OutputContent.fromStack(stack.copyWithAmount(amount));
    }

    public record StackRecipe(String id, List<InputContent> inputs, List<OutputContent> outputs) {}

    public static final class StackRecipeSystem implements RecipeSystem<StackRecipe> {
        private final RichIterable<StackRecipe> recipes;
        private final Map<String, StackRecipe> byId = new ConcurrentHashMap<>();

        private StackRecipeSystem(RichIterable<StackRecipe> recipes) {
            this.recipes = recipes;
            recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("bench", "item_stacks"); // NOPMD
        }

        @Override
        public Class<?> basicType() {
            return StackRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(StackRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("bench", recipe.id()); // NOPMD
        }

        @Override
        public @Nullable StackRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable StackRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(StackRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(StackRecipe left, StackRecipe right) {
            return Objects.equals(left.id(), right.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, StackRecipe recipe) {
            return ResolveResult
                    .resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RoutingRecipeView routingView(ResolveContext context, StackRecipe recipe) {
            return new RoutingRecipeView(recipe.inputs(), recipe.outputs());
        }

        @Override
        public RecipeScreeningHints routingHints(StackRecipe recipe) {
            return RecipeScreeningHints.empty();
        }

        @Override
        public RichIterable<StackRecipe> allRecipes() {
            return recipes;
        }
    }

    //endregion
}
