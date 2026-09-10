package dev.vfyjxf.taffy.collection;

import java.util.Arrays;

/**
 * CrystalGUI: {@link Int2FloatMap} as an open-addressed table with linear probing, replacing
 * fastutil's {@code Int2FloatOpenHashMap}.
 *
 * <p><b>There is no removal</b>, because Taffy never removes from one — which is also what lets the
 * probe never pass a tombstone: a chain ends at the first free slot and always will. Adding a
 * {@code remove} means adding the backward-shift deletion {@link Long2ObjectOpenHashMap} carries,
 * never a tombstone flag.</p>
 *
 * @see dev.vfyjxf.taffy.collection
 */
public final class Int2FloatOpenHashMap implements Int2FloatMap {

    /** 2^32 / golden ratio — fastutil's own multiplier, kept so key distribution is unchanged. */
    private static final int INT_PHI = 0x9E3779B9;

    private static final float LOAD_FACTOR = 0.75f;

    private int[] keys;
    private float[] values;
    private boolean[] used;
    private int mask;
    private int maxFill;
    private int size;
    private float defaultReturnValue;

    public Int2FloatOpenHashMap() {
        this(16);
    }

    public Int2FloatOpenHashMap(int expected) {
        allocate(tableSizeFor(expected));
    }

    @Override
    public void defaultReturnValue(float value) {
        this.defaultReturnValue = value;
    }

    @Override
    public float get(int key) {
        int slot = mix(key) & mask;
        while (used[slot]) {
            if (keys[slot] == key) return values[slot];
            slot = (slot + 1) & mask;
        }
        return defaultReturnValue;
    }

    @Override
    public float put(int key, float value) {
        int slot = mix(key) & mask;
        while (used[slot]) {
            if (keys[slot] == key) {
                float previous = values[slot];
                values[slot] = value;
                return previous;
            }
            slot = (slot + 1) & mask;
        }
        used[slot] = true;
        keys[slot] = key;
        values[slot] = value;
        if (++size >= maxFill) rehash();
        return defaultReturnValue;
    }

    public boolean containsKey(int key) {
        int slot = mix(key) & mask;
        while (used[slot]) {
            if (keys[slot] == key) return true;
            slot = (slot + 1) & mask;
        }
        return false;
    }

    public int size() {
        return size;
    }

    private void rehash() {
        int[] oldKeys = keys;
        float[] oldValues = values;
        boolean[] oldUsed = used;
        allocate(oldUsed.length << 1);
        for (int i = 0; i < oldUsed.length; i++) {
            if (!oldUsed[i]) continue;
            int slot = mix(oldKeys[i]) & mask;
            while (used[slot]) slot = (slot + 1) & mask;
            used[slot] = true;
            keys[slot] = oldKeys[i];
            values[slot] = oldValues[i];
        }
    }

    private void allocate(int capacity) {
        keys = new int[capacity];
        values = new float[capacity];
        used = new boolean[capacity];
        mask = capacity - 1;
        maxFill = (int) (capacity * LOAD_FACTOR);
        // A table that can fill completely cannot terminate a probe. Only reachable at capacity 1,
        // which tableSizeFor already refuses, so this is a belt on a brace.
        if (maxFill >= capacity) maxFill = capacity - 1;
    }

    /** The smallest power of two that holds {@code expected} entries under the load factor. */
    private static int tableSizeFor(int expected) {
        int needed = Math.max(2, (int) Math.ceil(Math.max(1, expected) / (double) LOAD_FACTOR));
        int capacity = 2;
        while (capacity < needed) capacity <<= 1;
        return capacity;
    }

    private static int mix(int x) {
        int h = x * INT_PHI;
        return h ^ (h >>> 16);
    }

    @Override
    public String toString() {
        return "Int2FloatOpenHashMap(size=" + size + ", capacity=" + used.length + ", keys="
                + Arrays.toString(keys) + ")";
    }
}
