package dev.vfyjxf.cirrus.recipe.lookup;

import com.google.common.collect.MapMaker;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookupExtractor;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Lazily built, version-validated inverted index for a {@link RecipeSystem}.
 *
 * <p>Recipes are indexed by {@link ResourceLocation} id and by normalized {@link ContentKind}
 * for inputs and outputs. Non-enumerable {@link ComplexContent} inputs are tracked in a small
 * {@code unindexedInputs} fallback bucket so complex custom ingredients are never missed.
 */
public final class RecipeLookupIndex<R> {

    private static final ConcurrentMap<RecipeSystem<?>, CachedIndex<?>> cache = new MapMaker().weakKeys().makeMap();

    private final List<Entry<R>> allEntries;
    private final Map<ResourceLocation, List<Entry<R>>> byId;
    private final Map<ContentKind<?>, List<Entry<R>>> byInputKind;
    private final Map<ContentKind<?>, List<Entry<R>>> byOutputKind;
    private final List<Entry<R>> unindexedInputs;

    private RecipeLookupIndex(
        List<Entry<R>> allEntries,
        Map<ResourceLocation, List<Entry<R>>> byId,
        Map<ContentKind<?>, List<Entry<R>>> byInputKind,
        Map<ContentKind<?>, List<Entry<R>>> byOutputKind,
        List<Entry<R>> unindexedInputs
    ) {
        this.allEntries = allEntries;
        this.byId = byId;
        this.byInputKind = byInputKind;
        this.byOutputKind = byOutputKind;
        this.unindexedInputs = unindexedInputs;
    }

    public static void invalidateAll() {
        cache.clear();
    }

    public static void invalidate(RecipeSystem<?> system) {
        cache.remove(system);
    }

    @SuppressWarnings("unchecked")
    public static <R> RecipeLookupIndex<R> getOrBuild(RecipeSystem<R> system, RecipeLookupExtractor<R> extractor) {
        long attachEpoch = RecipeApiImpl.attachEpoch();
        long systemVersion = safeLookupVersion(system);
        CachedIndex<?> cached = cache.get(system);
        if (cached != null && cached.attachEpoch == attachEpoch && cached.systemVersion == systemVersion) {
            return (RecipeLookupIndex<R>) cached.index;
        }
        RecipeLookupIndex<R> built = build(system, extractor);
        cache.put(system, new CachedIndex<>(attachEpoch, systemVersion, built));
        return built;
    }

    public static <R> RecipeLookupIndex<R> build(RecipeSystem<R> system, RecipeLookupExtractor<R> extractor) {
        List<Entry<R>> allEntries = new ArrayList<>();
        Map<ResourceLocation, List<Entry<R>>> byId = new HashMap<>();
        Map<ContentKind<?>, List<Entry<R>>> byInputKind = new HashMap<>();
        Map<ContentKind<?>, List<Entry<R>>> byOutputKind = new HashMap<>();
        List<Entry<R>> unindexedInputs = new ArrayList<>();

        Set<ContentKind<?>> seenInputKinds = new HashSet<>();
        Set<ContentKind<?>> seenOutputKinds = new HashSet<>();

        for (R recipe : system.allRecipes()) {
            if (recipe == null) {
                continue;
            }
            ResourceLocation id = safeGetId(system, recipe);
            RoutingRecipeView view = safeExtract(extractor, recipe);

            List<InputContent> inputs;
            List<OutputContent> outputs;
            if (view == null) {
                inputs = List.of();
                outputs = List.of();
            } else {
                List<InputContent> nonEmptyInputs = new ArrayList<>();
                for (InputContent input : view.inputs()) {
                    if (input != null && !input.isEmpty()) {
                        nonEmptyInputs.add(input);
                    }
                }
                List<OutputContent> nonEmptyOutputs = new ArrayList<>();
                for (OutputContent output : view.outputs()) {
                    if (output != null && !output.isEmpty()) {
                        nonEmptyOutputs.add(output);
                    }
                }
                inputs = nonEmptyInputs.isEmpty() ? List.of() : List.copyOf(nonEmptyInputs);
                outputs = nonEmptyOutputs.isEmpty() ? List.of() : List.copyOf(nonEmptyOutputs);
            }

            Entry<R> entry = new Entry<>(allEntries.size(), recipe, id, inputs, outputs);
            allEntries.add(entry);

            if (id != null) {
                byId.computeIfAbsent(id, ignored -> new ArrayList<>(1)).add(entry);
            }

            seenInputKinds.clear();
            boolean hasUnindexedInput = false;
            for (InputContent input : inputs) {
                if (!collectInputKinds(input, seenInputKinds)) {
                    hasUnindexedInput = true;
                }
            }
            for (ContentKind<?> kind : seenInputKinds) {
                byInputKind.computeIfAbsent(kind, ignored -> new ArrayList<>(4)).add(entry);
            }
            if (hasUnindexedInput) {
                unindexedInputs.add(entry);
            }

            seenOutputKinds.clear();
            for (OutputContent output : outputs) {
                collectOutputKinds(output, seenOutputKinds);
            }
            for (ContentKind<?> kind : seenOutputKinds) {
                byOutputKind.computeIfAbsent(kind, ignored -> new ArrayList<>(2)).add(entry);
            }
        }

        return new RecipeLookupIndex<>(
            List.copyOf(allEntries),
            byId,
            byInputKind,
            byOutputKind,
            List.copyOf(unindexedInputs)
        );
    }

