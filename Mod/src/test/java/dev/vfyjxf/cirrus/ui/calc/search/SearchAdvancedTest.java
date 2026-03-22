package dev.vfyjxf.cirrus.ui.calc.search;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValue;
import dev.vfyjxf.cirrus.ui.calc.database.DatabaseDefinition;
import dev.vfyjxf.cirrus.ui.calc.database.DatabaseKind;
import dev.vfyjxf.cirrus.ui.calc.target.RecipeCandidate;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchAdvancedTest {
    @Test
    void matchesWildcardAndRange() {
        DatabaseDefinition db = new DatabaseDefinition("db", "DB", DatabaseKind.system, true, 0, List.of(Namespace.of("core", "craft")));
        RecipeCandidate candidate = new RecipeCandidate(
            "core:craft", "r1", "Iron Plate", db, List.of(db), null, null,
            List.of("iron_plate"), List.of("iron_ingot")
        );

        SearchExpression expr = new SearchParser().parse("output:iron*");
        assertTrue(SearchMatcher.matches(expr, candidate));
    }

    @Test
    void matchesFuzzyAndTimeRange() {
        FakeSystem system = new FakeSystem();
        FakeRecipe recipe = new FakeRecipe("r2");
        RecipeEntry<FakeRecipe> entry = RecipeEntry.of(system, recipe);
        DatabaseDefinition db = new DatabaseDefinition("db", "DB", DatabaseKind.system, true, 0, List.of(system.systemId()));
        RecipeCandidate candidate = new RecipeCandidate(
            system.systemId().toString(), "r2", "Steel Plate", db, List.of(db),
            entry, null, List.of("steel_plate"), List.of("steel_ingot")
        );

        SearchExpression fuzzy = new SearchParser().parse("output:stee~1");
        SearchExpression time = new SearchParser().parse("time:5s..20s");

        assertTrue(SearchMatcher.matches(fuzzy, candidate));
        assertTrue(SearchMatcher.matches(time, candidate));
    }

    private record FakeRecipe(String id) {
    }

    private static final class FakeSystem implements RecipeSystem<FakeRecipe> {
        @Override
        public Namespace systemId() {
            return Namespace.of("test", "fake");
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
            return null;
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation registryName) {
            return null;
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe r1, FakeRecipe r2) {
            return r1.id().equals(r2.id());
        }

        @Override
        public RecipeProjection resolveRecipe(FakeRecipe recipe) {
            return RecipeProjection.create(
                RecipeEntry.of(this, recipe),
                List.of((InputContent) RecipeContent.empty()),
                List.of(OutputContent.fromTime(TimeValue.fromSecond(10)))
            );
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return Lists.immutable.empty();
        }
    }
}
