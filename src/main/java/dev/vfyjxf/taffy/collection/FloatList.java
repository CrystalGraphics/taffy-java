package dev.vfyjxf.taffy.collection;

/**
 * CrystalGUI: a growable list of {@code float}, replacing
 * {@code it.unimi.dsi.fastutil.floats.FloatList}.
 *
 * <p>{@code Iterable<Float>}, so an enhanced-for over it unboxes — which is what every one of Taffy's
 * call sites does. @see dev.vfyjxf.taffy.collection</p>
 */
public interface FloatList extends Iterable<Float> {

    /** Appends to the end. @return true, always — the signature upstream's call sites expect */
    boolean add(float value);

    float getFloat(int index);

    /** @return the value that was there before */
    float set(int index, float value);

    int size();

    boolean isEmpty();
}
