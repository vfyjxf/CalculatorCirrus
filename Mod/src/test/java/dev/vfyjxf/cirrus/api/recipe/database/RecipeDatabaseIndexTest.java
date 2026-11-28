package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeDatabaseIndexTest {
    private static final TestValueType oakLog = new TestValueType("oak_log");
    private static final TestValueType oakPlanks = new TestValueType("oak_planks");
    private static final TestValueType cobblestone = new TestValueType("cobblestone");
    private static final TestValueType furnace = new TestValueType("furnace");
    private static final TestValueType stick = new TestValueType("stick");

    @BeforeAll
    static void setupRecipeApi() {
        if (RecipeApiImpl.instance() == null) {
            RecipeApiImpl.attach(new RecipeApiImpl(new RecipeApiRegistryBuilder()));
        }
    }

    @Test
    void indexesEnabledDatabasesAndDedupesByPriority() {
        Namespace source = Namespace.of("test", "crafting");
        FakeRecipe recipe = new FakeRecipe(
            "craft_oak_planks",
            List.of(input(oakLog, 1)),
            List.of(output(oakPlanks, 4))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(source, List.of(recipe));
        List<DatabaseDefinition> databases = List.of(
            database("primary", 0, true, source),
            database("disabled", 1, false, source),
            database("secondary", 2, true, source)
        );

        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(databases, Lists.immutable.of(system), Map.of());

        assertEquals(1, index.records().size());
        assertEquals("primary", index.records().getFirst().database().id());
        assertEquals(1, index.search("id:craft_oak_planks").size());
        assertEquals(1, index.search("in.id:test:oak_log out.id:test:oak_planks out.id:test:oak_planks * >=4").size());
        assertTrue(
            index.textCandidates(CirrusSearchFields.outId, 8).stream().anyMatch(value -> value.contains("oak_planks"))
        );
        var candidateMap = index.textCandidateMap(8);
        assertEquals(1, Objects.requireNonNull(candidateMap.get(CirrusSearchFields.mod), "mod candidates").size());
    }

    @Test
    void filtersSystemsByDatabaseSource() {
        Namespace included = Namespace.of("test", "included");
        Namespace excluded = Namespace.of("test", "excluded");
        FakeRecipeSystem includedSystem = new FakeRecipeSystem(
            included,
            List.of(new FakeRecipe("included_recipe", List.of(input(cobblestone, 8)), List.of(output(furnace, 1))))
        );
        FakeRecipeSystem excludedSystem = new FakeRecipeSystem(
            excluded,
            List.of(new FakeRecipe("excluded_recipe", List.of(input(oakLog, 1)), List.of(output(stick, 4))))
        );

        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(
            List.of(database("only-included", 0, true, included)),
            Lists.immutable.of(includedSystem, excludedSystem),
            Map.of()
        );

        assertEquals(1, index.records().size());
        assertEquals(
            "included_recipe",
            Objects.requireNonNull(index.records().getFirst().recipe().id(), "recipe id").getPath()
        );
        assertEquals(1, index.search("out.id:test:furnace").size());
        assertTrue(index.search("out.id:test:stick").isEmpty());
    }

    @Test
    void jeiStyleSearchRanksDirectOutputBeforeInputReference() {
        Namespace source = Namespace.of("test", "ranking");
        FakeRecipe furnace = new FakeRecipe(
            "craft_furnace",
            List.of(input(cobblestone, 8)),
            List.of(output(RecipeDatabaseIndexTest.furnace, 1))
        );
        FakeRecipe smoker = new FakeRecipe(
            "craft_smoker",
            List.of(input(RecipeDatabaseIndexTest.furnace, 1), input(oakLog, 4)),
            List.of(output(new TestValueType("smoker"), 1))
        );
        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(
            List.of(database("primary", 0, true, source)),
            Lists.immutable.of(new FakeRecipeSystem(source, List.of(smoker, furnace))),
            Map.of()
        );

        var results = index.search("furnace");

        assertEquals(1, results.size());
        assertEquals(
            "craft_furnace",
            Objects.requireNonNull(results.getFirst().record().recipe().id(), "recipe id").getPath()
        );
    }

    @Test
    void indexesSystemTagsForCategorySearch() {
        Namespace source = Namespace.of("test", "tagged");
        Namespace category = Namespace.of("test", "category/crafting");
        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(
            List.of(database("primary", 0, true, source)),
            Lists.immutable.of(
                new FakeRecipeSystem(
                    source,
                    List.of(new FakeRecipe("craft_stick", List.of(input(oakPlanks, 2)), List.of(output(stick, 4))))
                )
            ),
            Map.of(source, List.of(category))
        );

        assertEquals(1, index.records().getFirst().systemTags().size());
        assertEquals(1, index.search("system:test:category/crafting").size());
        assertEquals(1, index.search("$test:category/crafting").size());
    }

    @Test
    void mergesDuplicateContentAmountsInsideRecipeBeforeAmountVerification() {
        Namespace source = Namespace.of("test", "merge_amounts");
        FakeRecipe recipe = new FakeRecipe(
            "duplicate_iron_inputs",
            List.of(input(furnace, 1), input(furnace, 2)),
            List.of(output(stick, 1))
        );
        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(
            List.of(database("primary", 0, true, source)),
            Lists.immutable.of(new FakeRecipeSystem(source, List.of(recipe))),
            Map.of()
        );

        assertEquals(1, index.search("in.id:test:furnace * >=3").size());
        assertEquals(0, index.search("in.id:test:furnace * >=4").size());
    }

    @Test
    void contentFieldsUseNameByDefaultAndRequireIdFieldForIds() {
        Namespace source = Namespace.of("test", "content_id_mode");
        FakeRecipe recipe = new FakeRecipe(
            "content_id_recipe",
            List.of(input(new DisplayValueType("Copper Dust", "copper_dust"), 3)),
            List.of(output(new DisplayValueType("Iron Gear", "iron_gear"), 2))
        );
        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(
            List.of(database("primary", 0, true, source)),
            Lists.immutable.of(new FakeRecipeSystem(source, List.of(recipe))),
            Map.of()
        );

        assertEquals(1, index.search("out:iron gear").size());
        assertEquals(0, index.search("out:test:iron_gear").size());
        assertEquals(1, index.search("out.id:test:iron_gear * >=2").size());
        assertEquals(0, index.search("out.id:\"Iron Gear\"").size());
        assertEquals(1, index.search("in.id:test:copper_dust * >=3").size());
    }

    @Test
    void wideContentFieldsFallBackToDocumentScanWithoutLosingMatches() {
        Namespace source = Namespace.of("test", "wide_content");
        ArrayList<InputContent> inputs = new ArrayList<>();
        for (int index = 0; index < 3_000; index++) {
            inputs.add(input(new DisplayValueType("bulk_" + index, "bulk_" + index), 1));
        }
        FakeRecipe recipe = new FakeRecipe("wide_input_recipe", inputs, List.of(output(stick, 1)));

        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(
            List.of(database("primary", 0, true, source)),
            Lists.immutable.of(new FakeRecipeSystem(source, List.of(recipe))),
            Map.of()
        );

        assertEquals(1, index.search("in:bulk_2999").size());
        assertEquals(1, index.search("in.id:test:bulk_2999 * >=1").size());
        assertEquals(0, index.search("in:missing_bulk").size());
    }

    @Test
    void explicitCustomDatabaseEntriesTakePriorityOverSystemCatalogs() {
        Namespace source = Namespace.of("test", "custom_priority");
        FakeRecipe recipe = new FakeRecipe("saved_recipe", List.of(input(oakLog, 1)), List.of(output(oakPlanks, 4)));
        FakeRecipeSystem system = new FakeRecipeSystem(source, List.of(recipe));
        DatabaseDefinition custom = new DatabaseDefinition(
            "custom:saved",
            "Saved Recipes",
            DatabaseKind.custom,
            true,
            0,
            List.of()
        );
        DatabaseDefinition catalog = database("system", DatabaseKind.system, 1, true, source);

        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(
            List.of(catalog, custom),
            Lists.immutable.of(system),
            Map.of(),
            Map.of(custom.id(), List.of(RecipeEntry.of(system, recipe)))
        );

        assertEquals(1, index.records().size());
        assertEquals("custom:saved", index.records().getFirst().database().id());
        assertEquals("Saved Recipes", index.search("db:saved").getFirst().record().database().name());
    }

    @Test
    void derivesCustomDatabaseFromCatalogWithoutRebuildingSearchIndex() {
        Namespace sourceA = Namespace.of("test", "derived_a");
        Namespace sourceB = Namespace.of("test", "derived_b");
        FakeRecipe recipeA = new FakeRecipe(
            "derived_a_recipe",
            List.of(input(oakLog, 1)),
            List.of(output(oakPlanks, 4))
        );
        FakeRecipe recipeB = new FakeRecipe(
            "derived_b_recipe",
            List.of(input(cobblestone, 8)),
            List.of(output(furnace, 1))
        );
        RecipeDatabaseIndex catalog = RecipeDatabaseIndex.build(
            List.of(database("system", DatabaseKind.system, 0, true, sourceA, sourceB)),
            Lists.immutable.of(
                new FakeRecipeSystem(sourceA, List.of(recipeA)),
                new FakeRecipeSystem(sourceB, List.of(recipeB))
            ),
            Map.of()
        );
        DatabaseDefinition saved = new DatabaseDefinition(
            "custom:saved",
            "Saved Recipes",
            DatabaseKind.custom,
            true,
            0,
            List.of(sourceA)
        );

        RecipeDatabaseIndex derived = RecipeDatabaseIndex.derive(catalog, saved, List.of(sourceA), List.of());

        assertEquals(1, derived.records().size());
        assertEquals("custom:saved", derived.records().getFirst().database().id());
        assertEquals("Saved Recipes", derived.search("db:saved").getFirst().record().database().name());
        assertEquals(1, derived.search("out.id:test:oak_planks db:saved").size());
        assertTrue(derived.search("out.id:test:furnace").isEmpty());
    }

    @Test
    void derivedCustomDatabaseCanIncludeExplicitRecipeRefsOutsideEnabledSources() {
        Namespace sourceA = Namespace.of("test", "derived_explicit_a");
        Namespace sourceB = Namespace.of("test", "derived_explicit_b");
        FakeRecipe recipeA = new FakeRecipe(
            "enabled_source_recipe",
            List.of(input(oakLog, 1)),
            List.of(output(oakPlanks, 4))
        );
        FakeRecipe recipeB = new FakeRecipe(
            "explicit_recipe",
            List.of(input(cobblestone, 8)),
            List.of(output(furnace, 1))
        );
        FakeRecipeSystem systemA = new FakeRecipeSystem(sourceA, List.of(recipeA));
        FakeRecipeSystem systemB = new FakeRecipeSystem(sourceB, List.of(recipeB));
        RecipeDatabaseIndex catalog = RecipeDatabaseIndex.build(
            List.of(database("system", DatabaseKind.system, 0, true, sourceA, sourceB)),
            Lists.immutable.of(systemA, systemB),
            Map.of()
        );
        DatabaseRecipeRef explicitRef = DatabaseRecipeRef.fromEntry(RecipeEntry.of(systemB, recipeB));
        assertNotNull(explicitRef);
        DatabaseDefinition saved = new DatabaseDefinition(
            "custom:saved",
            "Saved Recipes",
            DatabaseKind.custom,
            true,
            0,
            List.of(sourceA)
        );

        RecipeDatabaseIndex derived = RecipeDatabaseIndex
                .derive(catalog, saved, List.of(sourceA), List.of(explicitRef));

        assertEquals(2, derived.records().size());
        assertEquals(1, derived.search("out.id:test:furnace db:saved").size());
    }

    @Test
    void recordsSnapshotResolvedProjectionWhenSystemReusesInstances() {
        Namespace source = Namespace.of("test", "cached_projection");
        FakeRecipe recipe = new FakeRecipe("cached_recipe", List.of(input(oakLog, 1)), List.of(output(oakPlanks, 4)));
        FakeRecipeSystem system = new FakeRecipeSystem(source, List.of(recipe), true);

        RecipeDatabaseIndex index = RecipeDatabaseIndex
                .build(List.of(database("primary", 0, true, source)), Lists.immutable.of(system), Map.of());

        RecipeProjection cached = system.cachedProjection(recipe);
        RecipeProjection stored = index.records().getFirst().projection();

        assertNotNull(stored);
        assertNotSame(cached, stored);
        assertNotSame(cached.inputs().getFirst(), stored.inputs().getFirst());
        assertNotSame(cached.inputs().getFirst().content(), stored.inputs().getFirst().content());
    }

    @Test
    void recordsFailedRecipesInMemoryAndSkipsThemOnNextBuild() {
        Namespace source = Namespace.of("test", "persistent_failures");
        FakeRecipe broken = new FakeRecipe("broken_recipe", List.of(input(oakLog, 1)), List.of(output(oakPlanks, 4)));
        FakeRecipe working = new FakeRecipe(
            "working_recipe",
            List.of(input(cobblestone, 8)),
            List.of(output(furnace, 1))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(source, List.of(broken, working), false, Set.of(broken.id()));
        RecipeDatabaseFailureRegistry failureRegistry = new RecipeDatabaseFailureRegistry();
        DatabaseDefinition database = database("primary", 0, true, source);

        RecipeDatabaseIndex first = RecipeDatabaseIndex.build(
            List.of(database),
            Lists.immutable.of(system),
            Map.of(),
            Map.of(),
            RecipeDatabaseIndex.BuildProgress.none(),
            failureRegistry
        );

        DatabaseRecipeRef brokenRef = DatabaseRecipeRef.fromEntry(RecipeEntry.of(system, broken));
        assertNotNull(brokenRef);
        assertEquals(1, first.records().size());
        assertEquals(
            "working_recipe",
            Objects.requireNonNull(first.records().getFirst().recipe().id(), "recipe id").getPath()
        );
        assertEquals(1, system.resolveAttempts(broken));
        assertTrue(failureRegistry.failedRecipeRefs().contains(brokenRef));

        RecipeDatabaseIndex second = RecipeDatabaseIndex.build(
            List.of(database),
            Lists.immutable.of(system),
            Map.of(),
            Map.of(),
            RecipeDatabaseIndex.BuildProgress.none(),
            failureRegistry
        );

        assertEquals(1, second.records().size());
        assertEquals(
            "working_recipe",
            Objects.requireNonNull(second.records().getFirst().recipe().id(), "recipe id").getPath()
        );
        // broken recipes stay skipped on rebuild via the failure registry; working
        // recipes are resolved again because resolve keeps no cross-build state
        assertEquals(1, system.resolveAttempts(broken));
        assertEquals(2, system.resolveAttempts(working));
    }

    @Test
    void selectionStateNormalizesOrderAndEnabledView() {
        Namespace source = Namespace.of("test", "selection");
        DatabaseSelectionState state = new DatabaseSelectionState(
            List.of(
                database("jei", DatabaseKind.jei, 5, true, source),
                new DatabaseDefinition("system", "System", DatabaseKind.system, false, 5, List.of(source)),
                database("custom", 0, true, source)
            )
        );

        assertEquals(List.of("custom", "system", "jei"), state.ordered().stream().map(DatabaseDefinition::id).toList());
        assertEquals(List.of("custom", "jei"), state.enabledOrdered().stream().map(DatabaseDefinition::id).toList());

        state.moveDown("custom");
        state.toggleEnabled("system");

        assertEquals(List.of("system", "custom", "jei"), state.ordered().stream().map(DatabaseDefinition::id).toList());
        assertEquals(
            List.of("system", "custom", "jei"),
            state.enabledOrdered().stream().map(DatabaseDefinition::id).toList()
        );
        assertEquals(List.of(0, 1, 2), state.ordered().stream().map(DatabaseDefinition::priority).toList());
    }

    private static DatabaseDefinition database(String id, int priority, boolean enabled, Namespace source) {
        return new DatabaseDefinition(id, id, DatabaseKind.custom, enabled, priority, List.of(source));
    }

    private static DatabaseDefinition database(
        String id,
        DatabaseKind kind,
        int priority,
        boolean enabled,
        Namespace... sources
    ) {
        return new DatabaseDefinition(id, id, kind, enabled, priority, List.of(sources));
    }

    private static InputContent input(TestValueType type, long amount) {
        return InputContent.fromValue(type, amount);
    }

    private static OutputContent output(TestValueType type, long amount) {
        return OutputContent.fromValue(type, amount);
    }

    private static InputContent input(DisplayValueType type, long amount) {
        return InputContent.fromValue(type, amount);
    }

    private static OutputContent output(DisplayValueType type, long amount) {
        return OutputContent.fromValue(type, amount);
    }

    private record FakeRecipe(String id, Collection<InputContent> inputs, Collection<OutputContent> outputs) {}

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace systemId;
        private final RichIterable<FakeRecipe> recipes;
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();
        private final Map<String, RecipeProjection> projectionCache = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> resolveAttempts = new ConcurrentHashMap<>();
        private final Set<String> failingRecipes;
        private final boolean cacheProjections;

        private FakeRecipeSystem(Namespace systemId, Collection<FakeRecipe> recipes) {
            this(systemId, recipes, false);
        }

        private FakeRecipeSystem(Namespace systemId, Collection<FakeRecipe> recipes, boolean cacheProjections) {
            this(systemId, recipes, cacheProjections, Set.of());
        }

        private FakeRecipeSystem(
            Namespace systemId,
            Collection<FakeRecipe> recipes,
            boolean cacheProjections,
            Set<String> failingRecipes
        ) {
            this.systemId = systemId;
            this.recipes = Lists.immutable.ofAll(recipes);
            this.cacheProjections = cacheProjections;
            this.failingRecipes = Set.copyOf(failingRecipes);
            recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
        }

        @Override
        public Namespace systemId() {
            return systemId;
        }

        @Override
        public Class<?> basicType() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FakeRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable FakeRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe r1, FakeRecipe r2) {
            return Objects.equals(r1.id(), r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            resolveAttempts.computeIfAbsent(recipe.id(), ignored -> new AtomicInteger()).incrementAndGet();
            if (failingRecipes.contains(recipe.id())) {
                UnsupportedOperationException exception = new UnsupportedOperationException(
                    "Broken test recipe " + recipe.id()
                );
                RecipeDatabaseFailureCapture.record(this, recipe, exception);
                return ResolveResult.unsupported();
            }
            if (cacheProjections) {
                return ResolveResult.resolved(cachedProjection(recipe));
            }
            return ResolveResult
                    .resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        private int resolveAttempts(FakeRecipe recipe) {
            AtomicInteger attempts = resolveAttempts.get(recipe.id());
            return attempts == null ? 0 : attempts.get();
        }

        private RecipeProjection cachedProjection(FakeRecipe recipe) {
            return projectionCache.computeIfAbsent(
                recipe.id(),
                ignored -> RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs())
            );
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return recipes;
        }
    }

    private static final class TestValueType implements ValueType<Long> {
        private final Namespace id;

        private TestValueType(String path) {
            this.id = Namespace.of("test", path);
        }

        @Override
        public Namespace typeId() {
            return id;
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long first, Long second) {
            return Objects.equals(first, second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    }

    private static final class DisplayValueType implements ValueType<Long> {
        private final String display;
        private final Namespace id;

        private DisplayValueType(String display, String path) {
            this.display = display;
            this.id = Namespace.of("test", path);
        }

        @Override
        public Namespace typeId() {
            return id;
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long first, Long second) {
            return Objects.equals(first, second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }

        @Override
        public String toString(Long content) {
            return display;
        }
    }
}
