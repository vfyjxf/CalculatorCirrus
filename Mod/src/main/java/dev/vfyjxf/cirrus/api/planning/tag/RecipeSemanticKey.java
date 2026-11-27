package dev.vfyjxf.cirrus.api.planning.tag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Deterministic SHA-256 canonical semantic key for recipes (handles missing-ID JEI recipes).
 */
public record RecipeSemanticKey(
    String systemId,
    String sha256Hex,
    String canonicalDescriptor,
    String lineageGroup
) implements Comparable<RecipeSemanticKey> {

    public RecipeSemanticKey {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        Objects.requireNonNull(canonicalDescriptor, "canonicalDescriptor");
        Objects.requireNonNull(lineageGroup, "lineageGroup");
    }

    public static RecipeSemanticKey of(String systemId, String canonicalDescriptor, String lineageGroup) {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(canonicalDescriptor, "canonicalDescriptor");
        Objects.requireNonNull(lineageGroup, "lineageGroup");
        return new RecipeSemanticKey(systemId, sha256Hex(canonicalDescriptor), canonicalDescriptor, lineageGroup);
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public int compareTo(RecipeSemanticKey other) {
        int c = this.systemId.compareTo(other.systemId);
        if (c != 0) return c;
        c = this.sha256Hex.compareTo(other.sha256Hex);
        if (c != 0) return c;
        return this.canonicalDescriptor.compareTo(other.canonicalDescriptor);
    }
}
