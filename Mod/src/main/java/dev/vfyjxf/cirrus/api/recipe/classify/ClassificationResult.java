package dev.vfyjxf.cirrus.api.recipe.classify;

import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The outcome of a classification run: for every classification tag, the set
 * of recipe indices carrying it.
 *
 * <p>Bitsets are indexed by {@link RecipeClassifyView#index()}, which matches
 * the recipe's position in the classified catalog.
 */
public final class ClassificationResult {

    private final int recipeCount;
    private final Map<String, BitSet> tagToRecipes;

    ClassificationResult(int recipeCount, Map<String, BitSet> tagToRecipes) {
        this.recipeCount = recipeCount;
        Map<String, BitSet> copy = new LinkedHashMap<>();
        tagToRecipes.forEach((tag, bits) -> copy.put(tag, (BitSet) bits.clone()));
        this.tagToRecipes = Collections.unmodifiableMap(copy);
    }

    /** Total number of recipes that were classified. */
    public int recipeCount() {
        return recipeCount;
    }

    /** Tag -&gt; recipe indices map (defensive copy, unmodifiable). */
    public Map<String, BitSet> tagToRecipes() {
        return tagToRecipes;
    }

    /** Recipe indices carrying the given tag (empty set when absent). */
    public BitSet recipesWithTag(String tag) {
        BitSet found = tagToRecipes.get(tag);
        return found == null ? new BitSet(0) : (BitSet) found.clone();
    }

    /** Whether the recipe at {@code index} carries the given tag. */
    public boolean hasTag(int index, String tag) {
        BitSet found = tagToRecipes.get(tag);
        return found != null && found.get(index);
    }

    /** Number of recipes carrying the given tag. */
    public int tagCount(String tag) {
        BitSet found = tagToRecipes.get(tag);
        return found == null ? 0 : found.cardinality();
    }

    /** All tags attached to the recipe at {@code index}. */
    public Set<String> tagsOf(int index) {
        Set<String> out = new LinkedHashSet<>();
        tagToRecipes.forEach((tag, bits) -> {
            if (bits.get(index)) {
                out.add(tag);
            }
        });
        return out;
    }

    /** Per-tag recipe counts, in first-seen tag order. */
    public Map<String, Integer> tagCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        tagToRecipes.forEach((tag, bits) -> out.put(tag, bits.cardinality()));
        return out;
    }
}
