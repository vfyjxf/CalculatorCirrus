package dev.vfyjxf.cirrus.recipe.chainexport;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Offline, registry-free content model for chain export: content identity is a
 * canonical id string ({@code modid:path}, with data components folded into a
 * suffix digest), so the real routing solver can run on plain-JVM test
 * classpaths without bootstrapped Minecraft registries.
 *
 * <p>One {@link IdStackType} exists per exported content family
 * ({@code item}, {@code fluid}, or a custom type id); all matching is string
 * equality, which subsumes the component-aware strategies because components
 * are part of the canonical key.
 */
final class OfflineContent {

    static final String ITEM_FAMILY = "item";
    static final String FLUID_FAMILY = "fluid";
    private static final Namespace OFFLINE_NS = Namespace.of("cirrus_offline", "content");

    private OfflineContent() {
    }

    static String familyOfStackType(String stackTypeId) {
        return switch (stackTypeId) {
            case "minecraft:item_stack" -> ITEM_FAMILY;
            case "minecraft:fluid_stack" -> FLUID_FAMILY;
            default -> stackTypeId;
        };
    }

    static String familyOfContentType(String contentTypeId) {
        return switch (contentTypeId) {
            case "minecraft:item", "item" -> ITEM_FAMILY;
            case "minecraft:fluid", "fluid" -> FLUID_FAMILY;
            default -> contentTypeId;
        };
    }

    /** A session owns the offline type universe and the global ContentApi attachment. */
    static final class Session implements AutoCloseable {

        private final Map<String, IdStackType> stackTypesByFamily = new HashMap<>();
        private final Map<String, IdValueType> valueTypesById = new HashMap<>();
        private final ContentApiImpl previousApi;

        private Session() {
            // TypedContentStack construction consults ContentApi.instance(); attach a
            // minimal registry up-front so parsing can build stacks, then re-attach the
            // full family set before the graph build.
            this.previousApi = ContentApiImpl.instance();
            ContentApiImpl.attach(new ContentApiImpl(new ContentRegistryBuilder()));
        }

        synchronized IdStackType stackType(String family) {
            return stackTypesByFamily.computeIfAbsent(family, f -> new IdStackType(f));
        }

        synchronized IdValueType valueType(String valueTypeId) {
            return valueTypesById.computeIfAbsent(valueTypeId, IdValueType::new);
        }

        /** Attaches ContentApi with every family known so far (call after pre-scan and again after parse). */
        synchronized void attachRegistry() {
            ContentRegistryBuilder builder = new ContentRegistryBuilder();
            for (IdStackType type : stackTypesByFamily.values()) {
                builder.registerContentType(type.contentType);
                builder.registerStackType(type.contentType, type);
            }
            for (IdValueType type : valueTypesById.values()) {
                builder.registerValueType(type);
            }
            ContentApiImpl.attach(new ContentApiImpl(builder));
        }

        @Override
        public synchronized void close() {
            if (previousApi != null) {
                ContentApiImpl.attach(previousApi);
            }
        }
    }

    static Session newSession() {
        return new Session();
    }

    static final class IdContentType implements ContentType<String> {

        private final Namespace typeId;
        private final String family;

        IdContentType(String family) {
            this.family = family;
            String safe = family.replace(':', '_').replace('/', '_');
            this.typeId = Namespace.of(OFFLINE_NS.root(), "type/" + safe);
        }

        String family() {
            return family;
        }

        @Override
        public Namespace typeId() {
            return typeId;
        }

        @Override
        public @Nullable TagType<String> tagType() {
            return null;
        }

        private final java.util.concurrent.ConcurrentHashMap<String, @org.jetbrains.annotations.Nullable ResourceLocation> locationCache =
            new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public @Nullable ResourceLocation getLocation(String value) {
            // hot path: ClassifyViewFactory resolves ids on every oracle
            // evaluation; tryParse re-validates the path chars each time.
            // Id strings form a bounded set, so a cache pays for itself.
            return value == null ? null : locationCache.computeIfAbsent(value, ResourceLocation::tryParse);
        }

