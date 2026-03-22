package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.set.ImmutableSet;

/**
 * Set of {@link RuleCapabilityFlag} values declared by a {@link RuleProfile}.
 *
 * <p>Used by the {@link dev.vfyjxf.cirrus.api.recipe.routing.planner.AssuredPlanner}
 * to determine which planning modes are safe to use with the current profile.
 *
 * @param flags the immutable set of declared capability flags
 */
public record RuleCapabilities(
    ImmutableSet<RuleCapabilityFlag> flags
) {
    public RuleCapabilities {
        Checks.checkNotNull(flags, "flags");
    }

    /**
     * Checks whether a single capability flag is declared.
     *
     * @param flag the flag to check
     * @return {@code true} if the flag is present
     */
    public boolean has(RuleCapabilityFlag flag) {
        return flags.contains(flag);
    }

    /**
     * Checks whether <b>all</b> of the given flags are declared.
     *
     * @param required the set of required flags
     * @return {@code true} if every flag in {@code required} is present
     */
    public boolean hasAll(ImmutableSet<RuleCapabilityFlag> required) {
        return required.allSatisfy(flags::contains);
    }
}
