package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cloudlib.util.Checks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Objects;

/**
 * Stable integer-indexed catalog of recipe graph nodes.
 *
 * <p>Many routing subsystems (tag bitsets and source-policy decisions)
 * reference recipes by integer index rather than by object identity. This catalog
 * establishes a deterministic ordering of all nodes in the recipe graph and
 * provides O(1) bidirectional lookups between {@link RecipeEntry} and index.
 *
 * <p>The ordering follows the graph's source-order snapshot. Stable recipe
 * identity is still precomputed separately for candidate tie-breaking.
 *
 * @see RoutingService
 * @see dev.vfyjxf.cirrus.api.recipe.routing.tag.TagIndex
 */
public final class RoutingRecipeCatalog {

    private final ImmutableList<RecipeGraph.Node> nodes;
    private final Object2IntMap<RecipeEntry<?>> indexByRecipe;
    private final String[] stableRecipeIds;
    private final StableRecipeKey[] stableRecipeKeys;

    private RoutingRecipeCatalog(
        ImmutableList<RecipeGraph.Node> nodes,
        Object2IntMap<RecipeEntry<?>> indexByRecipe,
        String[] stableRecipeIds,
        StableRecipeKey[] stableRecipeKeys
    ) {
        this.nodes = nodes;
        this.indexByRecipe = indexByRecipe;
        this.stableRecipeIds = stableRecipeIds;
        this.stableRecipeKeys = stableRecipeKeys;
    }

    /**
     * Builds a catalog from the given recipe graph.
     *
     * <p>Nodes are read from the graph's deterministic source-order snapshot and
     * assigned sequential integer indices starting from 0.
     *
     * @param graph the recipe graph to index
     * @return a new catalog
     */
    public static RoutingRecipeCatalog fromGraph(RecipeGraph graph) {
        Checks.checkNotNull(graph, "graph");
        ImmutableList<RecipeGraph.Node> ordered = graph.stableNodes();

        Object2IntOpenHashMap<RecipeEntry<?>> indices = new Object2IntOpenHashMap<>(ordered.size());
        String[] stableIds = new String[ordered.size()];
        StableRecipeKey[] stableKeys = new StableRecipeKey[ordered.size()];
        indices.defaultReturnValue(-1);
        for (int i = 0; i < ordered.size(); i++) {
            RecipeEntry<?> recipe = ordered.get(i).recipe();
            indices.put(recipe, i);
            StableRecipeKey key = StableRecipeKey.from(ordered.get(i));
            stableKeys[i] = key;
        }
        return new RoutingRecipeCatalog(ordered, indices, stableIds, stableKeys);
    }

    /** Returns the total number of recipes in this catalog. */
    public int size() {
        return nodes.size();
    }

    /** Returns all nodes in graph order. */
    public ImmutableList<RecipeGraph.Node> nodes() {
        return nodes;
    }

    /**
     * Returns the node at the given index.
     *
     * @param index zero-based index
     * @return the node
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public RecipeGraph.Node nodeAt(int index) {
        return nodes.get(index);
    }

    /**
     * Returns the index of the given recipe, or {@code -1} if not found.
     *
     * @param recipe the recipe to look up
     * @return the zero-based index, or -1
     */
    public int indexOf(RecipeEntry<?> recipe) {
        return indexByRecipe.getInt(recipe);
    }

    /**
     * Returns a human-readable stable identifier for the recipe at the given index.
     *
     * @param index zero-based index
     * @return a string of the form {@code "systemId:recipeId"}
     */
    public String stableRecipeId(int index) {
        nodes.get(index);
        String stableId = stableRecipeIds[index];
        if (stableId == null) {
            stableId = StableRecipeKey.displayId(nodes.get(index).recipe());
            stableRecipeIds[index] = stableId;
        }
        return stableId;
    }

    /**
     * Returns the precomputed default tie-break key for the recipe at the given index.
     *
     * @param index zero-based index
     * @return the stable key used by built-in default rule profiles
     */
    public StableRecipeKey stableRecipeKey(int index) {
        nodes.get(index);
        return stableRecipeKeys[index];
    }

    public static final class StableRecipeKey implements Comparable<StableRecipeKey> {
        private final RecipeEntry<?> recipe;
        private final String systemId;
        private final String initialRecipeId;
        private final int hash;
        private final int sourceOrder;
        private volatile String resolvedRecipeId;

        private StableRecipeKey(
            RecipeEntry<?> recipe,
            String systemId,
            String initialRecipeId,
            int hash,
            int sourceOrder
        ) {
            this.recipe = recipe;
            this.systemId = systemId;
            this.initialRecipeId = initialRecipeId;
            this.hash = hash;
            this.sourceOrder = sourceOrder;
        }

        private static StableRecipeKey from(RecipeGraph.Node node) {
            return new StableRecipeKey(
                node.recipe(),
                node.stableSystemId(),
                node.stableRecipeId(),
                node.stableRecipeHash(),
                node.sourceOrder()
            );
        }

        private static String displayId(RecipeEntry<?> recipe) {
            ResourceLocation id = recipe.id();
            return recipe.systemId() + ":" + Objects.toString(id, Integer.toString(recipe.hashCode()));
        }

        @Override
        public int compareTo(StableRecipeKey other) {
            int cmp = systemId.compareTo(other.systemId);
            if (cmp != 0) {
                return cmp;
            }
            cmp = recipeId().compareTo(other.recipeId());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(hash, other.hash);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(sourceOrder, other.sourceOrder);
        }

        private String recipeId() {
            if (!initialRecipeId.isEmpty()) {
                return initialRecipeId;
            }
            String recipeId = resolvedRecipeId;
            if (recipeId == null) {
                ResourceLocation id = recipe.id();
                recipeId = Objects.toString(id, "null");
                resolvedRecipeId = recipeId;
            }
            return recipeId;
        }
    }
}
