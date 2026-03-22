package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

/**
 * Mutable inventory pool that tracks available resources for the routing engine.
 *
 * <p>{@code Stock} manages two parallel pools:
 * <ul>
 *     <li><b>Stack pool</b> — {@link ContentStack}-based items matched by
 *         identity and component (via {@link MatchStrategy#identityAndComponent}).</li>
 *     <li><b>Value pool</b> — {@link ContentValue}-based abstract quantities
 *         matched solely by their type (e.g. energy, experience).</li>
 * </ul>
 *
 * <p>During planning, the {@link dev.vfyjxf.cirrus.api.recipe.routing.state.ResourceLedger}
 * wraps a {@code Stock} to form the primary consumable resource layer.
 *
 * @see dev.vfyjxf.cirrus.api.recipe.routing.state.ResourceLedger
 */
public class Stock {

    private final MutableList<ContentStack<?, ?>> stacks = Lists.mutable.empty();
    private final Object2LongMap<ContentStack<?, ?>> stackAmounts = new Object2LongOpenHashMap<>();
    private final Object2LongMap<ContentValue<?>> valueAmounts = new Object2LongOpenHashMap<>();

    /** Creates an empty stock with no resources. */
    public static Stock empty() {
        return new Stock();
    }

    /**
     * Creates a stock pre-populated with the given stacks.
     *
     * @param stacks initial content stacks to add
     * @return a new populated stock
     */
    public static Stock of(Iterable<? extends ContentStack<?, ?>> stacks) {
        Stock stock = new Stock();
        for (ContentStack<?, ?> stack : stacks) {
            stock.add(stack);
        }
        return stock;
    }

    /**
     * Adds a content stack to this stock.
     *
     * <p>If an existing stack with the same identity is already present,
     * the amount is accumulated rather than creating a duplicate entry.
     *
     * @param stack the stack to add (ignored if empty)
     */
    public void add(ContentStack<?, ?> stack) {
        if (stack.isEmpty()) return;
        ContentStack<?, ?> normalized = stack.normalize();

        for (ContentStack<?, ?> existing : stacks) {
            if (existing.matches(normalized, MatchStrategy.identityAndComponent)) {
                long current = stackAmounts.getOrDefault(existing, 0L);
                stackAmounts.put(existing, current + stack.amount());
                return;
            }
        }

        stacks.add(normalized);
        stackAmounts.put(normalized, stack.amount());
    }

    /**
     * Adds an abstract value resource to this stock.
     *
     * @param value the value to add (amounts accumulate by type)
     */
    public void addValue(ContentValue<?> value) {
        long current = valueAmounts.getOrDefault(value, 0L);
        valueAmounts.put(value, current + value.value());
    }

    /**
     * Adds a {@link ContentResource} to this stock, dispatching to
     * {@link #add(ContentStack)} or {@link #addValue(ContentValue)} as appropriate.
     *
     * @param resource the resource to add
     */
    public void addResource(ContentResource resource) {
        if (resource instanceof ContentStack<?, ?> stack) {
            add(stack);
        } else if (resource instanceof ContentValue<?> value) {
            addValue(value);
        }
    }

    /**
     * Consumes up to {@code amount} of a matching stack from this stock.
     *
     * @param stack  the stack to match against (by identity and component)
     * @param amount the maximum amount to consume
     * @return the amount actually consumed (may be less than requested)
     */
    public long consume(ContentStack<?, ?> stack, long amount) {
        if (stack.isEmpty() || amount <= 0) return 0;
        ContentStack<?, ?> normalized = stack.normalize();

        for (ContentStack<?, ?> existing : stacks) {
            if (existing.matches(normalized, MatchStrategy.identityAndComponent)) {
                long available = stackAmounts.getOrDefault(existing, 0L);
                long consumed = Math.min(available, amount);
                if (consumed > 0) {
                    stackAmounts.put(existing, available - consumed);
                }
                return consumed;
            }
        }
        return 0;
    }

    /**
     * Consumes up to {@code amount} of a matching value from this stock.
     *
     * @param value  the value type to match
     * @param amount the maximum amount to consume
     * @return the amount actually consumed
     */
    public long consumeValue(ContentValue<?> value, long amount) {
        if (amount <= 0) return 0;

        for (var entry : valueAmounts.object2LongEntrySet()) {
            if (entry.getKey().type() == value.type()) {
                long available = entry.getLongValue();
                long consumed = Math.min(available, amount);
                if (consumed > 0) {
                    entry.setValue(available - consumed);
                }
                return consumed;
            }
        }
        return 0;
    }

    /**
     * Consumes a {@link ContentResource} from this stock, dispatching to
     * {@link #consume(ContentStack, long)} or {@link #consumeValue(ContentValue, long)}.
     *
     * @param resource the resource to consume
     * @param amount   the maximum amount to consume
     * @return the amount actually consumed
     */
    public long consumeResource(ContentResource resource, long amount) {
        if (resource instanceof ContentStack<?, ?> stack) {
            return consume(stack, amount);
        } else if (resource instanceof ContentValue<?> value) {
            return consumeValue(value, amount);
        }
        return 0;
    }

