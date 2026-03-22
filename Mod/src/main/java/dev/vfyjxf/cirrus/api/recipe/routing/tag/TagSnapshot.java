package dev.vfyjxf.cirrus.api.recipe.routing.tag;

import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.map.ImmutableMap;

import java.util.BitSet;

/**
 * Immutable point-in-time view of tag-to-recipe assignments.
 *
 * <p>A {@code TagSnapshot} is created by {@link TagIndex#snapshot()} or
 * {@link UserTagWorkspace#snapshot()} and captures all tag-to-recipe mappings
 * at the moment of creation. It is safe to share across threads and is the
 * form used by {@link dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest}
 * during a single routing run.
 *
 * @param version      the version counter of the source index at snapshot time
 * @param recipeCount  the total number of recipes in the catalog
 * @param tagToRecipes immutable map from tag names to recipe bitsets
 */
public record TagSnapshot(
    long version,
    int recipeCount,
    ImmutableMap<String, BitSet> tagToRecipes
) {
    public TagSnapshot {
        Checks.checkArgument(version >= 0, "version must be non-negative");
        Checks.checkArgument(recipeCount >= 0, "recipeCount must be non-negative");
        Checks.checkNotNull(tagToRecipes, "tagToRecipes");
    }

    /**
     * Returns a <b>cloned</b> bitset of the recipes carrying the given tag.
     *
     * <p>If the tag is unknown, returns an empty bitset. The returned bitset
     * is a defensive copy and may be mutated freely by the caller.
     *
     * @param tag the tag name to query
     * @return a mutable bitset of recipe indices carrying the tag
     */
    public BitSet recipesForTag(String tag) {
        BitSet bitSet = tagToRecipes.get(tag);
        return bitSet == null ? new BitSet(recipeCount) : (BitSet) bitSet.clone();
    }

    /**
     * Returns an empty snapshot with no tag assignments.
     *
     * @param recipeCount the number of recipes in the catalog
     * @return an empty snapshot
     */
    public static TagSnapshot empty(int recipeCount) {
        return new TagSnapshot(0, recipeCount, org.eclipse.collections.api.factory.Maps.immutable.empty());
    }
}

