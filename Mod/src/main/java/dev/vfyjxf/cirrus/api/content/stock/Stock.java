package dev.vfyjxf.cirrus.api.content.stock;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Mutable inventory pool that tracks available resources during planning.
 *
 * <p>{@code Stock} manages two parallel pools:
 * <ul>
 *     <li><b>Stack pool</b> — {@link ContentStack}-based items matched by
 *         identity and component (via {@link MatchStrategy#identityAndComponent}).</li>
 *     <li><b>Value pool</b> — {@link ContentValue}-based abstract quantities
 *         matched solely by their type (e.g. energy, experience).</li>
 * </ul>
 *
 * <p>During planning, a {@code Stock} forms the primary consumable resource layer.
 */
public class Stock {

    private final MutableList<ContentStack<?, ?>> stacks = Lists.mutable.empty();
    private final Object2LongMap<ComponentStackKey> stackAmounts = new Object2LongOpenHashMap<>();
    private final Object2LongMap<ContentValue<?>> valueAmounts = new Object2LongOpenHashMap<>();
    private final Object2LongMap<ValueType<?>> valueTypeAmounts = new Object2LongOpenHashMap<>();
    private final ArrayList<TransactionScope> transactionScopes = new ArrayList<>();
    private @Nullable Object2LongMap<ContentKind<?>> kindAmounts;
    private boolean kindAmountsDirty = true;

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

        // ComponentStackKey equality is exactly identityAndComponent: a map hit is equivalent to
        // (and O(1) instead of) the previous linear scan over the stack list.
        ComponentStackKey key = normalized.componentKey();
        if (stackAmounts.containsKey(key)) {
            long current = stackAmounts.getOrDefault(key, 0L);
            recordStackAmount(key);
            stackAmounts.put(key, current + stack.amount());
            invalidateKindAmounts();
            return;
        }

        recordStackList();
        recordStackAmount(key);
        stacks.add(normalized);
        stackAmounts.put(key, stack.amount());
        invalidateKindAmounts();
    }

    /**
     * Adds an abstract value resource to this stock.
     *
     * @param value the value to add (amounts accumulate by type)
     */
    public void addValue(ContentValue<?> value) {
        long current = valueAmounts.getOrDefault(value, 0L);
        recordValueAmount(value);
        valueAmounts.put(value, current + value.value());
        valueTypeAmounts.put(value.type(), valueTypeAmounts.getLong(value.type()) + value.value());
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

        ComponentStackKey key = normalized.componentKey();
        long available = stackAmounts.getOrDefault(key, 0L);
        long consumed = Math.min(available, amount);
        if (consumed > 0) {
            recordStackAmount(key);
            stackAmounts.put(key, available - consumed);
            invalidateKindAmounts();
        }
        return consumed;
    }

    /**
     * Consumes up to {@code amount} of a matching value from this stock.
     *
     * @param value  the value type to match
     * @param amount the maximum amount to consume
     * @return the amount actually consumed
     */
    public long consumeValue(ContentValue<?> value, long amount) {
        return consumeValueType(value.type(), amount);
    }

    public long consumeValueType(ValueType<?> type, long amount) {
        if (amount <= 0) return 0;
        long availableByType = valueTypeAmounts.getLong(type);
        if (availableByType <= 0L) {
            return 0L;
        }
        for (var entry : valueAmounts.object2LongEntrySet()) {
            ContentValue<?> value = entry.getKey();
            if (value.type() == type) {
                long available = entry.getLongValue();
                long consumed = Math.min(available, amount);
                if (consumed > 0) {
                    recordValueAmount(value);
                    entry.setValue(available - consumed);
                    valueTypeAmounts.put(type, availableByType - consumed);
                }
                return consumed;
            }
        }
        return 0;
    }

    public @Nullable ConsumedResource consumeValueTypeDetailed(ValueType<?> type, long amount) {
        if (amount <= 0) return null;
        long availableByType = valueTypeAmounts.getLong(type);
        if (availableByType <= 0L) {
            return null;
        }
        for (var entry : valueAmounts.object2LongEntrySet()) {
            ContentValue<?> value = entry.getKey();
            if (value.type() != type) {
                continue;
            }
            long available = entry.getLongValue();
            long consumed = Math.min(available, amount);
            if (consumed <= 0L) {
                return null;
            }
            recordValueAmount(value);
            entry.setValue(available - consumed);
            valueTypeAmounts.put(type, availableByType - consumed);
            return new ConsumedResource(value.normalize(), consumed);
        }
        return null;
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
        return countMatchingNormalized(stack.normalize());
    }

    public long countMatchingNormalized(ContentStack<?, ?> normalized) {
        if (normalized.isEmpty()) return 0;
        return stackAmounts.getOrDefault(normalized.componentKey(), 0L);
    }

    /**
     * Returns the available amount of a matching value in this stock.
     *
     * @param value the value type to query
     * @return the available amount, or 0 if not found
     */
    public long countMatching(ContentValue<?> value) {
        return countMatchingValueType(value.type());
    }

    public long countMatchingValueType(ValueType<?> type) {
        return valueTypeAmounts.getLong(type);
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
        return kindAmounts().getLong(kind);
    }

    private Object2LongMap<ContentKind<?>> kindAmounts() {
        Object2LongMap<ContentKind<?>> cached = kindAmounts;
        if (cached == null) {
            cached = new Object2LongOpenHashMap<>();
            kindAmounts = cached;
            kindAmountsDirty = true;
        }
        if (kindAmountsDirty) {
            rebuildKindAmounts(cached);
        }
        return cached;
    }

    private void rebuildKindAmounts(Object2LongMap<ContentKind<?>> cached) {
        cached.clear();
        for (ContentStack<?, ?> existing : stacks) {
            long amount = stackAmounts.getOrDefault(existing.componentKey(), 0L);
            if (amount != 0L) {
                ContentKind<?> kind = existing.kind();
                cached.put(kind, safeAdd(cached.getLong(kind), amount));
            }
        }
        kindAmountsDirty = false;
    }

    private void invalidateKindAmounts() {
        kindAmountsDirty = true;
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
     * Estimates how much of an input can be satisfied by the current stock without consuming it.
     *
     * <p>For enumerable inputs this sums the availability of every enumerated variant. The result is
     * used only for candidate ordering, so an approximate upper bound is acceptable even if variants
     * overlap semantically.
     */
    public long countMatchingInput(InputContent input) {
        if (input.isEmpty()) {
            return 0L;
        }
        if (input.isEnumerable()) {
            long total = 0L;
            for (ContentVariant variant : input.enumerate()) {
                total = safeAdd(total, countMatchingVariant(variant));
            }
            return total;
        }

        long total = 0L;
        for (ContentStack<?, ?> existing : stacks) {
            long available = stackAmounts.getOrDefault(existing.componentKey(), 0L);
            if (available > 0L && input.satisfy(existing)) {
                total = safeAdd(total, available);
            }
        }
        for (var entry : valueAmounts.object2LongEntrySet()) {
            long available = entry.getLongValue();
            if (available > 0L && input.satisfy(entry.getKey())) {
                total = safeAdd(total, available);
            }
        }
        return total;
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
                ComponentStackKey key = existing.componentKey();
                long available = stackAmounts.getOrDefault(key, 0L);
                long consumed = Math.min(available, remaining);
                if (consumed > 0) {
                    recordStackAmount(key);
                    stackAmounts.put(key, available - consumed);
                    invalidateKindAmounts();
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

    /**
     * Consumes up to {@code amount} for the given variant and returns the concrete resources
     * actually taken from stock.
     *
     * <p>This is used when a tag/kind/list demand is resolved against stock: callers need both
     * the total consumed amount and the exact concrete resources.
     */
    public MutableList<ConsumedResource> consumeVariantDetailed(ContentVariant variant, long amount) {
        MutableList<ConsumedResource> consumed = Lists.mutable.empty();
        if (amount <= 0) {
            return consumed;
        }

        switch (variant) {
            case ContentStack<?, ?> stack -> {
                long taken = consume(stack, amount);
                if (taken > 0) {
                    consumed.add(new ConsumedResource(stack.normalize(), taken));
                }
            }
            case ContentValue<?> value -> {
                long taken = consumeValue(value, amount);
                if (taken > 0) {
                    consumed.add(new ConsumedResource(value.normalize(), taken));
                }
            }
            case ContentKind<?> kind -> {
                long remaining = amount;
                for (ContentStack<?, ?> existing : stacks) {
                    if (remaining <= 0) {
                        break;
                    }
                    if (!existing.matchesContent(kind)) {
                        continue;
                    }
                    ComponentStackKey key = existing.componentKey();
                    long available = stackAmounts.getOrDefault(key, 0L);
                    long taken = Math.min(available, remaining);
                    if (taken <= 0) {
                        continue;
                    }
                    recordStackAmount(key);
                    stackAmounts.put(key, available - taken);
                    invalidateKindAmounts();
                    consumed.add(new ConsumedResource(existing.normalize(), taken));
                    remaining -= taken;
                }
            }
            case ContentResource resource -> {
                long taken = consumeResource(resource, amount);
                if (taken > 0) {
                    consumed.add(new ConsumedResource(resource.normalize(), taken));
                }
            }
        }
        return consumed;
    }

    /**
     * Consumes up to {@code amount} of concrete resources that satisfy the given input and
     * returns the exact resources actually removed from stock.
     *
     * <p>This path is used for non-enumerable inputs such as some {@code ComplexContent}
     * implementations where the planner cannot iterate all possible variants but can still
     * test concrete stock entries via {@link InputContent#satisfy(ContentVariant)}.
     */
    public MutableList<ConsumedResource> consumeMatchingDetailed(InputContent input, long amount) {
        MutableList<ConsumedResource> consumed = Lists.mutable.empty();
        if (amount <= 0) {
            return consumed;
        }

        long remaining = amount;
        for (ContentStack<?, ?> existing : stacks) {
            if (remaining <= 0) {
                break;
            }
            ComponentStackKey key = existing.componentKey();
            long available = stackAmounts.getOrDefault(key, 0L);
            if (available <= 0 || !input.satisfy(existing)) {
                continue;
            }
            long taken = Math.min(available, remaining);
            recordStackAmount(key);
            stackAmounts.put(key, available - taken);
            invalidateKindAmounts();
            consumed.add(new ConsumedResource(existing.normalize(), taken));
            remaining -= taken;
        }

        if (remaining > 0) {
            for (var entry : valueAmounts.object2LongEntrySet()) {
                if (remaining <= 0) {
                    break;
                }
                ContentValue<?> value = entry.getKey();
                long available = entry.getLongValue();
                if (available <= 0 || !input.satisfy(value)) {
                    continue;
                }
                long taken = Math.min(available, remaining);
                recordValueAmount(value);
                entry.setValue(available - taken);
                consumed.add(new ConsumedResource(value, taken));
                remaining -= taken;
            }
        }

        return consumed;
    }

    /**
     * Consumes up to {@code amount} of concrete resources that satisfy the given input and
     * returns the exact resources actually removed from stock.
     *
     * <p>This is the stock-only counterpart of planner demand resolution. Enumerable inputs are
     * consumed variant-by-variant in enumeration order; non-enumerable inputs fall back to
     * {@link #consumeMatchingDetailed(InputContent, long)}.
     */
    public MutableList<ConsumedResource> consumeInputDetailed(InputContent input, long amount) {
        MutableList<ConsumedResource> consumed = Lists.mutable.empty();
        if (input.isEmpty() || amount <= 0L) {
            return consumed;
        }
        if (!input.isEnumerable()) {
            consumed.addAllIterable(consumeMatchingDetailed(input, amount));
            return consumed;
        }

        long remaining = amount;
        for (ContentVariant variant : input.enumerate()) {
            if (remaining <= 0L) {
                break;
            }
            MutableList<ConsumedResource> fromVariant = consumeVariantDetailed(variant, remaining);
            consumed.addAllIterable(fromVariant);
            for (ConsumedResource resource : fromVariant) {
                remaining -= resource.amount();
            }
        }
        return consumed;
    }

    /**
     * Consumes up to {@code amount} satisfying the given input and returns the total amount taken.
     */
    public long consumeInput(InputContent input, long amount) {
        long consumed = 0L;
        for (ConsumedResource resource : consumeInputDetailed(input, amount)) {
            consumed = safeAdd(consumed, resource.amount());
        }
        return consumed;
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

    /**
     * Returns a concrete snapshot of all currently available resources.
     *
     * <p>Each entry is normalized and carries the total amount currently stored
     * for that exact resource identity.
     */
    public MutableList<ConsumedResource> snapshotResources() {
        MutableList<ConsumedResource> resources = Lists.mutable.empty();
        for (ContentStack<?, ?> existing : stacks) {
            long amount = stackAmounts.getOrDefault(existing.componentKey(), 0L);
            if (amount > 0L) {
                resources.add(new ConsumedResource(existing.normalize(), amount));
            }
        }
        for (var entry : valueAmounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount > 0L) {
                resources.add(new ConsumedResource(entry.getKey().normalize(), amount));
            }
        }
        return resources;
    }

    /** Creates a deep copy of this stock (both stack and value pools are cloned). */
    public Stock copy() {
        Stock copy = new Stock();
        copy.stacks.addAll(this.stacks);
        copy.stackAmounts.putAll(this.stackAmounts);
        copy.valueAmounts.putAll(this.valueAmounts);
        copy.valueTypeAmounts.putAll(this.valueTypeAmounts);
        return copy;
    }

    public void restoreFrom(Stock snapshot) {
        recordStackList();
        recordAllStackAmounts();
        recordAllValueAmounts();
        for (var entry : snapshot.stackAmounts.object2LongEntrySet()) {
            recordStackAmount(entry.getKey());
        }
        for (var entry : snapshot.valueAmounts.object2LongEntrySet()) {
            recordValueAmount(entry.getKey());
        }
        stackAmounts.clear();
        valueAmounts.clear();
        valueTypeAmounts.clear();
        stacks.clear();
        stacks.addAll(snapshot.stacks);
        stackAmounts.putAll(snapshot.stackAmounts);
        valueAmounts.putAll(snapshot.valueAmounts);
        valueTypeAmounts.putAll(snapshot.valueTypeAmounts);
        invalidateKindAmounts();
    }

    /** Removes all resources from this stock. */
    public void clear() {
        recordStackList();
        recordAllStackAmounts();
        recordAllValueAmounts();
        stacks.clear();
        stackAmounts.clear();
        valueAmounts.clear();
        valueTypeAmounts.clear();
        invalidateKindAmounts();
    }

    /** Returns {@code true} if this stock contains no resources of any kind. */
    public boolean isEmpty() {
        return stacks.isEmpty() && valueAmounts.isEmpty() && valueTypeAmounts.isEmpty();
    }

    public Mark mark() {
        TransactionScope scope = new TransactionScope();
        transactionScopes.add(scope);
        return new Mark(scope);
    }

    public void rollback(Mark mark) {
        TransactionScope scope = popScope(mark);
        for (int i = scope.changes.size() - 1; i >= 0; i--) {
            scope.changes.get(i).rollback(this);
        }
    }

    public void commit(Mark mark) {
        TransactionScope scope = popScope(mark);
        if (!transactionScopes.isEmpty()) {
            mergeIntoParent(scope, transactionScopes.get(transactionScopes.size() - 1));
        }
    }

    private TransactionScope popScope(Mark mark) {
        if (mark == null || mark.closed) {
            throw new IllegalStateException("stock transaction mark is not active");
        }
        if (transactionScopes.isEmpty() || transactionScopes.get(transactionScopes.size() - 1) != mark.scope) {
            throw new IllegalStateException("stock transaction marks must be closed in LIFO order");
        }
        mark.closed = true;
        return transactionScopes.remove(transactionScopes.size() - 1);
    }

    private void mergeIntoParent(TransactionScope child, TransactionScope parent) {
        parent.changes.addAll(child.changes);
    }

    private void recordStackAmount(ComponentStackKey key) {
        if (transactionScopes.isEmpty()) {
            return;
        }
        TransactionScope scope = transactionScopes.get(transactionScopes.size() - 1);
        if (scope.addStackAmountKey(key)) {
            boolean existed = stackAmounts.containsKey(key);
            scope.changes.add(new StackAmountChange(key, existed, existed ? stackAmounts.getLong(key) : 0L));
        }
    }

    private void recordValueAmount(ContentValue<?> key) {
        if (transactionScopes.isEmpty()) {
            return;
        }
        TransactionScope scope = transactionScopes.get(transactionScopes.size() - 1);
        if (scope.addValueAmountKey(key)) {
            boolean existed = valueAmounts.containsKey(key);
            scope.changes.add(
                new ValueAmountChange(
                    key,
                    existed,
                    existed ? valueAmounts.getLong(key) : 0L,
                    valueTypeAmounts.getLong(key.type())
                )
            );
        }
    }

    private void recordStackList() {
        if (transactionScopes.isEmpty()) {
            return;
        }
        TransactionScope scope = transactionScopes.get(transactionScopes.size() - 1);
        if (!scope.stackListChanged) {
            scope.stackListChanged = true;
            scope.changes.add(new StackListChange(Lists.mutable.ofAll(stacks)));
        }
    }

    private void recordAllStackAmounts() {
        for (var entry : stackAmounts.object2LongEntrySet()) {
            recordStackAmount(entry.getKey());
        }
    }

    private void recordAllValueAmounts() {
        for (var entry : valueAmounts.object2LongEntrySet()) {
            recordValueAmount(entry.getKey());
        }
    }

    public record ConsumedResource(ContentResource resource, long amount) {}

    public static final class Mark {
        private final TransactionScope scope;
        private boolean closed;

        private Mark(TransactionScope scope) {
            this.scope = scope;
        }
    }

    private static final class TransactionScope {
        private static final int smallKeySetLimit = 8;
        private final ArrayList<StockChange> changes = new ArrayList<>();
        private Object @Nullable [] smallStackAmountKeys;
        private int smallStackAmountKeyCount;
        private Object @Nullable [] smallValueAmountKeys;
        private int smallValueAmountKeyCount;
        private @Nullable HashSet<ComponentStackKey> stackAmountKeys;
        private @Nullable HashSet<ContentValue<?>> valueAmountKeys;
        private boolean stackListChanged;

        private boolean addStackAmountKey(ComponentStackKey key) {
            HashSet<ComponentStackKey> keys = stackAmountKeys;
            if (keys != null) {
                return keys.add(key);
            }
            Object[] smallKeys = smallStackAmountKeys;
            if (smallKeys != null) {
                for (int i = 0; i < smallStackAmountKeyCount; i++) {
                    Object existing = smallKeys[i];
                    if (existing.equals(key)) {
                        return false;
                    }
                }
            }
            if (smallStackAmountKeyCount < smallKeySetLimit) {
                smallStackAmountKeys = appendSmallKey(smallKeys, smallStackAmountKeyCount++, key);
                return true;
            }
            keys = new HashSet<>(smallKeySetLimit * 2);
            if (smallKeys != null) {
                for (int i = 0; i < smallStackAmountKeyCount; i++) {
                    keys.add((ComponentStackKey) smallKeys[i]);
                }
            }
            smallStackAmountKeys = null;
            stackAmountKeys = keys;
            return keys.add(key);
        }

        private boolean addValueAmountKey(ContentValue<?> key) {
            HashSet<ContentValue<?>> keys = valueAmountKeys;
            if (keys != null) {
                return keys.add(key);
            }
            Object[] smallKeys = smallValueAmountKeys;
            if (smallKeys != null) {
                for (int i = 0; i < smallValueAmountKeyCount; i++) {
                    Object existing = smallKeys[i];
                    if (existing.equals(key)) {
                        return false;
                    }
                }
            }
            if (smallValueAmountKeyCount < smallKeySetLimit) {
                smallValueAmountKeys = appendSmallKey(smallKeys, smallValueAmountKeyCount++, key);
                return true;
            }
            keys = new HashSet<>(smallKeySetLimit * 2);
            if (smallKeys != null) {
                for (int i = 0; i < smallValueAmountKeyCount; i++) {
                    keys.add((ContentValue<?>) smallKeys[i]);
                }
            }
            smallValueAmountKeys = null;
            valueAmountKeys = keys;
            return keys.add(key);
        }

        private static Object[] appendSmallKey(Object @Nullable [] keys, int index, Object key) {
            if (keys == null) {
                keys = new Object[2];
            } else if (index >= keys.length) {
                keys = Arrays.copyOf(keys, Math.min(keys.length * 2, smallKeySetLimit));
            }
            keys[index] = key;
            return keys;
        }
    }

    private sealed interface StockChange permits StackAmountChange, ValueAmountChange, StackListChange {
        void rollback(Stock stock);
    }

    private record StackAmountChange(ComponentStackKey key, boolean existed, long oldValue) implements StockChange {
        @Override
        public void rollback(Stock stock) {
            if (existed) {
                stock.stackAmounts.put(key, oldValue);
            } else {
                stock.stackAmounts.removeLong(key);
            }
            stock.invalidateKindAmounts();
        }
    }

    private record ValueAmountChange(ContentValue<?> key, boolean existed, long oldValue, long oldTypeValue)
            implements
                StockChange {
        @Override
        public void rollback(Stock stock) {
            if (existed) {
                stock.valueAmounts.put(key, oldValue);
            } else {
                stock.valueAmounts.removeLong(key);
            }
            if (oldTypeValue > 0L) {
                stock.valueTypeAmounts.put(key.type(), oldTypeValue);
            } else {
                stock.valueTypeAmounts.removeLong(key.type());
            }
        }
    }

    private record StackListChange(MutableList<ContentStack<?, ?>> oldStacks) implements StockChange {
        @Override
        public void rollback(Stock stock) {
            stock.stacks.clear();
            stock.stacks.addAll(oldStacks);
            stock.invalidateKindAmounts();
        }
    }

    private static long safeAdd(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        if (second < 0L && first < Long.MIN_VALUE - second) {
            return Long.MIN_VALUE;
        }
        return first + second;
    }

}
