package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RecipeDatabaseCatalogCacheTest {

    @AfterEach
    void resetCache() {
        RecipeDatabaseCatalogCache.resetForTests();
    }

    @Test
    void reusesCatalogIndexWithinTtl() {
        RecipeDatabaseCatalogCache.configureTtlForTests(Duration.ofMinutes(5));
        Namespace source = Namespace.of("test", "cache");
        CountingRecipeSystem system = new CountingRecipeSystem(source, List.of(new FakeRecipe("recipe_a")));

        RecipeDatabaseIndex first = RecipeDatabaseCatalogCache.getOrBuild(List.of(system), Map.of(), RecipeDatabaseIndex.BuildProgress.none());
        RecipeDatabaseIndex second = RecipeDatabaseCatalogCache.getOrBuild(List.of(system), Map.of(), RecipeDatabaseIndex.BuildProgress.none());

        assertSame(first, second);
    }

    @Test
    void expiresCatalogIndexAfterTtl() {
        RecipeDatabaseCatalogCache.configureTtlForTests(Duration.ZERO);
        Namespace source = Namespace.of("test", "cache_expire");
        CountingRecipeSystem system = new CountingRecipeSystem(source, List.of(new FakeRecipe("recipe_a")));

        RecipeDatabaseIndex first = RecipeDatabaseCatalogCache.getOrBuild(List.of(system), Map.of(), RecipeDatabaseIndex.BuildProgress.none());
        RecipeDatabaseIndex second = RecipeDatabaseCatalogCache.getOrBuild(List.of(system), Map.of(), RecipeDatabaseIndex.BuildProgress.none());

        assertNotSame(first, second);
    }

    @Test
    void invalidateForcesRebuildWithIdenticalKey() {
        // datapack reload swaps recipe content under stable system instances,
        // which the identity-based key cannot see; the lifecycle unload path
        // invalidates explicitly, so the next lookup must rebuild
        RecipeDatabaseCatalogCache.configureTtlForTests(Duration.ofMinutes(5));
        Namespace source = Namespace.of("test", "cache_invalidate");
        CountingRecipeSystem system = new CountingRecipeSystem(source, List.of(new FakeRecipe("recipe_a")));

        RecipeDatabaseIndex first = RecipeDatabaseCatalogCache.getOrBuild(List.of(system), Map.of(), RecipeDatabaseIndex.BuildProgress.none());
        RecipeDatabaseIndex hit = RecipeDatabaseCatalogCache.getOrBuild(List.of(system), Map.of(), RecipeDatabaseIndex.BuildProgress.none());
        assertSame(first, hit);

        RecipeDatabaseCatalogCache.invalidate();
        RecipeDatabaseIndex rebuilt = RecipeDatabaseCatalogCache.getOrBuild(List.of(system), Map.of(), RecipeDatabaseIndex.BuildProgress.none());
        assertNotSame(first, rebuilt);
    }

    private record FakeRecipe(String id) {
    }

    private static final class CountingRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace systemId;
        private final RichIterable<FakeRecipe> recipes;

        private CountingRecipeSystem(Namespace systemId, Collection<FakeRecipe> recipes) {
            this.systemId = systemId;
            this.recipes = Lists.immutable.ofAll(recipes);
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
        public ResourceLocation getId(FakeRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath(systemId.root(), recipe.id());
        }

        @Override
        public FakeRecipe findRecipe(String uniqueId) {
            return recipes.detect(recipe -> Objects.equals(recipe.id(), uniqueId));
        }

        @Override
        public FakeRecipe findRecipe(ResourceLocation registryName) {
            return findRecipe(registryName.getPath());
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe first, FakeRecipe second) {
            return Objects.equals(first.id(), second.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), List.of(), List.of()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return recipes;
        }
    }
}
