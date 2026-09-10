package dev.vfyjxf.taffy.collection;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * CrystalGUI: the vendored collections, against the JDK's own where one exists.
 *
 * <p>The map tests are <b>differential</b> and deliberately so. A linear-probing table with removal
 * fails by stranding entries behind a hole — they are in the table, {@code get} answers null, size is
 * right, and nothing throws. No hand-written case finds that; a few hundred thousand random
 * operations compared against a {@code HashMap} finds it on the first bad shift.</p>
 */
public class CollectionsTest {

    /** Small enough that collisions and wraparound are constant, which is where shifting breaks. */
    private static final int KEY_SPACE = 512;

    @Test
    public void longMapMatchesHashMapOverRandomOperations() {
        Random random = new Random(20260910L);
        Long2ObjectOpenHashMap<String> actual = new Long2ObjectOpenHashMap<>(4);
        Map<Long, String> expected = new HashMap<>();

        for (int step = 0; step < 200_000; step++) {
            long key = random.nextInt(KEY_SPACE);
            switch (random.nextInt(4)) {
                case 0: {
                    String value = "v" + step;
                    assertEquals("put returned the wrong previous value at step " + step,
                            expected.put(key, value), actual.put(key, value));
                    break;
                }
                case 1:
                    assertEquals("remove returned the wrong value at step " + step,
                            expected.remove(key), actual.remove(key));
                    break;
                case 2:
                    assertEquals("get disagreed at step " + step, expected.get(key), actual.get(key));
                    break;
                default:
                    assertEquals("containsKey disagreed at step " + step,
                            expected.containsKey(key), actual.containsKey(key));
                    break;
            }
            assertEquals("size diverged at step " + step, expected.size(), actual.size());
        }

        // EVERY key, not a sample: a stranded entry is one the probe cannot reach, and only a full
        // sweep of the key space distinguishes "absent" from "unreachable".
        for (long key = 0; key < KEY_SPACE; key++) {
            assertEquals("final get disagreed for " + key, expected.get(key), actual.get(key));
        }
        assertEquals(expected.keySet(), actual.keySet());
    }

    /** Negative and extreme keys: `mix` and the slot cast are where a sign bug would live. */
    @Test
    public void longMapHandlesNegativeAndExtremeKeys() {
        Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>(2);
        long[] keys = {0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, -1234567890123L, 1234567890123L};
        for (long key : keys) map.put(key, "v" + key);

        assertEquals(keys.length, map.size());
        for (long key : keys) {
            assertTrue("lost " + key, map.containsKey(key));
            assertEquals("v" + key, map.get(key));
        }
        Set<Long> seen = new HashSet<>();
        for (long key : keys) seen.add(key);
        assertEquals(seen, map.keySet());

        for (long key : keys) assertEquals("v" + key, map.remove(key));
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        for (long key : keys) assertNull(map.get(key));
    }

    @Test
    public void longMapClearForgetsEverything() {
        Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>(8);
        for (int i = 0; i < 100; i++) map.put(i, "v" + i);
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.keySet().isEmpty());
        for (int i = 0; i < 100; i++) assertNull(map.get(i));
        map.put(7, "again");
        assertEquals("again", map.get(7));
        assertEquals(1, map.size());
    }

    @Test
    public void intFloatMapMatchesHashMapOverRandomPuts() {
        Random random = new Random(4242L);
        Int2FloatOpenHashMap actual = new Int2FloatOpenHashMap(4);
        Map<Integer, Float> expected = new HashMap<>();
        actual.defaultReturnValue(Float.NaN);

        for (int step = 0; step < 100_000; step++) {
            int key = random.nextInt(KEY_SPACE) - KEY_SPACE / 2;
            if (random.nextBoolean()) {
                float value = random.nextFloat();
                Float previous = expected.put(key, value);
                float returned = actual.put(key, value);
                if (previous == null) {
                    assertTrue("put should answer the default for a new key", Float.isNaN(returned));
                } else {
                    assertEquals(previous, returned, 0f);
                }
            } else {
                Float want = expected.get(key);
                float got = actual.get(key);
                if (want == null) {
                    assertTrue("absent key must answer the default-return value", Float.isNaN(got));
                } else {
                    assertEquals(want, got, 0f);
                }
            }
            assertEquals(expected.size(), actual.size());
        }
    }

    /** The default-return value is the whole reason this type is not a `Map<Integer,Float>`. */
    @Test
    public void intFloatMapDefaultReturnValueIsZeroUntilSet() {
        Int2FloatOpenHashMap map = new Int2FloatOpenHashMap();
        assertEquals(0f, map.get(99), 0f);
        map.defaultReturnValue(Float.NaN);
        assertTrue(Float.isNaN(map.get(99)));
        map.put(99, 0f);
        assertEquals("a stored zero must be distinguishable from absent", 0f, map.get(99), 0f);
        assertTrue(map.containsKey(99));
        assertFalse(map.containsKey(98));
    }

    @Test
    public void intListGrowsAndReadsBack() {
        IntList list = new IntArrayList();
        assertTrue(list.isEmpty());
        for (int i = 0; i < 1000; i++) list.add(i * 3);

        assertEquals(1000, list.size());
        assertFalse(list.isEmpty());
        for (int i = 0; i < 1000; i++) assertEquals(i * 3, list.getInt(i));
        assertEquals(5, list.indexOf(15));
        assertEquals(-1, list.indexOf(16));
        assertTrue(list.contains(15));
        assertFalse(list.contains(16));

        int seen = 0;
        for (int value : list) assertEquals(seen++ * 3, value);
        assertEquals(1000, seen);
    }

    @Test
    public void floatListGrowsSetsAndReadsBack() {
        FloatList list = new FloatArrayList();
        assertTrue(list.isEmpty());
        for (int i = 0; i < 1000; i++) list.add(i * 0.5f);

        assertEquals(1000, list.size());
        for (int i = 0; i < 1000; i++) assertEquals(i * 0.5f, list.getFloat(i), 0f);

        assertEquals("set returns the previous value", 2f, list.set(4, 99f), 0f);
        assertEquals(99f, list.getFloat(4), 0f);

        int seen = 0;
        for (float value : list) {
            assertEquals(seen == 4 ? 99f : seen * 0.5f, value, 0f);
            seen++;
        }
        assertEquals(1000, seen);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void intListRefusesAnIndexPastTheEnd() {
        IntList list = new IntArrayList();
        list.add(1);
        list.getInt(1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void floatListRefusesAnIndexPastTheEnd() {
        FloatList list = new FloatArrayList();
        list.add(1f);
        list.getFloat(1);
    }
}
