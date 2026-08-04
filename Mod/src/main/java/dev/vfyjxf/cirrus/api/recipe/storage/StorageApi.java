package dev.vfyjxf.cirrus.api.recipe.storage;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.recipe.storage.StorageApiImpl;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.NonExtendable
public interface StorageApi {

    static StorageApi instance() {
        StorageApiImpl instance = StorageApiImpl.instance();
        if (instance == null) {
            throw new IllegalStateException("StorageApi not initialized");
        }
        return instance;
    }

    void addStorage(StorageSource source);

    void addStorage(TagKey<StorageSource> tagKey, StorageSource... sources);

    @UnmodifiableView
    RichIterable<? extends StorageSource> storage();

    @UnmodifiableView
    RichIterable<? extends StorageSource> getTaggedStorage(TagKey<StorageSource> tagKey);

    @Nullable StorageSource findStorage(Object identifier);

    <T extends StorageSource> @Nullable StorageSourceRenderer<T> findRenderer(StorageSourceType<T> type);

    /**
     * @return all storage sources that can provide (extract) resources
     */
    default RichIterable<? extends StorageSource> providers() {
        return storage().select(StorageSource::canProvide);
    }

    /**
     * @return all storage sources that can receive (insert) resources
     */
    default RichIterable<? extends StorageSource> receivers() {
        return storage().select(StorageSource::canReceive);
    }

    //region runtime capability

    default RichIterable<? extends StorageSource> accessibleStorage() {
        return storage().select(source -> source.accessibility().accessible());
    }

    default RichIterable<? extends StorageSource> providersFor(ContentResource resource) {
        return providers().select(source -> source.canProvide(resource));
    }

    default RichIterable<? extends StorageSource> receiversFor(ContentResource resource) {
        return receivers().select(source -> source.canReceive(resource));
    }

    default StorageTransferResult simulateExtract(StorageSource source, StorageTransferRequest request) {
        return source.simulateExtract(request);
    }

    default StorageTransferResult extract(StorageSource source, StorageTransferRequest request) {
        return source.extract(request);
    }

    default StorageTransferResult simulateInsert(StorageSource source, StorageTransferRequest request) {
        return source.simulateInsert(request);
    }

    default StorageTransferResult insert(StorageSource source, StorageTransferRequest request) {
        return source.insert(request);
    }

    default StorageTransferResult simulateExtractFirstAvailable(StorageTransferRequest request) {
        StorageTransferResult guidedFallback = null;
        for (StorageSource source : providers()) {
            // Do not pre-filter here with source.canProvide(request): inaccessible providers may
            // still carry useful guidance that the runtime should surface to the user.
            var result = simulateExtract(source, request);
            if (result.transferredAmount() > 0) {
                return result;
            }
            if (guidedFallback == null && result.guidance() != null) {
                guidedFallback = result;
            }
        }
        return guidedFallback != null
            ? guidedFallback
            : StorageTransferResult.none(null, request, null);
    }

    default long countAvailable(StorageTransferRequest request) {
        long remaining = request.amount();
        long total = 0L;
        for (StorageSource source : providers()) {
            if (remaining <= 0L) {
                break;
            }
            StorageTransferResult simulated = simulateExtract(
                source,
                new StorageTransferRequest(request.target(), remaining)
            );
            long transferred = Math.max(0L, Math.min(remaining, simulated.transferredAmount()));
            total = Math.addExact(total, transferred);
            remaining -= transferred;
        }
        return total;
    }

    default boolean hasAvailable(StorageTransferRequest request) {
        return countAvailable(request) >= request.amount();
    }

    default StorageTransferResult extractFirstAvailable(StorageTransferRequest request) {
        StorageTransferResult guidedFallback = null;
        for (StorageSource source : providers()) {
            StorageTransferResult simulated = simulateExtract(source, request);
            if (!simulated.completed()) {
                if (guidedFallback == null && simulated.guidance() != null) {
                    guidedFallback = simulated;
                }
                continue;
            }
            StorageTransferResult extracted = extract(source, request);
            if (extracted.transferredAmount() > 0) {
                return extracted;
            }
            if (guidedFallback == null && extracted.guidance() != null) {
                guidedFallback = extracted;
            }
        }
        return guidedFallback != null
            ? guidedFallback
            : StorageTransferResult.none(null, request, null);
    }

    default StorageTransferResult simulateInsertFirstAvailable(StorageTransferRequest request) {
        StorageTransferResult guidedFallback = null;
        for (StorageSource source : receivers()) {
            var result = simulateInsert(source, request);
            if (result.transferredAmount() > 0) {
                return result;
            }
            if (guidedFallback == null && result.guidance() != null) {
                guidedFallback = result;
            }
        }
        return guidedFallback != null
            ? guidedFallback
            : StorageTransferResult.none(null, request, null);
    }

    default StorageTransferResult insertFirstAvailable(StorageTransferRequest request) {
        StorageTransferResult guidedFallback = null;
        for (StorageSource source : receivers()) {
            StorageTransferResult simulated = simulateInsert(source, request);
            if (!simulated.completed()) {
                if (guidedFallback == null && simulated.guidance() != null) {
                    guidedFallback = simulated;
                }
                continue;
            }
            StorageTransferResult inserted = insert(source, request);
            if (inserted.transferredAmount() > 0) {
                return inserted;
            }
            if (guidedFallback == null && inserted.guidance() != null) {
                guidedFallback = inserted;
            }
        }
        return guidedFallback != null
            ? guidedFallback
            : StorageTransferResult.none(null, request, null);
    }
    //endregion

}
