package dev.vfyjxf.cirrus.api.recipe.routing.state;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
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
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

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

    public ResourceLedger(Stock stock) {
        this(stock, new Object2LongOpenHashMap<>());
    }

    private ResourceLedger(Stock stock, Object2LongMap<ContentResource> byproducts) {
        this.stock = Checks.checkNotNull(stock, "stock");
        this.byproducts = byproducts;
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

        for (Object2LongMap.Entry<ContentResource> entry : byproducts.object2LongEntrySet()) {
            long available = entry.getLongValue();
            if (available > 0L && input.satisfy(entry.getKey())) {
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

    /** Creates a deep copy of this ledger (both stock and byproduct pools are cloned). */
    public ResourceLedger copy() {
        Object2LongOpenHashMap<ContentResource> copied = new Object2LongOpenHashMap<>();
        copied.putAll(byproducts);
        return new ResourceLedger(stock.copy(), copied);
    }

    public void restoreFrom(ResourceLedger snapshot) {
        stock.restoreFrom(snapshot.stock);
        byproducts.clear();
        byproducts.putAll(snapshot.byproducts);
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
        for (Object2LongMap.Entry<ContentResource> entry : byproducts.object2LongEntrySet()) {
            if (remaining <= 0) {
                break;
            }
            if (input.satisfy(entry.getKey())) {
                long available = entry.getLongValue();
                long consumed = Math.min(available, remaining);
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
            case ContentValue<?> value -> countByproductResource(value);
            case ContentKind<?> kind -> countByproductKind(kind);
            case ContentResource resource -> countByproductResource(resource);
        };
    }

    private long consumeByproductResource(ContentResource resource, long amount) {
        ContentResource normalized = normalize(resource);
        for (Object2LongMap.Entry<ContentResource> entry : byproducts.object2LongEntrySet()) {
            if (entry.getKey().matches(normalized, MatchStrategy.identityAndComponent)) {
                long available = entry.getLongValue();
                long consumed = Math.min(available, amount);
                entry.setValue(available - consumed);
                return consumed;
            }
        }
        return 0;
    }

    private long countByproductResource(ContentResource resource) {
        long total = 0;
        ContentResource normalized = normalize(resource);
        for (Object2LongMap.Entry<ContentResource> entry : byproducts.object2LongEntrySet()) {
            if (entry.getKey().matches(normalized, MatchStrategy.identityAndComponent)) {
                total += entry.getLongValue();
            }
        }
        return total;
    }

    private long consumeByproductKind(ContentKind<?> kind, long amount) {
        long remaining = amount;
        for (Object2LongMap.Entry<ContentResource> entry : byproducts.object2LongEntrySet()) {
            if (remaining <= 0) {
                break;
            }
            if (entry.getKey() instanceof ContentStack<?, ?> stack && stack.matchesContent(kind)) {
                long available = entry.getLongValue();
                long consumed = Math.min(available, remaining);
                entry.setValue(available - consumed);
                remaining -= consumed;
            }
        }
        return amount - remaining;
    }

    private long countByproductKind(ContentKind<?> kind) {
        long total = 0;
        for (Object2LongMap.Entry<ContentResource> entry : byproducts.object2LongEntrySet()) {
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
