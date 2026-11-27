package dev.vfyjxf.cirrus.api.recipe.classify;

/**
 * Well-known classification tags produced by {@link RecipeClassifier}.
 *
 * <p>Classification tags are plain strings following the tag naming style
 * ({@code cirrus:<dimension>/<name>}). They flow into screening, so the default
 * screening policy can act on them without any planner changes.
 *
 * <p>Two dispositions exist:
 * <ul>
 *     <li><b>Exclusion tags</b> ({@link #JUNK}, {@link #DUPLICATE}) — removed
 *     from planning by the screening policy.</li>
 *     <li><b>Advisory tags</b> ({@link #RECOLOR}, {@link #EXCHANGE},
 *     {@link #RECYCLING}, {@link #GENERATION}) — kept available, but mapped to
 *     screening hint penalties or used by topology-aware planning.</li>
 * </ul>
 */
public final class ClassificationTags {

    /**
     * Fake or non-production recipes: quests, archaeology, trade tables,
     * loot/drop displays. Excluded by the default source policy.
     */
    public static final String junk = "cirrus:quality/junk";

    /**
     * Recipes that merely duplicate another system's view of the same craft
     * (e.g. colony teachable recipes, JEI mirrors of vanilla systems).
     * Excluded by the default source policy.
     */
    public static final String duplicate = "cirrus:quality/duplicate";

    /**
     * Recolor recipes that transform an already-colored family member into
     * another color (concrete powder re-dyeing, painting). Advisory:
     * discouraged, never excluded — for some items this is the only source.
     */
    public static final String recolor = "cirrus:pattern/recolor";

    /**
     * Variant exchange recipes whose output is also a member of their own
     * input (chisel/chipped swaps, 1:1 two-way conversions). Advisory:
     * discouraged.
     */
    public static final String exchange = "cirrus:pattern/exchange";

    /**
     * Recycling / valorization recipes that reduce a crafted product back to
     * base materials, or map large unrelated input sets onto one output
     * (arc furnace recycling, salvaging, composting). Advisory: last resort.
     */
    public static final String recycling = "cirrus:intent/recycling";

    /**
     * Resource generation recipes that produce items without consuming items
     * (cobblegen, laser drills, quarries). Mark-only: informational for
     * situational filtering, no routing penalty.
     */
    public static final String generation = "cirrus:intent/generation";

    private ClassificationTags() {}

    /**
     * Returns {@code true} when the tag is an exclusion-level classification
     * handled by the default source policy.
     */
    public static boolean isExclusionTag(String tag) {
        return junk.equals(tag) || duplicate.equals(tag);
    }
}
