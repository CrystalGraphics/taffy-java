package dev.vfyjxf.taffy.collection;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * CrystalGUI: {@link IntList} over a plain {@code int[]}, replacing fastutil's {@code IntArrayList}.
 *
 * @see dev.vfyjxf.taffy.collection
 */
public final class IntArrayList implements IntList {

    private static final int[] EMPTY = new int[0];

    private int[] values;
    private int size;

    public IntArrayList() {
        this.values = EMPTY;
    }

    public IntArrayList(int initialCapacity) {
        this.values = initialCapacity == 0 ? EMPTY : new int[initialCapacity];
    }

    @Override
    public boolean add(int value) {
        if (size == values.length) grow(size + 1);
        values[size++] = value;
        return true;
    }

    @Override
    public int getInt(int index) {
        checkIndex(index);
        return values[index];
    }

    @Override
    public int indexOf(int value) {
        for (int i = 0; i < size; i++) {
            if (values[i] == value) return i;
        }
        return -1;
    }

    @Override
    public boolean contains(int value) {
        return indexOf(value) >= 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            private int cursor;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public Integer next() {
                if (cursor >= size) throw new NoSuchElementException();
                return values[cursor++];
            }
        };
    }

    private void grow(int required) {
        // 1.5x, floored at 8: the same shape as ArrayList's, so a list built one element at a time
        // costs amortised O(1) rather than a copy per add.
        int capacity = Math.max(required, Math.max(8, values.length + (values.length >> 1)));
        values = Arrays.copyOf(values, capacity);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) text.append(", ");
            text.append(values[i]);
        }
        return text.append(']').toString();
    }
}
