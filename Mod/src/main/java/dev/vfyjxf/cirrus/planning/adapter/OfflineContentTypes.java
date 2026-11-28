package dev.vfyjxf.cirrus.planning.adapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.model.PlanningTagKeys;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight Cirrus {@link ContentType} / {@link StackType} implementation for offline exported JSON catalogs
 * and headless synthetic benchmarks, producing real {@link ContentStack}, {@link ComponentStackKey},
 * {@link TagKey}, and {@link MachineEntry} instances without requiring a running Minecraft registry.
 */
public final class OfflineContentTypes {

    public static final Namespace itemContentType = Namespace.ofMc("item");
    public static final Namespace fluidContentType = Namespace.ofMc("fluid");

    private static final Gson compactGson = new GsonBuilder().disableHtmlEscaping().create();

    private static final ConcurrentHashMap<Namespace, OfflineStackType> stackTypesByContentType = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ComponentStackKey> bareKeyCache = new ConcurrentHashMap<>();

    private OfflineContentTypes() {}

    public record OfflineContent(
        Namespace contentTypeId,
        ResourceLocation location,
        String rawId,
        @Nullable String canonicalComponents
    ) {
        public OfflineContent bare() {
            return canonicalComponents == null ? this : new OfflineContent(contentTypeId, location, rawId, null);
        }

        public boolean isEmpty() {
            return rawId.isEmpty();
        }
    }

    public record OfflineStack(OfflineContent content, long amount) {
        public static final OfflineStack empty = new OfflineStack(
            new OfflineContent(itemContentType, ResourceLocation.fromNamespaceAndPath("minecraft", "air"), "", null),
            0L
        );

        public boolean isEmpty() {
            return amount <= 0L || content.isEmpty();
        }
    }

    public static final class OfflineContentType implements ContentType<OfflineContent> {
        private final Namespace typeId;

        OfflineContentType(Namespace typeId) {
            this.typeId = Objects.requireNonNull(typeId, "typeId");
        }

        @Override
        public Namespace typeId() {
            return typeId;
        }

        @Override
        public @Nullable TagType<OfflineContent> tagType() {
            return null;
        }

        @Override
        public @Nullable String getNamespace(OfflineContent value) {
            return value == null ? null : value.location().getNamespace();
        }

        @Override
        public @Nullable ResourceLocation getLocation(OfflineContent value) {
            if (value == null) return null;
            // When canonicalComponents is attached, return null so ComponentStackKey.canonicalKey/displayId
            // uses asString(value) which deterministically includes the component payload.
            return value.canonicalComponents() == null ? value.location() : null;
        }

        @Override
        public @Nullable ResourceKey<Registry<OfflineContent>> getRegistryKey() {
            return null;
        }

        @Override
        public boolean isEmpty(OfflineContent value) {
            return value == null || value.isEmpty();
        }

        @Override
        public boolean matches(OfflineContent value1, OfflineContent value2) {
            return Objects.equals(value1, value2);
        }

        @Override
        public int hash(OfflineContent value) {
            return Objects.hashCode(value);
        }

        @Override
        public @Nullable Codec<OfflineContent> contentCodec() {
            return null;
        }

        @Override
        public String asString(OfflineContent value) {
            if (value == null) return "";
            return value.canonicalComponents() == null
                    ? value.rawId()
                    : value.rawId() + "|" + value.canonicalComponents();
        }
    }

    public static final class OfflineStackType implements StackType<OfflineContent, OfflineStack> {
        private final Namespace stackTypeId;
        private final OfflineContentType contentType;
        private final long unitQuantity;

        OfflineStackType(Namespace contentTypeId) {
            this.contentType = new OfflineContentType(contentTypeId);
            this.stackTypeId = Namespace.of(contentTypeId.namespace(), contentTypeId.path() + "_stack");
            this.unitQuantity = fluidContentType.equals(contentTypeId) ? 1000L : 1L;
        }

        @Override
        public Namespace typeId() {
            return stackTypeId;
        }

        @Override
        public ContentType<OfflineContent> contentType() {
            return contentType;
        }

        @Override
        public OfflineContent getContent(OfflineStack stack) {
            return stack.content();
        }

        @Override
        public long maxAmount() {
            return Long.MAX_VALUE;
        }

        @Override
        public long quantity() {
            return unitQuantity;
        }

        @Override
        public long getAmount(OfflineStack stack) {
            return stack.amount();
        }

        @Override
        public long getMaxStackSize(OfflineStack stack) {
            return fluidContentType.equals(contentType.typeId()) ? 1000L : 64L;
        }

        @Override
        public boolean isEmpty(OfflineStack stack) {
            return stack == null || stack.isEmpty();
        }

