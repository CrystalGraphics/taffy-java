package dev.vfyjxf.taffy.collection;

import java.util.HashSet;
import java.util.Set;

/**
 * CrystalGUI: a {@code long}-keyed map, open-addressed with linear probing, replacing fastutil's
 * {@code Long2ObjectOpenHashMap}. This is {@code TaffyTree}'s node store — every node, its children,
 * its parent and its measure function are looked up through one of these on every layout.
 *
 * <pre>{@code
 * Long2ObjectOpenHashMap<NodeData> nodes = new Long2ObjectOpenHashMap<>(64);
 * nodes.put(id, data);
 * NodeData found = nodes.get(id);        // null when absent
 * for (Long key : nodes.keySet()) { ... }
 * }</pre>
 *
 * <p><b>Removal shifts, it does not tombstone.</b> Linear probing ends a chain at the first free
 * slot, so blanking a slot in the middle of one strands every entry behind it — they are still in the
 * table, {@code get} returns null, and nothing reports it. {@link #shiftKeys} is Knuth 6.4 algorithm R
 * (fastutil's own formulation): it pulls a later entry back into the hole whenever doing so does not
 * move it in front of its ideal slot. {@code Long2ObjectOpenHashMapTest} drives it against a
 * {@code java.util.HashMap} over random operations, because a broken probe chain is invisible until a
 * particular deletion order produces it.</p>
 *
 * @see dev.vfyjxf.taffy.collection
 */
public final class Long2ObjectOpenHashMap<V> {

    /** 2^64 / golden ratio — fastutil's own multiplier, kept so key distribution is unchanged. */
    private static final long LONG_PHI = 0x9E3779B97F4A7C15L;

    private static final float LOAD_FACTOR = 0.75f;

    private long[] keys;
    private Object[] values;
    private boolean[] used;
    private int mask;
    private int maxFill;
    private int size;

    public Long2ObjectOpenHashMap() {
        this(16);
    }

    public Long2ObjectOpenHashMap(int expected) {
        allocate(tableSizeFor(expected));
    }

    @SuppressWarnings("unchecked")
    public V get(long key) {
        int slot = slotOf(key);
        while (used[slot]) {
            if (keys[slot] == key) return (V) values[slot];
            slot = (slot + 1) & mask;
        }
        return null;
    }

    public boolean containsKey(long key) {
        int slot = slotOf(key);
        while (used[slot]) {
            if (keys[slot] == key) return true;
            slot = (slot + 1) & mask;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public V put(long key, V value) {
        int slot = slotOf(key);
        while (used[slot]) {
            if (keys[slot] == key) {
                V previous = (V) values[slot];
                values[slot] = value;
                return previous;
            }
            slot = (slot + 1) & mask;
        }
        used[slot] = true;
        keys[slot] = key;
        values[slot] = value;
        if (++size >= maxFill) rehash();
        return null;
    }

    @SuppressWarnings("unchecked")
    public V remove(long key) {
        int slot = slotOf(key);
        while (used[slot]) {
            if (keys[slot] == key) {
                V previous = (V) values[slot];
                size--;
                shiftKeys(slot);
                return previous;
            }
            slot = (slot + 1) & mask;
        }
        return null;
    }

    public void clear() {
        java.util.Arrays.fill(used, false);
        java.util.Arrays.fill(values, null);
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * The keys, boxed. Cold path only — {@code TaffyTree.getAllNodes} builds a {@code Set} of its own
     * from the result, so the boxing here is not the one that would matter.
     */
    public Set<Long> keySet() {
        Set<Long> result = new HashSet<>(Math.max(4, size * 2));
        for (int i = 0; i < used.length; i++) {
            if (used[i]) result.add(keys[i]);
        }
        return result;
    }

    /**
     * Closes the hole at {@code from} by pulling later entries back into it.
     *
     * <p>An entry may move into the hole only when the hole is not in front of that entry's ideal
     * slot — otherwise a probe starting at the ideal slot would walk past it. The two arms of the
     * test are the non-wrapped and wrapped cases of "is {@code slot} inside the interval
     * {@code (last, pos]}"; when it is, the entry stays and the scan moves on.</p>
     */
    private void shiftKeys(int from) {
        int pos = from;
        while (true) {
            int last = pos;
            pos = (pos + 1) & mask;
            while (true) {
                if (!used[pos]) {
                    used[last] = false;
                    values[last] = null;
                    return;
                }
                int slot = slotOf(keys[pos]);
                if (last <= pos ? (last >= slot || slot > pos) : (last >= slot && slot > pos)) break;
                pos = (pos + 1) & mask;
            }
            keys[last] = keys[pos];
            values[last] = values[pos];
        }
    }

    private void rehash() {
        long[] oldKeys = keys;
        Object[] oldValues = values;
        boolean[] oldUsed = used;
        allocate(oldUsed.length << 1);
        for (int i = 0; i < oldUsed.length; i++) {
            if (!oldUsed[i]) continue;
            int slot = slotOf(oldKeys[i]);
            while (used[slot]) slot = (slot + 1) & mask;
            used[slot] = true;
            keys[slot] = oldKeys[i];
            values[slot] = oldValues[i];
        }
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new Object[capacity];
        used = new boolean[capacity];
        mask = capacity - 1;
        maxFill = (int) (capacity * LOAD_FACTOR);
        // A table that can fill completely cannot terminate a probe.
        if (maxFill >= capacity) maxFill = capacity - 1;
    }

    private int slotOf(long key) {
        return (int) (mix(key) & mask);
    }

    /** The smallest power of two that holds {@code expected} entries under the load factor. */
    private static int tableSizeFor(int expected) {
        int needed = Math.max(2, (int) Math.ceil(Math.max(1, expected) / (double) LOAD_FACTOR));
        int capacity = 2;
        while (capacity < needed) capacity <<= 1;
        return capacity;
    }

    private static long mix(long x) {
        long h = x * LONG_PHI;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }

    @Override
    public String toString() {
        return "Long2ObjectOpenHashMap(size=" + size + ", capacity=" + used.length + ")";
    }
}
