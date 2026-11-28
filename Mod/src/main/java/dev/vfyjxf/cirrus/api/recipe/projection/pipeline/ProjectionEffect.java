package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributeKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.TagQuery;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Unified GAS-inspired GameplayEffect for recipe projection.
 *
 * <p>Replaces the legacy split between {@code ProjectionTransformer}, {@code ContentTransformer},
 * and {@code ResolvePolicy} with one composable abstraction combining:
 * <ul>
 *     <li>Deterministic {@link ProjectionStage} + {@code priority} ordering</li>
 *     <li>Declarative {@link TagQuery} matching on recipe and/or slot tags</li>
 *     <li>Optional {@link ProjectionGuard}s (application requirements)</li>
 *     <li>Explicit {@link ExecutionContextKey} dependencies ({@link #watchedContextKeys()})</li>
 *     <li>Structured, address-preserving {@link ProjectionTransaction} mutations</li>
 * </ul>
 */
public final class ProjectionEffect {

    private final Namespace id;
    private final ProjectionPlugin plugin;
    private final ProjectionStage stage;
    private final int priority;
    private final TagQuery recipeTagQuery;
    private final List<ProjectionGuard> guards;
    private final Set<ExecutionContextKey<?>> watchedContextKeys;
    private final @Nullable BiPredicate<ExecutionContext, ProjectionView> matcher;
    private final Consumer<ProjectionTransaction> applicator;

    private ProjectionEffect(
        Namespace id,
        ProjectionPlugin plugin,
        ProjectionStage stage,
        int priority,
        TagQuery recipeTagQuery,
        List<ProjectionGuard> guards,
        Set<ExecutionContextKey<?>> watchedContextKeys,
        @Nullable BiPredicate<ExecutionContext, ProjectionView> matcher,
        Consumer<ProjectionTransaction> applicator
    ) {
        this.id = Checks.checkNotNull(id, "id");
        this.plugin = Checks.checkNotNull(plugin, "plugin");
        this.stage = Checks.checkNotNull(stage, "stage");
        this.priority = priority;
        this.recipeTagQuery = Checks.checkNotNull(recipeTagQuery, "recipeTagQuery");
        this.guards = List.copyOf(guards);
        this.watchedContextKeys = Collections.unmodifiableSet(new LinkedHashSet<>(watchedContextKeys));
        this.matcher = matcher;
        this.applicator = Checks.checkNotNull(applicator, "applicator");
    }

    public static Builder builder(Namespace id) {
        return new Builder(id);
    }

    public Namespace id() {
        return id;
    }

    public ProjectionPlugin plugin() {
        return plugin;
    }

    public ProjectionStage stage() {
        return stage;
    }

    public int priority() {
        return priority;
    }

    public TagQuery recipeTagQuery() {
        return recipeTagQuery;
    }

    public List<ProjectionGuard> guards() {
        return guards;
    }

    public Set<ExecutionContextKey<?>> watchedContextKeys() {
        return watchedContextKeys;
    }

    public boolean matches(ExecutionContext context, ProjectionView view) {
        if (!recipeTagQuery.isUnconstrained() && !view.matchesTags(recipeTagQuery)) {
            return false;
        }
        return matcher == null || matcher.test(context, view);
    }

    public void apply(ProjectionTransaction tx) {
        applicator.accept(tx);
    }

    /**
     * Composes this effect with an additional operation running immediately after it under the same
     * identity, guards, and match criteria.
     */
    public ProjectionEffect andThen(Consumer<ProjectionTransaction> next) {
        Checks.checkNotNull(next, "next");
        return new ProjectionEffect(
            id,
            plugin,
            stage,
            priority,
            recipeTagQuery,
            guards,
            watchedContextKeys,
            matcher,
            applicator.andThen(next)
        );
    }

    @Override
    public String toString() {
        return "ProjectionEffect{" + "id=" + id + ", stage=" + stage + ", priority=" + priority + '}';
    }

    @FunctionalInterface
    public interface SlotAttributeHandler<V> {
        void accept(ProjectionTransaction tx, ProjectionSlot<? extends RecipeContent> slot, V attributeValue);
    }

    public static final class Builder {
        private final Namespace id;
        private ProjectionPlugin plugin = ProjectionPlugin.builtin;
        private ProjectionStage stage = ProjectionStage.transform;
        private int priority = 0;
        private TagQuery recipeTagQuery = TagQuery.any();
        private final List<ProjectionGuard> guards = new ArrayList<>();
        private final Set<ExecutionContextKey<?>> watchedContextKeys = new LinkedHashSet<>();
        private @Nullable BiPredicate<ExecutionContext, ProjectionView> matcher;
        private Consumer<ProjectionTransaction> applicator = tx -> {};

        private Builder(Namespace id) {
            this.id = Checks.checkNotNull(id, "id");
        }

        public Builder plugin(ProjectionPlugin plugin) {
            this.plugin = Checks.checkNotNull(plugin, "plugin");
            return this;
        }

        public Builder stage(ProjectionStage stage) {
            this.stage = Checks.checkNotNull(stage, "stage");
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder recipeTags(TagQuery query) {
            this.recipeTagQuery = Checks.checkNotNull(query, "query");
            return this;
        }

        public Builder requireTags(Namespace... tags) {
            this.recipeTagQuery = this.recipeTagQuery.andRequireAll(tags);
            return this;
        }

        public Builder requireAnyTag(Namespace... tags) {
            this.recipeTagQuery = this.recipeTagQuery.andRequireAny(tags);
            return this;
        }

        public Builder forbidTags(Namespace... tags) {
            this.recipeTagQuery = this.recipeTagQuery.andForbid(tags);
            return this;
        }

        public Builder guard(ProjectionGuard guard) {
            guards.add(Checks.checkNotNull(guard, "guard"));
            return this;
        }

        public Builder guards(Iterable<ProjectionGuard> extraGuards) {
            for (ProjectionGuard guard : extraGuards) {
                guards.add(Checks.checkNotNull(guard, "guard"));
            }
            return this;
        }

        public Builder watches(ExecutionContextKey<?>... keys) {
            for (ExecutionContextKey<?> key : keys) {
                watchedContextKeys.add(Checks.checkNotNull(key, "key"));
            }
            return this;
        }

        public Builder when(BiPredicate<ExecutionContext, ProjectionView> predicate) {
            Checks.checkNotNull(predicate, "predicate");
            BiPredicate<ExecutionContext, ProjectionView> current = this.matcher;
            this.matcher = current == null ? predicate : current.and(predicate);
            return this;
        }

        /**
         * Composes a recipe-level transaction operation.
         */
        public Builder apply(Consumer<ProjectionTransaction> operation) {
            Checks.checkNotNull(operation, "operation");
            this.applicator = this.applicator.andThen(operation);
            return this;
        }

        /**
         * Ergonomic GAS slot matcher: iterates a snapshot of slots on {@code channel} matching
         * {@code slotTags} (or all channels if {@code channel == null}), and automatically adds a
         * fast pre-check so the effect only triggers when at least one matching slot exists.
         */
        public Builder forEachSlot(
            @Nullable SlotChannel channel,
            TagQuery slotTags,
            BiConsumer<ProjectionTransaction, ProjectionSlot<? extends RecipeContent>> handler
        ) {
            Checks.checkNotNull(slotTags, "slotTags");
            Checks.checkNotNull(handler, "handler");
            when((ctx, view) -> hasMatchingSlot(view, channel, slotTags));
            apply(tx -> {
                for (ProjectionSlot<? extends RecipeContent> slot : snapshotSlots(tx.view(), channel)) {
                    if (slot.tags().matches(slotTags)) {
                        handler.accept(tx, slot);
                    }
                }
            });
            return this;
        }

        /**
         * Ergonomic attribute-driven slot matcher: triggers only if at least one slot on
         * {@code channel} carries {@code key}, and invokes {@code handler} with the typed value.
         */
        public <V> Builder forEachSlotAttribute(
            @Nullable SlotChannel channel,
            ProjectionAttributeKey<V> key,
            SlotAttributeHandler<V> handler
        ) {
            Checks.checkNotNull(key, "key");
            Checks.checkNotNull(handler, "handler");
            when((ctx, view) -> hasSlotAttribute(view, channel, key));
            apply(tx -> {
                for (ProjectionSlot<? extends RecipeContent> slot : snapshotSlots(tx.view(), channel)) {
                    V value = slot.attribute(key);
                    if (value != null) {
                        handler.accept(tx, slot, value);
                    }
                }
            });
            return this;
        }

        public ProjectionEffect build() {
            return new ProjectionEffect(
                id,
                plugin,
                stage,
                priority,
                recipeTagQuery,
                guards,
                watchedContextKeys,
                matcher,
                applicator
            );
        }

        private static boolean hasMatchingSlot(
            ProjectionView view,
            @Nullable SlotChannel channel,
            TagQuery slotTags
        ) {
            if (channel == null || channel == SlotChannel.input) {
                for (ProjectionSlot<?> slot : view.inputSlots()) {
                    if (slot.tags().matches(slotTags)) {
                        return true;
                    }
                }
            }
            if (channel == null || channel == SlotChannel.output) {
                for (ProjectionSlot<?> slot : view.outputSlots()) {
                    if (slot.tags().matches(slotTags)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean hasSlotAttribute(
            ProjectionView view,
            @Nullable SlotChannel channel,
            ProjectionAttributeKey<?> key
        ) {
            if (channel == null || channel == SlotChannel.input) {
                for (ProjectionSlot<?> slot : view.inputSlots()) {
                    if (slot.attributes().has(key)) {
                        return true;
                    }
                }
            }
            if (channel == null || channel == SlotChannel.output) {
                for (ProjectionSlot<?> slot : view.outputSlots()) {
                    if (slot.attributes().has(key)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static List<ProjectionSlot<? extends RecipeContent>> snapshotSlots(
            ProjectionView view,
            @Nullable SlotChannel channel
        ) {
            List<ProjectionSlot<? extends RecipeContent>> snapshot = new ArrayList<>();
            if (channel == null || channel == SlotChannel.input) {
                snapshot.addAll(view.inputSlots());
            }
            if (channel == null || channel == SlotChannel.output) {
                snapshot.addAll(view.outputSlots());
            }
            return snapshot;
        }
    }
}