        @Override
        public boolean supportComponents() {
            return false;
        }

        @Override
        public @Nullable DataComponentMap getComponents(OfflineStack stack) {
            return null;
        }

        @Override
        public DataComponentPatch getComponentsPatch(OfflineStack stack) {
            return DataComponentPatch.EMPTY;
        }

        @Override
        public boolean hasComponent(OfflineStack stack, DataComponentType<?> componentType) {
            return false;
        }

        @Override
        public <C> @Nullable C getComponent(OfflineStack stack, DataComponentType<? extends C> componentType) {
            return null;
        }

        @Override
        public @Nullable Codec<OfflineStack> stackCodec() {
            return null;
        }

        @Override
        public boolean matchesIdentity(
            OfflineStack stack1,
            OfflineStack stack2,
            VariantType<OfflineContent, OfflineStack> variantType
        ) {
            return Objects.equals(stack1.content(), stack2.content());
        }

        @Override
        public boolean matches(
            OfflineStack stack1,
            OfflineStack stack2,
            VariantType<OfflineContent, OfflineStack> variantType,
            MatchStrategy strategy
        ) {
            if (!Objects.equals(stack1.content(), stack2.content())) {
                return false;
            }
            return !strategy.withAmount() || stack1.amount() == stack2.amount();
        }

        @Override
        public long mergeDiff(OfflineStack stack1, OfflineStack stack2, MatchStrategy strategy) {
            if (!Objects.equals(stack1.content(), stack2.content())) {
                return 0L;
            }
            return stack1.amount() - stack2.amount();
        }

        @Override
        public OfflineStack copy(OfflineStack stack) {
            return stack;
        }

        @Override
        public OfflineStack copyWithAmount(OfflineStack stack, long amount) {
            return amount <= 0L ? OfflineStack.empty : new OfflineStack(stack.content(), amount);
        }

        @Override
        public OfflineStack normalize(OfflineStack stack) {
            return stack.isEmpty() ? OfflineStack.empty : new OfflineStack(stack.content(), unitQuantity);
        }

        @Override
        public OfflineStack wildcard(OfflineStack stack) {
            return stack.isEmpty() ? OfflineStack.empty : new OfflineStack(stack.content().bare(), stack.amount());
        }

        @Override
        public OfflineStack fromStack(OfflineStack stack, long amount, DataComponentPatch componentPatch) {
            return amount <= 0L || stack.isEmpty() ? OfflineStack.empty : new OfflineStack(stack.content(), amount);
        }

        @Override
        public OfflineStack fromContent(OfflineContent content, long amount, DataComponentPatch componentPatch) {
            return amount <= 0L || content.isEmpty() ? OfflineStack.empty : new OfflineStack(content, amount);
        }
    }

    public static OfflineStackType stackTypeFor(Namespace contentTypeId) {
        return stackTypesByContentType.computeIfAbsent(contentTypeId, OfflineStackType::new);
    }

    public static ComponentStackKey item(String resourceId) {
        return bare(itemContentType, resourceId);
    }

    public static ComponentStackKey fluid(String resourceId) {
        return bare(fluidContentType, resourceId);
    }

    public static ComponentStackKey bare(Namespace contentTypeId, String resourceId) {
        String cleanId = resourceId == null ? "" : resourceId.trim();
        if (cleanId.isEmpty()) {
            throw new IllegalArgumentException("resourceId must not be empty");
        }
        String cacheKey = contentTypeId + "|" + cleanId;
        return bareKeyCache.computeIfAbsent(cacheKey, k -> buildKey(contentTypeId, cleanId, null));
    }

    public static ComponentStackKey withComponents(
        Namespace contentTypeId,
        String resourceId,
        @Nullable JsonElement components
    ) {
        String cleanId = resourceId == null ? "" : resourceId.trim();
        if (cleanId.isEmpty()) {
            throw new IllegalArgumentException("resourceId must not be empty");
        }
        if (components == null
                || components.isJsonNull()
                || (components.isJsonObject() && components.getAsJsonObject().isEmpty())) {
            return bare(contentTypeId, cleanId);
        }
        String canonical = canonicalizeJson(components);
        if (canonical.isEmpty() || "{}".equals(canonical) || "null".equals(canonical)) {
            return bare(contentTypeId, cleanId);
        }
        return buildKey(contentTypeId, cleanId, canonical);
    }

    public static MachineEntry machine(String resourceId) {
        return MachineEntry.fromKey(item(resourceId));
    }

