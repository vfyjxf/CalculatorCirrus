package dev.vfyjxf.cirrus.api.recipe.view.facet;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.function.BiPredicate;

public record RecipeFacetType(
    Namespace id,
    BiPredicate<RecipeEntry<?>, RecipeContent> selector
) {

    public RecipeFacetType {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(selector, "selector");
    }

    public boolean select(RecipeEntry<?> recipe, RecipeContent content) {
        return selector.test(recipe, content);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        RecipeFacetType that = (RecipeFacetType) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