    /**
     * Returns the available amount of a matching stack in this stock
     * without consuming it.
     *
     * @param stack the stack to query
     * @return the available amount, or 0 if not found
     */
    public long countMatching(ContentStack<?, ?> stack) {
        if (stack.isEmpty()) return 0;

        for (ContentStack<?, ?> existing : stacks) {
            if (existing.matches(stack, MatchStrategy.identityAndComponent)) {
                return stackAmounts.getOrDefault(existing, 0L);
            }
        }
        return 0;
    }

    /**
     * Returns the available amount of a matching value in this stock.
     *
     * @param value the value type to query
     * @return the available amount, or 0 if not found
     */
    public long countMatching(ContentValue<?> value) {
        for (var entry : valueAmounts.object2LongEntrySet()) {
            if (entry.getKey().type() == value.type()) {
                return entry.getLongValue();
            }
        }
        return 0;
    }

    /**
     * Returns the available amount of a matching resource, dispatching to the
     * appropriate typed {@code countMatching} method.
     *
     * @param resource the resource to query
     * @return the available amount, or 0 if not found
     */
    public long countMatchingResource(ContentResource resource) {
        if (resource instanceof ContentStack<?, ?> stack) {
            return countMatching(stack);
        } else if (resource instanceof ContentValue<?> value) {
            return countMatching(value);
        }
        return 0;
    }

    /**
     * Returns the total available amount of stacks whose content kind matches.
     *
     * <p>Unlike {@link #countMatching(ContentStack)}, this matches by
     * {@link ContentKind} only — ignoring stack-level identity.
     *
     * @param kind the content kind to match
     * @return the total amount across all matching stacks
     */
    public long countMatchingKind(ContentKind<?> kind) {
        if (kind.isEmpty()) return 0;

        long total = 0;
        for (ContentStack<?, ?> existing : stacks) {
            if (existing.matchesContent(kind)) {
                total += stackAmounts.getOrDefault(existing, 0L);
            }
        }
        return total;
    }

    /**
     * Returns the available amount for any {@link ContentVariant} type,
     * dispatching to the most specific matching method.
     *
     * @param variant the variant to query (stack, value, kind, or resource)
     * @return the available amount
     */
    public long countMatchingVariant(ContentVariant variant) {
        return switch (variant) {
            case ContentStack<?, ?> stack -> countMatching(stack);
            case ContentValue<?> value -> countMatching(value);
            case ContentKind<?> kind -> countMatchingKind(kind);
            case ContentResource resource -> countMatchingResource(resource);
        };
    }

    /**
     * Consumes up to {@code amount} of stacks whose content kind matches,
     * potentially spanning multiple stacks.
     *
     * @param kind   the content kind to match
     * @param amount the maximum total amount to consume
     * @return the amount actually consumed
     */
    public long consumeKind(ContentKind<?> kind, long amount) {
        if (kind.isEmpty() || amount <= 0) return 0;

        long remaining = amount;
        for (ContentStack<?, ?> existing : stacks) {
            if (remaining <= 0) break;
            if (existing.matchesContent(kind)) {
                long available = stackAmounts.getOrDefault(existing, 0L);
                long consumed = Math.min(available, remaining);
                if (consumed > 0) {
                    stackAmounts.put(existing, available - consumed);
                    remaining -= consumed;
                }
            }
        }
        return amount - remaining;
    }

    /**
     * Consumes up to {@code amount} of any {@link ContentVariant} type,
     * dispatching to the most specific consumption method.
     *
     * @param variant the variant to consume (stack, value, kind, or resource)
     * @param amount  the maximum amount to consume
     * @return the amount actually consumed
     */
    public long consumeVariant(ContentVariant variant, long amount) {
        return switch (variant) {
            case ContentStack<?, ?> stack -> consume(stack, amount);
            case ContentValue<?> value -> consumeValue(value, amount);
            case ContentKind<?> kind -> consumeKind(kind, amount);
            case ContentResource resource -> consumeResource(resource, amount);
        };
    }

    /** Returns {@code true} if the stock contains any amount of the given stack. */
    public boolean contains(ContentStack<?, ?> stack) {
        return countMatching(stack) > 0;
    }

    /** Returns {@code true} if the stock contains at least {@code amount} of the given stack. */
    public boolean containsAtLeast(ContentStack<?, ?> stack, long amount) {
        return countMatching(stack) >= amount;
    }

    /** Returns {@code true} if the stock contains any amount of the given resource. */
    public boolean containsResource(ContentResource resource) {
        return countMatchingResource(resource) > 0;
    }

    /** Returns {@code true} if the stock contains at least {@code amount} of the given resource. */
    public boolean containsResourceAtLeast(ContentResource resource, long amount) {
        return countMatchingResource(resource) >= amount;
    }

    /** Returns the underlying mutable stack list. */
    public MutableList<ContentStack<?, ?>> getStacks() {
        return stacks;
    }

    /** Creates a deep copy of this stock (both stack and value pools are cloned). */
    public Stock copy() {
        Stock copy = new Stock();
        copy.stacks.addAll(this.stacks);
        copy.stackAmounts.putAll(this.stackAmounts);
        copy.valueAmounts.putAll(this.valueAmounts);
        return copy;
    }

    /** Removes all resources from this stock. */
    public void clear() {
        stacks.clear();
        stackAmounts.clear();
        valueAmounts.clear();
    }

    /** Returns {@code true} if this stock contains no resources of any kind. */
    public boolean isEmpty() {
        return stacks.isEmpty() && valueAmounts.isEmpty();
    }
}
