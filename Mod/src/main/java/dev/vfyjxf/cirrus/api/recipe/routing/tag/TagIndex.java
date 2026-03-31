package dev.vfyjxf.cirrus.api.recipe.routing.tag;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;

import java.util.BitSet;

/**
 * Mutable reverse index that maps tag strings to the set of recipes carrying
 * each tag.
 *
 * <p>Internally each tag is represented as a {@link BitSet} indexed by the
 * recipe's position in the {@link RoutingRecipeCatalog}. This allows fast
 * bitwise intersection/union with source-policy rule expressions.
 *
 * <p>All mutating operations ({@link #add}, {@link #remove}) are
 * {@code synchronized} and increment a monotonic {@linkplain #version() version}
 * counter, making it safe to share a single {@code TagIndex} across threads
 * while still supporting cheap staleness checks on cached snapshots.
 *
 * @see TagSnapshot
 * @see UserTagWorkspace
 */
public final class TagIndex {

    private final RoutingRecipeCatalog catalog;
    private final MutableMap<String, BitSet> tagToRecipes = Maps.mutable.empty();
    private long version;

    /**
     * Creates a new tag index backed by the given recipe catalog.
     *
     * @param catalog the recipe catalog that defines the valid recipe indices
     */
    public TagIndex(RoutingRecipeCatalog catalog) {
        this.catalog = Checks.checkNotNull(catalog, "catalog");
    }

    /** Returns the total number of recipes in the underlying catalog. */
    public int recipeCount() {
        return catalog.size();
    }

    /** Returns the current version counter (incremented on every mutation). */
    public synchronized long version() {
        return version;
    }

    /**
     * Returns the catalog index of the given recipe entry.
     *
     * @param recipe the recipe entry to look up
     * @return the index, or {@code -1} if the recipe is not in the catalog
     */
    public int recipeIndex(RecipeEntry<?> recipe) {
        return catalog.indexOf(recipe);
    }

    /**
     * Adds a recipe to the given tag.
     *
     * <p>If the recipe is not in the catalog, this method is a no-op.
     *
     * @param tag    the tag name
     * @param recipe the recipe entry to tag
     */
    public synchronized void add(String tag, RecipeEntry<?> recipe) {
        Checks.checkNotNull(tag, "tag");
        Checks.checkNotNull(recipe, "recipe");
        int index = catalog.indexOf(recipe);
        if (index < 0) {
            return;
        }
        tagToRecipes.getIfAbsentPut(tag, () -> new BitSet(catalog.size())).set(index);
        version++;
    }

    /**
     * Removes a recipe from the given tag.
     *
     * <p>If the recipe is not in the catalog or is not currently tagged,
     * this method is a no-op.
     *
     * @param tag    the tag name
     * @param recipe the recipe entry to untag
     */
    public synchronized void remove(String tag, RecipeEntry<?> recipe) {
        Checks.checkNotNull(tag, "tag");
        Checks.checkNotNull(recipe, "recipe");
        int index = catalog.indexOf(recipe);
        if (index < 0) {
            return;
        }
        BitSet set = tagToRecipes.get(tag);
        if (set != null && set.get(index)) {
            set.clear(index);
            version++;
        }
    }

    /**
     * Takes a point-in-time snapshot of the entire tag index.
     *
     * <p>The returned {@link TagSnapshot} is immutable and safe to pass to
     * routing requests without holding the index lock.
     *
     * @return an immutable snapshot of all tag-to-recipe mappings
     */
    public synchronized TagSnapshot snapshot() {
        MutableMap<String, BitSet> copy = Maps.mutable.empty();
        tagToRecipes.forEachKeyValue((tag, bitSet) -> copy.put(tag, (BitSet) bitSet.clone()));
        return new TagSnapshot(version, catalog.size(), copy.toImmutable());
    }
}
