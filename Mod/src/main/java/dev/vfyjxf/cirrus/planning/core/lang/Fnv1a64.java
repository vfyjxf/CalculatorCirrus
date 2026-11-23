package dev.vfyjxf.cirrus.planning.core.lang;

import java.nio.charset.StandardCharsets;

public final class Fnv1a64 {

    private static final long OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private Fnv1a64() {
    }

    public static long hashUtf8(String canonical) {
        return hashBytes(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public static long hashBytes(byte[] bytes) {
        long h = OFFSET_BASIS;
        for (byte b : bytes) {
            h ^= (b & 0xffL);
            h *= PRIME;
        }
        return h;
    }

    public static long mixString(long h, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                h ^= c;
                h *= PRIME;
            } else {
                byte[] utf8 = s.substring(i).getBytes(StandardCharsets.UTF_8);
                for (byte b : utf8) {
                    h ^= (b & 0xffL);
                    h *= PRIME;
                }
                return h;
            }
        }
        return h;
    }

    public static long mixByte(long h, int b) {
        h ^= (b & 0xffL);
        return h * PRIME;
    }

    public static long mixLong(long h, long value) {
        return mixString(h, Long.toString(value));
    }

    public static long initial() {
        return OFFSET_BASIS;
    }
}
