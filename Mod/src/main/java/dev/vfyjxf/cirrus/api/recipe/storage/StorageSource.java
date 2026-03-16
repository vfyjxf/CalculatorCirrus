package dev.vfyjxf.cirrus.api.recipe.storage;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import org.eclipse.collections.api.RichIterable;

public interface StorageSource {

    StorageSourceType<?> type();

    /**
     *
     * @return an unique identifier for this storage source
     */
    Object identifier();

    RichIterable<? extends ContentVariant> contents();

    /**
     * Whether this storage source can provide (extract) resources.
     * Most storage sources can provide by default.
     */
    default boolean canProvide() {
        return true;
    }

    /**
     * Whether this storage source can receive (insert) resources.
     * Override to return {@code true} for receivers like machine inputs or crafting slots.
     */
    default boolean canReceive() {
        return false;
    }

    //region runtime capability

    default StorageAccessibility accessibility() {
        return StorageAccessibility.accessible;
    }

    default boolean canProvide(ContentResource resource) {
        if (!canProvide() || !accessibility().accessible()) {
            return false;
        }
        return contents().anySatisfy(content -> content instanceof ContentResource candidate && candidate.matchesResource(resource));
    }

    default boolean canReceive(ContentResource resource) {
        return canReceive() && accessibility().accessible();
    }

    default StorageTransferResult simulateExtract(StorageTransferRequest request) {
        if (!accessibility().accessible()) {
            return StorageTransferResult.none(this, request, accessibility().guidance());
        }
        if (!canProvide(request.resource())) {
            return StorageTransferResult.none(this, request, null);
        }
        long available = 0L;
        for (ContentVariant content : contents()) {
            if (content instanceof ContentResource candidate && candidate.matchesResource(request.resource())) {
                available = Math.addExact(available, candidate.amount());
            }
        }
        return StorageTransferResult.transferred(this, request, Math.min(available, request.amount()));
    }

    default StorageTransferResult extract(StorageTransferRequest request) {
        return simulateExtract(request);
    }

    default StorageTransferResult simulateInsert(StorageTransferRequest request) {
        if (!accessibility().accessible()) {
            return StorageTransferResult.none(this, request, accessibility().guidance());
        }
        if (!canReceive(request.resource())) {
            return StorageTransferResult.none(this, request, null);
        }
        return StorageTransferResult.transferred(this, request, request.amount());
    }

    default StorageTransferResult insert(StorageTransferRequest request) {
        return simulateInsert(request);
    }

    //endregion

}
