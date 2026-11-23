package dev.vfyjxf.cirrus.planning.adapter;

import com.google.gson.JsonElement;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class StackIdentity {

    public static final String ITEM_CONTENT_TYPE = "minecraft:item";
    public static final String FLUID_CONTENT_TYPE = "minecraft:fluid";

    private StackIdentity() {
    }

    public record MatchFlags(
            boolean withIdentity,
            boolean withAmount,
            boolean withComponent,
            boolean exactComponents
    ) {
        public static final MatchFlags IDENTITY = new MatchFlags(true, false, false, false);
        public static final MatchFlags IDENTITY_AND_COMPONENT = new MatchFlags(true, false, true, true);
        public static final MatchFlags IDENTITY_AND_AMOUNT = new MatchFlags(true, true, false, false);
        public static final MatchFlags ALL = new MatchFlags(true, true, true, true);
        public static final MatchFlags AMOUNT = new MatchFlags(false, true, false, false);
        public static final MatchFlags NONE = new MatchFlags(false, false, false, false);

        public MatchFlags {
            withIdentity = withIdentity || withComponent;
            exactComponents = withComponent && exactComponents;
        }

        public Optional<SlotMatchMode> toSlotMatchMode() {
            if (!withIdentity) {
                return Optional.empty();
            }
            if (withComponent) {
                return exactComponents ? Optional.of(SlotMatchMode.IDENTITY_AND_COMPONENT) : Optional.empty();
            }
            return Optional.of(SlotMatchMode.IDENTITY);
        }

        public static Optional<MatchFlags> parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.of(IDENTITY);
            }
            String norm = raw.trim().toLowerCase(Locale.ROOT);
            return switch (norm) {
                case "identity" -> Optional.of(IDENTITY);
                case "identityandcomponent", "identity_and_component" -> Optional.of(IDENTITY_AND_COMPONENT);
                case "identityandamount", "identity_and_amount" -> Optional.of(IDENTITY_AND_AMOUNT);
                case "all" -> Optional.of(ALL);
                case "amount" -> Optional.of(AMOUNT);
                case "none" -> Optional.of(NONE);
                default -> parseComposite(norm);
            };
        }

        private static Optional<MatchFlags> parseComposite(String norm) {
            if (norm.indexOf('+') < 0 && norm.indexOf(',') < 0 && norm.indexOf('|') < 0) {
                return Optional.empty();
            }
            boolean id = false;
            boolean amt = false;
            boolean comp = false;
            String[] parts = norm.split("[+,|]");
            for (String part : parts) {
                String token = part.trim();
                if (token.isEmpty()) {
                    return Optional.empty();
                }
                switch (token) {
                    case "identity" -> id = true;
                    case "amount" -> amt = true;
                    case "component", "components" -> comp = true;
                    default -> {
                        return Optional.empty();
                    }
                }
            }
            return Optional.of(new MatchFlags(id, amt, comp, comp));
        }
    }

    public record StackSpec(
            String contentTypeId,
            String resourceId,
            @Nullable JsonElement componentsJson,
            @Nullable String canonicalComponentsJson,
            MatchFlags matchFlags
    ) {
        public StackSpec(
                String contentTypeId,
                String resourceId,
                @Nullable JsonElement componentsJson,
                MatchFlags matchFlags
        ) {
            this(contentTypeId, resourceId, componentsJson, null, matchFlags);
        }

        public StackSpec(
                String contentTypeId,
                String resourceId,
                @Nullable String componentsJson,
                MatchFlags matchFlags
        ) {
            this(contentTypeId, resourceId, null, componentsJson, matchFlags);
        }

        public StackSpec {
            Objects.requireNonNull(matchFlags, "matchFlags");
        }
    }

    public record MappedStack(Optional<ResourceKey> key, Optional<SlotMatchMode> mode) {
        public MappedStack {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(mode, "mode");
        }

        public boolean isValid() {
            return key.isPresent() && mode.isPresent();
        }
    }

    public static String normalizeContentTypeId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        return switch (trimmed) {
            case "item", "minecraft:item", "minecraft:item_stack" -> ITEM_CONTENT_TYPE;
            case "fluid", "minecraft:fluid", "minecraft:fluid_stack" -> FLUID_CONTENT_TYPE;
            default -> trimmed.indexOf(':') >= 0 ? trimmed : "custom:" + trimmed;
        };
    }

    public static String unencodableFallbackToken(String componentId, Object value) {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(value, "value");
        return "!" + componentId + "=" + value.getClass().getName() + ":" + value.hashCode();
    }

    public static Optional<String> hashOf(@Nullable JsonElement componentsJson) {
        return CanonicalJson.variantHash(componentsJson);
    }

    public static Optional<String> hashOfCanonical(@Nullable String canonicalJson) {
        if (canonicalJson == null || canonicalJson.isBlank()) {
            return Optional.empty();
        }
        String trimmed = canonicalJson.trim();
        if (trimmed.equals("{}") || trimmed.equals("null")) {
            return Optional.empty();
        }
        return Optional.of(CanonicalJson.sha256Hex16(trimmed));
    }

    public static MappedStack mapInput(StackSpec spec) {
        Objects.requireNonNull(spec, "spec");
        Optional<SlotMatchMode> mode = spec.matchFlags().toSlotMatchMode();
        String typeId = normalizeContentTypeId(spec.contentTypeId());
        String resId = spec.resourceId() == null ? "" : spec.resourceId().trim();
        if (typeId.isEmpty() || resId.isEmpty()) {
            return new MappedStack(Optional.empty(), mode);
        }
        Optional<String> variantHash = Optional.empty();
        if (mode.isPresent() && mode.get() == SlotMatchMode.IDENTITY_AND_COMPONENT) {
            variantHash = resolveVariantHash(spec.componentsJson(), spec.canonicalComponentsJson());
        }
        return new MappedStack(Optional.of(new ResourceKey(typeId, resId, variantHash)), mode);
    }

    public static Optional<ResourceKey> mapConcrete(
            String contentTypeId,
            String resourceId,
            @Nullable JsonElement componentsJson
    ) {
        String typeId = normalizeContentTypeId(contentTypeId);
        String resId = resourceId == null ? "" : resourceId.trim();
        if (typeId.isEmpty() || resId.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> variantHash = CanonicalJson.variantHash(componentsJson);
        return Optional.of(new ResourceKey(typeId, resId, variantHash));
    }

    public static Optional<ResourceKey> mapConcrete(
            String contentTypeId,
            String resourceId,
            @Nullable String canonicalComponentsJson
    ) {
        String typeId = normalizeContentTypeId(contentTypeId);
        String resId = resourceId == null ? "" : resourceId.trim();
        if (typeId.isEmpty() || resId.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> variantHash = hashOfCanonical(canonicalComponentsJson);
        return Optional.of(new ResourceKey(typeId, resId, variantHash));
    }

    public static SlotMatchMode listSlotMatchMode(List<ResourceKey> candidates) {
        for (ResourceKey candidate : candidates) {
            if (candidate.canonicalVariantHash().isPresent()) {
                return SlotMatchMode.IDENTITY_AND_COMPONENT;
            }
        }
        return SlotMatchMode.IDENTITY;
    }

    private static Optional<String> resolveVariantHash(
            @Nullable JsonElement componentsJson,
            @Nullable String canonicalComponentsJson
    ) {
        if (componentsJson != null) {
            return CanonicalJson.variantHash(componentsJson);
        }
        if (canonicalComponentsJson != null && !canonicalComponentsJson.isBlank()) {
            Optional<String> canonical = CanonicalJson.canonicalizeString(canonicalComponentsJson);
            return canonical.map(CanonicalJson::sha256Hex16);
        }
        return Optional.empty();
    }
}
