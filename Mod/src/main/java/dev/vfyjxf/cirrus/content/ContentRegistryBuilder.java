package dev.vfyjxf.cirrus.content;

import dev.vfyjxf.cirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.cirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.cirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cirrus.api.register.ContentResolverRegister;
import dev.vfyjxf.cirrus.api.register.ContentTypeRegister;
import dev.vfyjxf.cirrus.api.register.ContentVariantRegister;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class ContentRegistryBuilder implements ContentTypeRegister, ContentVariantRegister, ContentResolverRegister {

    final ConcurrentHashMap<Namespace, ContentType<?>> contentTypes = new ConcurrentHashMap<>();

    final ConcurrentHashMap<ContentType<?>, StackType<?, ?>> stackTypesByType = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Namespace, StackType<?, ?>> stackTypesById = new ConcurrentHashMap<>();

    final ConcurrentHashMap<ContentType<?>, TagType<?>> tagTypesByType = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Namespace, TagType<?>> tagTypesById = new ConcurrentHashMap<>();

    final ConcurrentHashMap<Namespace, VariantType<?, ?>> variantTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Object, VariantType<?, ?>> variantTypeByContent = new ConcurrentHashMap<>();

    final ConcurrentHashMap<ContentResolverType<?>, Set<ContentResolver<?, ?>>> contentResolvers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ContentResolverType<?>, Set<ContentResolver<?, ?>>> fallbackContentResolvers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ContentResolverType<?>, Set<StackResolver<?, ?, ?>>> stackResolvers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ContentResolverType<?>, Set<StackResolver<?, ?, ?>>> fallbackStackResolvers = new ConcurrentHashMap<>();

    //region content and stack

    public <T> void registerContentType(ContentType<T> contentType) {
        Checks.checkNotNull(contentType, "contentType");
        Namespace id = contentType.typeId();
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
    public <T, R> void registerContentResolver(ContentResolverType<R> type, ContentType<T> contentType, ContentResolver<T, R> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(resolver, "resolver");
        contentResolvers.computeIfAbsent(type, (t) -> new HashSet<>()).add(resolver.cast());
    }

    @Override
    public <T, R> void registerFallbackContentResolver(ContentResolverType<R> type, ContentType<T> contentType, ContentResolver<T, R> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(resolver, "resolver");
        fallbackContentResolvers.computeIfAbsent(type, (t) -> new HashSet<>()).add(resolver.cast());
    }

    @Override
    public <T, S, R> void registerStackResolver(ContentResolverType<R> type, StackType<T, S> stackType, StackResolver<T, S, R> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(stackType, "stackType");
        Checks.checkNotNull(resolver, "resolver");
        stackResolvers.computeIfAbsent(type, (t) -> new HashSet<>()).add(resolver.cast());
    }

    @Override
    public <T, S, R> void registerFallbackStackResolver(ContentResolverType<R> type, StackType<T, S> stackType, StackResolver<T, S, R> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(stackType, "stackType");
        Checks.checkNotNull(resolver, "resolver");
        fallbackStackResolvers.computeIfAbsent(type, (t) -> new HashSet<>()).add(resolver.cast());
    }

    //endregion

}
