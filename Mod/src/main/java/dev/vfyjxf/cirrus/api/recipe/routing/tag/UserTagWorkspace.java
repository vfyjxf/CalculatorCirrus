package dev.vfyjxf.cirrus.api.recipe.routing.tag;

import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;

import java.util.BitSet;

/**
 * Player-private overlay over a base {@link TagIndex}.
 *
 * <p>Allows individual players to add or remove tag assignments without
 * modifying the shared base index. The effective tag view is computed as:
 * <pre>   effective = (base ∪ additions) \ removals</pre>
 *
 * <p>All operations are {@code synchronized} and maintain a local monotonic
 * {@linkplain #version() version} counter. A snapshot taken from this workspace
 * merges the base index snapshot with the local additions and removals.
 *
 * <p>Workspaces can be serialized to/from {@link TagWorkspacePatch} for
 * persistence or network transfer via {@link #exportPatch(long)} and
 * {@link #importPatch(TagWorkspacePatch, MergePolicy)}.
 *
 * @see TagIndex
 * @see TagWorkspacePatch
 */
public final class UserTagWorkspace {

    private final TagIndex baseIndex;
    private final MutableMap<String, BitSet> additions = Maps.mutable.empty();
    private final MutableMap<String, BitSet> removals = Maps.mutable.empty();
    private long version;

    /**
     * Creates a new workspace overlaying the given base index.
     *
     * @param baseIndex the shared base tag index
     */
    public UserTagWorkspace(TagIndex baseIndex) {
        this.baseIndex = Checks.checkNotNull(baseIndex, "baseIndex");
    }

    /**
     * Adds a recipe to the given tag in this workspace's overlay.
     *
     * <p>If the recipe was previously marked as removed for the same tag,
     * the removal is revoked.
     *
     * @param tag         the tag name
     * @param recipeIndex the recipe's catalog index
     * @return the new workspace version
     */
    public synchronized long addToTag(String tag, int recipeIndex) {
        validateRecipeIndex(recipeIndex);
        additions.getIfAbsentPut(tag, this::newBitSet).set(recipeIndex);
        BitSet removeSet = removals.get(tag);
        if (removeSet != null) {
            removeSet.clear(recipeIndex);
        }
        return ++version;
    }

    /**
     * Removes a recipe from the given tag in this workspace's overlay.
     *
     * <p>If the recipe was previously added for the same tag,
     * the addition is revoked.
     *
     * @param tag         the tag name
     * @param recipeIndex the recipe's catalog index
     * @return the new workspace version
     */
    public synchronized long removeFromTag(String tag, int recipeIndex) {
        validateRecipeIndex(recipeIndex);
        removals.getIfAbsentPut(tag, this::newBitSet).set(recipeIndex);
        BitSet addSet = additions.get(tag);
        if (addSet != null) {
            addSet.clear(recipeIndex);
        }
        return ++version;
    }

    /**
     * Clears all local additions and removals for the given tag.
     *
     * @param tag the tag name to reset
     * @return the new workspace version
     */
    public synchronized long clearTag(String tag) {
        additions.remove(tag);
        removals.remove(tag);
        return ++version;
    }

    /**
     * Takes a merged snapshot of the base index and this workspace's overlay.
     *
     * <p>The returned snapshot equals the base snapshot with additions OR'd
     * in and removals AND-NOT'd out. It is immutable and safe for routing.
     *
     * @return a merged immutable tag snapshot
     */
    public synchronized TagSnapshot snapshot() {
        TagSnapshot base = baseIndex.snapshot();
        MutableMap<String, BitSet> merged = Maps.mutable.empty();

        // Copy base first.
        base.tagToRecipes().forEachKeyValue((tag, bitSet) -> merged.put(tag, (BitSet) bitSet.clone()));

        // Apply additions.
        additions.forEachKeyValue((tag, bitSet) -> merged.getIfAbsentPut(tag, this::newBitSet).or(bitSet));

        // Apply removals.
        removals.forEachKeyValue((tag, bitSet) -> {
            BitSet set = merged.getIfAbsentPut(tag, this::newBitSet);
            set.andNot(bitSet);
        });

        MutableMap<String, BitSet> frozenCopy = Maps.mutable.empty();
        merged.forEachKeyValue((tag, value) -> frozenCopy.put(tag, (BitSet) value.clone()));
        ImmutableMap<String, BitSet> frozen = frozenCopy.toImmutable();
        long effectiveVersion = Math.max(base.version(), version);
        return new TagSnapshot(effectiveVersion, base.recipeCount(), frozen);
    }

    /**
     * Exports the current additions and removals as a serializable
     * {@link TagWorkspacePatch}.
     *
     * @param baseVersion the version of the base index at the time of export
     * @return a patch containing all local overrides
     */
    public synchronized TagWorkspacePatch exportPatch(long baseVersion) {
        MutableMap<String, BitSet> addCopy = Maps.mutable.empty();
        additions.forEachKeyValue((tag, value) -> addCopy.put(tag, (BitSet) value.clone()));
        MutableMap<String, BitSet> removeCopy = Maps.mutable.empty();
        removals.forEachKeyValue((tag, value) -> removeCopy.put(tag, (BitSet) value.clone()));
        return new TagWorkspacePatch(
            baseVersion,
            addCopy.toImmutable(),
            removeCopy.toImmutable()
        );
    }

    /**
     * Imports a previously exported patch into this workspace.
     *
     * @param patch       the patch to import
     * @param mergePolicy determines whether existing local overrides are kept
     *                    ({@link MergePolicy#lastWriteWins}) or replaced
     *                    ({@link MergePolicy#replace})
     * @return the new workspace version
     */
    public synchronized long importPatch(TagWorkspacePatch patch, MergePolicy mergePolicy) {
        Checks.checkNotNull(patch, "patch");
        Checks.checkNotNull(mergePolicy, "mergePolicy");
        if (mergePolicy == MergePolicy.replace) {
            additions.clear();
            removals.clear();
        }
        patch.additions().forEachKeyValue((tag, set) -> additions.getIfAbsentPut(tag, this::newBitSet).or(set));
        patch.removals().forEachKeyValue((tag, set) -> removals.getIfAbsentPut(tag, this::newBitSet).or(set));
        return ++version;
    }

    /** Returns the current local workspace version counter. */
    public synchronized long version() {
        return version;
    }

    /**
     * Strategy for importing a {@link TagWorkspacePatch}.
     */
    public enum MergePolicy {
        /** Merges patch overrides on top of existing local overrides. */
        lastWriteWins,
        /** Clears all existing local overrides before applying the patch. */
        replace
    }

    private BitSet newBitSet() {
        return new BitSet(baseIndex.recipeCount());
    }

    private void validateRecipeIndex(int recipeIndex) {
        Checks.checkArgument(recipeIndex >= 0 && recipeIndex < baseIndex.recipeCount(), "recipeIndex out of range");
    }
}
