package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cloudlib.util.Checks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Comparator;
import java.util.Objects;

/**
 * Stable integer-indexed catalog of recipe graph nodes.
 *
 * <p>Many routing subsystems (tag bitsets, SCC analysis, source-policy decisions)
 * reference recipes by integer index rather than by object identity. This catalog
 * establishes a deterministic, stable ordering of all nodes in the recipe graph
 * and provides O(1) bidirectional lookups between {@link RecipeEntry} and index.
 *
 * <p>The ordering is stable across identical graph constructions: it sorts by
 * system ID, then recipe ID string, then hash code as a final tie-break.
 *
 * @see RoutingService
 * @see dev.vfyjxf.cirrus.api.recipe.routing.tag.TagIndex
 * @see dev.vfyjxf.cirrus.api.recipe.routing.state.SccAnalysis
 */
public final class RoutingRecipeCatalog {

    private static final Comparator<RecipeGraph.Node> stableOrder = Comparator
        .comparing((RecipeGraph.Node node) -> node.recipe().systemId().toString())
        .thenComparing(node -> Objects.toString(node.recipe().id(), "null"))
        .thenComparingInt(node -> node.recipe().hashCode());

    private final ImmutableList<RecipeGraph.Node> nodes;
    private final Object2IntMap<RecipeEntry<?>> indexByRecipe;

    private RoutingRecipeCatalog(
        ImmutableList<RecipeGraph.Node> nodes,
        Object2IntMap<RecipeEntry<?>> indexByRecipe
    ) {
        this.nodes = nodes;
        this.indexByRecipe = indexByRecipe;
    }

    /**
     * Builds a catalog from the given recipe graph.
     *
     * <p>Nodes are sorted into a deterministic stable order and assigned
     * sequential integer indices starting from 0.
     *
     * @param graph the recipe graph to index
     * @return a new catalog
     */
    public static RoutingRecipeCatalog fromGraph(RecipeGraph graph) {
        Checks.checkNotNull(graph, "graph");
        var ordered = Lists.mutable.ofAll(graph.nodes());
        ordered.sortThis(stableOrder);

        Object2IntOpenHashMap<RecipeEntry<?>> indices = new Object2IntOpenHashMap<>(ordered.size());
        indices.defaultReturnValue(-1);
        for (int i = 0; i < ordered.size(); i++) {
            indices.put(ordered.get(i).recipe(), i);
        }
        return new RoutingRecipeCatalog(ordered.toImmutable(), indices);
    }

    /** Returns the total number of recipes in this catalog. */
    public int size() {
        return nodes.size();
    }

    /** Returns all nodes in stable order. */
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
        RecipeEntry<?> recipe = nodes.get(index).recipe();
        return recipe.systemId() + ":" + Objects.toString(recipe.id(), Integer.toString(recipe.hashCode()));
    }
}
