package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeApiSystemTagsTest {

    @Test
    void exposesRecipeSystemTagsInBothDirections() {
        Namespace craftingTag = Namespace.of("test", "category/crafting");
        Namespace machineTag = Namespace.of("test", "category/machine");
        FakeSystem crafting = new FakeSystem(Namespace.of("test", "crafting"));
        FakeSystem furnace = new FakeSystem(Namespace.of("test", "furnace"));

        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(crafting);
        builder.registerSystem(furnace);
        builder.registerSystemTagEntry(craftingTag, crafting.systemId());
        builder.registerSystemTagEntry(machineTag, furnace.systemId());
        builder.registerSystemTagEntry(craftingTag, furnace.systemId());

        RecipeApi api = new RecipeApiImpl(builder);

        assertEquals(2, api.getTaggedSystems(craftingTag).size());
        assertTrue(tags(api, furnace.systemId()).contains(craftingTag));
        assertTrue(tags(api, furnace.systemId()).contains(machineTag));
    }

    private static List<Namespace> tags(RecipeApi api, Namespace systemId) {
        ArrayList<Namespace> tags = new ArrayList<>();
        api.getSystemTags(systemId).forEach(tags::add);
        return tags;
    }

    private record FakeRecipe(String id) {
    }

    private static final class FakeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace systemId;

        private FakeSystem(Namespace systemId) {
            this.systemId = systemId;
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
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
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
