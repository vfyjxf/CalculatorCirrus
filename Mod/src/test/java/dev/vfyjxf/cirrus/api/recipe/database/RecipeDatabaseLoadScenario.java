package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestion;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class RecipeDatabaseLoadScenario {
    private RecipeDatabaseLoadScenario() {
    }

    static Scenario create(int systems, int recipesPerSystem, int databases) {
        int safeSystems = Math.max(1, systems);
        int safeRecipesPerSystem = Math.max(1, recipesPerSystem);
        int safeDatabases = Math.max(1, databases);

        ArrayList<DatabaseDefinition> definitions = new ArrayList<>(safeDatabases);
        ArrayList<FakeRecipeSystem> recipeSystems = new ArrayList<>(safeSystems);
        Map<Namespace, List<Namespace>> systemTags = new java.util.LinkedHashMap<>();
        Map<String, List<RecipeEntry<?>>> explicitEntries = new java.util.LinkedHashMap<>();

        for (int systemIndex = 0; systemIndex < safeSystems; systemIndex++) {
            Namespace systemId = Namespace.of("bench", "system/" + systemIndex);
            FakeRecipeSystem system = new FakeRecipeSystem(systemId, buildRecipes(systemId, safeRecipesPerSystem));
            recipeSystems.add(system);
            systemTags.put(systemId, List.of(Namespace.of("bench", "tag/" + systemIndex), Namespace.of("jei", "category/" + systemIndex)));
        }

        for (int databaseIndex = 0; databaseIndex < safeDatabases; databaseIndex++) {
            Namespace source = recipeSystems.get(databaseIndex % recipeSystems.size()).systemId();
            definitions.add(new DatabaseDefinition(
                "db-" + databaseIndex,
                "Database " + databaseIndex,
                switch (databaseIndex % 4) {
                    case 0 -> DatabaseKind.custom;
                    case 1 -> DatabaseKind.system;
                    case 2 -> DatabaseKind.profile;
                    default -> DatabaseKind.jei;
                },
                true,
                databaseIndex,
                List.of(source)
            ));
        }

        for (int databaseIndex = 0; databaseIndex < definitions.size(); databaseIndex++) {
            DatabaseDefinition definition = definitions.get(databaseIndex);
            ArrayList<RecipeEntry<?>> entries = new ArrayList<>();
            for (int systemIndex = 0; systemIndex < recipeSystems.size(); systemIndex++) {
                if ((databaseIndex + systemIndex) % 2 != 0) {
                    continue;
                }
                FakeRecipeSystem system = recipeSystems.get(systemIndex);
                for (FakeRecipe recipe : system.allRecipes()) {
                    if ((recipeIndex(recipe.id()) + databaseIndex) % 3 == 0) {
                        entries.add(RecipeEntry.of(system, recipe));
                    }
                }
            }
            explicitEntries.put(definition.id(), List.copyOf(entries));
        }

        return new Scenario(
            List.copyOf(definitions),
            Lists.immutable.ofAll(recipeSystems),
            Map.copyOf(systemTags),
            Map.copyOf(explicitEntries)
        );
    }

    static Scenario createLargeSearchScenario() {
        return create(6, 180, 9);
    }

    private static List<FakeRecipe> buildRecipes(Namespace systemId, int recipesPerSystem) {
        ArrayList<FakeRecipe> recipes = new ArrayList<>(recipesPerSystem);
        for (int index = 0; index < recipesPerSystem; index++) {
            SyntheticValueType inputType = new SyntheticValueType(systemId.path() + "/input_" + index);
            SyntheticValueType outputType = new SyntheticValueType(systemId.path() + "/output_" + index);
            SyntheticValueType catalystType = new SyntheticValueType(systemId.path() + "/catalyst_" + index);
            SyntheticValueType byproductType = new SyntheticValueType(systemId.path() + "/byproduct_" + index);
            String recipeId = systemId.path().replace('/', '_') + "_recipe_" + index;
            recipes.add(new FakeRecipe(
                recipeId,
                List.of(
                    InputContent.fromValue(inputType, index + 1L),
                    InputContent.fromValue(catalystType, 1L, InputContent.MIN_CHANCE),
                    InputContent.fromStackList(List.of(
                        ContentStack.fromStack(dev.vfyjxf.cirrus.api.CirrusTypes.Contents.ITEM_STACK, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 1))
                    ), index + 2L, 1f)
                ),
                List.of(
                    OutputContent.fromValue(outputType, index + 2L),
                    OutputContent.fromValue(byproductType, index + 1L)
                )
            ));
        }
        return List.copyOf(recipes);
    }

    private static int recipeIndex(String id) {
        int last = id.lastIndexOf('_');
        if (last < 0 || last == id.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(id.substring(last + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    record Scenario(
        List<DatabaseDefinition> databases,
        ImmutableList<FakeRecipeSystem> systems,
        Map<Namespace, List<Namespace>> systemTags,
        Map<String, List<RecipeEntry<?>>> explicitEntries
    ) {
        RecipeDatabaseIndex buildIndex() {
            RecipeApi previous = RecipeApiImpl.instance();
            attachRecipeApi();
            try {
                return RecipeDatabaseIndex.build(databases, systems, systemTags, explicitEntries);
            } finally {
                restoreRecipeApi(previous);
            }
        }

        SearchIndex searchIndex() {
            return new SearchIndex(buildIndex());
        }

        private void attachRecipeApi() {
            RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
            for (FakeRecipeSystem system : systems) {
                builder.registerSystem(system);
            }
            RecipeApiImpl.attach(new RecipeApiImpl(builder));
        }

        private static void restoreRecipeApi(@Nullable RecipeApi previous) {
            if (previous instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            } else {
                clearRecipeApi();
            }
        }

        private static void clearRecipeApi() {
            try {
                Field field = RecipeApiImpl.class.getDeclaredField("instance");
                field.setAccessible(true);
                field.set(null, null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to clear RecipeApi test instance", exception);
            }
        }
    }

    record SearchIndex(RecipeDatabaseIndex index) {
        List<RecipeDatabaseResult> search(String query) {
            return index.search(query);
        }

        List<SearchSuggestion> suggest(String query, int cursor, int limit) {
            return new dev.vfyjxf.cirrus.ui.widget.calculator.search.RecipeDatabaseSuggestionSource(index)
                .suggest(query, cursor, limit);
        }
    }

    private record FakeRecipe(
        String id,
        Collection<InputContent> inputs,
        Collection<OutputContent> outputs
    ) {
    }

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace systemId;
        private final RichIterable<FakeRecipe> recipes;
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();

        private FakeRecipeSystem(Namespace systemId, Collection<FakeRecipe> recipes) {
            this.systemId = systemId;
            this.recipes = Lists.immutable.ofAll(recipes);
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
            return ResourceLocation.fromNamespaceAndPath(systemId.root(), recipe.id());
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
        public boolean sameRecipe(FakeRecipe left, FakeRecipe right) {
            return Objects.equals(left.id(), right.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return recipes;
        }
    }

    private static final class SyntheticValueType implements ValueType<Long> {
        private final Namespace id;
        private final String name;

        private SyntheticValueType(String path) {
            this.id = Namespace.of("bench", path);
            this.name = path;
        }

        @Override
        public Namespace typeId() {
            return id;
        }

        @Override
        public long getValue(Long content) {
            return content == null ? 0L : content;
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
            return name + " x" + getValue(content);
        }
    }
}
