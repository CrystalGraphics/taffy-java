package dev.vfyjxf.taffy.collection;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * CrystalGUI: {@link FloatList} over a plain {@code float[]}, replacing fastutil's
 * {@code FloatArrayList}.
 *
 * @see dev.vfyjxf.taffy.collection
 */
public final class FloatArrayList implements FloatList {

    private static final float[] EMPTY = new float[0];

    private float[] values;
    private int size;

    public FloatArrayList() {
        this.values = EMPTY;
    }

    public FloatArrayList(int initialCapacity) {
        this.values = initialCapacity == 0 ? EMPTY : new float[initialCapacity];
    }

    @Override
    public boolean add(float value) {
        if (size == values.length) grow(size + 1);
        values[size++] = value;
        return true;
    }

    @Override
    public float getFloat(int index) {
        checkIndex(index);
        return values[index];
    }

    @Override
    public float set(int index, float value) {
        checkIndex(index);
        float previous = values[index];
        values[index] = value;
        return previous;
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
    public Iterator<Float> iterator() {
        return new Iterator<Float>() {
            private int cursor;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public Float next() {
                if (cursor >= size) throw new NoSuchElementException();
                return values[cursor++];
            }
        };
    }

    private void grow(int required) {
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
