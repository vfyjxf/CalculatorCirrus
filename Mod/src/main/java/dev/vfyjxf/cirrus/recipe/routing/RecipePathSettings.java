package dev.vfyjxf.cirrus.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.database.CustomRecipeDatabase;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseRecipeRef;
import dev.vfyjxf.cirrus.api.recipe.database.FileRecipeDatabaseRepository;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseWorkspace;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ActiveRoutingRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.service.InventorySensitivityProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecipePathSettings {
    private static final String databaseDirectory = "databases";
    private static final int maxCachedRoutingGraphs = 4;
    private static final int maxCachedCatalogs = 4;
    private static final Object routingCacheLock = new Object();
    private static final Map<RoutingCacheKey, RoutingCacheEntry> routingCache = new HashMap<>();
    private static final Object catalogCacheLock = new Object();
    private static final Map<CatalogCacheKey, CatalogCacheEntry> catalogCache = new HashMap<>();
    private static final Object workspaceCacheLock = new Object();
    private static @Nullable WorkspaceCacheEntry workspaceCache;

    private RecipePathSettings() {
    }

    public static Path defaultDatabaseRepositoryRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("cirrus").resolve(databaseDirectory);
    }

    static void clearCachesForTests() {
        synchronized (workspaceCacheLock) {
            workspaceCache = null;
        }
        synchronized (catalogCacheLock) {
            catalogCache.clear();
        }
        synchronized (routingCacheLock) {
            routingCache.clear();
        }
    }

    static int cachedCatalogCountForTests() {
        synchronized (catalogCacheLock) {
            return catalogCache.size();
        }
    }

    static int cachedRoutingGraphCountForTests() {
        synchronized (routingCacheLock) {
            return routingCache.size();
        }
    }

    public static RoutingContext calculatorRoutingContext(RecipeEntry<?> target) {
        Catalog catalog = calculatorCatalog(target);
        RuleDefinition rule = calculatorRule();
        RoutingCacheEntry cache = cachedRoutingGraph(catalog, rule, ExecutionOverlayClientConfig.calculatorPlannerMode(), ExecutionOverlayClientConfig.calculatorInventorySensitivity(), calculatorLoopPolicy(rule));
        return new RoutingContext(
            cache.service(),
            catalog,
            rule,
            ExecutionOverlayClientConfig.calculatorPlannerMode(),
            ExecutionOverlayClientConfig.calculatorInventorySensitivity(),
            calculatorLoopPolicy(rule)
        );
    }

    public static RoutingContext executionRoutingContext(RecipeEntry<?> target) {
        boolean shared = ExecutionOverlayClientConfig.executionUsesCalculatorPathSettings();
        Catalog catalog = shared ? calculatorCatalog(target) : allSystemsCatalog(target, "execution");
        RuleDefinition rule = shared ? calculatorRule() : ActiveRoutingRule.activeRule();
        PlannerMode plannerMode = shared
            ? ExecutionOverlayClientConfig.calculatorPlannerMode()
            : ExecutionOverlayClientConfig.executionPlannerMode();
        InventorySensitivityProfile inventorySensitivity = shared
            ? ExecutionOverlayClientConfig.calculatorInventorySensitivity()
            : InventorySensitivityProfile.balanced;
        LoopPolicy loopPolicy = shared ? calculatorLoopPolicy(rule) : LoopPolicy.forbidAmplification;
        RoutingCacheEntry cache = cachedRoutingGraph(catalog, rule, plannerMode, inventorySensitivity, loopPolicy);
        return new RoutingContext(
            cache.service(),
            catalog,
            rule,
            plannerMode,
            inventorySensitivity,
            loopPolicy
        );
    }

    public static long executionSettingsFingerprint() {
        long out = CirrusJeiPlugin.recipeSystemCatalogVersion();
        out = 31L * out + Boolean.hashCode(ExecutionOverlayClientConfig.executionUsesCalculatorPathSettings());
        if (ExecutionOverlayClientConfig.executionUsesCalculatorPathSettings()) {
            out = 31L * out + ExecutionOverlayClientConfig.calculatorRuleSelection().hashCode();
            out = 31L * out + ExecutionOverlayClientConfig.calculatorPlannerMode().hashCode();
            out = 31L * out + ExecutionOverlayClientConfig.calculatorInventorySensitivity().hashCode();
            out = 31L * out + calculatorCatalog(null).fingerprint();
        } else {
            RuleDefinition active = ActiveRoutingRule.activeRule();
            out = 31L * out + ActiveRoutingRule.version();
            out = 31L * out + active.ruleId().hashCode();
            out = 31L * out + ExecutionOverlayClientConfig.executionPlannerMode().hashCode();
        }
        return out;
    }

    public static RuleDefinition calculatorRule() {
        return ExecutionOverlayClientConfig.calculatorRuleSelection().resolve();
    }

    public static LoopPolicy calculatorLoopPolicy(RuleDefinition rule) {
        if (DefaultRuleDefinitions.calculatorPreviewProfileId.equals(rule.profile().profileId())) {
            return LoopPolicy.allowBoundedAmplification;
        }
        return LoopPolicy.forbidAmplification;
    }

    public static Stock calculatorStock() {
        if (!ExecutionOverlayClientConfig.calculatorUseLiveStock()) {
            return Stock.empty();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return Stock.empty();
        }
        Stock stock = Stock.empty();
        addInventory(stock, minecraft.player.getInventory());
        return stock;
    }

    public static Catalog calculatorCatalog(@Nullable RecipeEntry<?> target) {
        try {
            RecipeApi api = RecipeApi.instance();
            RecipeDatabaseWorkspace workspace = cachedWorkspace(defaultDatabaseRepositoryRoot());
            return cachedCatalogForWorkspace(api, workspace, target);
        } catch (RuntimeException ignored) {
            return allSystemsCatalog(target, "calculator:fallback");
        }
    }

    private static RecipeDatabaseWorkspace cachedWorkspace(Path root) {
        long stamp = workspaceStamp(root);
        synchronized (workspaceCacheLock) {
            WorkspaceCacheEntry cached = workspaceCache;
            if (cached != null && root.equals(cached.root()) && stamp == cached.stamp()) {
                return cached.workspace();
            }
        }
        RecipeDatabaseWorkspace workspace = new FileRecipeDatabaseRepository(root).loadWorkspace();
        synchronized (workspaceCacheLock) {
            WorkspaceCacheEntry cached = workspaceCache;
            if (cached != null && root.equals(cached.root()) && stamp == cached.stamp()) {
                return cached.workspace();
            }
            workspaceCache = new WorkspaceCacheEntry(root, stamp, workspace);
            return workspace;
        }
    }

    private static long workspaceStamp(Path root) {
        return 31L * fileStamp(root.resolve("recipe_databases.json"))
            + fileStamp(root.resolve("active_database.json"));
    }

    private static long fileStamp(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static Catalog cachedCatalogForWorkspace(RecipeApi api, RecipeDatabaseWorkspace workspace, @Nullable RecipeEntry<?> target) {
        CustomRecipeDatabase active = workspace.activeDatabase();
        RecipeEntry<?> extraTarget = extraTargetForCatalog(active, target);
        CatalogCacheKey key = new CatalogCacheKey(
            System.identityHashCode(api),
            CirrusJeiPlugin.recipeSystemCatalogVersion(),
            active.id(),
            active.recipes().hashCode(),
            active.sources().hashCode(),
            active.disabledSources().hashCode(),
            extraTarget == null ? 0L : recipeFingerprint(extraTarget)
        );
        synchronized (catalogCacheLock) {
            CatalogCacheEntry cached = catalogCache.get(key);
            if (cached != null) {
                CatalogCacheEntry touched = cached.touch();
                catalogCache.put(key, touched);
                return touched.catalog();
            }
        }

        Catalog catalog = catalogForWorkspace(api, workspace, target);
        CatalogCacheEntry entry = new CatalogCacheEntry(catalog, System.nanoTime());
        synchronized (catalogCacheLock) {
            CatalogCacheEntry existing = catalogCache.get(key);
            if (existing != null) {
                CatalogCacheEntry touched = existing.touch();
                catalogCache.put(key, touched);
                return touched.catalog();
            }
            catalogCache.put(key, entry);
            trimCatalogCache();
            return catalog;
        }
    }

    public static Catalog catalogForWorkspace(RecipeApi api, RecipeDatabaseWorkspace workspace, @Nullable RecipeEntry<?> target) {
        MutableList<RecipeEntry<?>> recipes = Lists.mutable.empty();
        Set<RecipeEntry<?>> seen = new HashSet<>();
        CustomRecipeDatabase active = workspace.activeDatabase();
        if (active.builtin()) {
            for (RecipeSystem<?> system : api.allSystems()) {
                addEntries(recipes, seen, entriesForSystem(system));
            }
        } else {
            addEntries(recipes, seen, workspace.explicitEntries(api).getOrDefault(active.definitionId(), List.of()));
            for (RecipeSystem<?> system : api.allSystems()) {
                if (active.enabledSources().contains(system.systemId())) {
                    addEntries(recipes, seen, entriesForSystem(system));
                }
            }
        }
        ensureTarget(recipes, seen, target);
        return new Catalog(recipes.toImmutable(), catalogFingerprint(active, recipes), active.id());
    }

    private static @Nullable RecipeEntry<?> extraTargetForCatalog(CustomRecipeDatabase active, @Nullable RecipeEntry<?> target) {
        if (target == null || active.builtin()) {
            return null;
        }
        DatabaseRecipeRef ref = DatabaseRecipeRef.fromEntry(target);
        if (ref != null && active.recipeEnabled(ref) && active.contains(ref)) {
            return null;
        }
        if (active.enabledSources().contains(target.systemId())) {
            return null;
        }
        return target;
    }

    private static Catalog allSystemsCatalog(@Nullable RecipeEntry<?> target, String databaseId) {
        MutableList<RecipeEntry<?>> recipes = Lists.mutable.empty();
        Set<RecipeEntry<?>> seen = new HashSet<>();
        try {
            RecipeApi api = RecipeApi.instance();
            for (RecipeSystem<?> system : api.allSystems()) {
                addEntries(recipes, seen, entriesForSystem(system));
            }
        } catch (RuntimeException ignored) {
            // The target still gives routing enough information for direct display fallback.
        }
        ensureTarget(recipes, seen, target);
        return new Catalog(recipes.toImmutable(), catalogFingerprint(CustomRecipeDatabase.builtinDatabase(), recipes), databaseId);
    }

    private static void ensureTarget(MutableList<RecipeEntry<?>> recipes, Set<RecipeEntry<?>> seen, @Nullable RecipeEntry<?> target) {
        if (target != null && seen.add(target)) {
            recipes.add(target);
        }
    }

    private static void addEntries(MutableList<RecipeEntry<?>> recipes, Set<RecipeEntry<?>> seen, Iterable<RecipeEntry<?>> entries) {
        for (RecipeEntry<?> entry : entries) {
            if (entry != null && seen.add(entry)) {
                recipes.add(entry);
            }
        }
    }

    private static ImmutableList<RecipeEntry<?>> entriesForSystem(RecipeSystem<?> system) {
        MutableList<RecipeEntry<?>> entries = Lists.mutable.empty();
        for (Object recipe : system.allRecipes()) {
            entries.add(wrapRecipe(system, recipe));
        }
        return entries.toImmutable();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecipeEntry<?> wrapRecipe(RecipeSystem<?> system, Object recipe) {
        return RecipeEntry.of((RecipeSystem) system, recipe);
    }

    private static long catalogFingerprint(CustomRecipeDatabase active, MutableList<RecipeEntry<?>> recipes) {
        long out = CirrusJeiPlugin.recipeSystemCatalogVersion();
        out = 31L * out + active.id().hashCode();
        out = 31L * out + active.recipes().hashCode();
        out = 31L * out + active.enabledSources().hashCode();
        out = 31L * out + active.disabledSources().hashCode();
        for (RecipeEntry<?> recipe : recipes) {
            out = 31L * out + recipeFingerprint(recipe);
        }
        return out;
    }

    private static RoutingCacheEntry cachedRoutingGraph(
        Catalog catalog,
        RuleDefinition rule,
        PlannerMode plannerMode,
        InventorySensitivityProfile inventorySensitivity,
        LoopPolicy loopPolicy
    ) {
        RoutingCacheKey key = new RoutingCacheKey(
            catalog.fingerprint(),
            rule.ruleId().hashCode(),
            rule.profile().hashCode(),
            rule.sourcePolicy().hashCode(),
            rule.clauses().hashCode(),
            plannerMode,
            inventorySensitivity,
            loopPolicy
        );
        synchronized (routingCacheLock) {
            RoutingCacheEntry cached = routingCache.get(key);
            if (cached != null) {
                RoutingCacheEntry touched = cached.touch();
                routingCache.put(key, touched);
                return touched;
            }
        }

        RecipeGraph graph = RecipeGraph.buildGraph(catalog.recipes());
        RoutingCacheEntry entry = new RoutingCacheEntry(graph, new RoutingService(graph), System.nanoTime());
        synchronized (routingCacheLock) {
            RoutingCacheEntry existing = routingCache.get(key);
            if (existing != null) {
                RoutingCacheEntry touched = existing.touch();
                routingCache.put(key, touched);
                return touched;
            }
            routingCache.put(key, entry);
            trimRoutingCache();
            return entry;
        }
    }

    private static void trimRoutingCache() {
        while (routingCache.size() > maxCachedRoutingGraphs) {
            RoutingCacheKey oldestKey = null;
            long oldestAccess = Long.MAX_VALUE;
            for (var entry : routingCache.entrySet()) {
                long lastAccess = entry.getValue().lastAccessNanos();
                if (lastAccess < oldestAccess) {
                    oldestAccess = lastAccess;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            routingCache.remove(oldestKey);
        }
    }

    private static void trimCatalogCache() {
        while (catalogCache.size() > maxCachedCatalogs) {
            CatalogCacheKey oldestKey = null;
            long oldestAccess = Long.MAX_VALUE;
            for (var entry : catalogCache.entrySet()) {
                long lastAccess = entry.getValue().lastAccessNanos();
                if (lastAccess < oldestAccess) {
                    oldestAccess = lastAccess;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            catalogCache.remove(oldestKey);
        }
    }

    private static long recipeFingerprint(RecipeEntry<?> recipe) {
        long out = recipe.systemId().hashCode();
        out = 31L * out + System.identityHashCode(recipe.system());
        if (recipe.id() != null) {
            out = 31L * out + recipe.id().hashCode();
        } else {
            out = 31L * out + recipe.hashCode();
        }
        out = 31L * out + recipe.hashCode();
        out = 31L * out + recipe.recipe().hashCode();
        out = 31L * out + System.identityHashCode(recipe.recipe());
        return out;
    }

    private static void addInventory(Stock stock, Inventory inventory) {
        for (ItemStack stack : inventory.items) {
            addItemStack(stock, stack);
        }
        for (ItemStack stack : inventory.offhand) {
            addItemStack(stock, stack);
        }
    }

    private static void addItemStack(Stock stock, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stock.add(ContentStack.fromStack(stack));
    }

    public record Catalog(
        ImmutableList<RecipeEntry<?>> recipes,
        long fingerprint,
        String databaseId
    ) {
    }

    public record RoutingContext(
        RoutingService service,
        Catalog catalog,
        RuleDefinition rule,
        PlannerMode plannerMode,
        InventorySensitivityProfile inventorySensitivity,
        LoopPolicy loopPolicy
    ) {
    }

    private record RoutingCacheKey(
        long catalogFingerprint,
        int ruleIdHash,
        int profileHash,
        int sourcePolicyHash,
        int clausesHash,
        PlannerMode plannerMode,
        InventorySensitivityProfile inventorySensitivity,
        LoopPolicy loopPolicy
    ) {
    }

    private record RoutingCacheEntry(
        RecipeGraph graph,
        RoutingService service,
        long lastAccessNanos
    ) {
        private RoutingCacheEntry touch() {
            return new RoutingCacheEntry(graph, service, System.nanoTime());
        }
    }

    private record CatalogCacheKey(
        int apiIdentity,
        long systemCatalogVersion,
        String activeDatabaseId,
        int recipesHash,
        int sourcesHash,
        int disabledSourcesHash,
        long targetFingerprint
    ) {
    }

    private record CatalogCacheEntry(
        Catalog catalog,
        long lastAccessNanos
    ) {
        private CatalogCacheEntry touch() {
            return new CatalogCacheEntry(catalog, System.nanoTime());
        }
    }

    private record WorkspaceCacheEntry(
        Path root,
        long stamp,
        RecipeDatabaseWorkspace workspace
    ) {
    }
}
