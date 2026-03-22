package dev.vfyjxf.cirrus.api.recipe.routing.tag;

import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.map.ImmutableMap;

import java.util.BitSet;

/**
 * Serializable delta of a player's tag workspace overrides.
 *
 * <p>A patch captures the set of tag additions and tag removals that a
 * {@link UserTagWorkspace} has accumulated relative to some base index version.
 * Patches are used for persistence (save/load) and network synchronization.
 *
 * @param baseVersion the version of the base {@link TagIndex} that this patch
 *                    was exported against
 * @param additions   immutable map of tag → recipe bitset for additions
 * @param removals    immutable map of tag → recipe bitset for removals
 * @see UserTagWorkspace#exportPatch(long)
 * @see UserTagWorkspace#importPatch(TagWorkspacePatch, UserTagWorkspace.MergePolicy)
 */
public record TagWorkspacePatch(
    long baseVersion,
    ImmutableMap<String, BitSet> additions,
    ImmutableMap<String, BitSet> removals
) {
    public TagWorkspacePatch {
        Checks.checkArgument(baseVersion >= 0, "baseVersion must be non-negative");
        Checks.checkNotNull(additions, "additions");
        Checks.checkNotNull(removals, "removals");
    }
}

