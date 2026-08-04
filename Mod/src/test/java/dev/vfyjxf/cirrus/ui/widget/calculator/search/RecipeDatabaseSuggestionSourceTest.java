package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseDefinition;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseKind;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseRecord;
import dev.vfyjxf.cirrus.api.recipe.database.search.RecipeDatabaseSearchDocument;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.search.SearchDocument;
import dev.vfyjxf.cirrus.api.recipe.search.SearchField;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeDatabaseSuggestionSourceTest {

    @Test
    void suggestsFieldValuesFromIndexedRecipeDocuments() {
        RecipeDatabaseSuggestionSource source = new RecipeDatabaseSuggestionSource(indexWithDocument());

        List<SearchSuggestion> modSuggestions = source.suggest("@", 1, 8);
        List<SearchSuggestion> outputSuggestions = source.suggest("out:smok", 8, 8);
        List<SearchSuggestion> outputIdSuggestions = source.suggest("out.id:smok", 11, 8);
        List<SearchSuggestion> catalystSuggestions = source.suggest("cat:buck", 8, 8);
        List<SearchSuggestion> containedSuggestions = source.suggest("has:fur", 7, 8);
        List<SearchSuggestion> generalSuggestions = source.suggest("furn", 4, 8);

        assertTrue(modSuggestions.stream().anyMatch(suggestion -> "@minecraft".equals(suggestion.replacement())));
        assertTrue(outputSuggestions.stream().anyMatch(suggestion -> "out:minecraft:smoker".equals(suggestion.replacement())));
        assertTrue(outputIdSuggestions.stream().anyMatch(suggestion -> "out.id:minecraft:smoker".equals(suggestion.replacement())));
        assertTrue(catalystSuggestions.stream().anyMatch(suggestion -> "cat:minecraft:bucket".equals(suggestion.replacement())));
        assertTrue(containedSuggestions.stream().anyMatch(suggestion -> "has:minecraft:furnace".equals(suggestion.replacement())));
        assertTrue(generalSuggestions.stream().anyMatch(suggestion -> "minecraft:furnace".equals(suggestion.replacement())));
    }

    @Test
    void valueSuggestionsPreserveLeadingNegation() {
        RecipeDatabaseSuggestionSource source = new RecipeDatabaseSuggestionSource(indexWithDocument());

        List<SearchSuggestion> prefixed = source.suggest("-@", 2, 8);
        List<SearchSuggestion> general = source.suggest("-furn", 5, 8);

        assertTrue(prefixed.stream().anyMatch(suggestion -> "-@minecraft".equals(suggestion.replacement())));
        assertTrue(general.stream().anyMatch(suggestion -> "-minecraft:furnace".equals(suggestion.replacement())));
    }

    @Test
    void contentContainsSuggestionsDoNotTreatNumbersAsTextValues() {
        RecipeDatabaseSuggestionSource source = new RecipeDatabaseSuggestionSource(indexWithNumericContent());

        List<SearchSuggestion> contentSuggestions = source.suggest("has:", 4, 16);
        List<SearchSuggestion> amountSuggestions = source.suggest("has:*", 5, 16);
        List<SearchSuggestion> generalNumberSuggestions = source.suggest("64", 2, 16);
        String modifierQuery = "has:minecraft:furnace *";
        List<SearchSuggestion> modifierSuggestions = source.suggest(modifierQuery, modifierQuery.length(), 16);
        String defaultModifierQuery = "minecraft:furnace *";
        List<SearchSuggestion> defaultModifierSuggestions = source.suggest(defaultModifierQuery, defaultModifierQuery.length(), 16);

        assertTrue(contentSuggestions.stream().anyMatch(suggestion -> "has:minecraft:furnace".equals(suggestion.replacement())));
        assertFalse(contentSuggestions.stream().anyMatch(suggestion -> "has:64".equals(suggestion.replacement())));
        assertFalse(amountSuggestions.stream().anyMatch(suggestion -> suggestion.replacement().matches("has:\\*\\s+\\d+")));
        assertFalse(generalNumberSuggestions.stream().anyMatch(suggestion -> "64".equals(suggestion.replacement())));
        assertTrue(modifierSuggestions.stream().anyMatch(suggestion -> "* 64".equals(suggestion.replacement())));
        assertTrue(defaultModifierSuggestions.stream().anyMatch(suggestion -> "* 64".equals(suggestion.replacement())));
    }

    @Test
    void laterValueSuggestionsComeFromEarlierFilteredResults() {
        RecipeDatabaseSuggestionSource source = new RecipeDatabaseSuggestionSource(indexWithMultipleDocuments());

        List<SearchSuggestion> outputSuggestions = source.suggest("mod:minecraft in.id:minecraft:furnace out:", 41, 16);

        assertTrue(outputSuggestions.stream().anyMatch(suggestion -> "out:minecraft:smoker".equals(suggestion.replacement())));
        assertFalse(outputSuggestions.stream().anyMatch(suggestion -> "out:minecraft:crafting_table".equals(suggestion.replacement())));
        assertFalse(outputSuggestions.stream().anyMatch(suggestion -> "out:create:mechanical_press".equals(suggestion.replacement())));
    }

    @Test
    void prefixedSuggestionsComeFromEarlierFilteredResults() {
        RecipeDatabaseSuggestionSource source = new RecipeDatabaseSuggestionSource(indexWithMultipleDocuments());

        List<SearchSuggestion> modSuggestions = source.suggest("out.id:minecraft:smoker @", 25, 16);

        assertTrue(modSuggestions.stream().anyMatch(suggestion -> "@minecraft".equals(suggestion.replacement())));
        assertFalse(modSuggestions.stream().anyMatch(suggestion -> "@create".equals(suggestion.replacement())));
    }

    private static RecipeDatabaseIndex indexWithDocument() {
        FakeRecipe recipe = new FakeRecipe("smoker");
        FakeRecipeSystem system = new FakeRecipeSystem();
        SearchDocument document = SearchDocument.builder()
            .text(SearchField.mod, "minecraft")
            .searchableText(SearchField.id, "minecraft:smoker")
            .searchableText(SearchField.name, "smoker")
            .searchableText(SearchField.in, "minecraft:furnace")
            .searchableText(SearchField.in_id, "minecraft:furnace")
            .searchableText(SearchField.out, "minecraft:smoker")
            .searchableText(SearchField.out_id, "minecraft:smoker")
            .searchableText(SearchField.cat, "minecraft:bucket")
            .searchableText(SearchField.cat_id, "minecraft:bucket")
            .build();
        RecipeDatabaseRecord record = new RecipeDatabaseRecord(
            RecipeEntry.of(system, recipe),
            new DatabaseDefinition("main", "Main", DatabaseKind.system, true, 0, List.of(system.systemId())),
            RecipeDatabaseSearchDocument.fromExisting(document),
            null,
            List.of()
        );
        return RecipeDatabaseIndex.ofRecords(List.of(record));
    }

    private static RecipeDatabaseIndex indexWithNumericContent() {
        FakeRecipeSystem system = new FakeRecipeSystem();
        DatabaseDefinition database = new DatabaseDefinition("main", "Main", DatabaseKind.system, true, 0, List.of(system.systemId()));
        return RecipeDatabaseIndex.ofRecords(List.of(record(system, database, "smoker", SearchDocument.builder()
            .text(SearchField.mod, "minecraft")
            .searchableContent(SearchField.in, "minecraft:furnace", 64)
            .searchableContent(SearchField.in_id, "minecraft:furnace", 64)
            .searchableText(SearchField.in, "64")
            .searchableContent(SearchField.out, "minecraft:smoker", 1)
            .searchableContent(SearchField.out_id, "minecraft:smoker", 1)
            .build()
        )));
    }

    private static RecipeDatabaseIndex indexWithMultipleDocuments() {
        FakeRecipeSystem system = new FakeRecipeSystem();
        DatabaseDefinition database = new DatabaseDefinition("main", "Main", DatabaseKind.system, true, 0, List.of(system.systemId()));
        return RecipeDatabaseIndex.ofRecords(List.of(
            record(system, database, "smoker", SearchDocument.builder()
                .text(SearchField.mod, "minecraft")
                .searchableText(SearchField.id, "minecraft:smoker")
                .searchableText(SearchField.name, "smoker")
                .searchableText(SearchField.in, "minecraft:furnace")
                .searchableText(SearchField.in_id, "minecraft:furnace")
                .searchableText(SearchField.out, "minecraft:smoker")
                .searchableText(SearchField.out_id, "minecraft:smoker")
                .build()
            ),
            record(system, database, "crafting_table", SearchDocument.builder()
                .text(SearchField.mod, "minecraft")
                .searchableText(SearchField.id, "minecraft:crafting_table")
                .searchableText(SearchField.name, "crafting_table")
                .searchableText(SearchField.in, "minecraft:planks")
                .searchableText(SearchField.in_id, "minecraft:planks")
                .searchableText(SearchField.out, "minecraft:crafting_table")
                .searchableText(SearchField.out_id, "minecraft:crafting_table")
                .build()
            ),
            record(system, database, "mechanical_press", SearchDocument.builder()
                .text(SearchField.mod, "create")
                .searchableText(SearchField.id, "create:mechanical_press")
                .searchableText(SearchField.name, "mechanical_press")
                .searchableText(SearchField.in, "minecraft:andesite_alloy")
                .searchableText(SearchField.in_id, "minecraft:andesite_alloy")
                .searchableText(SearchField.out, "create:mechanical_press")
                .searchableText(SearchField.out_id, "create:mechanical_press")
                .build()
            )
        ));
    }

    private static RecipeDatabaseRecord record(
        FakeRecipeSystem system,
        DatabaseDefinition database,
        String id,
        SearchDocument document
    ) {
        return new RecipeDatabaseRecord(
            RecipeEntry.of(system, new FakeRecipe(id)),
            database,
            RecipeDatabaseSearchDocument.fromExisting(document),
            null,
            List.of()
        );
    }

    private record FakeRecipe(String id) {
    }

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private static final Namespace id = Namespace.of("test", "recipes");

        @Override
        public Namespace systemId() {
            return id;
        }

        @Override
        public Class<?> basicType() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FakeRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", recipe.id());
        }

        @Override
        public @Nullable FakeRecipe findRecipe(String uniqueId) {
            return new FakeRecipe(uniqueId);
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation registryName) {
            return new FakeRecipe(registryName.getPath());
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
            return ResolveResult.unsupported();
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return Lists.immutable.empty();
        }
    }
}
