package dev.vfyjxf.cirrus.recipe;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeClaim;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * {@link RecipeApiImpl#resolve} keeps no cross-call state: every request, with or without a
 * resolve context, runs the recipe system's resolution again even for the same recipe instance.
 */
class EmptyContextResolveTest {

    @Test
    void repeatedEmptyContextResolvesRecomputeEveryTime() {
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeApiImpl api = api(system);
        TestRecipe recipe = new TestRecipe("target", false);

        ResolveResult first = api.resolve(ResolveContext.empty(), system, recipe);
        ResolveResult second = api.resolve(ResolveContext.empty(), system, recipe);

        assertNotSame(first, second);
        assertEquals(2, system.computationCount());
    }

    @Test
    void nonEmptyContextsResolveIndependently() {
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeApiImpl api = api(system);
        TestRecipe recipe = new TestRecipe("target", false);
        ResolveContext context = ResolveContext.empty().with(Namespace.of("test", "hint"), "value");

        ResolveResult first = api.resolve(context, system, recipe);
        ResolveResult second = api.resolve(context, system, recipe);
        ResolveResult third = api.resolve(context, system, recipe);

        assertEquals(3, system.computationCount());
        assertNotSame(first, second);
        assertNotSame(second, third);
    }

    @Test
    void equalRecipeInstancesAreResolvedSeparately() {
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeApiImpl api = api(system);

        TestRecipe firstInstance = new TestRecipe("twin", false);
        TestRecipe secondInstance = new TestRecipe("twin", false);
        assertEquals(firstInstance, secondInstance);

        ResolveResult first = api.resolve(ResolveContext.empty(), system, firstInstance);
        ResolveResult second = api.resolve(ResolveContext.empty(), system, secondInstance);

        assertEquals(2, system.computationCount());
        assertNotSame(first, second);
    }

    private static RecipeApiImpl api(TestRecipeSystem system) {
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        return new RecipeApiImpl(builder);
    }

    private record TestRecipe(String id, boolean unclaimed) {}

    /**
     * Counts every fallback invocation — the api falls back directly when no subsystem is
     * registered — returning a fresh result marker each time so a shared instance would mean a cache.
     */
    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final AtomicInteger computations = new AtomicInteger();

        int computationCount() {
            return computations.get();
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "stateless");
        }

        @Override
        public Class<?> basicType() {
            return TestRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(TestRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable TestRecipe findRecipe(String uniqueId) {
            return new TestRecipe(uniqueId, false);
        }

        @Override
        public @Nullable TestRecipe findRecipe(ResourceLocation registryName) {
            return new TestRecipe(registryName.getPath(), false);
        }

        @Override
        public int hash(TestRecipe recipe) {
            return Objects.hashCode(recipe.id());
        }

        @Override
        public boolean sameRecipe(TestRecipe first, TestRecipe second) {
            return Objects.equals(first.id(), second.id());
        }

        @Override
        public RecipeClaim claim(TestRecipe recipe) {
            return recipe.unclaimed() ? RecipeClaim.none : RecipeClaim.normal;
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            computations.incrementAndGet();
            return ResolveResult.failed(ResolveError.missingContext);
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.empty();
        }
    }
}
