package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.recipe.lookup.EmptyRecipeLookup;
import dev.vfyjxf.cirrus.recipe.lookup.IndexedRecipeLookup;
import dev.vfyjxf.cirrus.recipe.lookup.RecipeLookupIndex;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Simple, indexed, extensible fluent query interface over recipes in a {@link RecipeSystem}.
 *
 * <p>All query methods are non-destructive (caller collections are never mutated) and leverage
 * lazy per-system inverted indexing by {@link dev.vfyjxf.cirrus.api.content.value.ContentResource}
 * and {@link ResourceLocation}, with exact {@link MatchStrategy} verification and open
 * {@link RecipeLookupCondition} extensibility.
 */
public interface RecipeLookup<R> {

    @SuppressWarnings("unchecked")
    static <R> RecipeLookup<R> empty() {
        return EmptyRecipeLookup.instance;
    }

    static <R> RecipeLookup<R> ofSystem(RecipeSystem<R> system) {
        return new IndexedRecipeLookup<>(system, RecipeLookupExtractor.forSystem(system));
    }

    static <R> RecipeLookup<R> of(RecipeSystem<R> system, RecipeLookupExtractor<R> extractor) {
        return new IndexedRecipeLookup<>(system, extractor, false);
    }

    static void invalidateCache() {
        RecipeLookupIndex.invalidateAll();
    }

    @Contract("_->this")
    RecipeLookup<R> filter(Predicate<? super R> predicate);

    @Contract("_->this")
    default RecipeLookup<R> withCondition(RecipeLookupCondition<? super R> condition) {
        return filter(condition);
    }

    @Contract("_->this")
    RecipeLookup<R> withId(ResourceLocation id);

    default RecipeLookup<R> withInput(ContentStack<?, ?> input) {
        return withInput(input, MatchStrategy.identityAndComponent);
    }

    @Contract("_,_->this")
    RecipeLookup<R> withInput(ContentStack<?, ?> input, MatchStrategy strategy);

    @Contract("_,_->this")
    RecipeLookup<R> withInput(Collection<? extends ContentStack<?, ?>> inputs, MatchStrategy strategy);

    default RecipeLookup<R> withOutput(ContentStack<?, ?> output) {
        return withOutput(output, MatchStrategy.identityAndComponent);
    }

    @Contract("_,_->this")
    RecipeLookup<R> withOutput(ContentStack<?, ?> output, MatchStrategy strategy);

    @Contract("_,_->this")
    RecipeLookup<R> withOutput(Collection<? extends ContentStack<?, ?>> outputs, MatchStrategy strategy);

    /**
     * Evaluates the query and returns matching recipes.
     */
    RichIterable<R> get();

    default Stream<R> stream() {
        return StreamSupport.stream(get().spliterator(), false);
    }

    default @Nullable R first() {
        return get().getFirst();
    }

    default boolean anyMatch() {
        return first() != null;
    }

    default long count() {
        return get().size();
    }
}
