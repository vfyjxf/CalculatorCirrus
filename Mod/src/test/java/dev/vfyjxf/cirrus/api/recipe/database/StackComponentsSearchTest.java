package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.search.SearchField;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards the data-component path through database resolve: stacks that differ
 * only by components must stay distinct searchable contents (StackKey carries
 * the component patch), and their component-derived display names must be
 * searchable.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class StackComponentsSearchTest {

    @BeforeAll
    static void setupRecipeApi() {
        var builder = new RecipeApiRegistryBuilder();
        var api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
    }

    private record FixtureRecipe(String id, List<InputContent> inputs, List<OutputContent> outputs) {}

    private static final class FixtureSystem implements RecipeSystem<FixtureRecipe> {
        private final Namespace systemId = Namespace.of("cirrus_test", "components");
        private final List<FixtureRecipe> recipes;

        private FixtureSystem(List<FixtureRecipe> recipes) {
            this.recipes = recipes;
        }

        @Override
        public Namespace systemId() {
            return systemId;
        }

        @Override
        public Class<?> basicType() {
            return FixtureRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FixtureRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath(systemId.root(), recipe.id());
        }

        @Override
        public @Nullable FixtureRecipe findRecipe(String id) {
            return recipes.stream().filter(recipe -> recipe.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public @Nullable FixtureRecipe findRecipe(ResourceLocation id) {
            return findRecipe(id.getPath());
        }

        @Override
        public int hash(FixtureRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FixtureRecipe a, FixtureRecipe b) {
            return Objects.equals(a.id(), b.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext ctx, FixtureRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<FixtureRecipe> allRecipes() {
            return Lists.immutable.ofAll(recipes);
        }
    }

    @Test
    void componentBearingStacksStayDistinctAndSearchable(MinecraftServer server) {
        ItemStack plain = new ItemStack(Items.IRON_INGOT);
        ItemStack enchanted = new ItemStack(Items.IRON_INGOT);
        enchanted.set(DataComponents.CUSTOM_NAME, Component.literal("✦a1b2"));

        InputContent plainInput = InputContent.fromStack(ContentStack.fromStack(CirrusTypes.Contents.ITEM_STACK, plain, 1));
        InputContent markedInput = InputContent.fromStack(ContentStack.fromStack(CirrusTypes.Contents.ITEM_STACK, enchanted, 1));
        OutputContent output = OutputContent.fromStack(ContentStack.fromStack(CirrusTypes.Contents.ITEM_STACK, new ItemStack(Items.GOLD_INGOT), 1));

        FixtureSystem system = new FixtureSystem(List.of(
            new FixtureRecipe("plain_iron", List.of(plainInput), List.of(output)),
            new FixtureRecipe("marked_iron", List.of(markedInput), List.of(output))
        ));

        DatabaseDefinition database = new DatabaseDefinition(
            "components_db", "Components DB", DatabaseKind.system, true, 0, List.of(system.systemId()));
        RecipeDatabaseIndex index = RecipeDatabaseIndex.build(
            List.of(database), Lists.immutable.of(system), Map.of());

        Assertions.assertEquals(2, index.records().size(), "both recipes must resolve");

        var inText = index.textCandidates(CirrusSearchFields.in, 16);
        Assertions.assertTrue(inText.stream().anyMatch(value -> value.contains("✦a1b2")),
            "component-derived display name must be searchable, got: " + inText);

        List<RecipeDatabaseResult> marked = index.search("✦a1b2");
        Assertions.assertEquals(1, marked.size(), "only the component-bearing recipe matches the marker");
        Assertions.assertTrue(marked.getFirst().record().recipe().id().getPath().contains("marked"));

        List<RecipeDatabaseResult> iron = index.search("iron ingot");
        Assertions.assertEquals(2, iron.size(), "plain-name search still matches both variants");
    }
}
