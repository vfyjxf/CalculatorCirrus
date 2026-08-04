package dev.vfyjxf.cirrus.api.recipe.execution;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageAccessibility;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageGuidance;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceRenderer;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferRequest;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.UnmodifiableView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageExecutionContractTest {

    //region provider and receiver selection

    @Test
    void runtimeApiSelectsAccessibleProvidersAndReceiversByResource() {
        var requested = resource(4L);
        var provider = new FakeStorageSource("provider", Lists.immutable.of(resource(9L)), true, false, StorageAccessibility.accessible);
        var blockedProvider = new FakeStorageSource(
            "blocked",
            Lists.immutable.of(resource(9L)),
            true,
            false,
            StorageAccessibility.inaccessible(StorageGuidance.simple(Component.literal("Walk closer")))
        );
        var receiver = new FakeStorageSource("receiver", Lists.immutable.empty(), false, true, StorageAccessibility.accessible);
        StorageApi api = new FakeStorageApi(provider, blockedProvider, receiver);

        assertEquals(Lists.immutable.of(provider), Lists.immutable.ofAll(api.providersFor(requested)));
        assertEquals(Lists.immutable.of(receiver), Lists.immutable.ofAll(api.receiversFor(requested)));
        assertEquals(2, api.accessibleStorage().size());
    }

    //endregion

    //region transfer simulation

    @Test
    void runtimeApiSimulatesExtractAndInsert() {
        var requested = resource(4L);
        var provider = new FakeStorageSource("provider", Lists.immutable.of(resource(9L)), true, false, StorageAccessibility.accessible);
        var receiver = new FakeStorageSource("receiver", Lists.immutable.empty(), false, true, StorageAccessibility.accessible);
        StorageApi api = new FakeStorageApi(provider, receiver);

        var extract = api.simulateExtract(provider, StorageTransferRequest.forResource(requested, 4L));
        var insert = api.simulateInsert(receiver, StorageTransferRequest.forResource(requested, 4L));

        assertSame(provider, extract.source());
        assertEquals(4L, extract.transferredAmount());
        assertTrue(extract.completed());

        assertSame(receiver, insert.source());
        assertEquals(4L, insert.transferredAmount());
        assertTrue(insert.completed());
    }

    @Test
    void runtimeApiCountsAvailableResourcesAcrossAccessibleProviders() {
        var requested = resource(1L);
        var providerA = new FakeStorageSource("provider_a", Lists.immutable.of(resource(4L)), true, false, StorageAccessibility.accessible);
        var providerB = new FakeStorageSource("provider_b", Lists.immutable.of(resource(7L)), true, false, StorageAccessibility.accessible);
        var blocked = new FakeStorageSource(
            "blocked",
            Lists.immutable.of(resource(99L)),
            true,
            false,
            StorageAccessibility.inaccessible(StorageGuidance.simple(Component.literal("Walk closer")))
        );
        StorageApi api = new FakeStorageApi(providerA, providerB, blocked);

        StorageTransferRequest request = StorageTransferRequest.forResource(requested, 9L);

        assertEquals(9L, api.countAvailable(request));
        assertTrue(api.hasAvailable(request));
        assertEquals(11L, api.countAvailable(StorageTransferRequest.forResource(requested, 20L)));
        assertFalse(api.hasAvailable(StorageTransferRequest.forResource(requested, 20L)));
    }

    @Test
    void inaccessibleSourcesReportGuidanceAndZeroTransfer() {
        var requested = resource(4L);
        var blocked = new FakeStorageSource(
            "blocked",
            Lists.immutable.of(resource(9L)),
            true,
            false,
            StorageAccessibility.inaccessible(StorageGuidance.simple(Component.literal("Open the inventory")))
        );
        StorageApi api = new FakeStorageApi(blocked);

        var result = api.simulateExtract(blocked, StorageTransferRequest.forResource(requested, 4L));

        assertFalse(result.completed());
        assertEquals(0L, result.transferredAmount());
        assertEquals("Open the inventory", result.guidance().summary().getString());
    }

    //endregion

    //region overflow policy

    @Test
    void overflowPolicyCanStoreOutputsBeforePausing() {
        var requested = resource(6L);
        var playerInventory = new FakeStorageSource("player", Lists.immutable.empty(), false, false, StorageAccessibility.accessible);
        var chest = new FakeStorageSource("chest", Lists.immutable.empty(), false, true, StorageAccessibility.accessible);
        StorageApi api = new FakeStorageApi(playerInventory, chest);

        var result = api.simulateInsertFirstAvailable(StorageTransferRequest.forResource(requested, 6L));

        assertSame(chest, result.source());
        assertEquals(6L, result.transferredAmount());
        assertTrue(result.completed());
    }

    //endregion

    //region fixtures

    private static ContentResource resource(long amount) {
        return ContentValue.fromValue(longValueType, amount);
    }

    private static final ValueType<Long> longValueType = new ValueType<>() {
        @Override
        public Namespace typeId() {
            return Namespace.of("test", "amount");
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long fist, Long second) {
            return fist.equals(second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    };

    private static final class FakeStorageApi implements StorageApi {
        private final ImmutableList<FakeStorageSource> sources;

        private FakeStorageApi(FakeStorageSource... sources) {
            this.sources = Lists.immutable.of(sources);
        }

        @Override
        public void addStorage(StorageSource source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addStorage(TagKey<StorageSource> tagKey, StorageSource... sources) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> storage() {
            return sources;
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> getTaggedStorage(TagKey<StorageSource> tagKey) {
            return Lists.immutable.empty();
        }

        @Override
        public @Nullable StorageSource findStorage(Object identifier) {
            return sources.detect(source -> source.identifier().equals(identifier));
        }

        @Override
        public <T extends StorageSource> @Nullable StorageSourceRenderer<T> findRenderer(StorageSourceType<T> type) {
            return null;
        }
    }

    private record FakeStorageSource(
        String id,
        ImmutableList<? extends ContentVariant> contents,
        boolean provider,
        boolean receiver,
        StorageAccessibility accessibility
    ) implements StorageSource {
        private static final StorageSourceType<FakeStorageSource> type =
            new StorageSourceType<>(Namespace.of("test", "storage_source"), (Codec<FakeStorageSource>) null);

        @Override
        public StorageSourceType<?> type() {
            return type;
        }

        @Override
        public Object identifier() {
            return id;
        }

        @Override
        public boolean canProvide() {
            return provider;
        }

        @Override
        public boolean canReceive() {
            return receiver;
        }
    }

    //endregion
}
