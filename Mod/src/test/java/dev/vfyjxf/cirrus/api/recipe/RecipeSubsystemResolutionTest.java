package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.extension.RecipeSubsystem;
import dev.vfyjxf.cirrus.api.recipe.system.extension.SubsystemOrder;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeSubsystemResolutionTest {

    @Test
    void defaultSubsystemRegistrationUsesStandardOrder() {
        ArrayList<String> events = new ArrayList<>();
        TestRecipeSystem system = new TestRecipeSystem(events);
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        builder.registerSubsystem(system.systemId(), id("late"), tracing("late", events), SubsystemOrder.late());
        builder.registerSubsystem(system.systemId(), id("standard"), tracing("standard", events));
        builder.registerSubsystem(system.systemId(), id("early"), tracing("early", events), SubsystemOrder.early());

        new RecipeApiImpl(builder).resolve(ResolveContext.empty(), system, new TestRecipe("target"));

        assertEquals(
            List.of(
                "enter early",
                "enter standard",
                "enter late",
                "fallback",
                "exit late",
                "exit standard",
                "exit early"
            ),
            events
        );
    }

    @Test
    void beforeAfterConstraintsOverridePlainOrder() {
        ArrayList<String> events = new ArrayList<>();
        TestRecipeSystem system = new TestRecipeSystem(events);
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        builder.registerSubsystem(
            system.systemId(),
            id("third"),
            tracing("third", events),
            SubsystemOrder.early().after(id("second"))
        );
        builder.registerSubsystem(
            system.systemId(),
            id("first"),
            tracing("first", events),
            SubsystemOrder.late().before(id("second"))
        );
        builder.registerSubsystem(
            system.systemId(),
            id("second"),
            tracing("second", events),
            SubsystemOrder.standard()
        );

        new RecipeApiImpl(builder).resolve(ResolveContext.empty(), system, new TestRecipe("target"));

        assertEquals(
            List.of(
                "enter first",
                "enter second",
                "enter third",
                "fallback",
                "exit third",
                "exit second",
                "exit first"
            ),
            events
        );
    }

    @Test
    void missingSubsystemDependencyFailsAtRegistryBuild() {
        TestRecipeSystem system = new TestRecipeSystem(new ArrayList<>());
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        builder.registerSubsystem(
            system.systemId(),
            id("only"),
            tracing("only", new ArrayList<>()),
            SubsystemOrder.standard().after(id("missing"))
        );

        assertThrows(IllegalArgumentException.class, () -> new RecipeApiImpl(builder));
    }

    @Test
    void subsystemDependencyCycleFailsAtRegistryBuild() {
        TestRecipeSystem system = new TestRecipeSystem(new ArrayList<>());
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        builder.registerSubsystem(
            system.systemId(),
            id("a"),
            tracing("a", new ArrayList<>()),
            SubsystemOrder.standard().after(id("b"))
        );
        builder.registerSubsystem(
            system.systemId(),
            id("b"),
            tracing("b", new ArrayList<>()),
            SubsystemOrder.standard().after(id("a"))
        );

        assertThrows(IllegalArgumentException.class, () -> new RecipeApiImpl(builder));
    }

    private static RecipeSubsystemForTest tracing(String name, List<String> events) {
        return (context, recipe, chain) -> {
            events.add("enter " + name);
            ResolveResult result = chain.next(context, recipe);
            events.add("exit " + name);
            return result;
        };
    }

    private static Namespace id(String path) {
        return Namespace.of("test", path);
    }

    private record TestRecipe(String id) {}

    @FunctionalInterface
    private interface RecipeSubsystemForTest extends RecipeSubsystem<TestRecipe> {}

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final List<String> events;

        private TestRecipeSystem(List<String> events) {
            this.events = events;
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "subsystem");
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
            return new TestRecipe(uniqueId);
        }

        @Override
        public @Nullable TestRecipe findRecipe(ResourceLocation registryName) {
            return new TestRecipe(registryName.getPath());
        }

        @Override
        public int hash(TestRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(TestRecipe first, TestRecipe second) {
            return Objects.equals(first.id(), second.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            events.add("fallback");
            return ResolveResult.unsupported();
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.empty();
        }
    }
}
