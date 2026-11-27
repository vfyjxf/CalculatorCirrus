package dev.vfyjxf.cirrus.api.planning.model;

import java.util.Objects;
import java.util.Optional;

public record ResourceKey(String contentTypeId, String resourceId, Optional<String> canonicalVariantHash)
        implements
            Comparable<ResourceKey> {

    public ResourceKey {
        Objects.requireNonNull(contentTypeId, "contentTypeId");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(canonicalVariantHash, "canonicalVariantHash");
        if (contentTypeId.isEmpty()) {
            throw new IllegalArgumentException("contentTypeId must not be empty");
        }
        if (resourceId.isEmpty()) {
            throw new IllegalArgumentException("resourceId must not be empty");
        }
        if (canonicalVariantHash.isPresent() && canonicalVariantHash.get().isEmpty()) {
            canonicalVariantHash = Optional.empty();
        }
    }

    public static ResourceKey item(String itemId) {
        return new ResourceKey("minecraft:item", itemId, Optional.empty());
    }

    public static ResourceKey item(String itemId, String variantHash) {
        return new ResourceKey(
            "minecraft:item",
            itemId,
            (variantHash == null || variantHash.isEmpty()) ? Optional.empty() : Optional.of(variantHash)
        );
    }

    public static ResourceKey fluid(String fluidId) {
        return new ResourceKey("minecraft:fluid", fluidId, Optional.empty());
    }

    public boolean sameBaseIdentity(ResourceKey other) {
        return contentTypeId.equals(other.contentTypeId) && resourceId.equals(other.resourceId);
    }

    public String canonicalString() {
        String suffix = canonicalVariantHash.map(h -> "|" + h).orElse("");
        return contentTypeId + "|" + resourceId + suffix;
    }

    public String shortString() {
        String prefix = "minecraft:item".equals(contentTypeId) ? "" : "[" + contentTypeId + "]";
        String suffix = canonicalVariantHash.map(h -> "#" + h.substring(0, Math.min(6, h.length()))).orElse("");
        return prefix + resourceId + suffix;
    }

    @Override
    public int compareTo(ResourceKey other) {
        int c = contentTypeId.compareTo(other.contentTypeId);
        if (c != 0) return c;
        c = resourceId.compareTo(other.resourceId);
        if (c != 0) return c;
        return canonicalVariantHash.orElse("").compareTo(other.canonicalVariantHash.orElse(""));
    }
}
