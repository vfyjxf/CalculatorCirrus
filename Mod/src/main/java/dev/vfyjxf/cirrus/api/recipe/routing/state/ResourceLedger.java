package dev.vfyjxf.cirrus.api.recipe.routing.state;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cloudlib.util.Checks;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Mutable dual-pool resource tracker used during the planning search.
 *
 * <p>{@code ResourceLedger} manages two resource pools:
 * <ul>
 *     <li><b>Stock pool</b> — the player's initial inventory, wrapped in a
 *         {@link Stock} instance. Resources consumed from stock are recorded
 *         as {@link PlanAction.ConsumeStock}
 *         actions in the plan.</li>
 *     <li><b>Byproduct pool</b> — intermediate outputs produced by recipes
 *         executed during planning. These are available for consumption by
 *         subsequent recipes without generating additional stock-consumption
 *         actions.</li>
 * </ul>
 *
 * <p>The {@link #consumeDemand(InputContent, long, PlanBuilder)} method is the
 * primary demand-resolution entry point: it tries stock first, then byproducts,
 * and reports the remaining unsatisfied amount.
 *
 * @see Stock
 * @see dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder
 */
public final class ResourceLedger {
    private final Stock stock;
    private final Object2LongMap<ContentResource> byproducts;
    private final Object2LongMap<ValueType<?>> valueByproducts;
    private final ArrayList<TransactionScope> transactionScopes = new ArrayList<>();

    public ResourceLedger(Stock stock) {
        this(stock, new Object2LongOpenHashMap<>(), new Object2LongOpenHashMap<>());
    }

    private ResourceLedger(
        Stock stock,
        Object2LongMap<ContentResource> byproducts,
        Object2LongMap<ValueType<?>> valueByproducts
    ) {
        this.stock = Checks.checkNotNull(stock, "stock");
        this.byproducts = byproducts;
        this.valueByproducts = valueByproducts;
    }

    /** Returns the underlying stock pool. */
    public Stock stock() {
        return stock;
    }

    /**
     * Peeks at the available stock amount for the given variant without consuming.
     *
     * @param variant the variant to query
     * @return the available amount in stock
     */
    public long peekStockAvailable(ContentVariant variant) {
        return stock.countMatchingVariant(variant);
    }

    /**
     * Peeks at the available byproduct amount for the given variant without consuming.
     *
     * @param variant the variant to query
     * @return the available amount in byproducts
     */
    public long peekByproductAvailable(ContentVariant variant) {
        return countByproductVariant(variant);
    }

    /**
     * Estimates how much of an input can be satisfied immediately by stock plus byproducts.
     */
    public long peekAvailable(InputContent input) {
        long total = stock.countMatchingInput(input);
        if (input.isEnumerable()) {
            for (ContentVariant variant : input.enumerate()) {
                total = safeAdd(total, countByproductVariant(variant));
            }
            return total;
        }

        var iterator = Object2LongMaps.fastIterator(byproducts);
        while (iterator.hasNext()) {
            Object2LongMap.Entry<ContentResource> entry = iterator.next();
            long available = entry.getLongValue();
            if (available > 0L && input.satisfy(entry.getKey())) {
                total = safeAdd(total, available);
            }
        }
        var valueIterator = Object2LongMaps.fastIterator(valueByproducts);
        while (valueIterator.hasNext()) {
            Object2LongMap.Entry<ValueType<?>> entry = valueIterator.next();
            long available = entry.getLongValue();
            if (available > 0L && input.satisfy(valueResource(entry.getKey(), available))) {
                total = safeAdd(total, available);
            }
        }
        return total;
    }

    /**
     * Consumes up to {@code amount} of a variant from both stock and byproducts,
     * recording stock-consumption actions in the plan builder.
     *
     * <p>Stock is consumed first; any remainder is taken from byproducts.
     *
     * @param variant     the content variant to consume
     * @param amount      the maximum amount to consume
     * @param planBuilder the plan builder to record actions into
     * @return the total amount actually consumed
     */
    public long consumeVariantDemand(InputContent input, ContentVariant variant, long amount, PlanBuilder planBuilder) {
        if (amount <= 0) {
            return 0;
        }
        if (variant instanceof ContentValue<?> value) {
            return consumeValueDemand(input, value.type(), amount, planBuilder);
        }
        long remaining = amount;
        var fromStock = stock.consumeVariantDetailed(variant, remaining);
        for (Stock.ConsumedResource consumed : fromStock) {
            remaining -= consumed.amount();
            planBuilder.addConsumeStock(input, consumed.resource(), consumed.amount());
        }

        if (remaining > 0) {
            long fromByproduct = consumeByproductVariant(variant, remaining);
            remaining -= fromByproduct;
        }
        return amount - remaining;
    }

    public long consumeValueDemand(InputContent input, ValueType<?> type, long amount, PlanBuilder planBuilder) {
        if (amount <= 0L) {
            return 0L;
        }
        long remaining = amount;
        Stock.ConsumedResource consumedValue = stock.consumeValueTypeDetailed(type, remaining);
        if (consumedValue != null) {
            remaining -= consumedValue.amount();
            planBuilder.addConsumeStock(input, consumedValue.resource(), consumedValue.amount());
        }
        if (remaining > 0L) {
            remaining -= consumeByproductValue(type, remaining);
        }
        return amount - remaining;
    }

    /** Creates a deep copy of this ledger (both stock and byproduct pools are cloned). */
    public ResourceLedger copy() {
        Object2LongOpenHashMap<ContentResource> copied = new Object2LongOpenHashMap<>();
        copied.putAll(byproducts);
        Object2LongOpenHashMap<ValueType<?>> copiedValues = new Object2LongOpenHashMap<>();
        copiedValues.putAll(valueByproducts);
        return new ResourceLedger(stock.copy(), copied, copiedValues);
    }

    public void restoreFrom(ResourceLedger snapshot) {
        recordAllByproducts();
        var iterator = Object2LongMaps.fastIterator(snapshot.byproducts);
        while (iterator.hasNext()) {
            recordByproduct(iterator.next().getKey());
        }
        var valueIterator = Object2LongMaps.fastIterator(snapshot.valueByproducts);
        while (valueIterator.hasNext()) {
            recordValueByproduct(valueIterator.next().getKey());
        }
        stock.restoreFrom(snapshot.stock);
        byproducts.clear();
        byproducts.putAll(snapshot.byproducts);
        valueByproducts.clear();
        valueByproducts.putAll(snapshot.valueByproducts);
    }

    /**
     * Consume as much as possible for an input demand and record stock consumption actions.
     *
     * @return remaining amount still unresolved.
     */
    public long consumeDemand(InputContent input, long amount, PlanBuilder planBuilder) {
        long remaining = amount;
        var fromStock = stock.consumeInputDetailed(input, remaining);
        for (Stock.ConsumedResource consumed : fromStock) {
            remaining -= consumed.amount();
            planBuilder.addConsumeStock(input, consumed.resource(), consumed.amount());
        }
        if (remaining > 0) {
            remaining = consumeByproductSatisfying(input, remaining);
        }
        return remaining;
    }

    /**
     * Records an unresolved demand as a pending external input without forcing
     * {@code input} into an arbitrary concrete resource.
     */
    public void recordPendingInput(InputContent input, long amount, PlanBuilder planBuilder) {
        if (amount <= 0) {
            return;
        }
        planBuilder.addPendingInput(input, amount);
    }

    /**
     * Adds a byproduct resource produced by a recipe execution.
     *
     * @param resource the produced resource
     * @param amount   the amount produced
     */
    public void addByproduct(ContentResource resource, long amount) {
        if (amount <= 0) {
            return;
        }
        ContentResource normalized = normalize(resource);
        if (normalized instanceof ContentValue<?> value) {
            ValueType<?> type = value.type();
            recordValueByproduct(type);
            valueByproducts.put(type, valueByproducts.getLong(type) + amount);
            return;
        }
        recordByproduct(normalized);
        byproducts.put(normalized, byproducts.getOrDefault(normalized, 0L) + amount);
    }

    /**
     * Consumes from the byproduct pool any resources that satisfy the given input.
     *
     * @param input  the demanded input content
     * @param amount the maximum amount to consume
     * @return the remaining amount still unsatisfied
     */
    public long consumeByproductSatisfying(InputContent input, long amount) {
        if (amount <= 0) {
            return 0;
        }
        long remaining = amount;
        if (input.isEnumerable()) {
            for (ContentVariant variant : input.enumerate()) {
                if (remaining <= 0) {
                    break;
                }
                remaining -= consumeByproductVariant(variant, remaining);
            }
            return remaining;
        }

        var iterator = Object2LongMaps.fastIterator(byproducts);
        while (iterator.hasNext()) {
            Object2LongMap.Entry<ContentResource> entry = iterator.next();
            if (remaining <= 0) {
                break;
            }
            if (input.satisfy(entry.getKey())) {
                long available = entry.getLongValue();
                long consumed = Math.min(available, remaining);
                recordByproduct(entry.getKey());
                entry.setValue(available - consumed);
                remaining -= consumed;
            }
        }
        var valueIterator = Object2LongMaps.fastIterator(valueByproducts);
        while (valueIterator.hasNext()) {
            Object2LongMap.Entry<ValueType<?>> entry = valueIterator.next();
            if (remaining <= 0) {
                break;
            }
            ContentValue<?> value = valueResource(entry.getKey(), entry.getLongValue());
            if (input.satisfy(value)) {
                long available = entry.getLongValue();
                long consumed = Math.min(available, remaining);
                recordValueByproduct(entry.getKey());
                entry.setValue(available - consumed);
                remaining -= consumed;
            }
        }
        return remaining;
    }

    private long consumeByproductVariant(ContentVariant variant, long amount) {
        if (amount <= 0) {
            return 0;
        }
        return switch (variant) {
            case ContentStack<?, ?> stack -> consumeByproductResource(stack, amount);
            case ContentValue<?> value -> consumeByproductResource(value, amount);
            case ContentKind<?> kind -> consumeByproductKind(kind, amount);
            case ContentResource resource -> consumeByproductResource(resource, amount);
        };
    }

    private long countByproductVariant(ContentVariant variant) {
        return switch (variant) {
            case ContentStack<?, ?> stack -> countByproductResource(stack);
            case ContentValue<?> value -> countByproductValue(value);
            case ContentKind<?> kind -> countByproductKind(kind);
            case ContentResource resource -> countByproductMatching(resource);
        };
    }

    private long consumeByproductResource(ContentResource resource, long amount) {
        if (resource instanceof ContentValue<?> value) {
            return consumeByproductValue(value, amount);
        }
        ContentResource normalized = normalize(resource);
        long available = byproducts.getLong(normalized);
        if (available > 0L) {
            long consumed = Math.min(available, amount);
            recordByproduct(normalized);
            byproducts.put(normalized, available - consumed);
            return consumed;
        }
        return 0;
    }

    private long countByproductResource(ContentResource resource) {
        if (resource instanceof ContentValue<?> value) {
            return countByproductValue(value);
        }
        ContentResource normalized = normalize(resource);
        return byproducts.getLong(normalized);
    }

    private long countByproductValue(ContentValue<?> value) {
        return valueByproducts.getLong(value.type());
    }

    private long countByproductMatching(ContentResource resource) {
        if (resource instanceof ContentValue<?> value) {
            return countByproductValue(value);
        }
        ContentResource normalized = normalize(resource);
        long total = 0;
        var iterator = Object2LongMaps.fastIterator(byproducts);
        while (iterator.hasNext()) {
            Object2LongMap.Entry<ContentResource> entry = iterator.next();
            if (entry.getKey().matches(normalized, MatchStrategy.identityAndComponent)) {
                total = safeAdd(total, entry.getLongValue());
            }
        }
        return total;
    }

    private long consumeByproductKind(ContentKind<?> kind, long amount) {
        long remaining = amount;
        var iterator = Object2LongMaps.fastIterator(byproducts);
        while (iterator.hasNext()) {
            Object2LongMap.Entry<ContentResource> entry = iterator.next();
            if (remaining <= 0) {
                break;
            }
            if (entry.getKey() instanceof ContentStack<?, ?> stack && stack.matchesContent(kind)) {
                long available = entry.getLongValue();
                long consumed = Math.min(available, remaining);
                recordByproduct(entry.getKey());
                entry.setValue(available - consumed);
                remaining -= consumed;
            }
        }
        return amount - remaining;
    }

    private long countByproductKind(ContentKind<?> kind) {
        long total = 0;
        var iterator = Object2LongMaps.fastIterator(byproducts);
        while (iterator.hasNext()) {
            Object2LongMap.Entry<ContentResource> entry = iterator.next();
            if (entry.getKey() instanceof ContentStack<?, ?> stack && stack.matchesContent(kind)) {
                total += entry.getLongValue();
            }
        }
        return total;
    }

    private ContentResource normalize(ContentResource resource) {
        if (resource instanceof ContentStack<?, ?> stack) {
            return stack.normalize();
        }
        if (resource instanceof ContentValue<?> value) {
            return value;
        }
        return resource;
    }

    private long consumeByproductValue(ContentValue<?> value, long amount) {
        return consumeByproductValue(value.type(), amount);
    }

    private long consumeByproductValue(ValueType<?> type, long amount) {
        long available = valueByproducts.getLong(type);
        if (available <= 0L) {
            return 0L;
        }
        long consumed = Math.min(available, amount);
        recordValueByproduct(type);
        valueByproducts.put(type, available - consumed);
        return consumed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ContentValue<?> valueResource(ValueType<?> type, long amount) {
        ValueType rawType = type;
        return ContentValue.fromValue(rawType, rawType.fromValue(amount));
    }

    public Mark mark() {
        return new Mark(stock.mark(), beginScope());
    }

    public void rollback(Mark mark) {
        TransactionScope scope = popScope(mark);
        for (int i = scope.changes.size() - 1; i >= 0; i--) {
            scope.changes.get(i).rollback(this);
        }
        stock.rollback(mark.stockMark);
    }

    public void commit(Mark mark) {
        TransactionScope scope = popScope(mark);
        if (!transactionScopes.isEmpty()) {
            mergeIntoParent(scope, transactionScopes.get(transactionScopes.size() - 1));
        }
        stock.commit(mark.stockMark);
    }

    private TransactionScope beginScope() {
        TransactionScope scope = new TransactionScope();
        transactionScopes.add(scope);
        return scope;
    }

    private TransactionScope popScope(Mark mark) {
        if (mark == null || mark.closed) {
            throw new IllegalStateException("ledger transaction mark is not active");
        }
        if (transactionScopes.isEmpty() || transactionScopes.get(transactionScopes.size() - 1) != mark.scope) {
            throw new IllegalStateException("ledger transaction marks must be closed in LIFO order");
        }
        mark.closed = true;
        return transactionScopes.remove(transactionScopes.size() - 1);
    }

    private void mergeIntoParent(TransactionScope child, TransactionScope parent) {
        parent.changes.addAll(child.changes);
    }

    private void recordByproduct(ContentResource key) {
        if (transactionScopes.isEmpty()) {
            return;
        }
        TransactionScope scope = transactionScopes.get(transactionScopes.size() - 1);
        if (scope.addByproductKey(key)) {
            boolean existed = byproducts.containsKey(key);
            scope.changes.add(new ByproductChange(key, existed, existed ? byproducts.getLong(key) : 0L));
        }
    }

    private void recordValueByproduct(ValueType<?> key) {
        if (transactionScopes.isEmpty()) {
            return;
        }
        TransactionScope scope = transactionScopes.get(transactionScopes.size() - 1);
        if (scope.addValueByproductKey(key)) {
            boolean existed = valueByproducts.containsKey(key);
            scope.changes.add(new ValueByproductChange(key, existed, existed ? valueByproducts.getLong(key) : 0L));
        }
    }

    private void recordAllByproducts() {
        var iterator = Object2LongMaps.fastIterator(byproducts);
        while (iterator.hasNext()) {
            recordByproduct(iterator.next().getKey());
        }
        var valueIterator = Object2LongMaps.fastIterator(valueByproducts);
        while (valueIterator.hasNext()) {
            recordValueByproduct(valueIterator.next().getKey());
        }
    }

    public static final class Mark {
        private final Stock.Mark stockMark;
        private final TransactionScope scope;
        private boolean closed;

        private Mark(Stock.Mark stockMark, TransactionScope scope) {
            this.stockMark = stockMark;
            this.scope = scope;
        }
    }

    private static final class TransactionScope {
        private static final int smallKeySetLimit = 8;
        private final ArrayList<LedgerChange> changes = new ArrayList<>();
        private @Nullable Object[] smallByproductKeys;
        private int smallByproductKeyCount;
        private @Nullable Object[] smallValueByproductKeys;
        private int smallValueByproductKeyCount;
        private @Nullable HashSet<ContentResource> byproductKeys;
        private @Nullable HashSet<ValueType<?>> valueByproductKeys;

        private boolean addByproductKey(ContentResource key) {
            HashSet<ContentResource> keys = byproductKeys;
            if (keys != null) {
                return keys.add(key);
            }
            Object[] smallKeys = smallByproductKeys;
            for (int i = 0; i < smallByproductKeyCount; i++) {
                Object existing = smallKeys[i];
                if (existing.equals(key)) {
                    return false;
                }
            }
            if (smallByproductKeyCount < smallKeySetLimit) {
                smallByproductKeys = appendSmallKey(smallKeys, smallByproductKeyCount++, key);
                return true;
            }
            keys = new HashSet<>(smallKeySetLimit * 2);
            for (int i = 0; i < smallByproductKeyCount; i++) {
                keys.add((ContentResource) smallKeys[i]);
            }
            smallByproductKeys = null;
            byproductKeys = keys;
            return keys.add(key);
        }

        private boolean addValueByproductKey(ValueType<?> key) {
            HashSet<ValueType<?>> keys = valueByproductKeys;
            if (keys != null) {
                return keys.add(key);
            }
            Object[] smallKeys = smallValueByproductKeys;
            for (int i = 0; i < smallValueByproductKeyCount; i++) {
                Object existing = smallKeys[i];
                if (existing.equals(key)) {
                    return false;
                }
            }
            if (smallValueByproductKeyCount < smallKeySetLimit) {
                smallValueByproductKeys = appendSmallKey(smallKeys, smallValueByproductKeyCount++, key);
                return true;
            }
            keys = new HashSet<>(smallKeySetLimit * 2);
            for (int i = 0; i < smallValueByproductKeyCount; i++) {
                keys.add((ValueType<?>) smallKeys[i]);
            }
            smallValueByproductKeys = null;
            valueByproductKeys = keys;
            return keys.add(key);
        }

        private static Object[] appendSmallKey(@Nullable Object[] keys, int index, Object key) {
            if (keys == null) {
                keys = new Object[2];
            } else if (index >= keys.length) {
                keys = Arrays.copyOf(keys, Math.min(keys.length * 2, smallKeySetLimit));
            }
            keys[index] = key;
            return keys;
        }
    }

    private sealed interface LedgerChange permits ByproductChange, ValueByproductChange {
        void rollback(ResourceLedger ledger);
    }

    private record ByproductChange(ContentResource key, boolean existed, long oldValue) implements LedgerChange {
        @Override
        public void rollback(ResourceLedger ledger) {
            if (existed) {
                ledger.byproducts.put(key, oldValue);
            } else {
                ledger.byproducts.removeLong(key);
            }
        }
    }

    private record ValueByproductChange(ValueType<?> key, boolean existed, long oldValue) implements LedgerChange {
        @Override
        public void rollback(ResourceLedger ledger) {
            if (existed) {
                ledger.valueByproducts.put(key, oldValue);
            } else {
                ledger.valueByproducts.removeLong(key);
            }
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
