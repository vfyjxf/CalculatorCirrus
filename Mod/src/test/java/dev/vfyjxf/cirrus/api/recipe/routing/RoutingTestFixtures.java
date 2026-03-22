package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class RoutingTestFixtures {

    private RoutingTestFixtures() {
    }

    static InputContent in(Item item, long amount) {
        return InputContent.fromItemStack(new ItemStack(item), amount);
    }

    static OutputContent out(Item item, long amount) {
        return OutputContent.fromItemStack(new ItemStack(item), amount);
    }

    static RecipeGraph graph(FakeRecipe... recipes) {
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(recipes));
        var entries = Lists.mutable.<RecipeEntry<?>>empty();
        for (FakeRecipe recipe : recipes) {
            entries.add(RecipeEntry.of(system, recipe));
        }
        return RecipeGraph.buildGraph(entries);
    }

    static RoutingService service(FakeRecipe... recipes) {
        return new RoutingService(graph(recipes));
    }

    static @Nullable RecipeEntry<?> findRecipeById(RecipeGraph graph, String id) {
        for (RecipeGraph.Node node : graph.nodes()) {
            if (node.recipe().id() != null && Objects.equals(node.recipe().id().getPath(), id)) {
                return node.recipe();
            }
        }
        return null;
    }

    record FakeRecipe(
        String id,
        Collection<InputContent> inputs,
        Collection<OutputContent> outputs
    ) {
    }

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final RichIterable<FakeRecipe> recipes;
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();

        private FakeRecipeSystem(RichIterable<FakeRecipe> recipes) {
            this.recipes = recipes;
            recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "routing");
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
        public RecipeProjection resolveRecipe(FakeRecipe recipe) {
            return RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs());
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return recipes;
        }
    }
}

