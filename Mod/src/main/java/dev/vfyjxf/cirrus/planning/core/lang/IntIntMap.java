package dev.vfyjxf.cirrus.planning.core.lang;

import java.util.Arrays;

public final class IntIntMap {

    private static final int EMPTY = Integer.MIN_VALUE;

    private int[] keys;
    private int[] values;
    private int mask;
    private int size;
    private int threshold;

    public IntIntMap(int expectedSize) {
        int cap = 16;
        int minCap = Math.max(16, (int) (expectedSize / 0.65) + 1);
        while (cap < minCap && cap > 0) {
            cap <<= 1;
        }
        if (cap <= 0) {
            cap = 1 << 30;
        }
        this.keys = new int[cap];
        this.values = new int[cap];
        Arrays.fill(this.keys, EMPTY);
        this.mask = cap - 1;
        this.threshold = (int) (cap * 0.65);
    }

    public int getOrDefault(int key, int defaultValue) {
        int idx = mix(key) & mask;
        while (true) {
            int k = keys[idx];
            if (k == EMPTY) {
                return defaultValue;
            }
            if (k == key) {
                return values[idx];
            }
            idx = (idx + 1) & mask;
        }
    }

    public int put(int key, int value) {
        if (key == EMPTY) {
            throw new IllegalArgumentException("reserved sentinel key");
        }
        if (size >= threshold) {
            rehash();
        }
        int idx = mix(key) & mask;
        while (true) {
            int k = keys[idx];
            if (k == EMPTY) {
                keys[idx] = key;
                values[idx] = value;
                size++;
                return -1;
            }
            if (k == key) {
                int prev = values[idx];
                values[idx] = value;
                return prev;
            }
            idx = (idx + 1) & mask;
        }
    }

    public int size() {
        return size;
    }

    public void clear() {
        Arrays.fill(keys, EMPTY);
        size = 0;
    }

    private void rehash() {
        int[] oldK = keys;
        int[] oldV = values;
        int newCap = oldK.length << 1;
        keys = new int[newCap];
        values = new int[newCap];
        Arrays.fill(keys, EMPTY);
        mask = newCap - 1;
        threshold = (int) (newCap * 0.65);
        for (int i = 0; i < oldK.length; i++) {
            int k = oldK[i];
            if (k != EMPTY) {
                int idx = mix(k) & mask;
                while (keys[idx] != EMPTY) {
                    idx = (idx + 1) & mask;
                }
                keys[idx] = k;
                values[idx] = oldV[i];
            }
        }
    }

    private static int mix(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }
}
