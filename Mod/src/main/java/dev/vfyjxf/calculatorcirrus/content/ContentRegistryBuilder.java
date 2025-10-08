package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.ResolverType;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.type.VariantType;
import dev.vfyjxf.calculatorcirrus.api.register.ContentResolverRegister;
import dev.vfyjxf.calculatorcirrus.api.register.ContentTypeRegister;
import dev.vfyjxf.calculatorcirrus.api.register.ContentVariantRegister;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class ContentRegistryBuilder implements ContentTypeRegister, ContentVariantRegister, ContentResolverRegister {

    final ConcurrentHashMap<ResourceLocation, ContentType<?>> contentTypes = new ConcurrentHashMap<>();

    final ConcurrentHashMap<ContentType<?>, StackType<?, ?>> stackTypesByType = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResourceLocation, StackType<?, ?>> stackTypesById = new ConcurrentHashMap<>();

    final ConcurrentHashMap<ContentType<?>, TagType<?>> tagTypesByType = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResourceLocation, TagType<?>> tagTypesById = new ConcurrentHashMap<>();

    final ConcurrentHashMap<ResourceLocation, VariantType<?, ?>> variantTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Object, VariantType<?, ?>> variantTypeByContent = new ConcurrentHashMap<>();

    final ConcurrentHashMap<ResolverType<?>, Set<ContentResolver<?, ?>>> contentResolvers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResolverType<?>, Set<ContentResolver<?, ?>>> fallbackContentResolvers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResolverType<?>, Set<StackResolver<?, ?, ?>>> stackResolvers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResolverType<?>, Set<StackResolver<?, ?, ?>>> fallbackStackResolvers = new ConcurrentHashMap<>();

    //region content and stack

    public <T> void registerContentType(ContentType<T> contentType) {
        Checks.checkNotNull(contentType, "contentType");
        ResourceLocation id = contentType.typeId();
        if (contentTypes.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate content type: " + id);
        }
        contentTypes.put(id, contentType);
    }

    public <T, S> void registerStackType(ContentType<T> contentType, StackType<T, S> stackType) {
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(stackType, "stackType");
        if (stackTypesById.containsKey(stackType.typeId())) {
            throw new IllegalArgumentException("Duplicate stack type: " + stackType.typeId());
        }
        if (stackTypesByType.containsKey(contentType)) {
            throw new IllegalArgumentException("Duplicate stack type for content type: " + contentType.typeId());
        }
        stackTypesByType.put(contentType, stackType);
        stackTypesById.put(stackType.typeId(), stackType);
    }

    public <T> void registerTagType(ContentType<T> contentType, TagType<T> tagType) {
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(tagType, "tagType");
        if (tagTypesById.containsKey(tagType.typeId())) {
            throw new IllegalArgumentException("Duplicate tag type: " + tagType.typeId());
        }

        if (tagTypesByType.containsKey(contentType)) {
            throw new IllegalArgumentException("Duplicate tag type for content type: " + contentType.typeId());
        }
        tagTypesByType.put(contentType, tagType);
        tagTypesById.put(tagType.typeId(), tagType);
    }

    //endregion

    //region variant

    public <T, S> void registerVariantType(StackType<T, S> stackType, VariantType<T, S> variantType) {
        Checks.checkNotNull(stackType, "stackType");
        Checks.checkNotNull(variantType, "variantType");
        if (variantTypes.containsKey(variantType.typeId())) {
            throw new IllegalArgumentException("Duplicate variant type: " + variantType.typeId());
        }
        if (!stackTypesById.containsValue(stackType)) {
            throw new IllegalArgumentException("Stack type not registered: " + stackType.typeId());
        }
        variantTypes.put(variantType.typeId(), variantType);
    }

    public <T, S> void registerVariantForContent(T content, VariantType<T, S> variantType) {
        Checks.checkNotNull(content, "content");
        Checks.checkNotNull(variantType, "variantType");
        if (variantTypeByContent.containsKey(content)) {
            throw new IllegalArgumentException("Duplicate variant type for content: " + content);
        }
        if (!variantTypes.containsValue(variantType)) {
            throw new IllegalArgumentException("Variant type not registered: " + variantType.typeId());
        }
        variantTypeByContent.put(content, variantType);
    }

    //endregion

    //region resolver

    @Override
    public <T, R> void registerContentResolver(ResolverType<R> type, ContentType<T> contentType, ContentResolver<T, R> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(resolver, "resolver");
        contentResolvers.computeIfAbsent(type, (t) -> new HashSet<>()).add(resolver.cast());
    }

    @Override
    public <T, R> void registerFallbackContentResolver(ResolverType<R> type, ContentType<T> contentType, ContentResolver<T, R> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(resolver, "resolver");
        fallbackContentResolvers.computeIfAbsent(type, (t) -> new HashSet<>()).add(resolver.cast());
    }

    @Override
    public <T, S, R> void registerStackResolver(ResolverType<R> type, StackType<T, S> stackType, StackResolver<T, S, R> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(stackType, "stackType");
        Checks.checkNotNull(resolver, "resolver");
        stackResolvers.computeIfAbsent(type, (t) -> new HashSet<>()).add(resolver.cast());
    }

    @Override
    public <T, S, R> void registerFallbackStackResolver(ResolverType<R> type, StackType<T, S> stackType, StackResolver<T, S, R> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(stackType, "stackType");
        Checks.checkNotNull(resolver, "resolver");
        fallbackStackResolvers.computeIfAbsent(type, (t) -> new HashSet<>()).add(resolver.cast());
    }

    //endregion

}
