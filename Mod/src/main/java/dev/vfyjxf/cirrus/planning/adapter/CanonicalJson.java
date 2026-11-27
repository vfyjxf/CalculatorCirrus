package dev.vfyjxf.cirrus.planning.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;

public final class CanonicalJson {

    private static final char[] hex = "0123456789abcdef".toCharArray();

    private CanonicalJson() {}

    public static String canonicalize(@Nullable JsonElement element) {
        StringBuilder sb = new StringBuilder(64);
        writeCanonical(element, sb);
        return sb.toString();
    }

    public static Optional<String> canonicalizeString(@Nullable String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement parsed = JsonParser.parseString(rawJson);
            if (parsed == null || parsed.isJsonNull()) {
                return Optional.empty();
            }
            if (parsed.isJsonObject() && parsed.getAsJsonObject().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(canonicalize(parsed));
        } catch (JsonSyntaxException e) {
            return Optional.of(rawJson.trim());
        }
    }

    public static Optional<String> canonicalizeObject(@Nullable JsonObject obj) {
        if (obj == null || obj.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(canonicalize(obj));
    }

    public static String sha256Hex16(String utf8Text) {
        byte[] digest = sha256(utf8Text.getBytes(StandardCharsets.UTF_8));
        char[] out = new char[16];
        for (int i = 0; i < 8; i++) {
            int b = digest[i] & 0xFF;
            out[i * 2] = hex[b >>> 4];
            out[i * 2 + 1] = hex[b & 0x0F];
        }
        return new String(out);
    }

    public static Optional<String> variantHash(@Nullable JsonElement components) {
        if (components == null || components.isJsonNull()) {
            return Optional.empty();
        }
        if (components.isJsonObject() && components.getAsJsonObject().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sha256Hex16(canonicalize(components)));
    }

    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static void appendQuotedString(String value, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u00");
                        sb.append(hex[(c >>> 4) & 0x0F]);
                        sb.append(hex[c & 0x0F]);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void writeCanonical(@Nullable JsonElement element, StringBuilder sb) {
        if (element == null || element instanceof JsonNull) {
            sb.append("null");
            return;
        }
        if (element instanceof JsonObject obj) {
            int size = obj.size();
            if (size == 0) {
                sb.append("{}");
                return;
            }
            String[] keys = obj.keySet().toArray(new String[size]);
            Arrays.sort(keys);
            sb.append('{');
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                String key = keys[i];
                appendQuotedString(key, sb);
                sb.append(':');
                writeCanonical(obj.get(key), sb);
            }
            sb.append('}');
            return;
        }
        if (element instanceof JsonArray arr) {
            sb.append('[');
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeCanonical(arr.get(i), sb);
            }
            sb.append(']');
            return;
        }
        if (element instanceof JsonPrimitive prim) {
            if (prim.isBoolean()) {
                sb.append(prim.getAsBoolean() ? "true" : "false");
            } else if (prim.isString()) {
                appendQuotedString(prim.getAsString(), sb);
            } else if (prim.isNumber()) {
                sb.append(canonicalNumber(prim));
            } else {
                sb.append("null");
            }
            return;
        }
        sb.append("null");
    }

    private static String canonicalNumber(JsonPrimitive prim) {
        String raw = prim.getAsString();
        if (isSimpleInteger(raw)) {
            return raw.equals("-0") ? "0" : raw;
        }
        try {
            BigDecimal bd = new BigDecimal(raw);
            if (bd.compareTo(BigDecimal.ZERO) == 0) {
                return "0";
            }
            BigDecimal stripped = bd.stripTrailingZeros();
            return stripped.scale() <= 0 ? stripped.toPlainString() : stripped.toString();
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static boolean isSimpleInteger(String s) {
        if (s.isEmpty()) {
            return false;
        }
        int start = s.charAt(0) == '-' ? 1 : 0;
        if (start == s.length()) {
            return false;
        }
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