    public static TagKey<?> tagFor(Namespace contentTypeId, String rawTagId) {
        ResourceLocation loc = toSafeLocation(rawTagId);
        if (fluidContentType.equals(contentTypeId)) {
            return PlanningTagKeys.fluidTag(loc);
        }
        if (itemContentType.equals(contentTypeId)) {
            return PlanningTagKeys.itemTag(loc);
        }
        ResourceLocation regLoc = ResourceLocation.tryParse(contentTypeId.toString());
        return regLoc != null ? PlanningTagKeys.createUntyped(regLoc, loc) : PlanningTagKeys.itemTag(loc);
    }

    public static @Nullable Namespace normalizeContentTypeId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "item", "minecraft:item", "minecraft:item_stack", "item_stack" -> itemContentType;
            case "fluid", "minecraft:fluid", "minecraft:fluid_stack", "fluid_stack" -> fluidContentType;
            default -> {
                if (trimmed.endsWith("_stack")) {
                    trimmed = trimmed.substring(0, trimmed.length() - "_stack".length());
                }
                yield parseNamespace(trimmed);
            }
        };
    }

    public static Namespace parseNamespace(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return Namespace.ofMc("unknown");
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            try {
                return Namespace.parse(trimmed);
            } catch (RuntimeException ignored) {}
        }
        return Namespace.ofMc(sanitizePath(trimmed));
    }

    public static Optional<ResourceLocation> tryParseLocation(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw.trim());
        return Optional.ofNullable(parsed);
    }

    public static Optional<SlotMatchMode> parseMatchStrategy(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(SlotMatchMode.exactComponent);
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        return switch (norm) {
            case "identity", "identity_only", "identity_and_amount" -> Optional.of(SlotMatchMode.identityOnly);
            case "component_subset", "subset", "partial_component" -> Optional.of(SlotMatchMode.componentSubset);
            case "identity_and_component", "exact_component", "all", "exact", "default" ->
                Optional.of(SlotMatchMode.exactComponent);
            default -> {
                if (norm.contains("identity")) {
                    if (norm.contains("component")) {
                        yield Optional.of(
                            norm.contains("subset") || norm.contains("partial")
                                    ? SlotMatchMode.componentSubset
                                    : SlotMatchMode.exactComponent
                        );
                    }
                    yield Optional.of(SlotMatchMode.identityOnly);
                }
                yield Optional.empty();
            }
        };
    }

    public static SlotMatchMode listSlotMatchMode(List<ComponentStackKey> candidates) {
        for (ComponentStackKey c : candidates) {
            if (c.hasComponents()) {
                return SlotMatchMode.exactComponent;
            }
            if (c.stack().content() instanceof OfflineContent oc && oc.canonicalComponents() != null) {
                return SlotMatchMode.exactComponent;
            }
        }
        return SlotMatchMode.identityOnly;
    }

    public static String canonicalizeJson(JsonElement element) {
        return compactGson.toJson(sortJson(element));
    }

    private static JsonElement sortJson(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
            return element;
        }
        if (element.isJsonArray()) {
            JsonArray src = element.getAsJsonArray();
            JsonArray out = new JsonArray(src.size());
            for (JsonElement e : src) {
                out.add(sortJson(e));
            }
            return out;
        }
        JsonObject src = element.getAsJsonObject();
        TreeMap<String, JsonElement> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : src.entrySet()) {
            sorted.put(e.getKey(), sortJson(e.getValue()));
        }
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : sorted.entrySet()) {
            out.add(e.getKey(), e.getValue());
        }
        return out;
    }

    private static ComponentStackKey buildKey(
        Namespace contentTypeId,
        String rawId,
        @Nullable String canonicalComponents
    ) {
        OfflineStackType stackType = stackTypeFor(contentTypeId);
        ResourceLocation loc = toSafeLocation(rawId);
        OfflineContent content = new OfflineContent(contentTypeId, loc, rawId, canonicalComponents);
        ContentStack<OfflineContent, OfflineStack> stack = ContentStack.fromContent(
            stackType,
            content,
            stackType.quantity()
        );
        return ComponentStackKey.of(stack);
    }

    private static ResourceLocation toSafeLocation(String raw) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed != null) {
            return parsed;
        }
        int colon = raw.indexOf(':');
        if (colon > 0 && colon < raw.length() - 1) {
            String ns = sanitizeNamespace(raw.substring(0, colon));
            String path = sanitizePath(raw.substring(colon + 1));
            return ResourceLocation.fromNamespaceAndPath(ns, path);
        }
        return ResourceLocation.fromNamespaceAndPath("minecraft", sanitizePath(raw));
    }

    private static String sanitizeNamespace(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "minecraft" : sb.toString();
    }

    private static String sanitizePath(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "unknown" : sb.toString();
    }
}
