package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchHighlightKind;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchHighlightSegment;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestion;
import dev.vfyjxf.cirrus.ui.workbench.database.DatabaseCategorySuggestionSource;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseCategoryTreeTest {

    private static final TestValueType ingot = new TestValueType("ingot");
    private static final TestValueType plate = new TestValueType("plate");
    private static @Nullable RecipeApi previousRecipeApi;

    @BeforeAll
    static void setupRecipeApi() {
        previousRecipeApi = RecipeApiImpl.instance();
        if (previousRecipeApi == null) {
            RecipeApiImpl.attach(new RecipeApiImpl(new RecipeApiRegistryBuilder()));
        }
    }

    @AfterAll
    static void restoreRecipeApi() {
        if (previousRecipeApi instanceof RecipeApiImpl impl) {
            RecipeApiImpl.attach(impl);
        }
    }

    @Test
    void folderTagCreatesNestedHierarchyAndAutomaticFallbackSeparatesMinecraftJeiNativeAndDebug() {
        Namespace mcCraft = Namespace.of("minecraft", "crafting");
        Namespace jeiMcCraft = Namespace.of("jei", "category/minecraft/crafting");
        Namespace jeiCreateMix = Namespace.of("jei", "category/create/mixing");
        Namespace nativeMek = Namespace.of("mekanism", "crushing");
        Namespace customSys = Namespace.of("mymod", "alloy_press");
        Namespace debugSys = Namespace.of("calculator_cirrus", "debug_projection");

        RecipeDatabaseIndex index = buildIndex(
            List.of(
                new FakeSystem(mcCraft, List.of(recipe("minecraft:stick"))),
                new FakeSystem(jeiMcCraft, List.of(recipe("minecraft:stick"))),
                new FakeSystem(jeiCreateMix, List.of(recipe("create:brass_ingot"), recipe("create:rose_quartz"))),
                new FakeSystem(nativeMek, List.of(recipe("mekanism:iron_dust"))),
                new FakeSystem(customSys, List.of(recipe("mymod:steel_plate"))),
                new FakeSystem(debugSys, List.of(recipe("calculator_cirrus:probe")))
            ),
            Map.of(
                mcCraft, List.of(RecipeSystemTags.vanilla, RecipeSystemTags.crafting),
                jeiMcCraft, List.of(RecipeSystemTags.jeiFallback, RecipeSystemTags.mirrorDuplicate),
                jeiCreateMix, List.of(RecipeSystemTags.jeiFallback),
                nativeMek, List.of(RecipeSystemTags.modNative, RecipeSystemTags.oreProcessing),
                customSys, List.of(RecipeSystemTags.folder("custom_tech", "presses"), RecipeSystemTags.assembly),
                debugSys, List.of(RecipeSystemTags.debug)
            )
        );

        List<DatabaseCategoryTree.CategoryNode> tree = DatabaseCategoryTree.buildSystemTree(index.records());
        List<String> rootKeys = tree.stream().map(DatabaseCategoryTree.CategoryNode::key).toList();
        assertEquals(
            List.of("dir:minecraft", "dir:native", "dir:custom_tech", "dir:jei", "dir:debug"),
            rootKeys
        );

        DatabaseCategoryTree.CategoryNode mcFolder = findNode(tree, "dir:minecraft");
        assertNotNull(mcFolder);
        assertTrue(mcFolder.defaultExpanded());
        assertEquals(1, mcFolder.count());
        assertEquals("Crafting", mcFolder.children().getFirst().label());

        DatabaseCategoryTree.CategoryNode jeiFolder = findNode(tree, "dir:jei");
        assertNotNull(jeiFolder);
        assertFalse(jeiFolder.defaultExpanded());
        assertEquals(3, jeiFolder.count());
        assertNotNull(findNode(jeiFolder.children(), "dir:jei/minecraft"));
        assertNotNull(findNode(jeiFolder.children(), "dir:jei/create"));

        DatabaseCategoryTree.CategoryNode customFolder = findNode(tree, "dir:custom_tech");
        assertNotNull(customFolder);
        DatabaseCategoryTree.CategoryNode nestedPresses = findNode(customFolder.children(), "dir:custom_tech/presses");
        assertNotNull(nestedPresses);
        assertEquals("Alloy Press", nestedPresses.children().getFirst().label());
        assertEquals("Custom Tech / Presses / Alloy Press", DatabaseCategoryTree.findBreadcrumb(tree, "sys:" + customSys));
    }

    @Test
    void folderFilterMatchesEntireSubtreeAndFlattenSupportsCollapseAndSearchExpansion() {
        Namespace mcCraft = Namespace.of("minecraft", "crafting");
        Namespace jeiMcCraft = Namespace.of("jei", "category/minecraft/crafting");
        Namespace jeiCreateMix = Namespace.of("jei", "category/create/mixing");

        RecipeDatabaseIndex index = buildIndex(
            List.of(
                new FakeSystem(mcCraft, List.of(recipe("minecraft:stick"))),
                new FakeSystem(jeiMcCraft, List.of(recipe("minecraft:stick"))),
                new FakeSystem(jeiCreateMix, List.of(recipe("create:brass_ingot")))
            ),
            Map.of(
                mcCraft, List.of(RecipeSystemTags.vanilla),
                jeiMcCraft, List.of(RecipeSystemTags.jeiFallback),
                jeiCreateMix, List.of(RecipeSystemTags.jeiFallback)
            )
        );

        assertEquals(1, countMatches(index, "dir:minecraft"));
        assertEquals(2, countMatches(index, "dir:jei"));
        assertEquals(1, countMatches(index, "dir:jei/create"));
        assertEquals(1, countMatches(index, "sys:" + jeiCreateMix));

        List<DatabaseCategoryTree.CategoryNode> tree = DatabaseCategoryTree.buildSystemTree(index.records());

        // By default: Minecraft expanded (dir:minecraft + sys:minecraft:crafting), JEI collapsed (only dir:jei)
        List<String> defaultRows = DatabaseCategoryTree.flatten(tree, Set.of(), Set.of(), null)
                .stream().map(r -> r.node().key()).toList();
        assertEquals(List.of("dir:minecraft", "sys:minecraft:crafting", "dir:jei"), defaultRows);

        // Expanding JEI reveals its mod subfolders
        List<String> expandedJei = DatabaseCategoryTree.flatten(tree, Set.of("dir:jei"), Set.of(), null)
                .stream().map(r -> r.node().key()).toList();
        assertTrue(expandedJei.contains("dir:jei/create"));
        assertTrue(expandedJei.contains("dir:jei/minecraft"));

        // Searching "mixing" auto-expands JEI -> Create -> Mixing even while JEI is collapsed
        List<String> searchRows = DatabaseCategoryTree.flatten(tree, Set.of(), Set.of("dir:jei"), "mixing")
                .stream().map(r -> r.node().key()).toList();
        assertEquals(List.of("dir:jei", "dir:jei/create", "sys:" + jeiCreateMix), searchRows);
    }

    @Test
    void technicalTagTreeGroupsAxesAndExcludesInternalFolderTags() {
        Namespace mcCraft = Namespace.of("minecraft", "crafting");
        Namespace jeiFuel = Namespace.of("jei", "category/minecraft/fuel");

        RecipeDatabaseIndex index = buildIndex(
            List.of(
                new FakeSystem(mcCraft, List.of(recipe("minecraft:stick"))),
                new FakeSystem(jeiFuel, List.of(recipe("minecraft:coal")))
            ),
            Map.of(
                mcCraft, List.of(
                    RecipeSystemTags.vanilla,
                    RecipeSystemTags.crafting,
                    RecipeSystemTags.handOperable,
                    RecipeSystemTags.folderMinecraft
                ),
                jeiFuel, List.of(
                    RecipeSystemTags.jeiFallback,
                    RecipeSystemTags.displayOnly,
                    RecipeSystemTags.folder("jei", "minecraft")
                )
            )
        );

        List<DatabaseCategoryTree.CategoryNode> tagTree = DatabaseCategoryTree.buildTagTree(index.records());
        assertNotNull(findNode(tagTree, "tagaxis:provenance"));
        assertNotNull(findNode(tagTree, "tagaxis:process"));
        assertNotNull(findNode(tagTree, "tagaxis:trait"));

        List<DatabaseCategoryTree.FlatRow> allTagRows = DatabaseCategoryTree.flatten(
            tagTree,
            Set.of("tagaxis:provenance", "tagaxis:process", "tagaxis:trait", "tagaxis:custom"),
            Set.of(),
            null
        );
        List<String> keys = allTagRows.stream().map(r -> r.node().key()).toList();
        assertTrue(keys.contains("tag:" + RecipeSystemTags.vanilla));
        assertTrue(keys.contains("tag:" + RecipeSystemTags.jeiFallback));
        assertTrue(keys.contains("tag:" + RecipeSystemTags.handOperable));
        assertFalse(keys.stream().anyMatch(k -> k.contains(RecipeSystemTags.databaseFolderPrefix)));

        assertEquals(1, countMatches(index, "tag:" + RecipeSystemTags.vanilla));
        assertEquals(1, countMatches(index, "tag:" + RecipeSystemTags.displayOnly));
    }

    @Test
    void sidebarSearchMatchesMachineNamesAndProvidesRankedAutocompleteSuggestions() {
        Namespace mcCraft = Namespace.of("minecraft", "crafting");
        Namespace mcSmelt = Namespace.of("minecraft", "smelting");
        Namespace jeiCreateMix = Namespace.of("jei", "category/create/mixing");

        RecipeDatabaseIndex index = buildIndex(
            List.of(
                new FakeSystem(mcCraft, List.of(recipe("minecraft:stick"), recipe("minecraft:chest"))),
                new FakeSystem(mcSmelt, List.of(recipe("minecraft:iron_ingot"))),
                new FakeSystem(jeiCreateMix, List.of(recipe("create:brass_ingot")))
            ),
            Map.of(
                mcCraft, List.of(RecipeSystemTags.vanilla),
                mcSmelt, List.of(RecipeSystemTags.vanilla),
                jeiCreateMix, List.of(RecipeSystemTags.jeiFallback)
            )
        );

        List<DatabaseCategoryTree.CategoryNode> tree = DatabaseCategoryTree.buildSystemTree(index.records());
        Map<String, List<String>> machineNames = Map.of(
            "sys:" + mcCraft, List.of("Crafting Table"),
            "sys:" + mcSmelt, List.of("Furnace", "Blast Furnace"),
            "sys:" + jeiCreateMix, List.of("Mechanical Mixer", "Basin")
        );

        List<String> byMachinePrefix = DatabaseCategoryTree
                .flatten(tree, Set.of(), Set.of(), "machine:furnace", n -> machineNames.getOrDefault(n.key(), List.of()))
                .stream().map(r -> r.node().key()).toList();
        assertEquals(List.of("dir:minecraft", "sys:" + mcSmelt), byMachinePrefix);

        List<String> byPlainMachine = DatabaseCategoryTree
                .flatten(tree, Set.of(), Set.of("dir:jei"), "mixer", n -> machineNames.getOrDefault(n.key(), List.of()))
                .stream().map(r -> r.node().key()).toList();
        assertEquals(List.of("dir:jei", "dir:jei/create", "sys:" + jeiCreateMix), byPlainMachine);

        DatabaseCategorySuggestionSource source = new DatabaseCategorySuggestionSource(
            () -> tree,
            n -> machineNames.getOrDefault(n.key(), List.of())
        );

        List<SearchSuggestion> machineSuggestions = source.suggest("machine:fur", 11, 8);
        assertTrue(machineSuggestions.stream().anyMatch(s -> s.replacement().equals("machine:Furnace")));
        assertTrue(machineSuggestions.stream().anyMatch(s -> s.replacement().equals("machine:Blast Furnace")));

        List<SearchSuggestion> prefixSuggestions = source.suggest("mac", 3, 8);
        assertTrue(prefixSuggestions.stream().anyMatch(s -> s.replacement().equals("machine:")));

        List<SearchSuggestion> systemSuggestions = source.suggest("mix", 3, 8);
        assertTrue(systemSuggestions.stream().anyMatch(s -> s.replacement().contains("create/mixing")));

        List<SearchHighlightSegment> hl = DatabaseCategorySuggestionSource.highlight("machine:Furnace");
        assertEquals(2, hl.size());
        assertEquals(SearchHighlightKind.field, hl.getFirst().kind());
        assertEquals(SearchHighlightKind.text, hl.get(1).kind());
    }

    private static int countMatches(RecipeDatabaseIndex index, String filter) {
        int n = 0;
        for (RecipeDatabaseRecord record : index.records()) {
            if (DatabaseCategoryTree.matchesFilter(record, filter)) {
                n++;
            }
        }
        return n;
    }

    private static DatabaseCategoryTree.@Nullable CategoryNode findNode(
        List<DatabaseCategoryTree.CategoryNode> nodes,
        String key
    ) {
        for (DatabaseCategoryTree.CategoryNode node : nodes) {
            if (node.key().equals(key)) {
                return node;
            }
        }
        return null;
    }

    private static RecipeDatabaseIndex buildIndex(
        List<FakeSystem> systems,
        Map<Namespace, List<Namespace>> systemTags
    ) {
        List<Namespace> sysIds = systems.stream().map(RecipeSystem::systemId).toList();
        DatabaseDefinition db = new DatabaseDefinition("main", "Main", DatabaseKind.system, true, 0, sysIds);
        return RecipeDatabaseIndex.build(List.of(db), Lists.immutable.ofAll(systems), systemTags);
    }

    private static FakeRecipe recipe(String id) {
        return new FakeRecipe(id);
    }

    private record FakeRecipe(String id) {}

    private static final class FakeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace id;
        private final List<FakeRecipe> recipes;

        private FakeSystem(Namespace id, List<FakeRecipe> recipes) {
            this.id = id;
            this.recipes = List.copyOf(recipes);
        }

        @Override
        public Namespace systemId() {
            return id;
        }

        @Override
        public Class<?> basicType() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FakeRecipe recipe) {
            return ResourceLocation.parse(recipe.id());
        }

        @Override
        public @Nullable FakeRecipe findRecipe(String uniqueId) {
            for (FakeRecipe r : recipes) {
                if (r.id().equals(uniqueId)) {
                    return r;
                }
            }
            return null;
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation registryName) {
            return findRecipe(registryName.toString());
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe first, FakeRecipe second) {
            return first.id().equals(second.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            return ResolveResult.resolved(
                RecipeProjection.create(
                    this,
                    recipe,
                    List.of(InputContent.fromValue(ingot, 1L)),
                    List.of(OutputContent.fromValue(plate, 1L))
                )
            );
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return Lists.immutable.ofAll(recipes);
        }
    }

    private record TestValueType(String name) implements ValueType<Long> {
        @Override
        public Namespace typeId() {
            return Namespace.of("test", name);
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long first, Long second) {
            return first.equals(second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    }
}