        @Override
        public @Nullable ResourceKey<net.minecraft.core.Registry<String>> getRegistryKey() {
            return null;
        }

        @Override
        public boolean isEmpty(String value) {
            return value == null || value.isEmpty();
        }

        @Override
        public boolean matches(String value1, String value2) {
            return Objects.equals(value1, value2);
        }

        @Override
        public int hash(String value) {
            return value == null ? 0 : value.hashCode();
        }

        @Override
        public @Nullable Codec<String> contentCodec() {
            return null;
        }
    }

    static final class IdStackType implements StackType<String, String> {

        final IdContentType contentType;

        IdStackType(String family) {
            this.contentType = new IdContentType(family);
        }

        String family() {
            return contentType.family();
        }

        @Override
        public Namespace typeId() {
            return Namespace.of(OFFLINE_NS.root(), "stack/" + contentType.family().replace(':', '_').replace('/', '_'));
        }

        @Override
        public ContentType<String> contentType() {
            return contentType;
        }

        @Override
        public String getContent(String stack) {
            return stack;
        }

        @Override
        public long maxAmount() {
            return Long.MAX_VALUE;
        }

        @Override
        public long quantity() {
            return 1;
        }

        @Override
        public long getAmount(String stack) {
            return 1;
        }

        @Override
        public long getMaxStackSize(String stack) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isEmpty(String stack) {
            return stack == null || stack.isEmpty();
        }

        @Override
        public boolean supportComponents() {
            return false;
        }

        @Override
        public @Nullable DataComponentMap getComponents(String stack) {
            return DataComponentMap.EMPTY;
        }

        @Override
        public DataComponentPatch getComponentsPatch(String stack) {
            return DataComponentPatch.EMPTY;
        }

        @Override
        public boolean hasComponent(String stack, DataComponentType<?> componentType) {
            return false;
        }

        @Override
        public <C> C getComponent(String stack, DataComponentType<? extends C> componentType) {
            throw new IllegalArgumentException("Components not supported by offline stack type " + typeId());
        }

        @Override
        public @Nullable Codec<String> stackCodec() {
            return null;
        }

        @Override
        public boolean matchesIdentity(String stack1, String stack2, @Nullable VariantType<String, String> variantType) {
            return Objects.equals(stack1, stack2);
        }

        @Override
        public boolean matches(String stack1, String stack2, @Nullable VariantType<String, String> variantType, MatchStrategy strategy) {
            return Objects.equals(stack1, stack2);
        }

        @Override
        public long mergeDiff(String stack1, String stack2, MatchStrategy strategy) {
            return Objects.equals(stack1, stack2) ? 0 : 0;
        }

        @Override
        public String copy(String stack) {
            return stack;
        }

        @Override
        public String copyWithAmount(String stack, long amount) {
            return stack;
        }

        @Override
        public String normalize(String stack) {
            return stack;
        }

        @Override
        public String wildcard(String stack) {
            return stack;
        }

        @Override
        public String fromStack(String stack, long amount, DataComponentPatch componentPatch) {
            return stack;
        }

        @Override
        public String fromContent(String content, long amount, DataComponentPatch componentPatch) {
            return content;
        }

        @Override
        public String toString(String stack) {
            return String.valueOf(stack);
        }
    }

    /** Long-valued offline value type (energy, time, ...). */
    static final class IdValueType implements ValueType<Long> {

        private final Namespace typeId;

        IdValueType(String valueTypeId) {
            String safe = valueTypeId.replace(':', '_').replace('/', '_');
            this.typeId = Namespace.of(OFFLINE_NS.root(), "value/" + safe);
        }

        @Override
        public Namespace typeId() {
            return typeId;
        }

        @Override
        public long getValue(Long content) {
            return content == null ? 0 : content;
        }

        @Override
        public boolean matches(Long first, Long second) {
            return Objects.equals(first, second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }

        @Override
        public String toString(Long content) {
            return String.valueOf(content);
        }
    }
}