    List<Entry<R>> allEntries() {
        return allEntries;
    }

    List<Entry<R>> byId(ResourceLocation id) {
        return byId.getOrDefault(id, List.of());
    }

    List<Entry<R>> byInputKind(ContentKind<?> kind) {
        return byInputKind.getOrDefault(kind, List.of());
    }

    List<Entry<R>> byOutputKind(ContentKind<?> kind) {
        return byOutputKind.getOrDefault(kind, List.of());
    }

    List<Entry<R>> unindexedInputs() {
        return unindexedInputs;
    }

    static boolean matchesInput(InputContent content, ContentStack<?, ?> query, MatchStrategy strategy) {
        return switch (content) {
            case EmptyRecipeContent ignored -> false;
            case ValueContent<?> ignored -> false;
            case StackContent<?, ?> stack -> stack.content().matches(query, strategy);
            case TagStackContent<?> tag -> {
                if (strategy.withAmount() && tag.amount() != query.amount()) {
                    yield false;
                }
                yield tag.satisfy(query);
            }
            case StackListContent list -> {
                if (strategy.withAmount() && list.amount() != query.amount()) {
                    yield false;
                }
                for (ContentStack<?, ?> stack : list.stacks()) {
                    if (stack.matches(query, strategy)) {
                        yield true;
                    }
                }
                yield false;
            }
            case TypedStackListContent<?, ?> list -> {
                if (strategy.withAmount() && list.amount() != query.amount()) {
                    yield false;
                }
                if (list.type() != query.type()) {
                    yield false;
                }
                for (ContentStack<?, ?> stack : list.stacks()) {
                    if (stack.matches(query, strategy)) {
                        yield true;
                    }
                }
                yield false;
            }
            case ComplexContent complex -> {
                if (strategy.withAmount() && complex.amount() != query.amount()) {
                    yield false;
                }
                yield complex.satisfy(query);
            }
        };
    }

    static boolean matchesOutput(OutputContent content, ContentStack<?, ?> query, MatchStrategy strategy) {
        return switch (content) {
            case EmptyRecipeContent ignored -> false;
            case ValueContent<?> ignored -> false;
            case StackContent<?, ?> stack -> stack.content().matches(query, strategy);
        };
    }

    /**
     * Collects all {@link ContentKind}s referenced by an {@link InputContent}.
     *
     * @return {@code true} if the input was completely enumerable, {@code false} if it requires fallback scanning.
     */
    private static boolean collectInputKinds(InputContent input, Set<ContentKind<?>> out) {
        return switch (input) {
            case EmptyRecipeContent ignored -> true;
            case ValueContent<?> ignored -> true;
            case StackContent<?, ?> stack -> {
                addStackKind(stack.content(), out);
                yield true;
            }
            case TagStackContent<?> tag -> {
                for (ContentVariant variant : tag.enumerate()) {
                    addVariantKind(variant, out);
                }
                yield true;
            }
            case StackListContent list -> {
                for (ContentStack<?, ?> stack : list.stacks()) {
                    addStackKind(stack, out);
                }
                yield true;
            }
            case TypedStackListContent<?, ?> list -> {
                for (ContentStack<?, ?> stack : list.stacks()) {
                    addStackKind(stack, out);
                }
                yield true;
            }
            case ComplexContent complex -> {
                if (!complex.isEnumerable()) {
                    yield false;
                }
                boolean any = false;
                for (ContentVariant variant : complex.enumerate()) {
                    addVariantKind(variant, out);
                    any = true;
                }
                yield any;
            }
        };
    }

    private static void collectOutputKinds(OutputContent output, Set<ContentKind<?>> out) {
        if (output instanceof StackContent<?, ?> stack) {
            addStackKind(stack.content(), out);
        }
    }

    private static void addStackKind(ContentStack<?, ?> stack, Set<ContentKind<?>> out) {
        if (stack.notEmpty()) {
            ContentKind<?> kind = stack.kind();
            if (kind.notEmpty()) {
                out.add(kind);
            }
        }
    }

    private static void addVariantKind(ContentVariant variant, Set<ContentKind<?>> out) {
        if (variant instanceof ContentKind<?> kind) {
            if (kind.notEmpty()) {
                out.add(kind);
            }
        } else if (variant instanceof ContentStack<?, ?> stack) {
            addStackKind(stack, out);
        }
    }

    private static <R> long safeLookupVersion(RecipeSystem<R> system) {
        try {
            return system.lookupVersion();
        } catch (RuntimeException | LinkageError ignored) {
            return 0L;
        }
    }

    private static <R> @Nullable ResourceLocation safeGetId(RecipeSystem<R> system, R recipe) {
        try {
            return system.getId(recipe);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static <R> @Nullable RoutingRecipeView safeExtract(RecipeLookupExtractor<R> extractor, R recipe) {
        try {
            return extractor.extract(recipe);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private record CachedIndex<R>(long attachEpoch, long systemVersion, RecipeLookupIndex<R> index) {}

    record Entry<R>(
        int ordinal,
        R recipe,
        @Nullable ResourceLocation id,
        List<InputContent> inputs,
        List<OutputContent> outputs
    ) {}
}
