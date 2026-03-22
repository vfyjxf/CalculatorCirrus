package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;

public sealed interface ContentResource extends ContentVariant permits ContentStack, ContentValue {

    long amount();

    boolean matchesResource(ContentResource resource);

    /**
     * Returns a normalized version of this resource (typically with amount set to 1 or base unit).
     * Used as a key for matching in maps and collections.
     */
    ContentResource normalize();

    /**
     * Matches this resource against another using the specified strategy.
     *
     * @param other    the resource to match against
     * @param strategy the matching strategy
     * @return true if the resources match
     */
    default boolean matches(ContentResource other, MatchStrategy strategy) {
        if (this == other) return true;
        if (this instanceof ContentStack<?, ?> thisStack && other instanceof ContentStack<?, ?> otherStack) {
            return thisStack.matches(otherStack, strategy);
        }
        if (this instanceof ContentValue<?> thisValue && other instanceof ContentValue<?> otherValue) {
            return thisValue.matches(otherValue);
        }
        return false;
    }


}
