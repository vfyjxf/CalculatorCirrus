package dev.vfyjxf.cirrus.recipe.routing;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.database.CustomRecipeDatabase;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseRecipeRef;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseWorkspace;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipePathSettingsTest {

    @AfterEach
    void clearCaches() {
        RecipePathSettings.clearCachesForTests();
    }

    @Test
    void executionPathSettingsShareCalculatorSettingsByDefault() {
        assertTrue(ExecutionOverlayClientConfig.executionUsesCalculatorPathSettings());
        assertTrue(ExecutionOverlayClientConfig.executionUsesCalculatorPathSettingsDefault);
        assertEquals(PlannerMode.standard, ExecutionOverlayClientConfig.calculatorPlannerMode());
        assertEquals(PlannerMode.standard, ExecutionOverlayClientConfig.executionPlannerMode());
    }

    @Test
    void builtinWorkspaceCatalogUsesAllSystemRecipes(MinecraftServer server) {
        FakeRecipe makePlanks = recipe("make_planks", input(Items.OAK_LOG, 1), output(Items.OAK_PLANKS, 4));
        FakeRecipe makeSticks = recipe("make_sticks", input(Items.OAK_PLANKS, 2), output(Items.STICK, 4));
        FakeRecipeSystem system = new FakeRecipeSystem(Namespace.of("test", "path"), makePlanks, makeSticks);
        RecipePathSettings.Catalog catalog = RecipePathSettings.catalogForWorkspace(
            api(system),
            RecipeDatabaseWorkspace.defaults(),
            RecipeEntry.of(system, makeSticks)
        );

        assertEquals(CustomRecipeDatabase.builtinId, catalog.databaseId());
        assertEquals(2, catalog.recipes().size());
        assertTrue(containsRecipe(catalog, "make_planks"));
        assertTrue(containsRecipe(catalog, "make_sticks"));
    }

    @Test
    void customWorkspaceCatalogUsesSelectedRecipesAndKeepsTarget(MinecraftServer server) {
        Namespace systemId = Namespace.of("test", "focused_path");
        FakeRecipe makePlanks = recipe("make_planks", input(Items.OAK_LOG, 1), output(Items.OAK_PLANKS, 4));
        FakeRecipe makeSticks = recipe("make_sticks", input(Items.OAK_PLANKS, 2), output(Items.STICK, 4));
        FakeRecipe unrelated = recipe("unrelated", input(Items.DIRT, 1), output(Items.COBBLESTONE, 1));
        FakeRecipeSystem system = new FakeRecipeSystem(systemId, makePlanks, makeSticks, unrelated);
        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(
            List.of(new CustomRecipeDatabase(
                "focused",
                "Focused",
                List.of(ref(systemId, "make_planks")),
                List.of(),
                List.of()
            )),
            "focused"
        );

        RecipePathSettings.Catalog catalog = RecipePathSettings.catalogForWorkspace(
            api(system),
            workspace,
            RecipeEntry.of(system, makeSticks)
        );

        assertEquals("focused", catalog.databaseId());
        assertEquals(2, catalog.recipes().size());
        assertTrue(containsRecipe(catalog, "make_planks"));
        assertTrue(containsRecipe(catalog, "make_sticks"));
        assertFalse(containsRecipe(catalog, "unrelated"));
    }

    @Test
    void customWorkspaceCatalogUsesOnlyEnabledSources(MinecraftServer server) {
        Namespace enabledId = Namespace.of("test", "enabled_path");
        Namespace disabledId = Namespace.of("test", "disabled_path");
        FakeRecipe enabledRecipe = recipe("enabled", input(Items.DIRT, 1), output(Items.STICK, 1));
        FakeRecipe disabledRecipe = recipe("disabled", input(Items.COBBLESTONE, 1), output(Items.STONE, 1));
        FakeRecipeSystem enabledSystem = new FakeRecipeSystem(enabledId, enabledRecipe);
        FakeRecipeSystem disabledSystem = new FakeRecipeSystem(disabledId, disabledRecipe);
        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(
            List.of(new CustomRecipeDatabase(
                "sources",
                "Sources",
                List.of(),
                List.of(enabledId, disabledId),
                List.of(disabledId)
            )),
            "sources"
        );

        RecipePathSettings.Catalog catalog = RecipePathSettings.catalogForWorkspace(
            api(enabledSystem, disabledSystem),
            workspace,
            null
        );

        assertEquals(1, catalog.recipes().size());
        assertTrue(containsRecipe(catalog, "enabled"));
        assertFalse(containsRecipe(catalog, "disabled"));
    }

    @Test
    void customWorkspaceCatalogDisablesExplicitRecipesBySource(MinecraftServer server) {
        Namespace systemId = Namespace.of("test", "disabled_explicit_path");
        FakeRecipe selected = recipe("selected", input(Items.DIRT, 1), output(Items.STICK, 1));
        FakeRecipeSystem system = new FakeRecipeSystem(systemId, selected);
        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(
            List.of(new CustomRecipeDatabase(
                "focused",
                "Focused",
                List.of(ref(systemId, "selected")),
                List.of(),
                List.of(systemId)
            )),
            "focused"
        );

        RecipePathSettings.Catalog catalog = RecipePathSettings.catalogForWorkspace(api(system), workspace, null);

        assertEquals("focused", catalog.databaseId());
        assertEquals(0, catalog.recipes().size());
        assertFalse(containsRecipe(catalog, "selected"));
    }

    @Test
    void customWorkspaceCatalogFingerprintIncludesDisabledSources(MinecraftServer server) {
        Namespace systemId = Namespace.of("test", "fingerprint_path");
        FakeRecipe selected = recipe("selected", input(Items.DIRT, 1), output(Items.STICK, 1));
        FakeRecipeSystem system = new FakeRecipeSystem(systemId, selected);
        RecipeDatabaseWorkspace enabled = new RecipeDatabaseWorkspace(
            List.of(new CustomRecipeDatabase(
                "focused",
                "Focused",
                List.of(ref(systemId, "selected")),
                List.of(),
                List.of()
            )),
            "focused"
        );
        RecipeDatabaseWorkspace disabled = new RecipeDatabaseWorkspace(
            List.of(enabled.activeDatabase().withSourceEnabled(systemId, false)),
            "focused"
        );

        RecipePathSettings.Catalog enabledCatalog = RecipePathSettings.catalogForWorkspace(api(system), enabled, null);
        RecipePathSettings.Catalog disabledCatalog = RecipePathSettings.catalogForWorkspace(api(system), disabled, null);

        assertEquals(1, enabledCatalog.recipes().size());
        assertEquals(0, disabledCatalog.recipes().size());
        assertFalse(enabledCatalog.fingerprint() == disabledCatalog.fingerprint());
    }

    @Test
    void catalogFingerprintChangesForDifferentRecipeObjectsWithSameIds(MinecraftServer server) {
        Namespace systemId = Namespace.of("test", "cache_identity_path");
        FakeRecipe makePlanks = recipe("make_planks", input(Items.OAK_LOG, 1), output(Items.OAK_PLANKS, 4));
        FakeRecipe makeSticks = recipe("make_sticks", input(Items.OAK_PLANKS, 2), output(Items.STICK, 4));
        FakeRecipeSystem firstSystem = new FakeRecipeSystem(systemId, makePlanks, makeSticks);
        RecipePathSettings.Catalog firstCatalog = RecipePathSettings.catalogForWorkspace(
            api(firstSystem),
            RecipeDatabaseWorkspace.defaults(),
            RecipeEntry.of(firstSystem, makeSticks)
        );

        FakeRecipe onlyTarget = recipe("make_sticks", input(Items.OAK_LOG, 1), output(Items.STICK, 1));
        FakeRecipeSystem secondSystem = new FakeRecipeSystem(systemId, onlyTarget);
        RecipePathSettings.Catalog secondCatalog = RecipePathSettings.catalogForWorkspace(
            api(secondSystem),
            RecipeDatabaseWorkspace.defaults(),
            RecipeEntry.of(secondSystem, onlyTarget)
        );

        assertFalse(firstCatalog.fingerprint() == secondCatalog.fingerprint());
    }

    @Test
    void builtinCachedCatalogIsSharedAcrossDifferentTargets(MinecraftServer server) {
        FakeRecipe makePlanks = recipe("make_planks", input(Items.OAK_LOG, 1), output(Items.OAK_PLANKS, 4));
        FakeRecipe makeSticks = recipe("make_sticks", input(Items.OAK_PLANKS, 2), output(Items.STICK, 4));
        FakeRecipeSystem system = new FakeRecipeSystem(Namespace.of("test", "shared_cache_path"), makePlanks, makeSticks);
        RecipeApiImpl previous = attachApi(system);
        try {
            RecipePathSettings.clearCachesForTests();
            RecipePathSettings.Catalog first = RecipePathSettings.calculatorCatalog(RecipeEntry.of(system, makePlanks));
            RecipePathSettings.Catalog second = RecipePathSettings.calculatorCatalog(RecipeEntry.of(system, makeSticks));

            assertTrue(first == second);
            assertEquals(1, RecipePathSettings.cachedCatalogCountForTests());
        } finally {
            restoreApi(previous);
        }
    }

    @Test
    void calculatorRoutingGraphCacheIsSharedAcrossDifferentBuiltinTargets(MinecraftServer server) {
        FakeRecipe makePlanks = recipe("make_planks", input(Items.OAK_LOG, 1), output(Items.OAK_PLANKS, 4));
        FakeRecipe makeSticks = recipe("make_sticks", input(Items.OAK_PLANKS, 2), output(Items.STICK, 4));
        FakeRecipeSystem system = new FakeRecipeSystem(Namespace.of("test", "shared_graph_cache_path"), makePlanks, makeSticks);
        RecipeApiImpl previous = attachApi(system);
        try {
            RecipePathSettings.clearCachesForTests();
            RecipePathSettings.calculatorRoutingContext(RecipeEntry.of(system, makePlanks));
            RecipePathSettings.calculatorRoutingContext(RecipeEntry.of(system, makeSticks));

            assertEquals(1, RecipePathSettings.cachedCatalogCountForTests());
            assertEquals(1, RecipePathSettings.cachedRoutingGraphCountForTests());
        } finally {
            restoreApi(previous);
        }
    }

    private static RecipeApiImpl api(RecipeSystem<?>... systems) {
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        for (RecipeSystem<?> system : systems) {
            builder.registerSystem(system);
        }
        return new RecipeApiImpl(builder);
    }

    private static RecipeApiImpl attachApi(RecipeSystem<?>... systems) {
        RecipeApiImpl previous = RecipeApiImpl.instance() instanceof RecipeApiImpl impl ? impl : null;
        RecipeApiImpl.attach(api(systems));
        return previous;
    }

    private static void restoreApi(@Nullable RecipeApiImpl previous) {
        if (previous != null) {
            RecipeApiImpl.attach(previous);
        }
    }

    private static boolean containsRecipe(RecipePathSettings.Catalog catalog, String id) {
        return catalog.recipes().anySatisfy(recipe -> recipe.id() != null && Objects.equals(recipe.id().getPath(), id));
    }

    private static DatabaseRecipeRef ref(Namespace system, String recipe) {
        return new DatabaseRecipeRef(system, ResourceLocation.fromNamespaceAndPath("test", recipe));
    }

    private static FakeRecipe recipe(String id, InputContent input, OutputContent output) {
        return new FakeRecipe(id, List.of(input), List.of(output));
    }

    private static InputContent input(Item item, long amount) {
        return InputContent.fromItemStack(new ItemStack(item), amount);
    }

    private static OutputContent output(Item item, long amount) {
        return OutputContent.fromItemStack(new ItemStack(item), amount);
    }

    private record FakeRecipe(
        String id,
        Collection<InputContent> inputs,
        Collection<OutputContent> outputs
    ) {
    }

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace systemId;
        private final RichIterable<FakeRecipe> recipes;
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();

        private FakeRecipeSystem(Namespace systemId, FakeRecipe... recipes) {
            this.systemId = systemId;
            this.recipes = Lists.immutable.of(recipes);
            this.recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
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
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public FakeRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public FakeRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe left, FakeRecipe right) {
            return Objects.equals(left.id(), right.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return recipes;
        }
    }
}
