package dev.vfyjxf.cirrus.recipe.lookup;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookupExtractor;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Indexed, non-destructive {@link RecipeLookup} implementation backed by {@link RecipeLookupIndex}.
 */
public final class IndexedRecipeLookup<R> implements RecipeLookup<R> {

    private final RecipeSystem<R> system;
    private final RecipeLookupExtractor<R> extractor;
    private final boolean cacheIndex;

    private @Nullable ResourceLocation requiredId;
    private final List<StackCriterion> inputCriteria = new ArrayList<>(2);
    private final List<StackCriterion> outputCriteria = new ArrayList<>(2);
    private final List<Predicate<? super R>> predicates = new ArrayList<>(2);
    private boolean impossible;

    public IndexedRecipeLookup(RecipeSystem<R> system, RecipeLookupExtractor<R> extractor) {
        this(system, extractor, true);
    }

    public IndexedRecipeLookup(RecipeSystem<R> system, RecipeLookupExtractor<R> extractor, boolean cacheIndex) {
        this.system = Checks.checkNotNull(system, "system");
        this.extractor = Checks.checkNotNull(extractor, "extractor");
        this.cacheIndex = cacheIndex;
    }

    @Override
    public RecipeLookup<R> filter(Predicate<? super R> predicate) {
        Checks.checkNotNull(predicate, "predicate");
        predicates.add(predicate);
        return this;
    }

    @Override
    public RecipeLookup<R> withId(ResourceLocation id) {
        Checks.checkNotNull(id, "id");
        if (requiredId != null && !requiredId.equals(id)) {
            impossible = true;
        } else {
            requiredId = id;
        }
        return this;
    }

    @Override
    public RecipeLookup<R> withInput(ContentStack<?, ?> input, MatchStrategy strategy) {
        Checks.checkNotNull(strategy, "strategy");
        if (input == null || input.isEmpty()) {
            impossible = true;
            return this;
        }
        inputCriteria.add(new StackCriterion(input, strategy));
        return this;
    }

    @Override
    public RecipeLookup<R> withInput(Collection<? extends ContentStack<?, ?>> inputs, MatchStrategy strategy) {
        Checks.checkNotNull(strategy, "strategy");
        if (inputs == null || inputs.isEmpty()) {
            return this;
        }
        for (ContentStack<?, ?> input : inputs) {
            if (input == null || input.isEmpty()) {
                impossible = true;
                return this;
            }
            inputCriteria.add(new StackCriterion(input, strategy));
        }
        return this;
    }

    @Override
    public RecipeLookup<R> withOutput(ContentStack<?, ?> output, MatchStrategy strategy) {
        Checks.checkNotNull(strategy, "strategy");
        if (output == null || output.isEmpty()) {
            impossible = true;
            return this;
        }
        outputCriteria.add(new StackCriterion(output, strategy));
        return this;
    }

    @Override
    public RecipeLookup<R> withOutput(Collection<? extends ContentStack<?, ?>> outputs, MatchStrategy strategy) {
        Checks.checkNotNull(strategy, "strategy");
        if (outputs == null || outputs.isEmpty()) {
            return this;
        }
        for (ContentStack<?, ?> output : outputs) {
            if (output == null || output.isEmpty()) {
                impossible = true;
                return this;
            }
            outputCriteria.add(new StackCriterion(output, strategy));
        }
        return this;
    }

    @Override
    public RichIterable<R> get() {
        if (impossible) {
            return Lists.immutable.empty();
        }
        RecipeLookupIndex<R> index = cacheIndex
                ? RecipeLookupIndex.getOrBuild(system, extractor)
                : RecipeLookupIndex.build(system, extractor);

        List<RecipeLookupIndex.Entry<R>> primary = index.allEntries();
        List<RecipeLookupIndex.Entry<R>> secondary = List.of();
        int bestCost = primary.size();

        if (requiredId != null) {
            List<RecipeLookupIndex.Entry<R>> idBucket = index.byId(requiredId);
            if (idBucket.isEmpty()) {
                return Lists.immutable.empty();
            }
            primary = idBucket;
            bestCost = idBucket.size();
        }

        for (StackCriterion criterion : outputCriteria) {
            if (!criterion.strategy.withIdentity()) {
                continue;
            }
            ContentKind<?> kind = criterion.stack.kind();
            List<RecipeLookupIndex.Entry<R>> bucket = index.byOutputKind(kind);
            if (bucket.isEmpty()) {
                return Lists.immutable.empty();
            }
            if (bucket.size() < bestCost) {
                primary = bucket;
                secondary = List.of();
                bestCost = bucket.size();
            }
        }

        List<RecipeLookupIndex.Entry<R>> unindexed = index.unindexedInputs();
        for (StackCriterion criterion : inputCriteria) {
            if (!criterion.strategy.withIdentity()) {
                continue;
            }
            ContentKind<?> kind = criterion.stack.kind();
            List<RecipeLookupIndex.Entry<R>> bucket = index.byInputKind(kind);
            int totalCost = bucket.size() + unindexed.size();
            if (totalCost == 0) {
                return Lists.immutable.empty();
            }
            if (totalCost < bestCost) {
                primary = bucket;
                secondary = unindexed;
                bestCost = totalCost;
            }
        }

        List<R> results = new ArrayList<>();
        if (secondary.isEmpty()) {
            for (RecipeLookupIndex.Entry<R> entry : primary) {
                if (matchesEntry(entry)) {
                    results.add(entry.recipe());
                }
            }
        } else {
            BitSet seen = new BitSet();
            for (RecipeLookupIndex.Entry<R> entry : primary) {
                seen.set(entry.ordinal());
                if (matchesEntry(entry)) {
                    results.add(entry.recipe());
                }
            }
            for (RecipeLookupIndex.Entry<R> entry : secondary) {
                if (!seen.get(entry.ordinal()) && matchesEntry(entry)) {
                    results.add(entry.recipe());
                }
            }
        }
        return Lists.immutable.withAll(results);
    }

    private boolean matchesEntry(RecipeLookupIndex.Entry<R> entry) {
        if (requiredId != null && !Objects.equals(requiredId, entry.id())) {
            return false;
        }
        for (StackCriterion criterion : outputCriteria) {
            if (!matchesAnyOutput(entry.outputs(), criterion.stack, criterion.strategy)) {
                return false;
            }
        }
        for (StackCriterion criterion : inputCriteria) {
            if (!matchesAnyInput(entry.inputs(), criterion.stack, criterion.strategy)) {
                return false;
            }
        }
        for (Predicate<? super R> predicate : predicates) {
            if (!predicate.test(entry.recipe())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAnyInput(
        List<InputContent> inputs,
        ContentStack<?, ?> query,
        MatchStrategy strategy
    ) {
        for (InputContent input : inputs) {
            if (RecipeLookupIndex.matchesInput(input, query, strategy)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyOutput(
        List<OutputContent> outputs,
        ContentStack<?, ?> query,
        MatchStrategy strategy
    ) {
        for (OutputContent output : outputs) {
            if (RecipeLookupIndex.matchesOutput(output, query, strategy)) {
                return true;
            }
        }
        return false;
    }

    private record StackCriterion(ContentStack<?, ?> stack, MatchStrategy strategy) {}
}
