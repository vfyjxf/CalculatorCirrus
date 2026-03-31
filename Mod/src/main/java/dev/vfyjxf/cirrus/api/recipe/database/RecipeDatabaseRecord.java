package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.database.search.RecipeDatabaseSearchDocument;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record RecipeDatabaseRecord(
    RecipeEntry<?> recipe,
    DatabaseDefinition database,
    RecipeDatabaseSearchDocument searchDocument,
    @Nullable RecipeProjection projection,
    List<Namespace> systemTags
) {
    public RecipeDatabaseRecord {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(database, "database");
        Checks.checkNotNull(searchDocument, "searchDocument");
        Checks.checkNotNull(systemTags, "systemTags");
        systemTags = List.copyOf(systemTags);
    }
}
