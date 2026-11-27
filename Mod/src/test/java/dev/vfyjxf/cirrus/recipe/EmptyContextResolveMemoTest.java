package dev.vfyjxf.cirrus.recipe;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers the empty-context resolution memo inside {@link RecipeApiImpl}: cached
 * results are shared across calls, lifecycle invalidation forces recomputation,
 * non-empty contexts bypass the cache, and keys use recipe identity (weak-keyed,
 * JEI/datapack reloads swap instances) rather than {@link RecipeSystem#sameRecipe}.
 */
class EmptyContextResolveMemoTest {

    private final RecipeApi previousApi = RecipeApi.instance();

    @AfterEach
    void restorePreviousApi() {
        if (previousApi instanceof RecipeApiImpl impl) {
            RecipeApiImpl.attach(impl);
        }
    }

    @Test
    void repeatedEmptyContextResolveReturnsTheMemoizedInstance() {
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeApiImpl api = countingApi(system);
        TestRecipe recipe = new TestRecipe("target", false);

        ResolveResult first = api.resolve(ResolveContext.empty(), system, recipe);
        ResolveResult second = api.resolve(ResolveContext.empty(), system, recipe);

        assertSame(first, second);
        assertEquals(1, system.computationCount());
    }

    @Test
    void failedResultsAreMemoizedToo() {
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeApiImpl api = countingApi(system);
        // claim-rejected recipes fail inside the memoized call path itself
        // (before any fallback runs), so sharing is observed via instance reuse
        TestRecipe recipe = new TestRecipe("unclaimed", true);

        ResolveResult first = api.resolve(ResolveContext.empty(), system, recipe);
        ResolveResult second = api.resolve(ResolveContext.empty(), system, recipe);

        assertSame(first, second);
        assertEquals(ResolveError.unsupported, first.error());
    }

    @Test
    void clearEmptyContextResolveMemosForcesRecomputation() {
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeApiImpl api = countingApi(system);
        TestRecipe recipe = new TestRecipe("target", false);
        RecipeApiImpl.attach(api);

        RecipeApi live = RecipeApi.instance();
        ResolveResult first = live.resolve(ResolveContext.empty(), system, recipe);
        assertSame(first, live.resolve(ResolveContext.empty(), system, recipe));

        RecipeApiImpl.clearEmptyContextResolveMemos();

        ResolveResult second = live.resolve(ResolveContext.empty(), system, recipe);
        assertEquals(2, system.computationCount());
        assertNotSame(first, second);
    }

    @Test
    void nonEmptyContextsBypassTheMemo() {
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeApiImpl api = countingApi(system);
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
    void memoKeysUseRecipeIdentityNotEquality() {
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeApiImpl api = countingApi(system);

        TestRecipe firstInstance = new TestRecipe("twin", false);
        TestRecipe secondInstance = new TestRecipe("twin", false);
        assertEquals(firstInstance, secondInstance);

        ResolveResult first = api.resolve(ResolveContext.empty(), system, firstInstance);
        ResolveResult second = api.resolve(ResolveContext.empty(), system, secondInstance);

        assertEquals(2, system.computationCount());
        assertNotSame(first, second);
    }

    private static RecipeApiImpl countingApi(TestRecipeSystem system) {
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        return new RecipeApiImpl(builder);
    }

    private record TestRecipe(String id, boolean unclaimed) {}

    /**
     * Counts every fallback invocation — {@link RecipeApiImpl} falls back directly
     * when no subsystem is registered — returning a fresh result marker each time so
     * memoization shows up as instance reuse plus a stable computation count.
     */
    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final AtomicInteger computations = new AtomicInteger();

        int computationCount() {
            return computations.get();
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "memo");
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
