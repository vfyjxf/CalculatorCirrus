package dev.vfyjxf.cirrus.api.recipe.classify;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only global indexes over the classified catalog, available to rules
 * that need cross-recipe information (e.g. two-way swap detection, mass-sink
 * detection). Built once per classification run.
 */
public final class ClassificationContext {

    private final List<RecipeClassifyView> recipes;
    private final Map<String, int[]> producersByOutput;
    private final Map<String, int[]> consumersByInput;

    ClassificationContext(
        List<RecipeClassifyView> recipes,
        Map<String, int[]> producersByOutput,
        Map<String, int[]> consumersByInput
    ) {
        this.recipes = recipes;
        this.producersByOutput = producersByOutput;
        this.consumersByInput = consumersByInput;
    }

    /** Builds the context indexes over the given catalog of views. */
    public static ClassificationContext of(List<RecipeClassifyView> recipes) {
        Map<String, IntBag> producers = new HashMap<>();
        Map<String, IntBag> consumers = new HashMap<>();
        for (RecipeClassifyView view : recipes) {
            for (String output : view.allOutputIds()) {
                producers.computeIfAbsent(output, k -> new IntBag()).add(view.index());
            }
            for (String input : view.allInputIds()) {
                consumers.computeIfAbsent(input, k -> new IntBag()).add(view.index());
            }
        }
        return new ClassificationContext(recipes, freeze(producers), freeze(consumers));
    }

    private static Map<String, int[]> freeze(Map<String, IntBag> source) {
        Map<String, int[]> out = new HashMap<>(source.size());
        source.forEach((key, bag) -> out.put(key, bag.toArray()));
        return out;
    }

    /** All classified recipes, in index order. */
    public List<RecipeClassifyView> recipes() {
        return recipes;
    }

    /** Indices of recipes producing the given content id (empty when none). */
    public int[] producersOf(String outputId) {
        int[] found = producersByOutput.get(outputId);
        return found == null ? new int[0] : found;
    }

    /** Indices of recipes consuming the given content id (empty when none). */
    public int[] consumersOf(String inputId) {
        int[] found = consumersByInput.get(inputId);
        return found == null ? new int[0] : found;
    }

    /** Number of recipes producing the given content id. */
    public int producerCount(String outputId) {
        return producersOf(outputId).length;
    }

    private static final class IntBag {
        private int[] data = new int[4];
        private int size;

        void add(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
