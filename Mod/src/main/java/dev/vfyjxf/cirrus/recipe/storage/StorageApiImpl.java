package dev.vfyjxf.cirrus.recipe.storage;

import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceRenderer;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.Nullable;

public class StorageApiImpl implements StorageApi {
    private static @Nullable StorageApiImpl instance;

    private final ImmutableMap<Namespace, StorageSourceType<?>> storageTypes;

    private final MutableMap<Object, StorageSource> sources = Maps.mutable.empty();
    private final MutableMap<TagKey<StorageSource>, MutableList<StorageSource>> taggedSources = Maps.mutable.empty();

    private final ImmutableMap<Namespace, StorageSourceRenderer<?>> renderers;

    public StorageApiImpl(StorageApiBuilder builder) {
        this.storageTypes = Maps.immutable.withAll(builder.storageTypes);
        this.renderers = Maps.immutable.withAll(builder.renderers);
    }

    public static void attach(StorageApiImpl storageApi) {
        Checks.checkNotNull(storageApi, "storageApi");
        instance = storageApi;
    }

    public static @Nullable StorageApiImpl instance() {
        return instance;
    }

    @Override
    public void addStorage(StorageSource source) {
        sources.put(source.identifier(), source);
    }

    @Override
    public void addStorage(TagKey<StorageSource> tagKey, StorageSource... sources) {
        MutableList<StorageSource> existing = this.taggedSources.get(tagKey);
        if (existing == null) {
            existing = Lists.mutable.empty();
            this.taggedSources.put(tagKey, existing);
        }
        existing.addAll(Lists.mutable.of(sources));
        for (StorageSource source : sources) {
            addStorage(source);
        }
    }

    @Override
    public RichIterable<? extends StorageSource> storage() {
        return sources.valuesView();
    }

    @Override
    public @UnmodifiableView RichIterable<? extends StorageSource> getTaggedStorage(TagKey<StorageSource> tagKey) {
        var tagged = taggedSources.get(tagKey);
        return tagged != null ? tagged.toImmutable() : Lists.immutable.empty();
    }

    @Override
    public @Nullable StorageSource findStorage(Object identifier) {
        return sources.get(identifier);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends StorageSource> @Nullable StorageSourceRenderer<T> findRenderer(StorageSourceType<T> type) {
        return (StorageSourceRenderer<T>) renderers.get(type.id());
    }

}
